package com.damiongans.anchor.viewmodel

import com.damiongans.anchor.domain.SongLibrary
import com.damiongans.anchor.model.AccentColorOption
import com.damiongans.anchor.model.AudioDeviceType
import com.damiongans.anchor.model.AudioOutputInfo
import com.damiongans.anchor.model.BeatAccent
import com.damiongans.anchor.model.ButtonTriggerMode
import com.damiongans.anchor.model.Playlist
import com.damiongans.anchor.model.Song
import com.damiongans.anchor.model.SoundSet
import com.damiongans.anchor.model.Subdivision
import com.damiongans.anchor.model.TapTempoStatus
import com.damiongans.anchor.model.ThemeMode
import com.damiongans.anchor.update.AppReleaseInfo

/**
 * Single immutable snapshot of everything the UI renders, published by [MetronomeController]
 * as a `StateFlow`. Android/Windows (Compose) and iOS (SwiftUI via `AnchorIosApp`) read it;
 * only the controller creates new values.
 */
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
    val updateCheckMessage: String? = null,
    /** False for store / F-Droid builds, which are updated by their store. */
    val canCheckForUpdates: Boolean = true,
    /** Store that delivers updates for this build (e.g. "Google Play"), or null for GitHub builds. */
    val updateStoreName: String? = null,
    /** Song library, playlists and set-list position (see [SongLibrary]). */
    val library: SongLibrary = SongLibrary()
) {
    val songs: List<Song> get() = library.songs
    val playlists: List<Playlist> get() = library.playlists
    val activePlaylistId: String? get() = library.activePlaylistId
    val activeSongIndex: Int? get() = library.activeSongIndex
    val activeSongId: String? get() = library.activeSongId
    val activePlaylist: Playlist? get() = library.activePlaylist
    val activeSong: Song? get() = library.activeSong

    /** True when the live bpm, meter or accents differ from the active song's stored values. */
    val isActiveSongModified: Boolean
        get() = activeSong?.let { song ->
            song.bpm != bpm || song.totalBeats != totalBeats ||
                song.subdivision != currentSubdivision || song.accents != accents
        } ?: false

    val effectiveVisualDelayMs: Int
        get() = flashDelayMs + (if (audioInfo.deviceType == AudioDeviceType.BLUETOOTH) bluetoothCompensationMs else 0)

    val effectiveVibrationDelayMs: Int
        get() = vibrationDelayMs + (if (audioInfo.deviceType == AudioDeviceType.BLUETOOTH) bluetoothCompensationMs else 0)

    /** Time signature denominator shown in the UI and system media controls (x/8 for eighth-note meters). */
    val timeSignatureDenominator: Int
        get() = if (currentSubdivision == Subdivision.EIGHTH) 8 else 4
}
