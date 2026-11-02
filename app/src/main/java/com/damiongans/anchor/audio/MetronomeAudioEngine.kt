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

    private val isRunning = AtomicBoolean(false)
    private var audioThread: Thread? = null
    private var audioTrack: AudioTrack? = null

    private val deviceCallback = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        object : AudioDeviceCallback() {
            override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>?) {
                detectCurrentAudioDevice()
            }
            override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>?) {
                detectCurrentAudioDevice()
            }
        }
    } else null

    init {
        detectCurrentAudioDevice()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && deviceCallback != null) {
            audioManager.registerAudioDeviceCallback(deviceCallback, mainHandler)
        }
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
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
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
    }

    /**
     * Discovers Android media outputs and publishes the active route. List normalization
     * (Built-in Speaker always present, ordering) and fallback priority are shared via
     * [AudioRouteSelector].
     */
    override fun detectCurrentAudioDevice() {
        val discovered = mutableListOf<AudioOutputDevice>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
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
        }
        val sortedDevices = AudioRouteSelector.normalize(discovered)

        val currentPrefId = preferredDeviceId
        if (currentPrefId != null && sortedDevices.none { it.id == currentPrefId }) {
            // Previously selected device was disconnected; revert to automatic default routing
            preferredDeviceId = null
        }

        val routedDevice: AudioOutputDevice? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            try {
                audioTrack?.routedDevice
                    ?.takeIf { isMediaOutputSink(it.type) }
                    ?.let { routed ->
                        val (rType, rName) = classifyDevice(routed)
                        AudioOutputDevice(routed.id, rType, rName)
                    }
            } catch (_: Exception) {
                null
            }
        } else null

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
     * Starts continuous low-latency audio playback on the highest priority audio thread.
     */
    @Synchronized
    override fun start() {
        if (isRunning.get()) return

        detectCurrentAudioDevice()
        acquireAudioWakeLock()

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

        val track = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
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

        applyPreferredDeviceToTrack(track, preferredDeviceId)
        audioTrack = track
        isRunning.set(true)

        audioThread = Thread {
            // Linux kernel urgent audio scheduling (-19)
            Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO)
            runAudioLoop(track)
        }.apply {
            name = "MetronomeFastAudioThread"
            start()
        }
    }

    /**
     * Stops metronome playback immediately.
     */
    @Synchronized
    override fun stop() {
        if (!isRunning.getAndSet(false)) return

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
        releaseAudioWakeLock()
    }

    /**
     * Releases audio callbacks and resources.
     */
    @Synchronized
    override fun release() {
        stop()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && deviceCallback != null) {
            try {
                audioManager.unregisterAudioDeviceCallback(deviceCallback)
            } catch (_: Exception) {}
        }
    }

    private fun shouldRun(): Boolean = isRunning.get() && !Thread.currentThread().isInterrupted

    /**
     * Android I/O loop: writes the shared sequencer's warmup dither and click chunks to the
     * `AudioTrack` and feeds the hardware playhead back for playhead-anchored dispatch.
     */
    private fun runAudioLoop(track: AudioTrack) {
        val chunkFrames = bufferChunkFrames
        val chunk = ShortArray(chunkFrames)

        // Warmup: 2 pre-prime chunks + >= 55ms of -90 dBFS (+/-1 LSB) dither so AudioFlinger's
        // 20ms ramp-in and the DAC's 35ms wake-up complete before sample 0 of Beat 1.
        val warmupPostPlayChunks = MetronomeSequencer.warmupChunkCount(sampleRate, chunkFrames, minimumChunks = 8)
        val plannedWarmupFrames = (2 + warmupPostPlayChunks).toLong() * chunkFrames

        syncCompensatorSettings()
        sequencer.prepare(sampleRate, plannedWarmupFrames)

        val headCounter = WrappingFrameCounter()
        val playhead = PlayheadEstimator(sampleRate, maxInterpolationFrames = chunkFrames * 2L)
        playhead.reset(SystemClock.elapsedRealtimeNanos())

        fun readPlayheadFrame(): Long {
            val rawHead = try {
                track.playbackHeadPosition.toLong()
            } catch (_: Exception) {
                0L
            }
            return playhead.estimate(
                hardwareFrames = headCounter.unwrap(rawHead),
                nowNanos = SystemClock.elapsedRealtimeNanos(),
                totalWritten = sequencer.totalFramesWritten
            )
        }

        // 1. Hardware mixer & DAC warmup
        if (shouldRun()) {
            try {
                MetronomeSequencer.fillWarmupDither(chunk, chunkFrames)
                // Pre-fill 2 chunks before play() to satisfy the MODE_STREAM start threshold
                repeat(2) {
                    val written = track.write(chunk, 0, chunkFrames)
                    if (written > 0) sequencer.advanceWarmup(written)
                }

                track.play()
                playhead.reset(SystemClock.elapsedRealtimeNanos())

                for (w in 0 until warmupPostPlayChunks) {
                    if (!shouldRun()) break
                    val written = track.write(chunk, 0, chunkFrames)
                    if (written < 0) return
                    sequencer.advanceWarmup(written)
                    // Dispatch early (negative-offset) Beat 1 triggers due during warmup
                    sequencer.processPlayhead(readPlayheadFrame(), emitPhase = false)
                }
            } catch (_: Exception) {
                return
            }
        }

        if (!shouldRun()) return

        // 2. Anchor Beat 1 (seqId = 0) at the exact post-warmup frame
        sequencer.startFirstBeat()

        // 3. Continuous real-time synthesis & playhead-synchronized dispatch
        while (shouldRun()) {
            sequencer.renderChunk(chunk, chunkFrames)
            if (!isRunning.get()) break
            val written = try {
                track.write(chunk, 0, chunkFrames)
            } catch (_: Exception) {
                -1
            }
            if (written < 0) break
            sequencer.processPlayhead(readPlayheadFrame())
        }
    }

    companion object {
        private fun nativeSampleRate(context: Context): Int {
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            return audioManager?.getProperty(AudioManager.PROPERTY_OUTPUT_SAMPLE_RATE)?.toIntOrNull() ?: 48000
        }
    }
}
