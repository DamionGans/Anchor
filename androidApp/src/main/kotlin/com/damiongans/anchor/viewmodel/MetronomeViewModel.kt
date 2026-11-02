package com.damiongans.anchor.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.damiongans.anchor.BuildConfig
import com.damiongans.anchor.audio.IAudioEngine
import com.damiongans.anchor.audio.MetronomeAudioEngine
import com.damiongans.anchor.domain.TapTempoCalculator
import com.damiongans.anchor.haptics.AndroidHapticsManager
import com.damiongans.anchor.haptics.IHapticsManager
import com.damiongans.anchor.model.AccentColorOption
import com.damiongans.anchor.model.ButtonTriggerMode
import com.damiongans.anchor.model.SoundSet
import com.damiongans.anchor.model.Subdivision
import com.damiongans.anchor.model.ThemeMode
import com.damiongans.anchor.model.TimeSignaturePreset
import com.damiongans.anchor.playback.IPlaybackSession
import com.damiongans.anchor.repository.ISettingsRepository
import com.damiongans.anchor.repository.SharedPreferencesSettingsRepository
import com.damiongans.anchor.service.AndroidPlaybackSession
import com.damiongans.anchor.update.AppVersionInfo
import com.damiongans.anchor.update.DistributionChannel
import com.damiongans.anchor.update.GitHubUpdateRepository
import com.damiongans.anchor.update.IUpdateRepository
import kotlinx.coroutines.flow.StateFlow

/**
 * Android lifecycle host for the shared [MetronomeController].
 *
 * All application logic lives in the shared Kotlin Multiplatform module; this class only wires
 * the Android implementations (AudioTrack engine, Vibrator haptics, SharedPreferences settings,
 * GitHub/HttpURLConnection updates, foreground-service media session) and ties the controller
 * to `viewModelScope`.
 *
 * CRITICAL: keep `@JvmOverloads`. `AndroidViewModelFactory` instantiates this class via the
 * single-argument `(Application)` constructor.
 */
class MetronomeViewModel @JvmOverloads constructor(
    application: Application,
    val audioEngine: IAudioEngine = MetronomeAudioEngine(application),
    hapticsManager: IHapticsManager = AndroidHapticsManager(application),
    settingsRepository: ISettingsRepository = SharedPreferencesSettingsRepository(application),
    updateRepository: IUpdateRepository = GitHubUpdateRepository(application),
    tapCalculator: TapTempoCalculator = TapTempoCalculator(),
    playbackSession: IPlaybackSession = AndroidPlaybackSession(application)
) : AndroidViewModel(application) {

    val controller = MetronomeController(
        audioEngine = audioEngine,
        hapticsManager = hapticsManager,
        settingsRepository = settingsRepository,
        updateRepository = updateRepository,
        appVersion = AppVersionInfo(
            versionName = BuildConfig.VERSION_NAME.ifBlank { "1.5.2" },
            releaseTag = BuildConfig.APP_RELEASE_TAG,
            channel = DistributionChannel.fromName(BuildConfig.DISTRIBUTION_CHANNEL)
        ),
        scope = viewModelScope,
        playbackSession = playbackSession,
        tapCalculator = tapCalculator
    )

    val uiState: StateFlow<MetronomeUiState> = controller.uiState

    fun syncPlaybackService() = controller.syncPlaybackSession()
    fun setThemeMode(mode: ThemeMode) = controller.setThemeMode(mode)
    fun setAccentColor(accent: AccentColorOption) = controller.setAccentColor(accent)
    fun setFlashDelay(delayMs: Int) = controller.setFlashDelay(delayMs)
    fun setVibrationDelay(delayMs: Int) = controller.setVibrationDelay(delayMs)
    fun setBluetoothCompensation(offsetMs: Int) = controller.setBluetoothCompensation(offsetMs)
    fun resetTimingOffsets() = controller.resetTimingOffsets()
    fun togglePlayPause() = controller.togglePlayPause()
    fun play() = controller.play()
    fun pause() = controller.pause()
    fun setBpm(newBpm: Int) = controller.setBpm(newBpm)
    fun adjustBpm(delta: Int) = controller.adjustBpm(delta)
    fun multiplyBpm(factor: Double) = controller.multiplyBpm(factor)
    fun tapTempo(customTimestampMs: Long? = null) = controller.tapTempo(customTimestampMs)
    fun cycleBeatAccent(beatIndex: Int) = controller.cycleBeatAccent(beatIndex)
    fun setTimeSignature(beats: Int, subdivision: Subdivision) = controller.setTimeSignature(beats, subdivision)
    fun selectTimeSignature(preset: TimeSignaturePreset) = controller.selectTimeSignature(preset)
    fun setSubdivision(subdivision: Subdivision) = controller.setSubdivision(subdivision)
    fun selectAudioOutputDevice(deviceId: Int?) = controller.selectAudioOutputDevice(deviceId)
    fun setButtonTriggerMode(mode: ButtonTriggerMode) = controller.setButtonTriggerMode(mode)
    fun setSoundSet(soundSet: SoundSet) = controller.setSoundSet(soundSet)
    fun setPitchMultiplier(pitch: Float) = controller.setPitchMultiplier(pitch)
    fun toggleVibration() = controller.toggleVibration()
    fun toggleFlash() = controller.toggleFlash()
    fun checkForUpdates(manual: Boolean = false) = controller.checkForUpdates(manual)
    fun onAcceptUpdate() = controller.onAcceptUpdate()
    fun onAskLater() = controller.onAskLater()
    fun onDeclineUpdate() = controller.onDeclineUpdate()

    override fun onCleared() {
        super.onCleared()
        controller.release()
    }
}
