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
import com.damiongans.anchor.domain.DelayCompensationEngine
import com.damiongans.anchor.model.AudioDeviceType
import com.damiongans.anchor.model.AudioOutputDevice
import com.damiongans.anchor.model.AudioOutputInfo
import com.damiongans.anchor.model.BeatAccent
import com.damiongans.anchor.model.SoundSet
import com.damiongans.anchor.model.Subdivision
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.sin

/**
 * Ultra low-latency PCM streaming metronome engine using Android AudioTrack Fast Path.
 * Uses [DelayCompensationEngine] anchored to the hardware `AudioTrack.getPlaybackHeadPosition()`
 * crystal clock so visual beats, screen flash, pendulum phase, and haptics are locked
 * to the actual audible output across all Android vendors.
 */
class MetronomeAudioEngine(private val context: Context) : IAudioEngine {

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val mainHandler = Handler(Looper.getMainLooper())

    var sampleRate: Int = getNativeSampleRate()
        private set
    var bufferChunkFrames: Int = getLowLatencyFramesPerBuffer()
        private set

    val isProAudioSupported: Boolean = context.packageManager.hasSystemFeature(PackageManager.FEATURE_AUDIO_PRO)
    val isLowLatencySupported: Boolean = context.packageManager.hasSystemFeature(PackageManager.FEATURE_AUDIO_LOW_LATENCY)

    val delayCompensator = DelayCompensationEngine(sampleRate = sampleRate)

    @Volatile private var preferredDeviceId: Int? = null

    @Volatile override var currentAudioInfo: AudioOutputInfo = AudioOutputInfo(
        deviceType = AudioDeviceType.SPEAKER,
        deviceName = "Built-in Speaker",
        sampleRate = sampleRate,
        bufferFrames = bufferChunkFrames,
        isProAudioSupported = isProAudioSupported,
        selectedDeviceId = -1,
        availableDevices = listOf(
            AudioOutputDevice(id = -1, deviceType = AudioDeviceType.SPEAKER, name = "Built-in Speaker")
        )
    )

    @Volatile override var bpm: Double = 120.0
        set(value) {
            field = value.coerceIn(20.0, 320.0)
        }

    @Volatile override var totalBeats: Int = 4
    @Volatile override var currentSubdivision: Subdivision = Subdivision.QUARTER
    @Volatile override var accents: List<BeatAccent> = listOf(
        BeatAccent.ACCENT,
        BeatAccent.REGULAR,
        BeatAccent.REGULAR,
        BeatAccent.REGULAR
    )
    @Volatile override var currentSoundSet: SoundSet = SoundSet.WOODBLOCK
        set(value) {
            field = value
            regenerateSounds()
        }

    @Volatile override var pitchMultiplier: Float = 1.0f
        set(value) {
            field = value.coerceIn(0.5f, 2.0f)
            regenerateSounds()
        }

    @Volatile override var visualDelayMs: Int = 0
        set(value) {
            val clamped = value.coerceIn(
                DelayCompensationEngine.MIN_VISUAL_DELAY_MS,
                DelayCompensationEngine.MAX_VISUAL_DELAY_MS
            )
            field = clamped
            delayCompensator.visualDelayMs = clamped
        }

    @Volatile override var vibrationDelayMs: Int = 0
        set(value) {
            val clamped = value.coerceIn(
                DelayCompensationEngine.MIN_VIBRATION_DELAY_MS,
                DelayCompensationEngine.MAX_VIBRATION_DELAY_MS
            )
            field = clamped
            delayCompensator.vibrationDelayMs = clamped
        }

    @Volatile override var manualBluetoothCompensationMs: Int = 0
        set(value) {
            val clamped = value.coerceIn(
                DelayCompensationEngine.MIN_BLUETOOTH_OFFSET_MS,
                DelayCompensationEngine.MAX_BLUETOOTH_OFFSET_MS
            )
            field = clamped
            delayCompensator.bluetoothOffsetMs = clamped
        }

    private val isRunning = AtomicBoolean(false)
    private var audioThread: Thread? = null
    private var audioTrack: AudioTrack? = null

    // Pre-computed click buffers (16-bit PCM mono)
    private var accentWave: ShortArray = ShortArray(0)
    private var regularWave: ShortArray = ShortArray(0)
    private var subWave: ShortArray = ShortArray(0)

    // Listeners for UI animation & haptics
    override var onBeatTick: ((beatIndex: Int, isAccent: Boolean, subdivisionIndex: Int) -> Unit)? = null
    override var onVibrationTick: ((beatIndex: Int, isAccent: Boolean) -> Unit)? = null
    override var onPhaseUpdate: ((beatIndex: Int, phase: Float) -> Unit)? = null
    override var onBarCompleted: (() -> Unit)? = null
    override var onAudioDeviceChanged: ((AudioOutputInfo) -> Unit)? = null

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
        regenerateSounds()
        detectCurrentAudioDevice()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && deviceCallback != null) {
            audioManager.registerAudioDeviceCallback(deviceCallback, mainHandler)
        }
    }

    private fun getNativeSampleRate(): Int {
        val rateString = audioManager.getProperty(AudioManager.PROPERTY_OUTPUT_SAMPLE_RATE)
        return rateString?.toIntOrNull() ?: 48000
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
                        ?: if (targetId == -1) {
                            rawDevices.firstOrNull { it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER }
                        } else null
                    track.preferredDevice = matched
                }
            } catch (_: Exception) {
            }
        }
    }

    /**
     * Inspects active routing devices (Speaker, USB DAC, Bluetooth, Wired),
     * populates all available outputs (always including Built-in Speaker even when it is the
     * sole output), and updates the delay compensation engine without applying hidden magic offsets.
     */
    override fun detectCurrentAudioDevice() {
        val discoveredDevices = mutableListOf<AudioOutputDevice>()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val rawDevices = try {
                audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
            } catch (_: Exception) {
                emptyArray()
            }

            for (dev in rawDevices) {
                if (!isMediaOutputSink(dev.type)) continue
                val (type, name) = classifyDevice(dev)
                val alreadyListed = discoveredDevices.any {
                    it.deviceType == type && it.name.equals(name, ignoreCase = true)
                }
                if (!alreadyListed) {
                    discoveredDevices.add(
                        AudioOutputDevice(
                            id = dev.id,
                            deviceType = type,
                            name = name
                        )
                    )
                }
            }
        }

        // Guarantee Built-in Speaker is always present so output selection always shows all
        // available outputs even when only one output (Speaker) exists.
        if (discoveredDevices.none { it.deviceType == AudioDeviceType.SPEAKER }) {
            discoveredDevices.add(
                0,
                AudioOutputDevice(
                    id = -1,
                    deviceType = AudioDeviceType.SPEAKER,
                    name = "Built-in Speaker"
                )
            )
        }

        // Order: Speaker first, then Wired, USB, Bluetooth, Other
        val sortedDevices = discoveredDevices.sortedBy {
            when (it.deviceType) {
                AudioDeviceType.SPEAKER -> 0
                AudioDeviceType.WIRED_HEADPHONES -> 1
                AudioDeviceType.USB_AUDIO -> 2
                AudioDeviceType.BLUETOOTH -> 3
                AudioDeviceType.OTHER -> 4
            }
        }

        val currentPrefId = preferredDeviceId
        val selectedByPref = if (currentPrefId != null) {
            sortedDevices.firstOrNull { it.id == currentPrefId }
        } else null

        if (currentPrefId != null && selectedByPref == null) {
            // Previously selected device was disconnected; revert to automatic default routing
            preferredDeviceId = null
        }

        val activeDevice: AudioOutputDevice = selectedByPref ?: run {
            val routed = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                try {
                    audioTrack?.routedDevice
                } catch (_: Exception) {
                    null
                }
            } else null

            if (routed != null && isMediaOutputSink(routed.type)) {
                val (rType, rName) = classifyDevice(routed)
                sortedDevices.firstOrNull { it.id == routed.id }
                    ?: sortedDevices.firstOrNull { it.deviceType == rType }
                    ?: AudioOutputDevice(routed.id, rType, rName)
            } else {
                // Default Android media routing priority: USB > Wired > Bluetooth > Speaker
                sortedDevices.firstOrNull { it.deviceType == AudioDeviceType.USB_AUDIO }
                    ?: sortedDevices.firstOrNull { it.deviceType == AudioDeviceType.WIRED_HEADPHONES }
                    ?: sortedDevices.firstOrNull { it.deviceType == AudioDeviceType.BLUETOOTH }
                    ?: sortedDevices.first()
            }
        }

        sampleRate = getNativeSampleRate()
        bufferChunkFrames = getLowLatencyFramesPerBuffer()

        val isBt = activeDevice.deviceType == AudioDeviceType.BLUETOOTH
        delayCompensator.sampleRate = sampleRate
        delayCompensator.isBluetoothActive = isBt
        delayCompensator.bluetoothOffsetMs = manualBluetoothCompensationMs

        val info = AudioOutputInfo(
            deviceType = activeDevice.deviceType,
            deviceName = activeDevice.name,
            sampleRate = sampleRate,
            bufferFrames = bufferChunkFrames,
            isProAudioSupported = isProAudioSupported,
            compensationMs = if (isBt) manualBluetoothCompensationMs else 0,
            selectedDeviceId = activeDevice.id,
            availableDevices = sortedDevices
        )
        currentAudioInfo = info
        onAudioDeviceChanged?.invoke(info)
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

    /**
     * Synthesizes click waveforms into memory based on the chosen SoundSet.
     * Applies a smooth micro-fade window at the end to guarantee zero DC offset / clicks.
     */
    private fun regenerateSounds() {
        val soundSet = currentSoundSet
        val pitch = pitchMultiplier
        val sr = sampleRate.toDouble()

        // 25ms duration
        val numSamples = (sampleRate * 0.025).toInt()
        val fadeSamples = (sampleRate * 0.003).toInt().coerceAtLeast(1) // 3ms smooth fade-out window

        accentWave = ShortArray(numSamples)
        regularWave = ShortArray(numSamples)
        subWave = ShortArray(numSamples)

        for (i in 0 until numSamples) {
            val t = i / sr
            val fadeOut = if (i >= numSamples - fadeSamples) {
                (numSamples - 1 - i).toDouble() / fadeSamples
            } else {
                1.0
            }

            when (soundSet) {
                SoundSet.WOODBLOCK -> {
                    val env = exp(-120.0 * t) * fadeOut
                    val fAcc = (1450.0 * pitch * exp(-150.0 * t) + 680.0 * pitch)
                    val sAcc = (sin(2.0 * PI * fAcc * t) * 0.75 + sin(4.0 * PI * fAcc * t) * 0.25) * env
                    accentWave[i] = (sAcc * 32000.0).toInt().coerceIn(-32767, 32767).toShort()

                    val fReg = (1100.0 * pitch * exp(-150.0 * t) + 500.0 * pitch)
                    val sReg = (sin(2.0 * PI * fReg * t) * 0.75 + sin(4.0 * PI * fReg * t) * 0.25) * env * 0.82
                    regularWave[i] = (sReg * 32000.0).toInt().coerceIn(-32767, 32767).toShort()

                    val fSub = (780.0 * pitch * exp(-150.0 * t) + 390.0 * pitch)
                    val sSub = (sin(2.0 * PI * fSub * t) * 0.8) * env * 0.52
                    subWave[i] = (sSub * 32000.0).toInt().coerceIn(-32767, 32767).toShort()
                }

                SoundSet.MECHANICAL -> {
                    val clickEnv = exp(-420.0 * t) * fadeOut
                    val bodyEnv = exp(-65.0 * t) * fadeOut
                    val clickF = 2700.0 * pitch
                    val bodyF = 600.0 * pitch

                    val sAcc = (sin(2.0 * PI * clickF * t) * clickEnv * 0.7 + sin(2.0 * PI * bodyF * t) * bodyEnv * 0.5)
                    accentWave[i] = (sAcc * 32000.0).toInt().coerceIn(-32767, 32767).toShort()

                    val sReg = (sin(2.0 * PI * (clickF * 0.85) * t) * clickEnv * 0.6 + sin(2.0 * PI * (bodyF * 0.85) * t) * bodyEnv * 0.4)
                    regularWave[i] = (sReg * 32000.0).toInt().coerceIn(-32767, 32767).toShort()

                    val sSub = (sin(2.0 * PI * (clickF * 0.7) * t) * clickEnv * 0.4)
                    subWave[i] = (sSub * 32000.0).toInt().coerceIn(-32767, 32767).toShort()
                }

                SoundSet.DIGITAL -> {
                    val env = exp(-90.0 * t) * fadeOut
                    val sAcc = sin(2.0 * PI * (1760.0 * pitch) * t) * env
                    accentWave[i] = (sAcc * 31000.0).toInt().coerceIn(-32767, 32767).toShort()

                    val sReg = sin(2.0 * PI * (880.0 * pitch) * t) * env * 0.8
                    regularWave[i] = (sReg * 31000.0).toInt().coerceIn(-32767, 32767).toShort()

                    val sSub = sin(2.0 * PI * (440.0 * pitch) * t) * env * 0.5
                    subWave[i] = (sSub * 31000.0).toInt().coerceIn(-32767, 32767).toShort()
                }

                SoundSet.RIMSHOT -> {
                    val snapEnv = exp(-360.0 * t) * fadeOut
                    val shellEnv = exp(-80.0 * t) * fadeOut
                    val snapF = 3500.0 * pitch
                    val shellF = 840.0 * pitch

                    val sAcc = (sin(2.0 * PI * snapF * t) * snapEnv * 0.7 + sin(2.0 * PI * shellF * t) * shellEnv * 0.4)
                    accentWave[i] = (sAcc * 32000.0).toInt().coerceIn(-32767, 32767).toShort()

                    val sReg = (sin(2.0 * PI * (snapF * 0.8) * t) * snapEnv * 0.55 + sin(2.0 * PI * (shellF * 0.8) * t) * shellEnv * 0.35)
                    regularWave[i] = (sReg * 32000.0).toInt().coerceIn(-32767, 32767).toShort()

                    val sSub = (sin(2.0 * PI * (snapF * 0.6) * t) * snapEnv * 0.35)
                    subWave[i] = (sSub * 32000.0).toInt().coerceIn(-32767, 32767).toShort()
                }

                SoundSet.ELECTRO_808 -> {
                    val env = exp(-95.0 * t) * fadeOut
                    val f1 = 587.0 * pitch
                    val f2 = 845.0 * pitch

                    val sAcc = (sin(2.0 * PI * f1 * t) * 0.6 + sin(2.0 * PI * f2 * t) * 0.4) * env
                    accentWave[i] = (sAcc * 32000.0).toInt().coerceIn(-32767, 32767).toShort()

                    val sReg = (sin(2.0 * PI * (f1 * 0.8) * t) * 0.5 + sin(2.0 * PI * (f2 * 0.8) * t) * 0.35) * env * 0.85
                    regularWave[i] = (sReg * 32000.0).toInt().coerceIn(-32767, 32767).toShort()

                    val sSub = sin(2.0 * PI * (f1 * 0.6) * t) * env * 0.45
                    subWave[i] = (sSub * 32000.0).toInt().coerceIn(-32767, 32767).toShort()
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

    /**
     * Core real-time synthesis loop with hardware DAC/mixer warmup before Beat 1
     * and playhead-locked synchronization via [DelayCompensationEngine].
     */
    private fun runAudioLoop(track: AudioTrack) {
        val chunk = ShortArray(bufferChunkFrames)
        var currentBeat = 0
        var currentSubdivisionIdx = 0
        var tickSeqId = 0L
        var beatSequenceNumber = 0L
        var phase = 0.0 // 0.0 to 1.0 within the current subdivision
        var totalFramesWritten = 0L

        var activeSmoothedBpm = bpm
        val sr = sampleRate.toDouble()

        val initialSubCount = currentSubdivision.count.coerceAtLeast(1)
        val initialFramesPerSub = (60.0 * sr) / (activeSmoothedBpm * initialSubCount)
        val initialAccentType = accents.firstOrNull() ?: BeatAccent.ACCENT

        // Warmup configuration: 2 pre-prime chunks + ~55ms of -90 dBFS (+/-1 LSB) dither
        // so AudioFlinger's 20ms ramp-in and the hardware DAC's 35ms wake-up complete
        // before sample 0 of Beat 1 arrives at the speaker.
        val warmupPostPlayChunks = maxOf(8, ((sampleRate * 0.055) / bufferChunkFrames).toInt())
        val plannedWarmupFrames = (2 + warmupPostPlayChunks).toLong() * bufferChunkFrames

        delayCompensator.sampleRate = sampleRate
        delayCompensator.visualDelayMs = visualDelayMs
        delayCompensator.vibrationDelayMs = vibrationDelayMs
        delayCompensator.bluetoothOffsetMs = manualBluetoothCompensationMs
        delayCompensator.isBluetoothActive = currentAudioInfo.deviceType == AudioDeviceType.BLUETOOTH
        delayCompensator.reset(
            warmupFrames = plannedWarmupFrames,
            initialIsAccent = initialAccentType == BeatAccent.ACCENT,
            framesPerSubdivision = initialFramesPerSub
        )

        var lastRawHead = 0L
        var headWrapCount = 0L
        var lastHeadAdvanceFrame = 0L
        var lastHeadAdvanceNano = SystemClock.elapsedRealtimeNanos()
        var monotonicPlayheadFrame = 0L
        var playStartNano = SystemClock.elapsedRealtimeNanos()

        fun readPlayheadFrame(totalWritten: Long): Long {
            val nowNano = SystemClock.elapsedRealtimeNanos()
            val rawHead = try {
                track.playbackHeadPosition.toLong() and 0xFFFFFFFFL
            } catch (_: Exception) {
                0L
            }
            if (rawHead < lastRawHead) {
                headWrapCount++
            }
            lastRawHead = rawHead
            val hardwareFrames = (headWrapCount shl 32) + rawHead

            val estimatedFrame = if (hardwareFrames > 0L) {
                if (hardwareFrames != lastHeadAdvanceFrame) {
                    lastHeadAdvanceFrame = hardwareFrames
                    lastHeadAdvanceNano = nowNano
                }
                val deltaNs = (nowNano - lastHeadAdvanceNano).coerceAtLeast(0L)
                val subBurstFrames = ((deltaNs * sampleRate) / 1_000_000_000L)
                    .coerceIn(0L, bufferChunkFrames * 2L)
                (hardwareFrames + subBurstFrames).coerceAtMost(totalWritten)
            } else {
                val elapsedSincePlayNs = (nowNano - playStartNano).coerceAtLeast(0L)
                val wallFrames = (elapsedSincePlayNs * sampleRate) / 1_000_000_000L
                wallFrames.coerceAtMost(totalWritten)
            }

            if (estimatedFrame > monotonicPlayheadFrame) {
                monotonicPlayheadFrame = estimatedFrame
            }
            return monotonicPlayheadFrame
        }

        // 1. Hardware Mixer & DAC Warm-Up:
        if (isRunning.get() && !Thread.currentThread().isInterrupted) {
            try {
                for (i in 0 until bufferChunkFrames) {
                    chunk[i] = if ((i and 1) == 0) 1 else -1
                }
                // Pre-fill 2 initial chunks before play() to satisfy MODE_STREAM start threshold
                val w1 = track.write(chunk, 0, bufferChunkFrames)
                if (w1 > 0) totalFramesWritten += w1
                val w2 = track.write(chunk, 0, bufferChunkFrames)
                if (w2 > 0) totalFramesWritten += w2

                track.play()
                playStartNano = SystemClock.elapsedRealtimeNanos()
                lastHeadAdvanceNano = playStartNano

                for (w in 0 until warmupPostPlayChunks) {
                    if (!isRunning.get() || Thread.currentThread().isInterrupted) break
                    val written = track.write(chunk, 0, bufferChunkFrames)
                    if (written < 0) return
                    totalFramesWritten += written

                    // Dispatch any early (negative-offset) Beat 1 triggers due during warmup
                    val playhead = readPlayheadFrame(totalFramesWritten)
                    delayCompensator.processPlayhead(
                        playheadFrame = playhead,
                        liveAccents = accents,
                        totalBeats = totalBeats,
                        subdivisionCount = currentSubdivision.count,
                        currentFramesPerSubdivision = (60.0 * sr) / (activeSmoothedBpm * currentSubdivision.count.coerceAtLeast(1)),
                        onVisualTick = { bIdx, isAcc, sIdx -> onBeatTick?.invoke(bIdx, isAcc, sIdx) },
                        onVibrationTick = { bIdx, isAcc -> onVibrationTick?.invoke(bIdx, isAcc) },
                        onBarCompleted = { onBarCompleted?.invoke() }
                    )
                }
            } catch (_: Exception) {
                return
            }
        }

        if (!isRunning.get() || Thread.currentThread().isInterrupted) return

        // 2. Anchor Beat 1 (seqId = 0) at exact post-warmup frame `totalFramesWritten`
        val liveInitialAccent = accents.firstOrNull() ?: BeatAccent.ACCENT
        var activeWave: ShortArray? = when (liveInitialAccent) {
            BeatAccent.ACCENT -> accentWave
            BeatAccent.REGULAR -> regularWave
            BeatAccent.SUB -> subWave
            BeatAccent.MUTE -> null
        }
        var activeWavePos = 0

        delayCompensator.recordSynthesizedTick(
            seqId = 0L,
            beatIndex = 0,
            subdivisionIndex = 0,
            beatSequenceNumber = 0L,
            isAccent = liveInitialAccent == BeatAccent.ACCENT,
            isBarWrap = false,
            audioFrame = totalFramesWritten,
            framesPerSubdivision = (60.0 * sr) / (activeSmoothedBpm * currentSubdivision.count.coerceAtLeast(1))
        )

        // 3. Continuous Real-Time Synthesis & Playhead-Synchronized Dispatch Loop
        while (isRunning.get() && !Thread.currentThread().isInterrupted) {
            val targetBpm = bpm
            val subCount = currentSubdivision.count.coerceAtLeast(1)
            val beats = totalBeats.coerceAtLeast(1)
            val liveAccents = accents

            // Smooth BPM transition: slew rate limit toward target BPM
            val bpmDiff = targetBpm - activeSmoothedBpm
            if (abs(bpmDiff) > 0.05) {
                val maxStep = 2.0
                activeSmoothedBpm += bpmDiff.coerceIn(-maxStep, maxStep)
            } else {
                activeSmoothedBpm = targetBpm
            }

            val phaseIncrement = (activeSmoothedBpm * subCount) / (60.0 * sr)
            val currentFramesPerSub = 1.0 / phaseIncrement

            for (i in 0 until bufferChunkFrames) {
                if (phase >= 1.0) {
                    phase -= 1.0
                    tickSeqId++

                    var isBarWrap = false
                    currentSubdivisionIdx++
                    if (currentSubdivisionIdx >= subCount) {
                        currentSubdivisionIdx = 0
                        beatSequenceNumber++
                        currentBeat++
                        if (currentBeat >= beats) {
                            currentBeat = 0
                            isBarWrap = true
                        }
                    }

                    val isTickAccent: Boolean
                    if (currentSubdivisionIdx == 0) {
                        val accentType = liveAccents.getOrNull(currentBeat) ?: BeatAccent.REGULAR
                        isTickAccent = accentType == BeatAccent.ACCENT
                        when (accentType) {
                            BeatAccent.ACCENT -> {
                                activeWave = accentWave
                                activeWavePos = 0
                            }
                            BeatAccent.REGULAR -> {
                                activeWave = regularWave
                                activeWavePos = 0
                            }
                            BeatAccent.SUB -> {
                                activeWave = subWave
                                activeWavePos = 0
                            }
                            BeatAccent.MUTE -> {
                                activeWave = null
                            }
                        }
                    } else {
                        isTickAccent = false
                        activeWave = subWave
                        activeWavePos = 0
                    }

                    delayCompensator.recordSynthesizedTick(
                        seqId = tickSeqId,
                        beatIndex = currentBeat,
                        subdivisionIndex = currentSubdivisionIdx,
                        beatSequenceNumber = beatSequenceNumber,
                        isAccent = isTickAccent,
                        isBarWrap = isBarWrap,
                        audioFrame = totalFramesWritten + i,
                        framesPerSubdivision = currentFramesPerSub
                    )
                }

                // Render sample from active click waveform at unity gain (controlled by Android system STREAM_MUSIC volume)
                var sampleVal = 0
                val wave = activeWave
                if (wave != null && activeWavePos < wave.size) {
                    sampleVal = wave[activeWavePos].toInt()
                    activeWavePos++
                } else if (wave != null && activeWavePos >= wave.size) {
                    activeWave = null
                }

                chunk[i] = sampleVal.coerceIn(-32767, 32767).toShort()
                phase += phaseIncrement
            }

            // Write audio chunk to AudioTrack Fast Path
            if (isRunning.get()) {
                val written = track.write(chunk, 0, bufferChunkFrames)
                if (written < 0) {
                    break
                }
                totalFramesWritten += written

                val playheadFrame = readPlayheadFrame(totalFramesWritten)
                val snapshot = delayCompensator.processPlayhead(
                    playheadFrame = playheadFrame,
                    liveAccents = liveAccents,
                    totalBeats = beats,
                    subdivisionCount = subCount,
                    currentFramesPerSubdivision = currentFramesPerSub,
                    onVisualTick = { bIdx, isAcc, sIdx ->
                        onBeatTick?.invoke(bIdx, isAcc, sIdx)
                    },
                    onVibrationTick = { bIdx, isAcc ->
                        onVibrationTick?.invoke(bIdx, isAcc)
                    },
                    onBarCompleted = {
                        onBarCompleted?.invoke()
                    }
                )

                onPhaseUpdate?.invoke(
                    (snapshot.visualBeatSequenceNumber and 0x7FFFFFFFL).toInt(),
                    snapshot.visualBeatPhase
                )
            }
        }
    }
}
