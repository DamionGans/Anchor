package com.damiongans.pulse.viewmodel

import android.app.Application
import android.content.Context
import android.os.SystemClock
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.damiongans.pulse.audio.IAudioEngine
import com.damiongans.pulse.audio.MetronomeAudioEngine
import com.damiongans.pulse.domain.DelayCompensationEngine
import com.damiongans.pulse.domain.TapTempoCalculator
import com.damiongans.pulse.haptics.AndroidHapticsManager
import com.damiongans.pulse.haptics.IHapticsManager
import com.damiongans.pulse.model.AccentColorOption
import com.damiongans.pulse.model.AudioDeviceType
import com.damiongans.pulse.model.AudioOutputInfo
import com.damiongans.pulse.model.BeatAccent
import com.damiongans.pulse.model.ButtonTriggerMode
import com.damiongans.pulse.model.SoundSet
import com.damiongans.pulse.model.Subdivision
import com.damiongans.pulse.model.TapTempoStatus
import com.damiongans.pulse.model.TempoMarking
import com.damiongans.pulse.model.ThemeMode
import com.damiongans.pulse.model.TimeSignaturePreset
import com.damiongans.pulse.repository.ISettingsRepository
import com.damiongans.pulse.repository.SharedPreferencesSettingsRepository
import com.damiongans.pulse.service.MetronomePlaybackService
import com.damiongans.pulse.update.AppReleaseInfo
import com.damiongans.pulse.update.GitHubUpdateRepository
import com.damiongans.pulse.update.IUpdateRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class MetronomeUiState(
    val isPlaying: Boolean = false,
    val bpm: Int = 120,
    val tempoMarking: String = "Allegro",
    val totalBeats: Int = 4,
    val accents: List<BeatAccent> = listOf(
        BeatAccent.ACCENT,
        BeatAccent.REGULAR,
        BeatAccent.REGULAR,
        BeatAccent.REGULAR
    ),
    val currentSubdivision: Subdivision = Subdivision.QUARTER,
    val currentSoundSet: SoundSet = SoundSet.WOODBLOCK,
    val pitchMultiplier: Float = 1.0f,
    val buttonTriggerMode: ButtonTriggerMode = ButtonTriggerMode.ON_PRESS,
    val isVibrationEnabled: Boolean = false,
    val isFlashEnabled: Boolean = false,
    val flashDelayMs: Int = 0,
    val vibrationDelayMs: Int = 0,
    val bluetoothCompensationMs: Int = 0,
    val themeMode: ThemeMode = ThemeMode.DARK,
    val accentColor: AccentColorOption = AccentColorOption.CYAN,
    val currentBeat: Int = 0,
    val currentSubdivisionIndex: Int = 0,
    val isCurrentBeatAccent: Boolean = false,
    val pendulumAngle: Float = 0f,
    val flashPulse: Boolean = false,
    val totalBarsPlayed: Int = 0,
    val audioInfo: AudioOutputInfo = AudioOutputInfo(),
    val tapStatus: TapTempoStatus = TapTempoStatus(),
    val availableUpdate: AppReleaseInfo? = null,
    val showUpdateDialog: Boolean = false,
    val isUpdateDeclined: Boolean = false,
    val isCheckingForUpdates: Boolean = false,
    val updateCheckMessage: String? = null
) {
    val effectiveVisualDelayMs: Int
        get() = flashDelayMs + (if (audioInfo.deviceType == AudioDeviceType.BLUETOOTH) bluetoothCompensationMs else 0)

    val effectiveVibrationDelayMs: Int
        get() = vibrationDelayMs + (if (audioInfo.deviceType == AudioDeviceType.BLUETOOTH) bluetoothCompensationMs else 0)
}

/**
 * Clean Architecture ViewModel for Pulse Metronome.
 * Inversion of control enables constructor-injected abstractions
 * for testability across all domain operations and services.
 */
class MetronomeViewModel @JvmOverloads constructor(
    application: Application,
    val audioEngine: IAudioEngine = MetronomeAudioEngine(application),
    private val hapticsManager: IHapticsManager = AndroidHapticsManager(application),
    private val settingsRepository: ISettingsRepository = SharedPreferencesSettingsRepository(application),
    private val updateRepository: IUpdateRepository = GitHubUpdateRepository(),
    private val tapCalculator: TapTempoCalculator = TapTempoCalculator()
) : AndroidViewModel(application) {

    private val pendulumCalculator = DelayCompensationEngine()

    private val rememberedAccents: MutableList<BeatAccent> = run {
        val defaults = SharedPreferencesSettingsRepository.defaultAccentsForBeats(
            SharedPreferencesSettingsRepository.MAX_BEATS
        ).toMutableList()
        val stored = settingsRepository.getRememberedAccents()
        for (i in 0 until minOf(stored.size, defaults.size)) {
            defaults[i] = stored[i]
        }
        defaults
    }

    private val initialBpm = settingsRepository.getBpm()
    private val initialBeats = settingsRepository.getTotalBeats().coerceIn(1, SharedPreferencesSettingsRepository.MAX_BEATS)
    private val initialAccents = rememberedAccents.take(initialBeats)

    private val _uiState = MutableStateFlow(
        MetronomeUiState(
            bpm = initialBpm,
            tempoMarking = TempoMarking.forBpm(initialBpm),
            totalBeats = initialBeats,
            accents = initialAccents,
            currentSubdivision = settingsRepository.getSubdivision(),
            currentSoundSet = settingsRepository.getSoundSet(),
            pitchMultiplier = settingsRepository.getPitchMultiplier(),
            buttonTriggerMode = settingsRepository.getButtonTriggerMode(),
            isVibrationEnabled = settingsRepository.isVibrationEnabled(),
            isFlashEnabled = settingsRepository.isFlashEnabled(),
            flashDelayMs = settingsRepository.getFlashDelayMs(),
            vibrationDelayMs = settingsRepository.getVibrationDelayMs(),
            bluetoothCompensationMs = settingsRepository.getBluetoothCompensationMs(),
            themeMode = settingsRepository.getThemeMode(),
            accentColor = settingsRepository.getAccentColor(),
            audioInfo = audioEngine.currentAudioInfo
        )
    )
    val uiState: StateFlow<MetronomeUiState> = _uiState.asStateFlow()

    // Tap status reset Job
    private var tapResetJob: Job? = null
    // Flash turn-off Job
    private var flashOffJob: Job? = null

    init {
        audioEngine.bpm = _uiState.value.bpm.toDouble()
        audioEngine.totalBeats = _uiState.value.totalBeats
        audioEngine.currentSubdivision = _uiState.value.currentSubdivision
        audioEngine.accents = _uiState.value.accents
        audioEngine.currentSoundSet = _uiState.value.currentSoundSet
        audioEngine.pitchMultiplier = _uiState.value.pitchMultiplier
        audioEngine.visualDelayMs = _uiState.value.flashDelayMs
        audioEngine.vibrationDelayMs = _uiState.value.vibrationDelayMs
        audioEngine.manualBluetoothCompensationMs = _uiState.value.bluetoothCompensationMs

        audioEngine.onAudioDeviceChanged = { info ->
            _uiState.update { it.copy(audioInfo = info) }
        }
        audioEngine.detectCurrentAudioDevice()

        // Visual beat & screen flash callback (already playhead-compensated by DelayCompensationEngine)
        audioEngine.onBeatTick = { beatIndex, isAccent, subIdx ->
            applyVisualBeatAndFlash(beatIndex, isAccent, subIdx)
        }

        // Haptic vibration callback (already playhead-compensated by DelayCompensationEngine)
        audioEngine.onVibrationTick = { _, isAccent ->
            val state = _uiState.value
            if (state.isPlaying && state.isVibrationEnabled) {
                hapticsManager.triggerTick(isAccent)
            }
        }

        // Continuous pendulum phase callback (already playhead-compensated by DelayCompensationEngine)
        audioEngine.onPhaseUpdate = { beatSequenceNumber, phase ->
            onEnginePhaseUpdate(beatSequenceNumber, phase)
        }

        audioEngine.onBarCompleted = {
            onEngineBarCompleted()
        }

        MetronomePlaybackService.onPlayRequested = {
            play()
        }
        MetronomePlaybackService.onPauseRequested = {
            pause()
        }
        MetronomePlaybackService.onTogglePlayPauseRequested = {
            togglePlayPause()
        }
        MetronomePlaybackService.onAdjustBpmRequested = { delta ->
            adjustBpm(delta)
        }
        MetronomePlaybackService.onStopRequested = {
            pause()
        }
        syncPlaybackService()

        // Check for latest GitHub release in background on app launch
        viewModelScope.launch {
            delay(1200L)
            checkForUpdates(manual = false)
        }
    }

    fun syncPlaybackService() {
        val state = _uiState.value
        val denominator = if (state.currentSubdivision == Subdivision.EIGHTH) 8 else 4
        MetronomePlaybackService.startOrUpdate(
            context = getApplication(),
            bpm = state.bpm,
            beats = state.totalBeats,
            denominator = denominator,
            isPlaying = state.isPlaying
        )
    }

    fun setThemeMode(mode: ThemeMode) {
        settingsRepository.setThemeMode(mode)
        _uiState.update { it.copy(themeMode = mode) }
    }

    fun setAccentColor(accent: AccentColorOption) {
        settingsRepository.setAccentColor(accent)
        _uiState.update { it.copy(accentColor = accent) }
    }

    /**
     * Sets the delay (offset in ms) shared by Screen Flash, BeatBar, and Pendulum.
     * Supports negative values (earlier than audio) and positive values (later than audio).
     */
    fun setFlashDelay(delayMs: Int) {
        val clamped = delayMs.coerceIn(
            DelayCompensationEngine.MIN_VISUAL_DELAY_MS,
            DelayCompensationEngine.MAX_VISUAL_DELAY_MS
        )
        audioEngine.visualDelayMs = clamped
        settingsRepository.setFlashDelayMs(clamped)
        _uiState.update { it.copy(flashDelayMs = clamped) }
    }

    /**
     * Sets the independent delay (offset in ms) for haptic vibration.
     * Supports negative values (earlier than audio to compensate for motor spin-up)
     * and positive values (later than audio).
     */
    fun setVibrationDelay(delayMs: Int) {
        val clamped = delayMs.coerceIn(
            DelayCompensationEngine.MIN_VIBRATION_DELAY_MS,
            DelayCompensationEngine.MAX_VIBRATION_DELAY_MS
        )
        audioEngine.vibrationDelayMs = clamped
        settingsRepository.setVibrationDelayMs(clamped)
        _uiState.update { it.copy(vibrationDelayMs = clamped) }
    }

    /**
     * Sets Bluetooth latency compensation offset (ms) and persists setting.
     */
    fun setBluetoothCompensation(offsetMs: Int) {
        val clamped = offsetMs.coerceIn(
            DelayCompensationEngine.MIN_BLUETOOTH_OFFSET_MS,
            DelayCompensationEngine.MAX_BLUETOOTH_OFFSET_MS
        )
        settingsRepository.setBluetoothCompensationMs(clamped)
        audioEngine.manualBluetoothCompensationMs = clamped
        _uiState.update { it.copy(bluetoothCompensationMs = clamped) }
        audioEngine.detectCurrentAudioDevice()
    }

    /**
     * Resets all user timing offsets (Visual, Vibration, and Bluetooth) to 0ms.
     */
    fun resetTimingOffsets() {
        setFlashDelay(0)
        setVibrationDelay(0)
        setBluetoothCompensation(0)
    }

    private fun applyVisualBeatAndFlash(beatIndex: Int, isAccent: Boolean, subIdx: Int) {
        val triggerFlash = subIdx == 0 && _uiState.value.isFlashEnabled
        _uiState.update { current ->
            current.copy(
                currentBeat = beatIndex,
                currentSubdivisionIndex = subIdx,
                isCurrentBeatAccent = isAccent,
                flashPulse = if (triggerFlash) true else current.flashPulse
            )
        }

        if (triggerFlash) {
            flashOffJob?.cancel()
            flashOffJob = viewModelScope.launch {
                delay(65L)
                _uiState.update { it.copy(flashPulse = false) }
            }
        }
    }

    private fun onEnginePhaseUpdate(beatSequenceNumber: Int, phase: Float) {
        val angle = pendulumCalculator.computePendulumAngle(
            beatSequenceNumber = beatSequenceNumber.toLong(),
            beatPhase = phase
        )
        _uiState.update { it.copy(pendulumAngle = angle) }
    }

    private fun onEngineBarCompleted() {
        _uiState.update { it.copy(totalBarsPlayed = it.totalBarsPlayed + 1) }
    }

    /**
     * Toggles metronome playback.
     */
    fun togglePlayPause() {
        if (_uiState.value.isPlaying) {
            pause()
        } else {
            play()
        }
    }

    fun play() {
        val state = _uiState.value
        val initialAccent = state.accents.firstOrNull() == BeatAccent.ACCENT
        _uiState.update {
            it.copy(
                isPlaying = true,
                currentBeat = -1,
                currentSubdivisionIndex = 0,
                isCurrentBeatAccent = initialAccent,
                pendulumAngle = DelayCompensationEngine.MAX_SWING_DEGREES,
                flashPulse = false
            )
        }
        syncPlaybackService()
        audioEngine.start()
    }

    fun pause() {
        audioEngine.stop()
        hapticsManager.cancel()
        flashOffJob?.cancel()
        _uiState.update {
            it.copy(
                isPlaying = false,
                currentBeat = 0,
                currentSubdivisionIndex = 0,
                flashPulse = false,
                pendulumAngle = 0f
            )
        }
        syncPlaybackService()
    }

    /**
     * Updates the BPM in real time and persists to settings.
     */
    fun setBpm(newBpm: Int) {
        val clamped = newBpm.coerceIn(20, 300)
        audioEngine.bpm = clamped.toDouble()
        settingsRepository.setBpm(clamped)
        _uiState.update {
            it.copy(
                bpm = clamped,
                tempoMarking = TempoMarking.forBpm(clamped)
            )
        }
        syncPlaybackService()
    }

    /**
     * Adjusts the BPM by a delta (+1, -1, +5, -5, etc.).
     */
    fun adjustBpm(delta: Int) {
        setBpm(_uiState.value.bpm + delta)
    }

    /**
     * Multiplies current BPM (e.g. 0.5 for half-time, 2.0 for double-time).
     */
    fun multiplyBpm(factor: Double) {
        val newBpm = (_uiState.value.bpm * factor).toInt()
        setBpm(newBpm)
    }

    /**
     * Tap tempo with outlier filtering and pulse detection via TapTempoCalculator.
     */
    fun tapTempo(customTimestampMs: Long? = null) {
        val now = customTimestampMs ?: SystemClock.uptimeMillis()
        val result = tapCalculator.registerTap(now) ?: return

        result.bpmToApply?.let { setBpm(it) }
        _uiState.update { it.copy(tapStatus = result.status) }

        // Schedule status timeout
        tapResetJob?.cancel()
        tapResetJob = viewModelScope.launch {
            delay(2500L)
            tapCalculator.reset()
            _uiState.update { it.copy(tapStatus = TapTempoStatus()) }
        }
    }

    /**
     * Cycles beat accent state (Accent -> Normal -> Sub -> Mute -> Accent)
     * and remembers the accent for that beat count across time signature changes.
     */
    fun cycleBeatAccent(beatIndex: Int) {
        val currentAccents = _uiState.value.accents.toMutableList()
        if (beatIndex in 0 until currentAccents.size) {
            val nextAccent = currentAccents[beatIndex].next()
            currentAccents[beatIndex] = nextAccent
            if (beatIndex in 0 until rememberedAccents.size) {
                rememberedAccents[beatIndex] = nextAccent
            }
            audioEngine.accents = currentAccents
            settingsRepository.setRememberedAccents(rememberedAccents.toList())
            settingsRepository.setAccents(currentAccents)
            _uiState.update { it.copy(accents = currentAccents) }
        }
    }

    /**
     * Sets time signature and restores remembered accents for each beat count
     * without resetting user-configured accent patterns.
     */
    fun setTimeSignature(beats: Int, subdivision: Subdivision) {
        val clampedBeats = beats.coerceIn(1, SharedPreferencesSettingsRepository.MAX_BEATS)
        val restoredAccents = rememberedAccents.take(clampedBeats)

        audioEngine.totalBeats = clampedBeats
        audioEngine.currentSubdivision = subdivision
        audioEngine.accents = restoredAccents

        settingsRepository.setTotalBeats(clampedBeats)
        settingsRepository.setSubdivision(subdivision)
        settingsRepository.setAccents(restoredAccents)

        _uiState.update {
            it.copy(
                totalBeats = clampedBeats,
                currentSubdivision = subdivision,
                accents = restoredAccents,
                currentBeat = 0,
                currentSubdivisionIndex = 0
            )
        }
        syncPlaybackService()
    }

    fun selectTimeSignature(preset: TimeSignaturePreset) {
        setTimeSignature(preset.beats, preset.subdivision)
    }

    fun setSubdivision(subdivision: Subdivision) {
        audioEngine.currentSubdivision = subdivision
        settingsRepository.setSubdivision(subdivision)
        _uiState.update {
            it.copy(
                currentSubdivision = subdivision,
                currentSubdivisionIndex = 0
            )
        }
        syncPlaybackService()
    }

    /**
     * Selects a specific audio output route (or null / -1 for speaker / default).
     */
    fun selectAudioOutputDevice(deviceId: Int?) {
        audioEngine.selectAudioOutputDevice(deviceId)
    }

    /**
     * Configures whether Play/Pause and Tap Tempo buttons register on finger press (tab) or release.
     */
    fun setButtonTriggerMode(mode: ButtonTriggerMode) {
        settingsRepository.setButtonTriggerMode(mode)
        _uiState.update { it.copy(buttonTriggerMode = mode) }
    }

    /**
     * Selects active sound set.
     */
    fun setSoundSet(soundSet: SoundSet) {
        audioEngine.currentSoundSet = soundSet
        settingsRepository.setSoundSet(soundSet)
        _uiState.update { it.copy(currentSoundSet = soundSet) }
    }

    /**
     * Adjusts pitch multiplier (0.5x to 2.0x).
     */
    fun setPitchMultiplier(pitch: Float) {
        val clamped = pitch.coerceIn(0.5f, 2.0f)
        audioEngine.pitchMultiplier = clamped
        settingsRepository.setPitchMultiplier(clamped)
        _uiState.update { it.copy(pitchMultiplier = clamped) }
    }

    /**
     * Toggles haptic vibration feedback and persists setting.
     */
    fun toggleVibration() {
        val next = !_uiState.value.isVibrationEnabled
        settingsRepository.setVibrationEnabled(next)
        _uiState.update { it.copy(isVibrationEnabled = next) }
    }

    /**
     * Toggles screen flash visual feedback and persists setting.
     */
    fun toggleFlash() {
        val next = !_uiState.value.isFlashEnabled
        settingsRepository.setFlashEnabled(next)
        _uiState.update { it.copy(isFlashEnabled = next) }
    }

    /**
     * Checks for updates from GitHub release DamionGans/PulseMetronome.
     */
    fun checkForUpdates(manual: Boolean = false) {
        viewModelScope.launch {
            _uiState.update { it.copy(isCheckingForUpdates = true, updateCheckMessage = null) }
            val currentVersion = com.damiongans.pulse.BuildConfig.VERSION_NAME.ifBlank { "1.2.4" }
            val currentReleaseTag = com.damiongans.pulse.BuildConfig.APP_RELEASE_TAG
            val release = updateRepository.checkForUpdate(currentVersion, currentReleaseTag)

            _uiState.update { current ->
                if (release != null && release.isNewerVersion) {
                    val isDeclined = current.isUpdateDeclined || settingsRepository.isUpdateDeclined(release.tagName)
                    val shouldShowDialog = if (manual) false else !isDeclined
                    current.copy(
                        availableUpdate = release,
                        showUpdateDialog = shouldShowDialog,
                        isCheckingForUpdates = false,
                        isUpdateDeclined = isDeclined,
                        updateCheckMessage = if (manual) "Update ${release.tagName} available" else null
                    )
                } else {
                    current.copy(
                        availableUpdate = null,
                        showUpdateDialog = false,
                        isCheckingForUpdates = false,
                        updateCheckMessage = if (manual) "Pulse Metronome is up to date (v$currentVersion)" else null
                    )
                }
            }
        }
    }

    fun onAcceptUpdate(context: Context) {
        val release = _uiState.value.availableUpdate ?: return
        _uiState.update { it.copy(showUpdateDialog = false) }
        val targetUrl = release.apkDownloadUrl.ifBlank { release.releasePageUrl }
        updateRepository.openUpdateUrl(context, targetUrl)
    }

    fun onAskLater() {
        _uiState.update { it.copy(showUpdateDialog = false) }
    }

    fun onDeclineUpdate() {
        val release = _uiState.value.availableUpdate
        if (release != null) {
            settingsRepository.setUpdateDeclined(release.tagName, true)
        }
        _uiState.update { it.copy(showUpdateDialog = false, isUpdateDeclined = true) }
    }

    override fun onCleared() {
        super.onCleared()
        MetronomePlaybackService.onPlayRequested = null
        MetronomePlaybackService.onPauseRequested = null
        MetronomePlaybackService.onTogglePlayPauseRequested = null
        MetronomePlaybackService.onAdjustBpmRequested = null
        MetronomePlaybackService.onStopRequested = null
        MetronomePlaybackService.stop(getApplication())
        audioEngine.release()
        hapticsManager.cancel()
        flashOffJob?.cancel()
    }
}
