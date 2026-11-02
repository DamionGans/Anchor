package com.damiongans.anchor.ui.dialogs

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import com.damiongans.anchor.ui.icons.AnchorIcons
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import com.damiongans.anchor.ui.theme.NeonCyan
import com.damiongans.anchor.ui.theme.primaryColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.damiongans.anchor.BuildConfig
import com.damiongans.anchor.model.AccentColorOption
import com.damiongans.anchor.model.AudioDeviceType
import com.damiongans.anchor.model.AudioOutputDevice
import com.damiongans.anchor.model.AudioOutputInfo
import com.damiongans.anchor.model.ButtonTriggerMode
import com.damiongans.anchor.model.SoundSet
import com.damiongans.anchor.model.ThemeMode
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

            // Tab Content (No Scrolling, each tab fits compactly)
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
        Column(
            verticalArrangement = Arrangement.spacedBy(5.dp),
            modifier = Modifier.testTag("audio_output_selector_list")
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
                text = "${String.format("%.2f", pitchMultiplier)}x",
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

@Composable
private fun TimingSettingsTab(
    audioInfo: AudioOutputInfo,
    bluetoothCompensationMs: Int,
    flashDelayMs: Int,
    vibrationDelayMs: Int,
    buttonTriggerMode: ButtonTriggerMode,
    isVibrationEnabled: Boolean,
    isFlashEnabled: Boolean,
    onBluetoothCompensationChange: (Int) -> Unit,
    onFlashDelayChange: (Int) -> Unit,
    onVibrationDelayChange: (Int) -> Unit,
    onButtonTriggerModeSelect: (ButtonTriggerMode) -> Unit,
    onResetTimingOffsets: () -> Unit,
    onToggleVibration: () -> Unit,
    onToggleFlash: () -> Unit
) {
    val isBt = audioInfo.deviceType == AudioDeviceType.BLUETOOTH
    val effectiveVisualMs = flashDelayMs + (if (isBt) bluetoothCompensationMs else 0)
    val effectiveVibMs = vibrationDelayMs + (if (isBt) bluetoothCompensationMs else 0)
    val hasNonZeroOffset = flashDelayMs != 0 || vibrationDelayMs != 0 || (isBt && bluetoothCompensationMs != 0)

    Column {
        // Output device & active playhead sync badge
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = if (isBt) AnchorIcons.Headphones else AnchorIcons.VolumeUp,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = "${audioInfo.deviceName} • ${audioInfo.sampleRate}Hz",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Active Sync — Visual: ${formatSignedMs(effectiveVisualMs)} • Haptic: ${formatSignedMs(effectiveVibMs)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                if (hasNonZeroOffset) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f))
                            .border(1.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(8.dp))
                            .clickable(onClick = onResetTimingOffsets)
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                            .testTag("reset_timing_offsets_button"),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Reset 0ms",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Screen Flash & Main Beat Visual Offset Control (-200ms to +300ms)
        OffsetCalibrationControl(
            title = "Visual & Screen Flash Offset",
            subtitle = offsetDirectionLabel(flashDelayMs),
            valueMs = flashDelayMs,
            valueRange = -200f..300f,
            accentColor = MaterialTheme.colorScheme.primary,
            sliderTestTag = "flash_delay_slider",
            onValueChange = onFlashDelayChange
        )

        Spacer(modifier = Modifier.height(6.dp))

        // Independent Vibration / Haptic Offset Control (-200ms to +300ms)
        OffsetCalibrationControl(
            title = "Vibration / Haptic Offset",
            subtitle = offsetDirectionLabel(vibrationDelayMs),
            valueMs = vibrationDelayMs,
            valueRange = -200f..300f,
            accentColor = MaterialTheme.colorScheme.primary,
            sliderTestTag = "vibration_delay_slider",
            onValueChange = onVibrationDelayChange
        )

        // Bluetooth Audio Offset (shown when Bluetooth output is active)
        if (isBt) {
            Spacer(modifier = Modifier.height(6.dp))
            OffsetCalibrationControl(
                title = "Bluetooth Audio Offset",
                subtitle = offsetDirectionLabel(bluetoothCompensationMs),
                valueMs = bluetoothCompensationMs,
                valueRange = -100f..400f,
                accentColor = MaterialTheme.colorScheme.secondary,
                sliderTestTag = "bluetooth_offset_slider",
                onValueChange = onBluetoothCompensationChange
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Switches Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Vibration Switch
            Row(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp))
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Vibration",
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Switch(
                    checked = isVibrationEnabled,
                    onCheckedChange = { onToggleVibration() },
                    modifier = Modifier.testTag("vibration_switch"),
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = MaterialTheme.colorScheme.primary,
                        checkedTrackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
                    )
                )
            }

            // Screen Flash Switch
            Row(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp))
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Screen Flash",
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Switch(
                    checked = isFlashEnabled,
                    onCheckedChange = { onToggleFlash() },
                    modifier = Modifier.testTag("screen_flash_switch"),
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = MaterialTheme.colorScheme.primary,
                        checkedTrackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
                    )
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Play & Tap Tempo Button Trigger Mode (On Tap / Press vs On Release)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f))
                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp))
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            Text(
                text = "Play & Tap Tempo Trigger",
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = buttonTriggerMode.description,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ButtonTriggerMode.values().forEach { mode ->
                    val isSelected = mode == buttonTriggerMode
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(36.dp)
                            .clip(RoundedCornerShape(9.dp))
                            .background(
                                if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                                else MaterialTheme.colorScheme.surface
                            )
                            .border(
                                1.dp,
                                if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                                RoundedCornerShape(9.dp)
                            )
                            .clickable { onButtonTriggerModeSelect(mode) }
                            .testTag("trigger_mode_${mode.name.lowercase()}"),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = mode.shortLabel,
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                fontSize = 12.sp
                            ),
                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun OffsetCalibrationControl(
    title: String,
    subtitle: String,
    valueMs: Int,
    valueRange: ClosedFloatingPointRange<Float>,
    accentColor: Color,
    sliderTestTag: String,
    onValueChange: (Int) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f))
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                text = formatSignedMs(valueMs),
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                color = accentColor
            )
        }

        Slider(
            value = valueMs.toFloat().coerceIn(valueRange.start, valueRange.endInclusive),
            onValueChange = { onValueChange(it.toInt()) },
            valueRange = valueRange,
            modifier = Modifier
                .fillMaxWidth()
                .height(32.dp)
                .testTag(sliderTestTag),
            colors = SliderDefaults.colors(
                thumbColor = accentColor,
                activeTrackColor = accentColor
            )
        )

        // Fine-tuning steppers (-10ms, -1ms, 0ms, +1ms, +10ms)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            val steps = listOf(-10 to "-10ms", -1 to "-1ms", 0 to "0ms", 1 to "+1ms", 10 to "+10ms")
            steps.forEach { (delta, label) ->
                val isZeroBtn = delta == 0
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(28.dp)
                        .clip(RoundedCornerShape(7.dp))
                        .background(
                            if (isZeroBtn && valueMs == 0) accentColor.copy(alpha = 0.2f)
                            else MaterialTheme.colorScheme.surface
                        )
                        .border(
                            1.dp,
                            if (isZeroBtn && valueMs == 0) accentColor else MaterialTheme.colorScheme.outline,
                            RoundedCornerShape(7.dp)
                        )
                        .clickable {
                            if (isZeroBtn) onValueChange(0)
                            else onValueChange(valueMs + delta)
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp
                        ),
                        color = if (isZeroBtn && valueMs == 0) accentColor else MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}

private fun formatSignedMs(ms: Int): String {
    return if (ms > 0) "+${ms}ms" else "${ms}ms"
}

private fun offsetDirectionLabel(ms: Int): String {
    return when {
        ms < 0 -> "${-ms}ms earlier than audio (advance)"
        ms > 0 -> "${ms}ms later than audio (delay)"
        else -> "0ms (locked to hardware audio playhead)"
    }
}

@Composable
private fun ThemeSettingsTab(
    themeMode: ThemeMode,
    accentColor: AccentColorOption,
    onThemeModeSelect: (ThemeMode) -> Unit,
    onAccentColorSelect: (AccentColorOption) -> Unit
) {
    val context = LocalContext.current
    val systemInDark = isSystemInDarkTheme()

    Column {
        Text(
            text = "THEME MODE",
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            ),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Theme mode selector (System, Dark, Light)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ThemeMode.values().forEach { mode ->
                val isSelected = mode == themeMode
                val modeIcon = when (mode) {
                    ThemeMode.SYSTEM -> AnchorIcons.BrightnessAuto
                    ThemeMode.DARK -> AnchorIcons.DarkMode
                    ThemeMode.LIGHT -> AnchorIcons.LightMode
                }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(46.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.2f) else MaterialTheme.colorScheme.surfaceVariant)
                        .border(
                            1.dp,
                            if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                            RoundedCornerShape(10.dp)
                        )
                        .clickable { onThemeModeSelect(mode) },
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = modeIcon,
                            contentDescription = mode.displayName,
                            tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = mode.displayName,
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                fontSize = 13.sp
                            ),
                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "ACCENT COLOR",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (accentColor.isDynamic) {
                Text(
                    text = "Android Phone Theme",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Accent colors row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically
        ) {
            AccentColorOption.values().forEach { option ->
                val isSelected = option == accentColor
                val previewColor = if (option.isDynamic) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        if (systemInDark) dynamicDarkColorScheme(context).primary
                        else dynamicLightColorScheme(context).primary
                    } else {
                        NeonCyan
                    }
                } else {
                    option.primaryColor
                }

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.clickable { onAccentColorSelect(option) }
                ) {
                    Box(
                        modifier = Modifier
                            .size(46.dp)
                            .clip(CircleShape)
                            .border(
                                2.dp,
                                if (isSelected) MaterialTheme.colorScheme.onSurface else Color.Transparent,
                                CircleShape
                            )
                            .padding(4.dp)
                            .clip(CircleShape)
                            .background(previewColor),
                        contentAlignment = Alignment.Center
                    ) {
                        if (option.isDynamic) {
                            Icon(
                                imageVector = AnchorIcons.Palette,
                                contentDescription = "Phone Theme",
                                tint = if (previewColor.luminance() > 0.5f) Color.Black else Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = if (option.isDynamic) "Phone" else option.displayName.split(" ").last(),
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 11.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                        ),
                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun AboutSettingsTab(
    availableUpdate: AppReleaseInfo?,
    isCheckingForUpdates: Boolean,
    updateCheckMessage: String?,
    canCheckForUpdates: Boolean,
    updateStoreName: String?,
    onCheckForUpdates: () -> Unit,
    onUpdateNow: () -> Unit,
    onViewChangelog: () -> Unit
) {
    Column {
        // App info box
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp)
            ) {
                Text(
                    text = "Anchor Metronome",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Version ${BuildConfig.VERSION_NAME}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Rock-solid time for serious play.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Release notes button
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(44.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp))
                .clickable(onClick = onViewChangelog),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "View Release Notes",
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Store / F-Droid builds receive updates from their store and never self-update.
        if (!canCheckForUpdates) {
            Text(
                text = "Updates are delivered automatically by ${updateStoreName ?: "your app store"}.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            UpdateCheckSection(
                availableUpdate = availableUpdate,
                isCheckingForUpdates = isCheckingForUpdates,
                updateCheckMessage = updateCheckMessage,
                onCheckForUpdates = onCheckForUpdates,
                onUpdateNow = onUpdateNow
            )
        }
    }
}

/** GitHub builds only: manual update check and install button. */
@Composable
private fun UpdateCheckSection(
    availableUpdate: AppReleaseInfo?,
    isCheckingForUpdates: Boolean,
    updateCheckMessage: String?,
    onCheckForUpdates: () -> Unit,
    onUpdateNow: () -> Unit
) {
    Column {
        // Check for updates button
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(44.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f))
                .border(1.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(12.dp))
                .clickable(enabled = !isCheckingForUpdates, onClick = onCheckForUpdates),
            contentAlignment = Alignment.Center
        ) {
            if (isCheckingForUpdates) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    color = MaterialTheme.colorScheme.primary,
                    strokeWidth = 2.dp
                )
            } else {
                Text(
                    text = "Check for Updates",
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }

        if (updateCheckMessage != null) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = updateCheckMessage,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        if (availableUpdate != null && availableUpdate.isNewerVersion) {
            Spacer(modifier = Modifier.height(10.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.primary)
                    .clickable(onClick = onUpdateNow),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "INSTALL UPDATE (${availableUpdate.tagName})",
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    ),
                    color = MaterialTheme.colorScheme.onPrimary
                )
            }
        }
    }
}
