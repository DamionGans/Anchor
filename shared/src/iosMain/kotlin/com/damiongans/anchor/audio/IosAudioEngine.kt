package com.damiongans.anchor.audio

import com.damiongans.anchor.model.AudioDeviceType
import com.damiongans.anchor.model.AudioOutputDevice
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.get
import kotlinx.cinterop.set
import platform.AVFAudio.*
import platform.Foundation.*
import platform.darwin.NSObjectProtocol
import kotlin.concurrent.Volatile
import kotlin.time.TimeSource

/**
 * iOS platform audio engine built on `AVAudioEngine` + `AVAudioPlayerNode`.
 *
 * Mirrors the Android engine: all synthesis, warmup, sequencing, and playhead-anchored delay
 * compensation come from the shared [BaseMetronomeAudioEngine] / [MetronomeSequencer]. This class
 * only owns the iOS I/O: the `AVAudioSession` (playback category, background audio), a
 * high-priority loop thread that keeps a small queue of rendered PCM buffers scheduled on the
 * player node, and the hardware playhead (player-node sample time).
 */
@OptIn(ExperimentalForeignApi::class)
class IosAudioEngine : BaseMetronomeAudioEngine(currentSessionSampleRate()) {

    private val session = AVAudioSession.sharedInstance()
    private val clockOrigin = TimeSource.Monotonic.markNow()

    private var engine: AVAudioEngine? = null
    private var player: AVAudioPlayerNode? = null

    @Volatile private var running = false
    @Volatile private var loopActive = false

    private var routeObserver: NSObjectProtocol? = null
    private var configurationObserver: NSObjectProtocol? = null

    init {
        configureSession()
        detectCurrentAudioDevice()
        val center = NSNotificationCenter.defaultCenter
        routeObserver = center.addObserverForName(
            name = AVAudioSessionRouteChangeNotification,
            `object` = null,
            queue = NSOperationQueue.mainQueue
        ) { _ ->
            detectCurrentAudioDevice()
        }
        // AVAudioEngine stops itself when the hardware configuration changes (e.g. headphones
        // unplugged); restart playback on the new route.
        configurationObserver = center.addObserverForName(
            name = AVAudioEngineConfigurationChangeNotification,
            `object` = null,
            queue = NSOperationQueue.mainQueue
        ) { _ ->
            if (running) {
                stop()
                start()
            }
        }
    }

    private fun configureSession() {
        session.setCategory(AVAudioSessionCategoryPlayback, null)
        session.setPreferredIOBufferDuration(PREFERRED_IO_BUFFER_SECONDS, null)
    }

    /**
     * iOS chooses the media route itself (Control Center / AirPlay picker), so explicit
     * selection only refreshes the reported route.
     */
    override fun selectAudioOutputDevice(deviceId: Int?) {
        detectCurrentAudioDevice()
    }

    override fun detectCurrentAudioDevice() {
        val discovered = mutableListOf<AudioOutputDevice>()
        var routed: AudioOutputDevice? = null
        session.currentRoute.outputs.forEachIndexed { index, item ->
            val port = item as? AVAudioSessionPortDescription ?: return@forEachIndexed
            val type = classifyPort(port.portType)
            val device = if (type == AudioDeviceType.SPEAKER) {
                AudioRouteSelector.BUILT_IN_SPEAKER
            } else {
                AudioOutputDevice(id = index + 1, deviceType = type, name = port.portName)
            }
            discovered.add(device)
            if (routed == null) routed = device
        }

        val sorted = AudioRouteSelector.normalize(discovered)
        val active = AudioRouteSelector.selectActive(sorted, preferredDeviceId = null, routedDevice = routed)
        if (!running) {
            sampleRate = session.sampleRate.toInt()
        }
        val bufferFrames = (session.IOBufferDuration * session.sampleRate).toInt().coerceAtLeast(1)
        publishAudioRoute(active, sorted, bufferFrames, isProAudioSupported = false)
    }

    private fun classifyPort(portType: String?): AudioDeviceType = when (portType) {
        AVAudioSessionPortBuiltInSpeaker, AVAudioSessionPortBuiltInReceiver -> AudioDeviceType.SPEAKER
        AVAudioSessionPortHeadphones, AVAudioSessionPortLineOut -> AudioDeviceType.WIRED_HEADPHONES
        AVAudioSessionPortUSBAudio -> AudioDeviceType.USB_AUDIO
        AVAudioSessionPortBluetoothA2DP, AVAudioSessionPortBluetoothLE, AVAudioSessionPortBluetoothHFP -> AudioDeviceType.BLUETOOTH
        else -> AudioDeviceType.OTHER
    }

    override fun start() {
        if (running) return

        session.setActive(true, null)
        detectCurrentAudioDevice()

        val sr = sampleRate
        val format = AVAudioFormat(standardFormatWithSampleRate = sr.toDouble(), channels = 1u)
        val audioEngine = AVAudioEngine()
        val node = AVAudioPlayerNode()
        audioEngine.attachNode(node)
        audioEngine.connect(node, to = audioEngine.mainMixerNode, format = format)
        audioEngine.prepare()
        if (!audioEngine.startAndReturnError(null)) {
            return
        }
        engine = audioEngine
        player = node

        syncCompensatorSettings()
        val warmupChunks = MetronomeSequencer.warmupChunkCount(sr, CHUNK_FRAMES)
        sequencer.prepare(sr, warmupChunks.toLong() * CHUNK_FRAMES)

        running = true
        loopActive = true
        val thread = NSThread(block = { runLoop(node, format, warmupChunks) })
        thread.name = "AnchorAudioLoop"
        thread.threadPriority = 1.0
        thread.start()
    }

    override fun stop() {
        if (!running) return
        running = false

        var waits = 0
        while (loopActive && waits < 250) {
            NSThread.sleepForTimeInterval(0.001)
            waits++
        }

        player?.stop()
        engine?.stop()
        player = null
        engine = null
    }

    override fun release() {
        stop()
        val center = NSNotificationCenter.defaultCenter
        routeObserver?.let { center.removeObserver(it) }
        configurationObserver?.let { center.removeObserver(it) }
        routeObserver = null
        configurationObserver = null
        session.setActive(false, null)
    }

    private fun nowNanos(): Long = clockOrigin.elapsedNow().inWholeNanoseconds

    /**
     * iOS I/O loop: queues warmup + click chunks on the player node, keeps [LOOKAHEAD_CHUNKS]
     * chunks scheduled ahead of the playhead, and feeds the hardware playhead back to the shared
     * sequencer for playhead-anchored visual / haptic dispatch.
     */
    private fun runLoop(node: AVAudioPlayerNode, format: AVAudioFormat, warmupChunks: Int) {
        try {
            val chunk = ShortArray(CHUNK_FRAMES)
            val estimator = PlayheadEstimator(sequencer.sampleRate, maxInterpolationFrames = CHUNK_FRAMES * 4L)

            // 1. Hardware warmup: >= 55 ms of +/-1 LSB dither before Beat 1
            MetronomeSequencer.fillWarmupDither(chunk, CHUNK_FRAMES)
            repeat(warmupChunks) {
                scheduleChunk(node, format, chunk)
                sequencer.advanceWarmup(CHUNK_FRAMES)
            }

            // 2. Anchor Beat 1 right after the warmup, then start the player clock
            sequencer.startFirstBeat()
            node.play()
            estimator.reset(nowNanos())

            val lookaheadFrames = CHUNK_FRAMES.toLong() * LOOKAHEAD_CHUNKS
            var emitPhase = false

            // 3. Render ahead + playhead-synchronized dispatch
            while (running) {
                var playhead = estimator.estimate(readPlayerFrames(node), nowNanos(), sequencer.totalFramesWritten)
                while (running && sequencer.totalFramesWritten - playhead < lookaheadFrames) {
                    sequencer.renderChunk(chunk, CHUNK_FRAMES)
                    scheduleChunk(node, format, chunk)
                    playhead = estimator.estimate(readPlayerFrames(node), nowNanos(), sequencer.totalFramesWritten)
                }
                // Beat events every iteration; pendulum phase at half rate (UI refresh budget).
                emitPhase = !emitPhase
                sequencer.processPlayhead(playhead, emitPhase = emitPhase)
                NSThread.sleepForTimeInterval(LOOP_INTERVAL_SECONDS)
            }
        } finally {
            loopActive = false
        }
    }

    private fun scheduleChunk(node: AVAudioPlayerNode, format: AVAudioFormat, pcm: ShortArray) {
        val buffer = AVAudioPCMBuffer(pCMFormat = format, frameCapacity = CHUNK_FRAMES.toUInt())
        buffer.frameLength = CHUNK_FRAMES.toUInt()
        val channel = buffer.floatChannelData?.get(0) ?: return
        for (i in 0 until CHUNK_FRAMES) {
            channel[i] = pcm[i] / 32768f
        }
        node.scheduleBuffer(buffer, completionHandler = null)
    }

    private fun readPlayerFrames(node: AVAudioPlayerNode): Long {
        val nodeTime = node.lastRenderTime ?: return 0L
        val playerTime = node.playerTimeForNodeTime(nodeTime) ?: return 0L
        return if (playerTime.sampleTimeValid) playerTime.sampleTime.coerceAtLeast(0L) else 0L
    }

    companion object {
        private const val CHUNK_FRAMES = 256
        private const val LOOKAHEAD_CHUNKS = 4
        private const val LOOP_INTERVAL_SECONDS = 0.002
        private const val PREFERRED_IO_BUFFER_SECONDS = 0.005

        private fun currentSessionSampleRate(): Int =
            AVAudioSession.sharedInstance().sampleRate.toInt().takeIf { it >= 8000 } ?: 48000
    }
}
