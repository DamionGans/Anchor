package com.damiongans.pulse.audio

import com.damiongans.pulse.model.AudioOutputInfo
import com.damiongans.pulse.model.BeatAccent
import com.damiongans.pulse.model.SoundSet
import com.damiongans.pulse.model.Subdivision

/**
 * Interface contract for metronome audio generation, hardware routing,
 * and playhead-anchored delay compensation.
 * Decouples ViewModel and business logic from low-level Android AudioTrack APIs,
 * enabling full testability and mockability in JVM unit tests.
 */
interface IAudioEngine {
    var bpm: Double
    var totalBeats: Int
    var currentSubdivision: Subdivision
    var accents: List<BeatAccent>
    var currentSoundSet: SoundSet
    var pitchMultiplier: Float
    var visualDelayMs: Int
    var vibrationDelayMs: Int
    var manualBluetoothCompensationMs: Int
    val currentAudioInfo: AudioOutputInfo

    var onBeatTick: ((beatIndex: Int, isAccent: Boolean, subdivisionIndex: Int) -> Unit)?
    var onVibrationTick: ((beatIndex: Int, isAccent: Boolean) -> Unit)?
    var onPhaseUpdate: ((beatIndex: Int, phase: Float) -> Unit)?
    var onBarCompleted: (() -> Unit)?
    var onAudioDeviceChanged: ((AudioOutputInfo) -> Unit)?

    fun start()
    fun stop()
    fun release()
    fun detectCurrentAudioDevice()
    fun selectAudioOutputDevice(deviceId: Int?)
}
