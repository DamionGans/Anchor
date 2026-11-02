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
import kotlin.time.Duration.Companion.milliseconds
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

    /** True while the AVAudioEngine + loop thread exist (playing or in standby). */
    @Volatile private var outputActive = false
    @Volatile private var loopActive = false
    /** Metronome playback requested (start() .. stop()). Read by the loop thread. */
    @Volatile private var playRequested = false
    /** Keep the engine running between plays (app in the foreground) for an instant first beat. */
    @Volatile private var standbyRequested = false
    /** Incremented by every start(), so a fast stop()+start() still restarts at Beat 1. */
    @Volatile private var playGeneration = 0
    private var outputOpenedAt = TimeSource.Monotonic.markNow()
    private var sessionMixable: Boolean? = null

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
            if (outputActive) {
                closeOutput()
                if (playRequested || standbyRequested) openOutput()
            }
        }
    }

    private fun configureSession() {
        setSessionMixable(false)
        session.setPreferredIOBufferDuration(PREFERRED_IO_BUFFER_SECONDS, null)
    }

    /**
     * Standby (idle, app in the foreground) mixes with other apps so merely opening Anchor never
     * interrupts their audio; playback uses the regular non-mixing category (Now Playing owner).
     */
    private fun setSessionMixable(mixable: Boolean) {
        if (sessionMixable == mixable) return
        val options: AVAudioSessionCategoryOptions = if (mixable) AVAudioSessionCategoryOptionMixWithOthers else 0uL
        session.setCategory(AVAudioSessionCategoryPlayback, options, null)
        sessionMixable = mixable
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
        if (!outputActive) {
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

    /**
     * Output standby: while enabled (app in the foreground) the `AVAudioEngine` keeps running with
     * the player node stopped, so the audio hardware stays awake and [start] only has to queue
     * Beat 1 and start the player node, instead of building and starting a new engine and running
     * the cold-start warmup. Disabling it closes the engine unless the metronome is playing.
     */
    override fun setOutputStandby(enabled: Boolean) {
        standbyRequested = enabled
        if (enabled) {
            if (!outputActive) {
                setSessionMixable(true)
                openOutput()
            }
        } else if (!playRequested) {
            closeOutput()
        }
    }

    override fun start() {
        if (playRequested) return
        setSessionMixable(false)
        playGeneration++
        playRequested = true
        if (!outputActive) openOutput()
        if (!outputActive) playRequested = false // no usable output
    }

    override fun stop() {
        if (!playRequested) return
        playRequested = false
        if (!standbyRequested) closeOutput()
        // In standby the loop stops the player node (flushing queued clicks) within one iteration.
    }

    override fun release() {
        standbyRequested = false
        playRequested = false
        closeOutput()
        val center = NSNotificationCenter.defaultCenter
        routeObserver?.let { center.removeObserver(it) }
        configurationObserver?.let { center.removeObserver(it) }
        routeObserver = null
        configurationObserver = null
        session.setActive(false, null)
    }

    private fun openOutput() {
        if (outputActive) return

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
        outputOpenedAt = TimeSource.Monotonic.markNow()

        outputActive = true
        loopActive = true
        val thread = NSThread(block = { runLoop(node, format, sr) })
        thread.name = "AnchorAudioLoop"
        thread.threadPriority = 1.0
        thread.start()
    }

    private fun closeOutput() {
        if (!outputActive) return
        outputActive = false

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

    private fun nowNanos(): Long = clockOrigin.elapsedNow().inWholeNanoseconds

    /**
     * iOS I/O loop for the lifetime of one `AVAudioEngine`. Idle (standby): the engine runs with
     * the player node stopped. Playing: queues warmup (cold start only) + click chunks on the
     * player node, keeps [LOOKAHEAD_CHUNKS] chunks scheduled ahead of the playhead, and feeds the
     * hardware playhead back to the shared sequencer for playhead-anchored visual / haptic dispatch.
     * Every playback run restarts the player node, so its sample time starts at 0 together with
     * the sequencer timeline.
     */
    private fun runLoop(node: AVAudioPlayerNode, format: AVAudioFormat, sr: Int) {
        try {
            val chunk = ShortArray(CHUNK_FRAMES)
            val dither = ShortArray(CHUNK_FRAMES).also { MetronomeSequencer.fillWarmupDither(it, CHUNK_FRAMES) }
            val warmupChunks = MetronomeSequencer.warmupChunkCount(sr, CHUNK_FRAMES)
            val warmupDuration = (warmupChunks.toLong() * CHUNK_FRAMES * 1000L / sr).milliseconds
            val estimator = PlayheadEstimator(sr, maxInterpolationFrames = CHUNK_FRAMES * 4L)
            val lookaheadFrames = CHUNK_FRAMES.toLong() * LOOKAHEAD_CHUNKS
            var playing = false
            var playingGeneration = -1
            var emitPhase = false

            while (outputActive) {
                val generation = playGeneration
                if (playRequested && (!playing || generation != playingGeneration)) {
                    if (playing) node.stop() // fast restart: flush the previous run

                    // 1. Cold start only: >= 55 ms of +/-1 LSB dither while the hardware wakes up.
                    //    In standby the engine has been running longer than that already.
                    val coldChunks = if (outputOpenedAt.elapsedNow() < warmupDuration) warmupChunks else 0
                    syncCompensatorSettings()
                    sequencer.prepare(sr, coldChunks.toLong() * CHUNK_FRAMES)
                    repeat(coldChunks) {
                        scheduleChunk(node, format, dither)
                        sequencer.advanceWarmup(CHUNK_FRAMES)
                    }

                    // 2. Anchor Beat 1, queue it, then start the player clock (never starts empty)
                    sequencer.startFirstBeat()
                    sequencer.renderChunk(chunk, CHUNK_FRAMES)
                    scheduleChunk(node, format, chunk)
                    node.play()
                    estimator.reset(nowNanos())
                    playing = true
                    playingGeneration = generation
                } else if (!playRequested && playing) {
                    // Pause in standby: drop queued clicks at once; the engine keeps running.
                    node.stop()
                    playing = false
                }

                if (playing) {
                    // 3. Render ahead + playhead-synchronized dispatch
                    var playhead = estimator.estimate(readPlayerFrames(node), nowNanos(), sequencer.totalFramesWritten)
                    while (outputActive && playRequested && sequencer.totalFramesWritten - playhead < lookaheadFrames) {
                        sequencer.renderChunk(chunk, CHUNK_FRAMES)
                        scheduleChunk(node, format, chunk)
                        playhead = estimator.estimate(readPlayerFrames(node), nowNanos(), sequencer.totalFramesWritten)
                    }
                    // Beat events every iteration; pendulum phase at half rate (UI refresh budget).
                    emitPhase = !emitPhase
                    sequencer.processPlayhead(playhead, emitPhase = emitPhase)
                }
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
