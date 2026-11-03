package com.damiongans.anchor.viewmodel

import com.damiongans.anchor.audio.IAudioEngine
import com.damiongans.anchor.domain.DelayCompensationEngine
import com.damiongans.anchor.domain.SongLibrary
import com.damiongans.anchor.domain.TapTempoCalculator
import com.damiongans.anchor.haptics.IHapticsManager
import com.damiongans.anchor.model.AccentColorOption
import com.damiongans.anchor.model.BeatAccent
import com.damiongans.anchor.model.Playlist
import com.damiongans.anchor.model.Song
import com.damiongans.anchor.model.ButtonTriggerMode
import com.damiongans.anchor.model.SoundSet
import com.damiongans.anchor.model.Subdivision
import com.damiongans.anchor.model.TapTempoStatus
import com.damiongans.anchor.model.TempoMarking
import com.damiongans.anchor.model.ThemeMode
import com.damiongans.anchor.model.TimeSignaturePreset
import com.damiongans.anchor.playback.IPlaybackSession
import com.damiongans.anchor.playback.NoOpPlaybackSession
import com.damiongans.anchor.playback.PlaybackCommandListener
import com.damiongans.anchor.playback.PlaybackSessionState
import com.damiongans.anchor.repository.ISettingsRepository
import com.damiongans.anchor.repository.ISongLibraryRepository
import com.damiongans.anchor.repository.InMemorySongLibraryRepository
import com.damiongans.anchor.update.AppVersionInfo
import com.damiongans.anchor.update.IUpdateRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.time.TimeSource

/**
 * Shared, platform-agnostic application logic for Anchor (Android and iOS).
 *
 * Holds the single [MetronomeUiState] stream and implements every user action: transport,
 * tempo, tap tempo, accents with per-count memory, time signatures, sound, calibration offsets,
 * persistence, system media controls, and the update check. All platform behavior is injected
 * through contracts ([IAudioEngine], [IHapticsManager], [ISettingsRepository],
 * [IUpdateRepository], [IPlaybackSession]); coroutines run on the supplied [scope]
 * (Android `viewModelScope`, iOS `MainScope()`).
 */
class MetronomeController(
    val audioEngine: IAudioEngine,
    private val hapticsManager: IHapticsManager,
    private val settingsRepository: ISettingsRepository,
    private val updateRepository: IUpdateRepository,
    private val appVersion: AppVersionInfo,
    private val scope: CoroutineScope,
    private val playbackSession: IPlaybackSession = NoOpPlaybackSession(),
    private val songLibraryRepository: ISongLibraryRepository =
        settingsRepository as? ISongLibraryRepository ?: InMemorySongLibraryRepository(),
    private val tapCalculator: TapTempoCalculator = TapTempoCalculator(),
    private val clockMs: () -> Long = monotonicClockMs(),
    autoCheckForUpdates: Boolean = true
) {
    private val pendulumCalculator = DelayCompensationEngine()

    private val rememberedAccents: MutableList<BeatAccent> = run {
        val defaults = ISettingsRepository.defaultAccentsForBeats(ISettingsRepository.MAX_BEATS).toMutableList()
        val stored = settingsRepository.getRememberedAccents()
        for (i in 0 until minOf(stored.size, defaults.size)) {
            defaults[i] = stored[i]
        }
        defaults
    }

    private val initialBpm = settingsRepository.getBpm()
    private val initialBeats = settingsRepository.getTotalBeats().coerceIn(1, ISettingsRepository.MAX_BEATS)

    private val _uiState = MutableStateFlow(
        MetronomeUiState(
            bpm = initialBpm,
            tempoMarking = TempoMarking.forBpm(initialBpm),
            totalBeats = initialBeats,
            accents = rememberedAccents.take(initialBeats),
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
            audioInfo = audioEngine.currentAudioInfo,
            canCheckForUpdates = appVersion.channel.selfUpdates,
            updateStoreName = appVersion.channel.storeName,
            library = SongLibrary(
                songs = songLibraryRepository.getSongs(),
                playlists = songLibraryRepository.getPlaylists(),
                activePlaylistId = songLibraryRepository.getActivePlaylistId()
            ).pruned().withActiveSong(
                songLibraryRepository.getActiveSongId(),
                songLibraryRepository.getActiveSongIndex()
            )
        )
    )
    val uiState: StateFlow<MetronomeUiState> = _uiState.asStateFlow()

    private var tapResetJob: Job? = null
    private var flashOffJob: Job? = null
    private var sessionSyncJob: Job? = null

    init {
        val state = _uiState.value
        audioEngine.bpm = state.bpm.toDouble()
        audioEngine.totalBeats = state.totalBeats
        audioEngine.currentSubdivision = state.currentSubdivision
        audioEngine.accents = state.accents
        audioEngine.currentSoundSet = state.currentSoundSet
        audioEngine.pitchMultiplier = state.pitchMultiplier
        audioEngine.visualDelayMs = state.flashDelayMs
        audioEngine.vibrationDelayMs = state.vibrationDelayMs
        audioEngine.manualBluetoothCompensationMs = state.bluetoothCompensationMs

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
            val current = _uiState.value
            if (current.isPlaying && current.isVibrationEnabled) {
                hapticsManager.triggerTick(isAccent)
            }
        }

        // Continuous pendulum phase callback (already playhead-compensated by DelayCompensationEngine)
        audioEngine.onPhaseUpdate = { beatSequenceNumber, phase ->
            val angle = pendulumCalculator.computePendulumAngle(
                beatSequenceNumber = beatSequenceNumber.toLong(),
                beatPhase = phase
            )
            _uiState.update { it.copy(pendulumAngle = angle) }
        }

        audioEngine.onBarCompleted = {
            _uiState.update { it.copy(totalBarsPlayed = it.totalBarsPlayed + 1) }
        }

        playbackSession.commandListener = object : PlaybackCommandListener {
            override fun onPlay() = play()
            override fun onPause() = pause()
            override fun onTogglePlayPause() = togglePlayPause()
            override fun onAdjustBpm(delta: Int) = adjustBpm(delta)
            override fun onStop() = pause()
        }
        syncPlaybackSession()

        if (autoCheckForUpdates && appVersion.channel.selfUpdates) {
            // Check for the latest GitHub release in the background on app launch
            scope.launch {
                delay(1200L)
                checkForUpdates(manual = false)
            }
        }
    }

    /** Pushes the current tempo / meter / transport state to the system media controls. */
    fun syncPlaybackSession() {
        sessionSyncJob?.cancel()
        sessionSyncJob = null
        val state = _uiState.value
        playbackSession.update(
            PlaybackSessionState(
                bpm = state.bpm,
                beats = state.totalBeats,
                denominator = state.timeSignatureDenominator,
                isPlaying = state.isPlaying
            )
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
     * Sets the offset (ms) shared by Screen Flash, BeatBar, and Pendulum.
     * Negative values fire earlier than audio, positive values later.
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
     * Sets the independent haptic vibration offset (ms).
     * Negative values compensate for motor spin-up, positive values delay vibration.
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

    /** Sets the Bluetooth latency compensation offset (ms) and persists it. */
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

    /** Resets Visual, Vibration, and Bluetooth offsets to 0 ms. */
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
            flashOffJob = scope.launch {
                delay(FLASH_DURATION_MS)
                _uiState.update { it.copy(flashPulse = false) }
            }
        }
    }

    fun togglePlayPause() {
        if (_uiState.value.isPlaying) pause() else play()
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
        // Audio first: the media session / foreground service sync must not delay Beat 1.
        audioEngine.start()
        syncPlaybackSession()
    }

    /**
     * Keeps the audio output warm while the app is on screen so the first beat sounds without a
     * start-up delay (see [com.damiongans.anchor.audio.IAudioEngine.setOutputStandby]).
     */
    fun setOutputStandby(enabled: Boolean) {
        audioEngine.setOutputStandby(enabled)
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
        syncPlaybackSession()
    }

    /** Updates the BPM in real time (clamped to 20..300) and persists it. */
    fun setBpm(newBpm: Int) {
        val clamped = newBpm.coerceIn(MIN_BPM, MAX_BPM)
        audioEngine.bpm = clamped.toDouble()
        settingsRepository.setBpm(clamped)
        _uiState.update {
            it.copy(
                bpm = clamped,
                tempoMarking = TempoMarking.forBpm(clamped)
            )
        }
        scheduleSessionSync()
    }

    /**
     * Debounced media-session update for rapid changes (slider drags, quick time-signature
     * switching): the notification / Now Playing info is rebuilt once the value settles instead of
     * on every step, keeping the UI and audio threads free of system-service churn.
     */
    private fun scheduleSessionSync() {
        sessionSyncJob?.cancel()
        sessionSyncJob = scope.launch {
            delay(SESSION_SYNC_DEBOUNCE_MS)
            sessionSyncJob = null
            syncPlaybackSession()
        }
    }

    /** Adjusts the BPM by a delta (+1, -1, +5, -5, ...). */
    fun adjustBpm(delta: Int) {
        setBpm(_uiState.value.bpm + delta)
    }

    /** Multiplies the current BPM (0.5 for half-time, 2.0 for double-time). */
    fun multiplyBpm(factor: Double) {
        setBpm((_uiState.value.bpm * factor).toInt())
    }

    /** Tap tempo with outlier filtering and pulse detection via [TapTempoCalculator]. */
    fun tapTempo(customTimestampMs: Long? = null) {
        val now = customTimestampMs ?: clockMs()
        val result = tapCalculator.registerTap(now) ?: return

        result.bpmToApply?.let { setBpm(it) }
        _uiState.update { it.copy(tapStatus = result.status) }

        tapResetJob?.cancel()
        tapResetJob = scope.launch {
            delay(TAP_RESET_MS)
            tapCalculator.reset()
            _uiState.update { it.copy(tapStatus = TapTempoStatus()) }
        }
    }

    /**
     * Cycles a beat accent (Accent -> Normal -> Soft -> Mute -> Accent) and remembers it for that
     * beat count across time signature changes.
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
     * Sets the time signature and restores remembered accents for each beat count.
     */
    fun setTimeSignature(beats: Int, subdivision: Subdivision) {
        val clampedBeats = beats.coerceIn(1, ISettingsRepository.MAX_BEATS)
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
                accents = restoredAccents
                // currentBeat / currentSubdivisionIndex are intentionally kept: the audio pulse
                // continues seamlessly, and the next beat tick updates the visuals in time.
            )
        }
        scheduleSessionSync()
    }

    fun selectTimeSignature(preset: TimeSignaturePreset) {
        setTimeSignature(preset.beats, preset.subdivision)
    }

    fun setSubdivision(subdivision: Subdivision) {
        audioEngine.currentSubdivision = subdivision
        settingsRepository.setSubdivision(subdivision)
        _uiState.update {
            it.copy(currentSubdivision = subdivision)
        }
        scheduleSessionSync()
    }

    /** Selects a specific audio output route (`null` / -1 for the speaker / system default). */
    fun selectAudioOutputDevice(deviceId: Int?) {
        audioEngine.selectAudioOutputDevice(deviceId)
    }

    /** Configures whether Play/Pause and Tap Tempo register on press or on release. */
    fun setButtonTriggerMode(mode: ButtonTriggerMode) {
        settingsRepository.setButtonTriggerMode(mode)
        _uiState.update { it.copy(buttonTriggerMode = mode) }
    }

    fun setSoundSet(soundSet: SoundSet) {
        audioEngine.currentSoundSet = soundSet
        settingsRepository.setSoundSet(soundSet)
        _uiState.update { it.copy(currentSoundSet = soundSet) }
    }

    /** Adjusts the pitch multiplier (0.5x to 2.0x). */
    fun setPitchMultiplier(pitch: Float) {
        val clamped = pitch.coerceIn(0.5f, 2.0f)
        audioEngine.pitchMultiplier = clamped
        settingsRepository.setPitchMultiplier(clamped)
        _uiState.update { it.copy(pitchMultiplier = clamped) }
    }

    fun toggleVibration() {
        val next = !_uiState.value.isVibrationEnabled
        settingsRepository.setVibrationEnabled(next)
        _uiState.update { it.copy(isVibrationEnabled = next) }
    }

    fun toggleFlash() {
        val next = !_uiState.value.isFlashEnabled
        settingsRepository.setFlashEnabled(next)
        _uiState.update { it.copy(isFlashEnabled = next) }
    }

    /**
     * Checks GitHub Releases (DamionGans/Anchor) for a newer version.
     * No-op for store / F-Droid builds, which must not self-update.
     */
    fun checkForUpdates(manual: Boolean = false) {
        if (!appVersion.channel.selfUpdates) return
        scope.launch {
            _uiState.update { it.copy(isCheckingForUpdates = true, updateCheckMessage = null) }
            val currentVersion = appVersion.versionName
            val release = updateRepository.checkForUpdate(currentVersion, appVersion.releaseTag)

            _uiState.update { current ->
                if (release != null && release.isNewerVersion) {
                    val isDeclined = current.isUpdateDeclined || settingsRepository.isUpdateDeclined(release.tagName)
                    current.copy(
                        availableUpdate = release,
                        showUpdateDialog = if (manual) false else !isDeclined,
                        isCheckingForUpdates = false,
                        isUpdateDeclined = isDeclined,
                        updateCheckMessage = if (manual) "Update ${release.tagName} available" else null
                    )
                } else {
                    current.copy(
                        availableUpdate = null,
                        showUpdateDialog = false,
                        isCheckingForUpdates = false,
                        updateCheckMessage = if (manual) "Anchor is up to date (v$currentVersion)" else null
                    )
                }
            }
        }
    }

    fun onAcceptUpdate() {
        if (!appVersion.channel.selfUpdates) return
        val release = _uiState.value.availableUpdate ?: return
        _uiState.update { it.copy(showUpdateDialog = false) }
        updateRepository.openUpdateUrl(release.downloadUrl.ifBlank { release.releasePageUrl })
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

    // --- Song library & playlists (rules live in SongLibrary; this layer persists and loads) ---

    /** Saves the live bpm, meter and accents as a new library song and marks it active. */
    fun saveCurrentAsSong(name: String): Song {
        val state = _uiState.value
        val song = createSong(name, state.bpm, state.totalBeats, state.currentSubdivision, state.accents)
        editLibrary { it.withActiveSong(song.id) }
        return song
    }

    /** Adds a song with explicit values to the library (does not load it). */
    fun createSong(
        name: String,
        bpm: Int,
        totalBeats: Int,
        subdivision: Subdivision,
        accents: List<BeatAccent>
    ): Song {
        val library = _uiState.value.library
        val song = SongLibrary.normalize(Song(library.newId(), name, bpm, totalBeats, subdivision, accents))
        editLibrary { it.addSong(song) }
        return song
    }

    /** Replaces a song's stored values; the live metronome is not changed. */
    fun updateSong(
        songId: String,
        name: String,
        bpm: Int,
        totalBeats: Int,
        subdivision: Subdivision,
        accents: List<BeatAccent>
    ) = editLibrary { it.replaceSong(SongLibrary.normalize(Song(songId, name, bpm, totalBeats, subdivision, accents))) }

    /** Overwrites a library song with the live bpm, meter and accents (name unchanged) and marks it active. */
    fun updateSongFromCurrent(songId: String) {
        val state = _uiState.value
        val song = state.library.song(songId) ?: return
        updateSong(songId, song.name, state.bpm, state.totalBeats, state.currentSubdivision, state.accents)
        editLibrary { it.withActiveSong(songId) }
    }

    fun renameSong(songId: String, name: String) {
        val song = _uiState.value.library.song(songId) ?: return
        updateSong(songId, name, song.bpm, song.totalBeats, song.subdivision, song.accents)
    }

    /** Deletes a song from the library and from every playlist. */
    fun deleteSong(songId: String) = editLibrary { it.deleteSong(songId) }

    fun createPlaylist(name: String): Playlist {
        val playlist = Playlist(_uiState.value.library.newId(), name.trim(), emptyList())
        editLibrary { it.addPlaylist(playlist) }
        return playlist
    }

    fun renamePlaylist(playlistId: String, name: String) = editLibrary { it.renamePlaylist(playlistId, name) }

    fun deletePlaylist(playlistId: String) = editLibrary { it.deletePlaylist(playlistId) }

    fun addSongToPlaylist(playlistId: String, songId: String) = editLibrary { it.addToPlaylist(playlistId, songId) }

    fun removeSongFromPlaylist(playlistId: String, index: Int) =
        editLibrary { it.removeFromPlaylist(playlistId, index) }

    fun moveSongInPlaylist(playlistId: String, fromIndex: Int, toIndex: Int) =
        editLibrary { it.moveInPlaylist(playlistId, fromIndex, toIndex) }

    /** Applies a library song's bpm, meter and accents (also while playing; never starts or stops playback). */
    fun loadSong(songId: String) {
        val song = _uiState.value.library.song(songId) ?: return
        applySong(song)
        editLibrary { it.withActiveSong(song.id) }
    }

    /** Makes a playlist active and loads its first song; null clears the active playlist. */
    fun selectPlaylist(playlistId: String?) {
        editLibrary { it.withActivePlaylist(playlistId) }
        if (_uiState.value.activePlaylist?.songIds?.isNotEmpty() == true) selectPlaylistSong(0)
    }

    /** Loads the song at [index] of the active playlist. */
    fun selectPlaylistSong(index: Int) {
        val library = _uiState.value.library
        val song = library.song(library.activePlaylist?.songIds?.getOrNull(index)) ?: return
        applySong(song)
        editLibrary { it.withActiveSong(song.id, index) }
    }

    /** Loads the next song of the active playlist (no wrap; no-op at the end). */
    fun nextSong() {
        val index = _uiState.value.activeSongIndex
        selectPlaylistSong(if (index == null) 0 else index + 1)
    }

    /** Loads the previous song of the active playlist (no wrap; no-op at the start). */
    fun previousSong() {
        val index = _uiState.value.activeSongIndex ?: return
        if (index > 0) selectPlaylistSong(index - 1)
    }

    private fun applySong(song: Song) {
        setBpm(song.bpm)
        setTimeSignature(song.totalBeats, song.subdivision)
        val accents = song.accents.take(song.totalBeats)
        for (i in accents.indices) rememberedAccents[i] = accents[i]
        audioEngine.accents = accents
        settingsRepository.setAccents(accents)
        _uiState.update { it.copy(accents = accents) }
    }

    /** Applies a pure library edit, persisting only the parts that changed. */
    private inline fun editLibrary(edit: (SongLibrary) -> SongLibrary) {
        val before = _uiState.value.library
        val after = edit(before)
        if (after == before) return
        if (after.songs != before.songs) songLibraryRepository.setSongs(after.songs)
        if (after.playlists != before.playlists) songLibraryRepository.setPlaylists(after.playlists)
        if (after.activePlaylistId != before.activePlaylistId) songLibraryRepository.setActivePlaylistId(after.activePlaylistId)
        if (after.activeSongId != before.activeSongId) songLibraryRepository.setActiveSongId(after.activeSongId)
        if (after.activeSongIndex != before.activeSongIndex) songLibraryRepository.setActiveSongIndex(after.activeSongIndex)
        _uiState.update { it.copy(library = after) }
    }

    /** Releases audio, haptics, and system media resources. Call when the owner is destroyed. */
    fun release() {
        playbackSession.commandListener = null
        playbackSession.release()
        audioEngine.release()
        hapticsManager.cancel()
        flashOffJob?.cancel()
        tapResetJob?.cancel()
        sessionSyncJob?.cancel()
    }

    companion object {
        const val MIN_BPM = 20
        const val MAX_BPM = 300
        const val FLASH_DURATION_MS = 65L
        const val TAP_RESET_MS = 2500L
        const val SESSION_SYNC_DEBOUNCE_MS = 150L

        fun monotonicClockMs(): () -> Long {
            val start = TimeSource.Monotonic.markNow()
            return { start.elapsedNow().inWholeMilliseconds }
        }
    }
}
