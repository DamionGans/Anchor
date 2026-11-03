package com.damiongans.anchor.audio

import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.os.SystemClock
import com.damiongans.anchor.model.AudioDeviceType
import com.damiongans.anchor.model.AudioOutputDevice
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Android platform audio engine: ultra low-latency PCM streaming through the `AudioTrack`
 * Fast Path.
 *
 * All platform-independent logic (click synthesis, sequencing, warmup, delay compensation,
 * route list normalization) lives in the shared [BaseMetronomeAudioEngine] /
 * [MetronomeSequencer]. This class only owns Android I/O: the `AudioTrack`, the
 * `THREAD_PRIORITY_URGENT_AUDIO` write thread, the hardware playhead
 * (`getPlaybackHeadPosition()`), device discovery/routing, and the partial wake lock.
 */
class MetronomeAudioEngine(private val context: Context) :
    BaseMetronomeAudioEngine(nativeSampleRate(context)) {

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val mainHandler = Handler(Looper.getMainLooper())

    var bufferChunkFrames: Int = getLowLatencyFramesPerBuffer()
        private set

    val isProAudioSupported: Boolean = context.packageManager.hasSystemFeature(PackageManager.FEATURE_AUDIO_PRO)
    val isLowLatencySupported: Boolean = context.packageManager.hasSystemFeature(PackageManager.FEATURE_AUDIO_LOW_LATENCY)

    @Volatile private var preferredDeviceId: Int? = null

    /** True while the AudioTrack + output thread exist (playing or in standby). */
    private val outputActive = AtomicBoolean(false)
    /** Metronome playback requested (start() .. stop()). Read by the output thread. */
    @Volatile private var playRequested = false
    /** Incremented by every start(), so a fast stop()+start() still restarts at Beat 1. */
    @Volatile private var playGeneration = 0
    /** Keep the output open between plays (app on screen) for an instant first beat. */
    @Volatile private var standbyRequested = false
    private var audioThread: Thread? = null
    @Volatile private var audioTrack: AudioTrack? = null

    private val deviceCallback = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>?) {
            detectCurrentAudioDevice()
        }
        override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>?) {
            detectCurrentAudioDevice()
        }
    }

    init {
        detectCurrentAudioDevice()
        audioManager.registerAudioDeviceCallback(deviceCallback, mainHandler)
    }

    private fun getLowLatencyFramesPerBuffer(): Int {
        val framesString = audioManager.getProperty(AudioManager.PROPERTY_OUTPUT_FRAMES_PER_BUFFER)
        val frames = framesString?.toIntOrNull() ?: 192
        // Optimal chunk size for Android Fast Mixer path (128 to 384 frames)
        return frames.coerceIn(128, 384)
    }

    override fun selectAudioOutputDevice(deviceId: Int?) {
        preferredDeviceId = deviceId
        audioTrack?.let { track ->
            applyPreferredDeviceToTrack(track, deviceId)
        }
        detectCurrentAudioDevice()
    }

    private fun applyPreferredDeviceToTrack(track: AudioTrack, targetId: Int?) {
        try {
            if (targetId == null) {
                track.preferredDevice = null
            } else {
                val rawDevices = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
                val matched = rawDevices.firstOrNull { it.id == targetId }
                    ?: if (targetId == AudioRouteSelector.SPEAKER_DEVICE_ID) {
                        rawDevices.firstOrNull { it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER }
                    } else null
                track.preferredDevice = matched
            }
        } catch (_: Exception) {
        }
    }

    /**
     * Discovers Android media outputs and publishes the active route. List normalization
     * (Built-in Speaker always present, ordering) and fallback priority are shared via
     * [AudioRouteSelector].
     */
    override fun detectCurrentAudioDevice() {
        val discovered = mutableListOf<AudioOutputDevice>()
        val rawDevices = try {
            audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
        } catch (_: Exception) {
            emptyArray()
        }
        for (dev in rawDevices) {
            if (!isMediaOutputSink(dev.type)) continue
            val (type, name) = classifyDevice(dev)
            discovered.add(AudioOutputDevice(id = dev.id, deviceType = type, name = name))
        }
        val sortedDevices = AudioRouteSelector.normalize(discovered)

        val currentPrefId = preferredDeviceId
        if (currentPrefId != null && sortedDevices.none { it.id == currentPrefId }) {
            // Previously selected device was disconnected; revert to automatic default routing
            preferredDeviceId = null
        }

        val routedDevice: AudioOutputDevice? = try {
            audioTrack?.routedDevice
                ?.takeIf { isMediaOutputSink(it.type) }
                ?.let { routed ->
                    val (rType, rName) = classifyDevice(routed)
                    AudioOutputDevice(routed.id, rType, rName)
                }
        } catch (_: Exception) {
            null
        }

        val activeDevice = AudioRouteSelector.selectActive(sortedDevices, preferredDeviceId, routedDevice)

        sampleRate = nativeSampleRate(context)
        bufferChunkFrames = getLowLatencyFramesPerBuffer()
        publishAudioRoute(activeDevice, sortedDevices, bufferChunkFrames, isProAudioSupported)
    }

    private fun isMediaOutputSink(type: Int): Boolean {
        if (type == AudioDeviceInfo.TYPE_BUILTIN_EARPIECE ||
            type == AudioDeviceInfo.TYPE_TELEPHONY
        ) {
            return false
        }
        return when (type) {
            AudioDeviceInfo.TYPE_BUILTIN_SPEAKER,
            AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
            AudioDeviceInfo.TYPE_WIRED_HEADSET,
            AudioDeviceInfo.TYPE_LINE_ANALOG,
            AudioDeviceInfo.TYPE_LINE_DIGITAL,
            AudioDeviceInfo.TYPE_AUX_LINE,
            AudioDeviceInfo.TYPE_USB_DEVICE,
            AudioDeviceInfo.TYPE_USB_HEADSET,
            AudioDeviceInfo.TYPE_USB_ACCESSORY,
            AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
            AudioDeviceInfo.TYPE_HDMI,
            AudioDeviceInfo.TYPE_HDMI_ARC,
            AudioDeviceInfo.TYPE_DOCK -> true
            else -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    type == AudioDeviceInfo.TYPE_BLE_HEADSET ||
                        type == AudioDeviceInfo.TYPE_BLE_SPEAKER ||
                        type == AudioDeviceInfo.TYPE_BLE_BROADCAST ||
                        type == AudioDeviceInfo.TYPE_HDMI_EARC
                } else {
                    false
                }
            }
        }
    }

    private fun classifyDevice(dev: AudioDeviceInfo): Pair<AudioDeviceType, String> {
        val productName = dev.productName?.toString()?.trim().orEmpty()
        return when (dev.type) {
            AudioDeviceInfo.TYPE_BUILTIN_SPEAKER -> {
                AudioDeviceType.SPEAKER to "Built-in Speaker"
            }
            AudioDeviceInfo.TYPE_USB_DEVICE,
            AudioDeviceInfo.TYPE_USB_HEADSET,
            AudioDeviceInfo.TYPE_USB_ACCESSORY -> {
                AudioDeviceType.USB_AUDIO to productName.ifEmpty { "USB Audio DAC" }
            }
            AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
            AudioDeviceInfo.TYPE_WIRED_HEADSET,
            AudioDeviceInfo.TYPE_LINE_ANALOG,
            AudioDeviceInfo.TYPE_LINE_DIGITAL,
            AudioDeviceInfo.TYPE_AUX_LINE -> {
                AudioDeviceType.WIRED_HEADPHONES to "Wired Headphones"
            }
            AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
            AudioDeviceInfo.TYPE_BLUETOOTH_SCO -> {
                AudioDeviceType.BLUETOOTH to productName.ifEmpty { "Bluetooth Audio" }
            }
            else -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                    (dev.type == AudioDeviceInfo.TYPE_BLE_HEADSET ||
                        dev.type == AudioDeviceInfo.TYPE_BLE_SPEAKER ||
                        dev.type == AudioDeviceInfo.TYPE_BLE_BROADCAST)
                ) {
                    AudioDeviceType.BLUETOOTH to productName.ifEmpty { "Bluetooth LE Audio" }
                } else {
                    AudioDeviceType.OTHER to productName.ifEmpty { "Audio Output" }
                }
            }
        }
    }

    private var wakeLock: android.os.PowerManager.WakeLock? = null

    private fun acquireAudioWakeLock() {
        try {
            if (wakeLock?.isHeld == true) return
            val pm = context.getSystemService(Context.POWER_SERVICE) as? android.os.PowerManager ?: return
            wakeLock = pm.newWakeLock(
                android.os.PowerManager.PARTIAL_WAKE_LOCK,
                "Anchor::AudioEngineWakeLock"
            ).apply {
                setReferenceCounted(false)
                acquire()
            }
        } catch (_: Exception) {
        }
    }

    private fun releaseAudioWakeLock() {
        try {
            if (wakeLock?.isHeld == true) {
                wakeLock?.release()
            }
        } catch (_: Exception) {
        } finally {
            wakeLock = null
        }
    }

    /**
     * Output standby: while enabled (the app is on screen), the `AudioTrack` stays open and keeps
     * the audio path awake with inaudible +/-1 LSB dither, so [start] can place Beat 1 in the very
     * next chunk instead of paying for track creation, HAL wake-up, and the cold-start warmup.
     * Disabling it closes the output unless the metronome is playing.
     */
    @Synchronized
    override fun setOutputStandby(enabled: Boolean) {
        standbyRequested = enabled
        if (enabled) {
            openOutputIfNeeded()
        } else if (!playRequested) {
            closeOutput()
        }
    }

    /**
     * Starts metronome playback. With the output in standby, Beat 1 is synthesized into the next
     * chunk; otherwise the output is opened cold and the hardware warmup runs first.
     */
    @Synchronized
    override fun start() {
        if (playRequested) return
        acquireAudioWakeLock()
        playGeneration++
        playRequested = true
        openOutputIfNeeded()
    }

    /**
     * Stops metronome playback. The output returns to standby when requested, otherwise it closes.
     */
    @Synchronized
    override fun stop() {
        if (!playRequested) return
        playRequested = false
        releaseAudioWakeLock()
        if (!standbyRequested) closeOutput()
    }

    /**
     * Releases audio callbacks and resources.
     */
    @Synchronized
    override fun release() {
        standbyRequested = false
        stop()
        closeOutput()
        try {
            audioManager.unregisterAudioDeviceCallback(deviceCallback)
        } catch (_: Exception) {}
    }

    private fun openOutputIfNeeded() {
        if (outputActive.get()) return

        detectCurrentAudioDevice()

        val minBufSize = AudioTrack.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )

        // Keep hardware ring buffer compact (at least minBufSize and 4 low-latency bursts)
        // to minimize write-ahead latency while preventing underruns.
        val bufferSizeBytes = maxOf(minBufSize, bufferChunkFrames * 4 * 2)

        val attributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
            .build()

        val format = AudioFormat.Builder()
            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
            .setSampleRate(sampleRate)
            .build()

        val track = try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                AudioTrack.Builder()
                    .setAudioAttributes(attributes)
                    .setAudioFormat(format)
                    .setBufferSizeInBytes(bufferSizeBytes)
                    .setPerformanceMode(AudioTrack.PERFORMANCE_MODE_LOW_LATENCY)
                    .setTransferMode(AudioTrack.MODE_STREAM)
                    .build()
            } else {
                @Suppress("DEPRECATION")
                AudioTrack(
                    AudioManager.STREAM_MUSIC,
                    sampleRate,
                    AudioFormat.CHANNEL_OUT_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    bufferSizeBytes,
                    AudioTrack.MODE_STREAM
                )
            }
        } catch (_: Exception) {
            return
        }

        applyPreferredDeviceToTrack(track, preferredDeviceId)
        audioTrack = track
        outputActive.set(true)

        audioThread = Thread {
            // Linux kernel urgent audio scheduling (-19)
            Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO)
            runOutputLoop(track)
        }.apply {
            name = "MetronomeFastAudioThread"
            start()
        }
    }

    private fun closeOutput() {
        if (!outputActive.getAndSet(false)) return

        audioThread?.interrupt()
        audioThread = null

        audioTrack?.let { track ->
            try {
                track.pause()
                track.flush()
                track.stop()
                track.release()
            } catch (_: Exception) {}
        }
        audioTrack = null
    }

    private fun outputAlive(): Boolean = outputActive.get() && !Thread.currentThread().isInterrupted

    /**
     * Android I/O loop for the lifetime of one `AudioTrack`. Writes inaudible dither while idle
     * (standby), and the shared sequencer's clicks while playing; the hardware playhead is fed back
     * for playhead-anchored dispatch.
     *
     * Frame bookkeeping: [outputFrames] counts every frame written since the track was created,
     * which is the same timeline as `getPlaybackHeadPosition()`. When playback starts, the sequencer
     * is aligned to it (`prepare(firstBeatFrame)` + `advanceWarmup(outputFrames)`).
     */
    private fun runOutputLoop(track: AudioTrack) {
        val chunkFrames = bufferChunkFrames
        val chunk = ShortArray(chunkFrames)
        val dither = ShortArray(chunkFrames).also { MetronomeSequencer.fillWarmupDither(it, chunkFrames) }

        // Cold-start warmup target: 2 pre-prime chunks + >= 55 ms of -90 dBFS (+/-1 LSB) dither so
        // AudioFlinger's ramp-in and the DAC's wake-up complete before sample 0 of Beat 1. In
        // standby the output has already been running longer than this, so no extra warmup runs.
        val warmupPostPlayChunks = MetronomeSequencer.warmupChunkCount(sampleRate, chunkFrames, minimumChunks = 8)
        val warmupTargetFrames = (2 + warmupPostPlayChunks).toLong() * chunkFrames

        val headCounter = WrappingFrameCounter()
        val playhead = PlayheadEstimator(sampleRate, maxInterpolationFrames = chunkFrames * 2L)
        var outputFrames = 0L
        var playing = false
        var playingGeneration = -1

        fun readPlayheadFrame(): Long {
            val rawHead = try {
                track.playbackHeadPosition.toLong()
            } catch (_: Exception) {
                0L
            }
            return playhead.estimate(
                hardwareFrames = headCounter.unwrap(rawHead),
                nowNanos = SystemClock.elapsedRealtimeNanos(),
                totalWritten = outputFrames
            )
        }

        fun writeChunk(data: ShortArray): Boolean {
            val written = try {
                track.write(data, 0, chunkFrames)
            } catch (_: Exception) {
                -1
            }
            if (written > 0) outputFrames += written
            return written >= 0
        }

        try {
            // Pre-fill 2 chunks before play() to satisfy the MODE_STREAM start threshold
            repeat(2) { if (!writeChunk(dither)) return }
            track.play()
            playhead.reset(SystemClock.elapsedRealtimeNanos())

            while (outputAlive()) {
                val generation = playGeneration
                if (playRequested && (!playing || generation != playingGeneration)) {
                    // Begin playback: align the sequencer to the output timeline and run only the
                    // part of the warmup the output has not already covered.
                    val extraWarmupChunks = ((warmupTargetFrames - outputFrames).coerceAtLeast(0L) + chunkFrames - 1) / chunkFrames
                    syncCompensatorSettings()
                    sequencer.prepare(sampleRate, outputFrames + extraWarmupChunks * chunkFrames)
                    sequencer.alignToOutputFrame(outputFrames)
                    var warmupOk = true
                    for (w in 0 until extraWarmupChunks) {
                        if (!outputAlive() || !writeChunk(dither)) return
                        if (!playRequested) {
                            warmupOk = false
                            break
                        }
                        sequencer.advanceWarmup(chunkFrames)
                        // Dispatch early (negative-offset) Beat 1 triggers due during warmup
                        sequencer.processPlayhead(readPlayheadFrame(), emitPhase = false)
                    }
                    if (!warmupOk) {
                        playing = false
                        continue
                    }
                    // Anchor Beat 1 (seqId = 0) at the exact next frame
                    sequencer.startFirstBeat()
                    playing = true
                    playingGeneration = generation
                } else if (!playRequested && playing) {
                    playing = false
                }

                if (playing) {
                    sequencer.renderChunk(chunk, chunkFrames)
                    if (!writeChunk(chunk)) break
                    sequencer.processPlayhead(readPlayheadFrame())
                } else {
                    // Standby: keep the audio path awake with inaudible dither
                    if (!writeChunk(dither)) break
                    // Keep sampling the 32-bit head so the wrap counter stays continuous
                    readPlayheadFrame()
                }
            }
        } catch (_: Exception) {
            // Track released while writing; closeOutput() owns the state.
        }
    }

    companion object {
        private fun nativeSampleRate(context: Context): Int {
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            return audioManager?.getProperty(AudioManager.PROPERTY_OUTPUT_SAMPLE_RATE)?.toIntOrNull() ?: 48000
        }
    }
}
