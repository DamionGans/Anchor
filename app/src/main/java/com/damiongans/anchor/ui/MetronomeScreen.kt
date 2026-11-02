package com.damiongans.anchor.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.damiongans.anchor.model.AudioDeviceType
import com.damiongans.anchor.model.AudioOutputDevice
import com.damiongans.anchor.model.AudioOutputInfo
import com.damiongans.anchor.ui.components.BeatBar
import com.damiongans.anchor.ui.components.PendulumVisualizer
import com.damiongans.anchor.ui.components.QuickControlsBar
import com.damiongans.anchor.ui.components.TempoControls
import com.damiongans.anchor.ui.dialogs.SettingsBottomSheet
import com.damiongans.anchor.ui.dialogs.TimeSignatureDialog
import com.damiongans.anchor.ui.dialogs.UpdateAvailableDialog
import com.damiongans.anchor.ui.icons.AnchorIcons
import com.damiongans.anchor.ui.theme.AccentDownbeat
import com.damiongans.anchor.viewmodel.MetronomeViewModel

@Composable
fun MetronomeScreen(
    viewModel: MetronomeViewModel,
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsState()

    var showTimeSignatureDialog by remember { mutableStateOf(false) }
    var showSettingsSheet by remember { mutableStateOf(false) }

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
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(flashColor)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 14.dp, vertical = 6.dp)
                    .widthIn(max = 600.dp)
                    .align(Alignment.TopCenter),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // Top App Bar
                TopBar(
                    audioInfo = state.audioInfo,
                    onSelectAudioOutput = { deviceId -> viewModel.selectAudioOutputDevice(deviceId) },
                    onSettingsClick = { showSettingsSheet = true }
                )

                // Pendulum Physical Visualizer (dynamically scales to fit space)
                PendulumVisualizer(
                    angleDegrees = state.pendulumAngle,
                    isPlaying = state.isPlaying,
                    modifier = Modifier
                        .weight(1f, fill = false)
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
                    onBeatClick = { beatIdx -> viewModel.cycleBeatAccent(beatIdx) },
                    modifier = Modifier.padding(vertical = 2.dp)
                )

                // Real-time Dynamic Tempo Controls
                TempoControls(
                    bpm = state.bpm,
                    tempoMarking = state.tempoMarking,
                    onBpmChange = { newBpm -> viewModel.setBpm(newBpm) },
                    onAdjustBpm = { delta -> viewModel.adjustBpm(delta) },
                    onMultiplyBpm = { factor -> viewModel.multiplyBpm(factor) },
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
                    onSubdivisionSelect = { sub -> viewModel.setSubdivision(sub) },
                    onTapTempo = { viewModel.tapTempo() },
                    onPlayPauseToggle = { viewModel.togglePlayPause() },
                    modifier = Modifier.padding(vertical = 2.dp)
                )
            }
        }
    }

    // Dialogs & Sheets
    if (showTimeSignatureDialog) {
        TimeSignatureDialog(
            currentBeats = state.totalBeats,
            currentSubdivision = state.currentSubdivision,
            onSelectPreset = { preset -> viewModel.selectTimeSignature(preset) },
            onCustomSelect = { beats, sub -> viewModel.setTimeSignature(beats, sub) },
            onDismiss = { showTimeSignatureDialog = false }
        )
    }

    val updateInfo = state.availableUpdate
    if (state.showUpdateDialog && updateInfo != null && updateInfo.isNewerVersion) {
        UpdateAvailableDialog(
            releaseInfo = updateInfo,
            onYes = { viewModel.onAcceptUpdate() },
            onAskLater = { viewModel.onAskLater() },
            onNo = { viewModel.onDeclineUpdate() }
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
            onSoundSetSelect = { soundSet -> viewModel.setSoundSet(soundSet) },
            onPitchChange = { pitch -> viewModel.setPitchMultiplier(pitch) },
            onSelectAudioOutput = { deviceId -> viewModel.selectAudioOutputDevice(deviceId) },
            onButtonTriggerModeSelect = { mode -> viewModel.setButtonTriggerMode(mode) },
            onToggleVibration = { viewModel.toggleVibration() },
            onToggleFlash = { viewModel.toggleFlash() },
            onFlashDelayChange = { delayMs -> viewModel.setFlashDelay(delayMs) },
            onVibrationDelayChange = { delayMs -> viewModel.setVibrationDelay(delayMs) },
            onThemeModeSelect = { mode -> viewModel.setThemeMode(mode) },
            onAccentColorSelect = { accent -> viewModel.setAccentColor(accent) },
            onBluetoothCompensationChange = { offset -> viewModel.setBluetoothCompensation(offset) },
            onResetTimingOffsets = { viewModel.resetTimingOffsets() },
            onCheckForUpdates = { viewModel.checkForUpdates(manual = true) },
            onUpdateNow = { viewModel.onAcceptUpdate() },
            onDismiss = { showSettingsSheet = false }
        )
    }
}

@Composable
private fun TopBar(
    audioInfo: AudioOutputInfo,
    onSelectAudioOutput: (Int?) -> Unit,
    onSettingsClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showOutputMenu by remember { mutableStateOf(false) }
    val outputs = audioInfo.availableDevices.ifEmpty {
        listOf(
            AudioOutputDevice(
                id = -1,
                deviceType = AudioDeviceType.SPEAKER,
                name = "Built-in Speaker"
            )
        )
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // App Title & Tagline
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "ANCHOR",
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Black,
                        letterSpacing = 2.sp
                    ),
                    color = MaterialTheme.colorScheme.onBackground
                )
            }
            Text(
                text = "ROCK-SOLID TIME FOR SERIOUS PLAY",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 9.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 0.8.sp
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // Audio Output Selector Pill & Settings
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Audio Output Selector Badge (always shows all available outputs on tap, even if only 1 output exists)
            Box {
                Box(
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .clip(RoundedCornerShape(14.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(14.dp))
                        .clickable { showOutputMenu = true }
                        .padding(horizontal = 10.dp, vertical = 7.dp)
                        .testTag("top_audio_latency_badge"),
                    contentAlignment = Alignment.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(7.dp)
                                .clip(CircleShape)
                                .background(
                                    when (audioInfo.deviceType) {
                                        AudioDeviceType.BLUETOOTH -> MaterialTheme.colorScheme.secondary
                                        else -> Color(0xFF00E676)
                                    }
                                )
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = when (audioInfo.deviceType) {
                                AudioDeviceType.USB_AUDIO -> "USB"
                                AudioDeviceType.WIRED_HEADPHONES -> "Headphones"
                                AudioDeviceType.BLUETOOTH -> "Bluetooth"
                                else -> "Speaker"
                            },
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp
                            ),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                androidx.compose.material3.DropdownMenu(
                    expanded = showOutputMenu,
                    onDismissRequest = { showOutputMenu = false },
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                ) {
                    outputs.forEach { device ->
                        val isSelected = (audioInfo.selectedDeviceId == device.id) ||
                            (outputs.size == 1) ||
                            (audioInfo.selectedDeviceId == null && device.deviceType == audioInfo.deviceType)
                        androidx.compose.material3.DropdownMenuItem(
                            text = {
                                Column {
                                    Text(
                                        text = device.name,
                                        style = MaterialTheme.typography.bodyMedium.copy(
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                        ),
                                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = device.deviceType.latencyCategory,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = when (device.deviceType) {
                                        AudioDeviceType.BLUETOOTH,
                                        AudioDeviceType.WIRED_HEADPHONES,
                                        AudioDeviceType.USB_AUDIO -> AnchorIcons.Headphones
                                        else -> AnchorIcons.VolumeUp
                                    },
                                    contentDescription = null,
                                    tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(18.dp)
                                )
                            },
                            trailingIcon = if (isSelected) {
                                {
                                    Icon(
                                        imageVector = AnchorIcons.CheckCircle,
                                        contentDescription = "Selected",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            } else null,
                            onClick = {
                                onSelectAudioOutput(device.id)
                                showOutputMenu = false
                            }
                        )
                    }
                }
            }

            // Settings Button
            IconButton(
                onClick = onSettingsClick,
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
                    .testTag("top_settings_button")
            ) {
                Icon(
                    imageVector = AnchorIcons.Settings,
                    contentDescription = "Settings",
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}
