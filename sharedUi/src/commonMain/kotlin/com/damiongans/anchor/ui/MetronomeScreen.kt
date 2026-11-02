package com.damiongans.anchor.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
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
import com.damiongans.anchor.ui.platform.ScrollableArea
import com.damiongans.anchor.ui.platform.scrollbarGutter
import com.damiongans.anchor.ui.platform.touchVerticalScroll
import com.damiongans.anchor.ui.theme.AccentDownbeat
import com.damiongans.anchor.viewmodel.MetronomeController

@Composable
fun MetronomeScreen(
    controller: MetronomeController,
    modifier: Modifier = Modifier
) {
    val state by controller.uiState.collectAsState()

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
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(flashColor)
        ) {
            val screenWidth = maxWidth
            val screenHeight = maxHeight
            val isWideLayout = screenWidth >= 680.dp || (screenWidth >= 560.dp && screenWidth > screenHeight * 1.15f)
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
                        .align(Alignment.Center),
                    verticalArrangement = Arrangement.spacedBy(if (screenHeight >= 620.dp) 16.dp else 10.dp)
                ) {
                    TopBar(
                        audioInfo = state.audioInfo,
                        onSelectAudioOutput = { deviceId -> controller.selectAudioOutputDevice(deviceId) },
                        onSettingsClick = { showSettingsSheet = true }
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
                        .align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    // Top App Bar
                    TopBar(
                        audioInfo = state.audioInfo,
                        onSelectAudioOutput = { deviceId -> controller.selectAudioOutputDevice(deviceId) },
                        onSettingsClick = { showSettingsSheet = true }
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
                    // Capped and scrollable (touch drag + scrollbar on Windows): many outputs possible
                    val menuScroll = rememberScrollState()
                    ScrollableArea(state = menuScroll) {
                        Column(
                            modifier = Modifier
                                .heightIn(max = 320.dp)
                                .touchVerticalScroll(menuScroll)
                                .padding(end = scrollbarGutter())
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
