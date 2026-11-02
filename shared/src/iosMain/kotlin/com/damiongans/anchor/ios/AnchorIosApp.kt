package com.damiongans.anchor.ios

import com.damiongans.anchor.audio.IosAudioEngine
import com.damiongans.anchor.domain.DelayCompensationEngine
import com.damiongans.anchor.haptics.IosHapticsManager
import com.damiongans.anchor.model.AccentColorOption
import com.damiongans.anchor.model.ButtonTriggerMode
import com.damiongans.anchor.model.SoundSet
import com.damiongans.anchor.model.Subdivision
import com.damiongans.anchor.model.ThemeMode
import com.damiongans.anchor.model.TimeSignaturePreset
import com.damiongans.anchor.playback.IosPlaybackSession
import com.damiongans.anchor.repository.ISettingsRepository
import com.damiongans.anchor.repository.KeyValueSettingsRepository
import com.damiongans.anchor.repository.NSUserDefaultsKeyValueStore
import com.damiongans.anchor.update.AppVersionInfo
import com.damiongans.anchor.update.DistributionChannel
import com.damiongans.anchor.update.IosUpdateRepository
import com.damiongans.anchor.viewmodel.MetronomeController
import com.damiongans.anchor.viewmodel.MetronomeUiState
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import platform.Foundation.NSBundle

/**
 * Handle returned by [AnchorIosApp.observe]; call [cancel] to stop receiving state updates.
 */
class ObservationHandle internal constructor(private val job: Job) {
    fun cancel() {
        job.cancel()
    }
}

/**
 * Swift entry point for the shared Anchor logic.
 *
 * Wires the iOS platform implementations (AVAudioEngine audio, Taptic haptics, NSUserDefaults
 * settings, NSURLSession update check, Now Playing / remote commands) into the shared
 * [MetronomeController] and exposes a Swift-friendly API: plain methods without default
 * arguments, a callback-based state observer delivered on the main thread, and catalog lists
 * for pickers. Create once on the main thread (e.g. in the SwiftUI `App`).
 */
class AnchorIosApp {

    private val scope = MainScope()

    val controller: MetronomeController = MetronomeController(
        audioEngine = IosAudioEngine(),
        hapticsManager = IosHapticsManager(),
        settingsRepository = KeyValueSettingsRepository(NSUserDefaultsKeyValueStore()),
        updateRepository = IosUpdateRepository(),
        appVersion = currentAppVersion(),
        scope = scope,
        playbackSession = IosPlaybackSession()
    )

    val state: MetronomeUiState
        get() = controller.uiState.value

    val versionName: String = currentAppVersion().versionName

    /** Delivers the current state immediately and every subsequent change on the main thread. */
    fun observe(onChange: (MetronomeUiState) -> Unit): ObservationHandle {
        val job = scope.launch {
            controller.uiState.collect { onChange(it) }
        }
        return ObservationHandle(job)
    }

    // ---- Catalogs for pickers ----------------------------------------------------------------
    val soundSets: List<SoundSet> = SoundSet.entries
    val subdivisions: List<Subdivision> = Subdivision.entries
    val timeSignaturePresets: List<TimeSignaturePreset> = TimeSignaturePreset.entries
    val themeModes: List<ThemeMode> = ThemeMode.entries
    val accentColors: List<AccentColorOption> = AccentColorOption.entries
    val buttonTriggerModes: List<ButtonTriggerMode> = ButtonTriggerMode.entries

    val minBpm: Int = MetronomeController.MIN_BPM
    val maxBpm: Int = MetronomeController.MAX_BPM
    val minOffsetMs: Int = DelayCompensationEngine.MIN_VISUAL_DELAY_MS
    val maxOffsetMs: Int = DelayCompensationEngine.MAX_VISUAL_DELAY_MS
    val minBluetoothOffsetMs: Int = DelayCompensationEngine.MIN_BLUETOOTH_OFFSET_MS
    val maxBluetoothOffsetMs: Int = DelayCompensationEngine.MAX_BLUETOOTH_OFFSET_MS
    val maxBeats: Int = ISettingsRepository.MAX_BEATS

    /** `description` collides with NSObject in Swift, so it is exposed through these helpers. */
    fun soundSetSummary(soundSet: SoundSet): String = soundSet.description
    fun buttonTriggerModeSummary(mode: ButtonTriggerMode): String = mode.description

    // ---- Actions -------------------------------------------------------------------------------
    fun togglePlayPause() = controller.togglePlayPause()
    /** Keep the audio output warm while the app is in the foreground (instant first beat). */
    fun setOutputStandby(enabled: Boolean) = controller.setOutputStandby(enabled)
    fun play() = controller.play()
    fun pause() = controller.pause()
    fun changeBpm(bpm: Int) = controller.setBpm(bpm)
    fun adjustBpm(delta: Int) = controller.adjustBpm(delta)
    fun multiplyBpm(factor: Double) = controller.multiplyBpm(factor)
    fun tapTempo() = controller.tapTempo(null)
    fun cycleBeatAccent(beatIndex: Int) = controller.cycleBeatAccent(beatIndex)
    fun selectTimeSignature(preset: TimeSignaturePreset) = controller.selectTimeSignature(preset)
    fun changeTimeSignature(beats: Int, subdivision: Subdivision) = controller.setTimeSignature(beats, subdivision)
    fun changeSubdivision(subdivision: Subdivision) = controller.setSubdivision(subdivision)
    fun changeSoundSet(soundSet: SoundSet) = controller.setSoundSet(soundSet)
    fun changePitch(pitch: Float) = controller.setPitchMultiplier(pitch)
    fun changeThemeMode(mode: ThemeMode) = controller.setThemeMode(mode)
    fun changeAccentColor(accent: AccentColorOption) = controller.setAccentColor(accent)
    fun changeButtonTriggerMode(mode: ButtonTriggerMode) = controller.setButtonTriggerMode(mode)
    fun toggleFlash() = controller.toggleFlash()
    fun toggleVibration() = controller.toggleVibration()
    fun changeFlashDelay(delayMs: Int) = controller.setFlashDelay(delayMs)
    fun changeVibrationDelay(delayMs: Int) = controller.setVibrationDelay(delayMs)
    fun changeBluetoothCompensation(offsetMs: Int) = controller.setBluetoothCompensation(offsetMs)
    fun resetTimingOffsets() = controller.resetTimingOffsets()
    fun refreshAudioRoute() = controller.selectAudioOutputDevice(null)
    fun checkForUpdates() = controller.checkForUpdates(manual = true)
    fun acceptUpdate() = controller.onAcceptUpdate()
    fun askLater() = controller.onAskLater()
    fun declineUpdate() = controller.onDeclineUpdate()

    /** Stops playback and releases audio / media resources. */
    fun dispose() {
        controller.release()
        scope.cancel()
    }

    private companion object {
        fun currentAppVersion(): AppVersionInfo {
            val bundle = NSBundle.mainBundle
            val version = bundle.objectForInfoDictionaryKey("CFBundleShortVersionString") as? String
            // "AnchorDistribution" is set from the ANCHOR_DISTRIBUTION build setting
            // (GITHUB for the unsigned CI build, APP_STORE for App Store archives).
            val channel = bundle.objectForInfoDictionaryKey("AnchorDistribution") as? String
            return AppVersionInfo(
                versionName = version?.takeIf { it.isNotBlank() } ?: "1.6.3",
                channel = DistributionChannel.fromName(channel)
            )
        }
    }
}
