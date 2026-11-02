package com.damiongans.anchor.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.damiongans.anchor.ui.components.BeatBar
import com.damiongans.anchor.ui.components.PendulumVisualizer
import com.damiongans.anchor.ui.components.QuickControlsBar
import com.damiongans.anchor.ui.components.SetlistBar
import com.damiongans.anchor.ui.components.TempoControls
import com.damiongans.anchor.ui.components.TopBar
import com.damiongans.anchor.ui.dialogs.PlaylistsBottomSheet
import com.damiongans.anchor.ui.dialogs.SettingsBottomSheet
import com.damiongans.anchor.ui.dialogs.TimeSignatureDialog
import com.damiongans.anchor.ui.dialogs.UpdateAvailableDialog
import com.damiongans.anchor.ui.theme.AccentDownbeat
import com.damiongans.anchor.viewmodel.MetronomeController

/**
 * Pure layout predicate for the two-pane studio split (`SPEC-UI-001`).
 */
internal fun isWideStudioLayout(screenWidth: Dp, screenHeight: Dp): Boolean {
    return screenHeight >= 480.dp &&
        (screenWidth >= 680.dp || (screenWidth >= 560.dp && screenWidth > screenHeight * 1.15f))
}

@Composable
fun MetronomeScreen(
    controller: MetronomeController,
    modifier: Modifier = Modifier
) {
    val state by controller.uiState.collectAsState()

    var showTimeSignatureDialog by remember { mutableStateOf(false) }
    var showSettingsSheet by remember { mutableStateOf(false) }
    var showPlaylistsSheet by remember { mutableStateOf(false) }

    // Flash background color animation with instantaneous onset (zero-lag strobe)
    val flashColor by animateColorAsState(
        targetValue = if (state.flashPulse && state.isCurrentBeatAccent) {
            AccentDownbeat.copy(alpha = 0.30f)
        } else if (state.flashPulse) {
            MaterialTheme.colorScheme.primary.copy(alpha = 0.22f)
        } else {
            Color.Transparent
        },
        animationSpec = if (state.flashPulse) {
            androidx.compose.animation.core.snap()
        } else {
            androidx.compose.animation.core.tween(durationMillis = 50, easing = androidx.compose.animation.core.LinearOutSlowInEasing)
        },
        label = "flashAnimation"
    )

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(flashColor)
        ) {
            val screenWidth = maxWidth
            val screenHeight = maxHeight
            val isWideLayout = isWideStudioLayout(screenWidth, screenHeight)
            val isSpacious = screenWidth >= 820.dp && screenHeight >= 540.dp

            if (isWideLayout) {
                // Two-column studio layout for tablets, foldables, landscape, and desktop windows
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(
                            horizontal = if (screenWidth >= 900.dp) 32.dp else 20.dp,
                            vertical = if (screenHeight >= 620.dp) 18.dp else 10.dp
                        )
                        .widthIn(max = 1140.dp)
                        .heightIn(max = 760.dp)
                        .align(Alignment.Center)
                        .testTag("metronome_wide_studio_layout"),
                    verticalArrangement = Arrangement.spacedBy(if (screenHeight >= 620.dp) 14.dp else 8.dp)
                ) {
                    TopBar(
                        audioInfo = state.audioInfo,
                        hasActiveSetlist = state.activePlaylist != null || state.activeSong != null,
                        onSelectAudioOutput = { deviceId -> controller.selectAudioOutputDevice(deviceId) },
                        onPlaylistsClick = { showPlaylistsSheet = true },
                        onSettingsClick = { showSettingsSheet = true }
                    )

                    SetlistBar(
                        activePlaylist = state.activePlaylist,
                        activeSong = state.activeSong,
                        activeSongIndex = state.activeSongIndex,
                        isActiveSongModified = state.isActiveSongModified,
                        onPreviousSong = { controller.previousSong() },
                        onNextSong = { controller.nextSong() },
                        onOpenPlaylistsSheet = { showPlaylistsSheet = true }
                    )

                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(if (screenWidth >= 900.dp) 28.dp else 20.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Left Pane: Visual Stage (Pendulum + Beat Bar)
                        Surface(
                            modifier = Modifier
                                .weight(1.05f)
                                .fillMaxHeight(),
                            shape = RoundedCornerShape(28.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.65f))
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(
                                        horizontal = if (isSpacious) 24.dp else 16.dp,
                                        vertical = if (isSpacious) 20.dp else 12.dp
                                    ),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.SpaceBetween
                            ) {
                                PendulumVisualizer(
                                    angleDegrees = state.pendulumAngle,
                                    isPlaying = state.isPlaying,
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp)
                                )

                                BeatBar(
                                    totalBeats = state.totalBeats,
                                    currentBeat = state.currentBeat,
                                    currentSubdivisionIndex = state.currentSubdivisionIndex,
                                    subdivision = state.currentSubdivision,
                                    accents = state.accents,
                                    isPlaying = state.isPlaying,
                                    onBeatClick = { beatIdx -> controller.cycleBeatAccent(beatIdx) },
                                    isExpanded = isSpacious,
                                    modifier = Modifier.padding(top = 8.dp)
                                )
                            }
                        }

                        // Right Pane: Control Deck (Tempo Controls + Transport & Meter)
                        Column(
                            modifier = Modifier
                                .weight(0.95f)
                                .fillMaxHeight(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(
                                space = if (isSpacious) 20.dp else 14.dp,
                                alignment = Alignment.CenterVertically
                            )
                        ) {
                            TempoControls(
                                bpm = state.bpm,
                                tempoMarking = state.tempoMarking,
                                onBpmChange = { newBpm -> controller.setBpm(newBpm) },
                                onAdjustBpm = { delta -> controller.adjustBpm(delta) },
                                onMultiplyBpm = { factor -> controller.multiplyBpm(factor) },
                                isExpanded = isSpacious
                            )

                            QuickControlsBar(
                                totalBeats = state.totalBeats,
                                subdivision = state.currentSubdivision,
                                isPlaying = state.isPlaying,
                                tapStatus = state.tapStatus,
                                buttonTriggerMode = state.buttonTriggerMode,
                                onTimeSignatureClick = { showTimeSignatureDialog = true },
                                onSubdivisionSelect = { sub -> controller.setSubdivision(sub) },
                                onTapTempo = { controller.tapTempo() },
                                onPlayPauseToggle = { controller.togglePlayPause() },
                                isExpanded = isSpacious
                            )
                        }
                    }
                }
            } else {
                // Single-column layout for phones, portrait tablets, and narrow desktop windows
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 14.dp, vertical = 6.dp)
                        .widthIn(max = 560.dp)
                        .heightIn(max = 920.dp)
                        .align(Alignment.Center)
                        .testTag("metronome_compact_layout"),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    // Top App Bar
                    TopBar(
                        audioInfo = state.audioInfo,
                        hasActiveSetlist = state.activePlaylist != null || state.activeSong != null,
                        onSelectAudioOutput = { deviceId -> controller.selectAudioOutputDevice(deviceId) },
                        onPlaylistsClick = { showPlaylistsSheet = true },
                        onSettingsClick = { showSettingsSheet = true }
                    )

                    SetlistBar(
                        activePlaylist = state.activePlaylist,
                        activeSong = state.activeSong,
                        activeSongIndex = state.activeSongIndex,
                        isActiveSongModified = state.isActiveSongModified,
                        onPreviousSong = { controller.previousSong() },
                        onNextSong = { controller.nextSong() },
                        onOpenPlaylistsSheet = { showPlaylistsSheet = true },
                        modifier = Modifier.padding(vertical = 2.dp)
                    )

                    // Pendulum Physical Visualizer (dynamically scales to fit space)
                    PendulumVisualizer(
                        angleDegrees = state.pendulumAngle,
                        isPlaying = state.isPlaying,
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 110.dp, max = 300.dp)
                            .padding(vertical = 2.dp)
                    )

                    // Interactive Beat Bar
                    BeatBar(
                        totalBeats = state.totalBeats,
                        currentBeat = state.currentBeat,
                        currentSubdivisionIndex = state.currentSubdivisionIndex,
                        subdivision = state.currentSubdivision,
                        accents = state.accents,
                        isPlaying = state.isPlaying,
                        onBeatClick = { beatIdx -> controller.cycleBeatAccent(beatIdx) },
                        modifier = Modifier.padding(vertical = 2.dp)
                    )

                    // Real-time Dynamic Tempo Controls
                    TempoControls(
                        bpm = state.bpm,
                        tempoMarking = state.tempoMarking,
                        onBpmChange = { newBpm -> controller.setBpm(newBpm) },
                        onAdjustBpm = { delta -> controller.adjustBpm(delta) },
                        onMultiplyBpm = { factor -> controller.multiplyBpm(factor) },
                        modifier = Modifier.padding(vertical = 4.dp)
                    )

                    // Time Signature, Subdivisions, Tap Tempo & Ergonomic Play Button
                    QuickControlsBar(
                        totalBeats = state.totalBeats,
                        subdivision = state.currentSubdivision,
                        isPlaying = state.isPlaying,
                        tapStatus = state.tapStatus,
                        buttonTriggerMode = state.buttonTriggerMode,
                        onTimeSignatureClick = { showTimeSignatureDialog = true },
                        onSubdivisionSelect = { sub -> controller.setSubdivision(sub) },
                        onTapTempo = { controller.tapTempo() },
                        onPlayPauseToggle = { controller.togglePlayPause() },
                        modifier = Modifier.padding(vertical = 2.dp)
                    )
                }
            }
        }
    }

    // Dialogs & Sheets
    if (showTimeSignatureDialog) {
        TimeSignatureDialog(
            currentBeats = state.totalBeats,
            currentSubdivision = state.currentSubdivision,
            onSelectPreset = { preset -> controller.selectTimeSignature(preset) },
            onCustomSelect = { beats, sub -> controller.setTimeSignature(beats, sub) },
            onDismiss = { showTimeSignatureDialog = false }
        )
    }

    val updateInfo = state.availableUpdate
    if (state.showUpdateDialog && updateInfo != null && updateInfo.isNewerVersion) {
        UpdateAvailableDialog(
            releaseInfo = updateInfo,
            onYes = { controller.onAcceptUpdate() },
            onAskLater = { controller.onAskLater() },
            onNo = { controller.onDeclineUpdate() }
        )
    }

    if (showSettingsSheet) {
        SettingsBottomSheet(
            currentSoundSet = state.currentSoundSet,
            pitchMultiplier = state.pitchMultiplier,
            buttonTriggerMode = state.buttonTriggerMode,
            isVibrationEnabled = state.isVibrationEnabled,
            isFlashEnabled = state.isFlashEnabled,
            flashDelayMs = state.flashDelayMs,
            vibrationDelayMs = state.vibrationDelayMs,
            themeMode = state.themeMode,
            accentColor = state.accentColor,
            audioInfo = state.audioInfo,
            bluetoothCompensationMs = state.bluetoothCompensationMs,
            availableUpdate = state.availableUpdate,
            isUpdateDeclined = state.isUpdateDeclined,
            isCheckingForUpdates = state.isCheckingForUpdates,
            updateCheckMessage = state.updateCheckMessage,
            canCheckForUpdates = state.canCheckForUpdates,
            updateStoreName = state.updateStoreName,
            onSoundSetSelect = { soundSet -> controller.setSoundSet(soundSet) },
            onPitchChange = { pitch -> controller.setPitchMultiplier(pitch) },
            onSelectAudioOutput = { deviceId -> controller.selectAudioOutputDevice(deviceId) },
            onButtonTriggerModeSelect = { mode -> controller.setButtonTriggerMode(mode) },
            onToggleVibration = { controller.toggleVibration() },
            onToggleFlash = { controller.toggleFlash() },
            onFlashDelayChange = { delayMs -> controller.setFlashDelay(delayMs) },
            onVibrationDelayChange = { delayMs -> controller.setVibrationDelay(delayMs) },
            onThemeModeSelect = { mode -> controller.setThemeMode(mode) },
            onAccentColorSelect = { accent -> controller.setAccentColor(accent) },
            onBluetoothCompensationChange = { offset -> controller.setBluetoothCompensation(offset) },
            onResetTimingOffsets = { controller.resetTimingOffsets() },
            onCheckForUpdates = { controller.checkForUpdates(manual = true) },
            onUpdateNow = { controller.onAcceptUpdate() },
            onDismiss = { showSettingsSheet = false }
        )
    }

    if (showPlaylistsSheet) {
        PlaylistsBottomSheet(
            songs = state.songs,
            playlists = state.playlists,
            activePlaylistId = state.activePlaylistId,
            activeSongIndex = state.activeSongIndex,
            activeSongId = state.activeSongId,
            isActiveSongModified = state.isActiveSongModified,
            currentBpm = state.bpm,
            currentTotalBeats = state.totalBeats,
            currentSubdivision = state.currentSubdivision,
            currentAccents = state.accents,
            onSaveCurrentAsSong = { name -> controller.saveCurrentAsSong(name) },
            onCreateSong = { name, bpm, beats, sub, accents ->
                controller.createSong(name, bpm, beats, sub, accents)
            },
            onUpdateSong = { id, name, bpm, beats, sub, accents ->
                controller.updateSong(id, name, bpm, beats, sub, accents)
            },
            onUpdateSongFromCurrent = { id -> controller.updateSongFromCurrent(id) },
            onDeleteSong = { id -> controller.deleteSong(id) },
            onLoadSong = { id -> controller.loadSong(id) },
            onCreatePlaylist = { name -> controller.createPlaylist(name) },
            onRenamePlaylist = { id, name -> controller.renamePlaylist(id, name) },
            onDeletePlaylist = { id -> controller.deletePlaylist(id) },
            onAddSongToPlaylist = { playlistId, songId -> controller.addSongToPlaylist(playlistId, songId) },
            onRemoveSongFromPlaylist = { playlistId, idx -> controller.removeSongFromPlaylist(playlistId, idx) },
            onMoveSongInPlaylist = { playlistId, from, to -> controller.moveSongInPlaylist(playlistId, from, to) },
            onSelectPlaylist = { id -> controller.selectPlaylist(id) },
            onSelectPlaylistSong = { idx -> controller.selectPlaylistSong(idx) },
            onDismiss = { showPlaylistsSheet = false }
        )
    }
}
