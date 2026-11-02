package com.damiongans.anchor.audio

import com.damiongans.anchor.domain.DelayCompensationEngine
import com.damiongans.anchor.model.AudioDeviceType
import com.damiongans.anchor.model.AudioOutputDevice
import com.damiongans.anchor.model.AudioOutputInfo
import com.damiongans.anchor.model.BeatAccent
import com.damiongans.anchor.model.SoundSet
import com.damiongans.anchor.model.Subdivision
import kotlin.concurrent.Volatile

/**
 * Shared base for platform audio engines.
 *
 * Owns every platform-independent part of [IAudioEngine]: parameter clamping, click waveform
 * synthesis ([ClickSynthesizer]), the [MetronomeSequencer] render/dispatch core, delay
 * compensation configuration, and audio route publishing. Subclasses implement only the
 * platform I/O (`start`, `stop`, `release`) and route discovery/selection.
 */
abstract class BaseMetronomeAudioEngine(initialSampleRate: Int) : IAudioEngine {

    protected val sequencer = MetronomeSequencer()

    val delayCompensator: DelayCompensationEngine
        get() = sequencer.compensator

    @Volatile
    var sampleRate: Int = initialSampleRate.coerceAtLeast(8000)
        protected set(value) {
            val safe = value.coerceAtLeast(8000)
            if (safe != field) {
                field = safe
                delayCompensator.sampleRate = safe
                regenerateSounds()
            }
        }

    @Volatile
    override var currentAudioInfo: AudioOutputInfo = AudioOutputInfo(
        sampleRate = sampleRate,
        availableDevices = listOf(AudioRouteSelector.BUILT_IN_SPEAKER)
    )
        protected set

    @Volatile
    override var bpm: Double = 120.0
        set(value) {
            field = value.coerceIn(20.0, 320.0)
            sequencer.bpm = field
        }

    @Volatile
    override var totalBeats: Int = 4
        set(value) {
            field = value
            sequencer.totalBeats = value
        }

    @Volatile
    override var currentSubdivision: Subdivision = Subdivision.QUARTER
        set(value) {
            field = value
            sequencer.subdivision = value
        }

    @Volatile
    override var accents: List<BeatAccent> = listOf(
        BeatAccent.ACCENT,
        BeatAccent.REGULAR,
        BeatAccent.REGULAR,
        BeatAccent.REGULAR
    )
        set(value) {
            field = value
            sequencer.accents = value
        }

    @Volatile
    override var currentSoundSet: SoundSet = SoundSet.WOODBLOCK
        set(value) {
            field = value
            regenerateSounds()
        }

    @Volatile
    override var pitchMultiplier: Float = 1.0f
        set(value) {
            field = value.coerceIn(0.5f, 2.0f)
            regenerateSounds()
        }

    @Volatile
    override var visualDelayMs: Int = 0
        set(value) {
            field = value.coerceIn(
                DelayCompensationEngine.MIN_VISUAL_DELAY_MS,
                DelayCompensationEngine.MAX_VISUAL_DELAY_MS
            )
            delayCompensator.visualDelayMs = field
        }

    @Volatile
    override var vibrationDelayMs: Int = 0
        set(value) {
            field = value.coerceIn(
                DelayCompensationEngine.MIN_VIBRATION_DELAY_MS,
                DelayCompensationEngine.MAX_VIBRATION_DELAY_MS
            )
            delayCompensator.vibrationDelayMs = field
        }

    @Volatile
    override var manualBluetoothCompensationMs: Int = 0
        set(value) {
            field = value.coerceIn(
                DelayCompensationEngine.MIN_BLUETOOTH_OFFSET_MS,
                DelayCompensationEngine.MAX_BLUETOOTH_OFFSET_MS
            )
            delayCompensator.bluetoothOffsetMs = field
        }

    override var onBeatTick: ((beatIndex: Int, isAccent: Boolean, subdivisionIndex: Int) -> Unit)? = null
    override var onVibrationTick: ((beatIndex: Int, isAccent: Boolean) -> Unit)? = null
    override var onPhaseUpdate: ((beatIndex: Int, phase: Float) -> Unit)? = null
    override var onBarCompleted: (() -> Unit)? = null
    override var onAudioDeviceChanged: ((AudioOutputInfo) -> Unit)? = null

    init {
        sequencer.onVisualTick = { b, a, s -> onBeatTick?.invoke(b, a, s) }
        sequencer.onVibrationTick = { b, a -> onVibrationTick?.invoke(b, a) }
        sequencer.onBarCompleted = { onBarCompleted?.invoke() }
        sequencer.onPhaseUpdate = { seq, phase -> onPhaseUpdate?.invoke(seq, phase) }
        sequencer.bpm = bpm
        sequencer.totalBeats = totalBeats
        sequencer.subdivision = currentSubdivision
        sequencer.accents = accents
        delayCompensator.sampleRate = sampleRate
        regenerateSounds()
    }

    /** Re-synthesizes click waveforms for the current sound set, pitch, and sample rate. */
    protected fun regenerateSounds() {
        sequencer.waveforms = ClickSynthesizer.synthesize(currentSoundSet, pitchMultiplier, sampleRate)
    }

    /** Pushes the latest user offsets into the delay compensation engine before playback starts. */
    protected fun syncCompensatorSettings() {
        delayCompensator.sampleRate = sampleRate
        delayCompensator.visualDelayMs = visualDelayMs
        delayCompensator.vibrationDelayMs = vibrationDelayMs
        delayCompensator.bluetoothOffsetMs = manualBluetoothCompensationMs
        delayCompensator.isBluetoothActive = currentAudioInfo.deviceType == AudioDeviceType.BLUETOOTH
    }

    /**
     * Publishes the active route: applies Bluetooth compensation only when a Bluetooth output is
     * active (no hidden magic offsets) and notifies [onAudioDeviceChanged].
     */
    protected fun publishAudioRoute(
        activeDevice: AudioOutputDevice,
        sortedDevices: List<AudioOutputDevice>,
        bufferFrames: Int,
        isProAudioSupported: Boolean
    ) {
        val isBt = activeDevice.deviceType == AudioDeviceType.BLUETOOTH
        delayCompensator.sampleRate = sampleRate
        delayCompensator.isBluetoothActive = isBt
        delayCompensator.bluetoothOffsetMs = manualBluetoothCompensationMs

        val info = AudioOutputInfo(
            deviceType = activeDevice.deviceType,
            deviceName = activeDevice.name,
            sampleRate = sampleRate,
            bufferFrames = bufferFrames,
            isProAudioSupported = isProAudioSupported,
            compensationMs = if (isBt) manualBluetoothCompensationMs else 0,
            selectedDeviceId = activeDevice.id,
            availableDevices = sortedDevices
        )
        currentAudioInfo = info
        onAudioDeviceChanged?.invoke(info)
    }
}
