package com.damiongans.anchor.ui.dialogs

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.damiongans.anchor.model.AccentColorOption
import com.damiongans.anchor.model.AudioDeviceType
import com.damiongans.anchor.model.AudioOutputDevice
import com.damiongans.anchor.model.AudioOutputInfo
import com.damiongans.anchor.model.ButtonTriggerMode
import com.damiongans.anchor.model.SoundSet
import com.damiongans.anchor.model.ThemeMode
import com.damiongans.anchor.ui.icons.AnchorIcons
import com.damiongans.anchor.ui.platform.ScrollableArea
import com.damiongans.anchor.ui.platform.scrollbarGutter
import com.damiongans.anchor.ui.platform.touchVerticalScroll
import com.damiongans.anchor.update.AppReleaseInfo

private enum class SettingsTab(val title: String, val icon: ImageVector) {
    AUDIO("Audio", AnchorIcons.Tune),
    TIMING("Timing", AnchorIcons.Sync),
    THEME("Theme", AnchorIcons.Palette),
    ABOUT("About", AnchorIcons.Info)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsBottomSheet(
    currentSoundSet: SoundSet,
    pitchMultiplier: Float,
    buttonTriggerMode: ButtonTriggerMode = ButtonTriggerMode.ON_PRESS,
    isVibrationEnabled: Boolean,
    isFlashEnabled: Boolean,
    flashDelayMs: Int,
    vibrationDelayMs: Int,
    themeMode: ThemeMode,
    accentColor: AccentColorOption,
    audioInfo: AudioOutputInfo,
    bluetoothCompensationMs: Int,
    availableUpdate: AppReleaseInfo? = null,
    isUpdateDeclined: Boolean = false,
    isCheckingForUpdates: Boolean = false,
    updateCheckMessage: String? = null,
    canCheckForUpdates: Boolean = true,
    updateStoreName: String? = null,
    onSoundSetSelect: (SoundSet) -> Unit,
    onPitchChange: (Float) -> Unit,
    onSelectAudioOutput: (Int?) -> Unit = {},
    onButtonTriggerModeSelect: (ButtonTriggerMode) -> Unit = {},
    onToggleVibration: () -> Unit,
    onToggleFlash: () -> Unit,
    onFlashDelayChange: (Int) -> Unit,
    onVibrationDelayChange: (Int) -> Unit,
    onThemeModeSelect: (ThemeMode) -> Unit,
    onAccentColorSelect: (AccentColorOption) -> Unit,
    onBluetoothCompensationChange: (Int) -> Unit,
    onResetTimingOffsets: () -> Unit = {},
    onCheckForUpdates: () -> Unit,
    onUpdateNow: () -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var selectedTab by remember { mutableIntStateOf(0) }
    var showChangelogDialog by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        modifier = Modifier.testTag("settings_bottom_sheet")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .navigationBarsPadding()
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Settings",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface
                )
                IconButton(onClick = onDismiss) {
                    Icon(
                        imageVector = AnchorIcons.Close,
                        contentDescription = "Close",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Tab Bar (Audio, Timing, Theme, About)
            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.primary,
                indicator = { tabPositions ->
                    TabRowDefaults.SecondaryIndicator(
                        modifier = Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            ) {
                SettingsTab.values().forEachIndexed { index, tab ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        text = {
                            Text(
                                text = tab.title,
                                fontWeight = if (selectedTab == index) FontWeight.Bold else FontWeight.Normal,
                                fontSize = 13.sp
                            )
                        },
                        icon = {
                            Icon(
                                imageVector = tab.icon,
                                contentDescription = tab.title,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Tab content: fits on phones; scrolls when the window is short (desktop, landscape)
            val tabScroll = rememberScrollState()
            ScrollableArea(state = tabScroll, modifier = Modifier.weight(1f, fill = false)) {
                Column(modifier = Modifier.touchVerticalScroll(tabScroll).padding(end = scrollbarGutter())) {
                    when (SettingsTab.values()[selectedTab]) {
                        SettingsTab.AUDIO -> {
                            AudioSettingsTab(
                                audioInfo = audioInfo,
                                currentSoundSet = currentSoundSet,
                                pitchMultiplier = pitchMultiplier,
                                onSelectAudioOutput = onSelectAudioOutput,
                                onSoundSetSelect = onSoundSetSelect,
                                onPitchChange = onPitchChange
                            )
                        }
                        SettingsTab.TIMING -> {
                            TimingSettingsTab(
                                audioInfo = audioInfo,
                                bluetoothCompensationMs = bluetoothCompensationMs,
                                flashDelayMs = flashDelayMs,
                                vibrationDelayMs = vibrationDelayMs,
                                buttonTriggerMode = buttonTriggerMode,
                                isVibrationEnabled = isVibrationEnabled,
                                isFlashEnabled = isFlashEnabled,
                                onBluetoothCompensationChange = onBluetoothCompensationChange,
                                onFlashDelayChange = onFlashDelayChange,
                                onVibrationDelayChange = onVibrationDelayChange,
                                onButtonTriggerModeSelect = onButtonTriggerModeSelect,
                                onResetTimingOffsets = onResetTimingOffsets,
                                onToggleVibration = onToggleVibration,
                                onToggleFlash = onToggleFlash
                            )
                        }
                        SettingsTab.THEME -> {
                            ThemeSettingsTab(
                                themeMode = themeMode,
                                accentColor = accentColor,
                                onThemeModeSelect = onThemeModeSelect,
                                onAccentColorSelect = onAccentColorSelect
                            )
                        }
                        SettingsTab.ABOUT -> {
                            AboutSettingsTab(
                                availableUpdate = availableUpdate,
                                isCheckingForUpdates = isCheckingForUpdates,
                                updateCheckMessage = updateCheckMessage,
                                canCheckForUpdates = canCheckForUpdates,
                                updateStoreName = updateStoreName,
                                onCheckForUpdates = onCheckForUpdates,
                                onUpdateNow = onUpdateNow,
                                onViewChangelog = { showChangelogDialog = true }
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))
        }
    }

    if (showChangelogDialog) {
        ChangelogDialog(onDismiss = { showChangelogDialog = false })
    }
}

@Composable
private fun AudioSettingsTab(
    audioInfo: AudioOutputInfo,
    currentSoundSet: SoundSet,
    pitchMultiplier: Float,
    onSelectAudioOutput: (Int?) -> Unit,
    onSoundSetSelect: (SoundSet) -> Unit,
    onPitchChange: (Float) -> Unit
) {
    val outputs = audioInfo.availableDevices.ifEmpty {
        listOf(AudioOutputDevice(id = -1, deviceType = AudioDeviceType.SPEAKER, name = "Built-in Speaker"))
    }

    Column {
        Text(
            text = "AUDIO OUTPUT",
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            ),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(6.dp))

        // Always show all available outputs (even when there is only one output, e.g., Speaker)
        // Capped and scrollable: Windows can list many output devices.
        val outputsScroll = rememberScrollState()
        ScrollableArea(state = outputsScroll) {
            Column(
                verticalArrangement = Arrangement.spacedBy(5.dp),
                modifier = Modifier
                    .testTag("audio_output_selector_list")
                    .heightIn(max = 236.dp)
                    .touchVerticalScroll(outputsScroll)
                    .padding(end = scrollbarGutter())
            ) {
                outputs.forEach { device ->
                    val isSelected = (audioInfo.selectedDeviceId == device.id) ||
                        (outputs.size == 1) ||
                        (audioInfo.selectedDeviceId == null && device.deviceType == audioInfo.deviceType)
                    val deviceIcon = when (device.deviceType) {
                        AudioDeviceType.BLUETOOTH,
                        AudioDeviceType.WIRED_HEADPHONES,
                        AudioDeviceType.USB_AUDIO -> AnchorIcons.Headphones
                        else -> AnchorIcons.VolumeUp
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(
                                if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                                else MaterialTheme.colorScheme.surfaceVariant
                            )
                            .border(
                                1.dp,
                                if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                                RoundedCornerShape(10.dp)
                            )
                            .clickable { onSelectAudioOutput(device.id) }
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                            .testTag("audio_output_option_${device.deviceType.name.lowercase()}"),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = deviceIcon,
                                contentDescription = null,
                                tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = device.name,
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = device.deviceType.latencyCategory,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        if (isSelected) {
                            Icon(
                                imageVector = AnchorIcons.CheckCircle,
                                contentDescription = "Active Output",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = "SOUND PROFILE",
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            ),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(6.dp))

        // Sound profiles in compact list
        Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
            SoundSet.values().forEach { soundSet ->
                val isSelected = soundSet == currentSoundSet
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceVariant)
                        .border(
                            1.dp,
                            if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                            RoundedCornerShape(10.dp)
                        )
                        .clickable { onSoundSetSelect(soundSet) }
                        .padding(horizontal = 12.dp, vertical = 7.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = soundSet.displayName,
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = soundSet.description,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    if (isSelected) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Pitch Multiplier Slider
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "Pitch Tuning",
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "${formatTwoDecimals(pitchMultiplier)}x",
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.primary
            )
        }
        Slider(
            value = pitchMultiplier,
            onValueChange = onPitchChange,
            valueRange = 0.5f..2.0f,
            colors = SliderDefaults.colors(
                thumbColor = MaterialTheme.colorScheme.primary,
                activeTrackColor = MaterialTheme.colorScheme.primary
            )
        )
    }
}

/** Formats a non-negative value with exactly two decimals (multiplatform `"%.2f"`). */
internal fun formatTwoDecimals(value: Float): String {
    val hundredths = kotlin.math.round(value * 100f).toInt()
    return "${hundredths / 100}.${(hundredths % 100).toString().padStart(2, '0')}"
}
