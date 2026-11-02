package com.damiongans.anchor.audio

import com.damiongans.anchor.model.AudioOutputInfo
import com.damiongans.anchor.model.BeatAccent
import com.damiongans.anchor.model.SoundSet
import com.damiongans.anchor.model.Subdivision

/**
 * Platform-agnostic interface contract for metronome audio generation, hardware routing,
 * and playhead-anchored delay compensation.
 * Decouples ViewModel and domain logic from platform audio APIs (Android AudioTrack,
 * iOS AVAudioEngine/AudioUnit, Desktop CoreAudio/WASAPI/ALSA), enabling multiplatform
 * implementations and full testability in JVM unit tests.
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

    /**
     * Keeps the audio output open (writing inaudible dither) while the app is in the foreground,
     * so [start] can place Beat 1 almost immediately. Platforms without a warm-output path ignore it.
     */
    fun setOutputStandby(enabled: Boolean) {}
}
