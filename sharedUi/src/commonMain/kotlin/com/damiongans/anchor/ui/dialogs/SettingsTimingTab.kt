package com.damiongans.anchor.ui.dialogs

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.damiongans.anchor.model.AudioDeviceType
import com.damiongans.anchor.model.AudioOutputInfo
import com.damiongans.anchor.model.ButtonTriggerMode
import com.damiongans.anchor.ui.icons.AnchorIcons
import com.damiongans.anchor.ui.platform.PlatformUi

@Composable
internal fun TimingSettingsTab(
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

        // Independent Vibration / Haptic Offset Control (-200ms to +300ms); hidden without a motor
        if (PlatformUi.supportsVibration) OffsetCalibrationControl(
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
            // Vibration Switch (only on devices with a vibration motor)
            if (PlatformUi.supportsVibration) Row(
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

internal fun formatSignedMs(ms: Int): String {
    return if (ms > 0) "+${ms}ms" else "${ms}ms"
}

internal fun offsetDirectionLabel(ms: Int): String {
    return when {
        ms < 0 -> "${-ms}ms earlier than audio (advance)"
        ms > 0 -> "${ms}ms later than audio (delay)"
        else -> "0ms (locked to hardware audio playhead)"
    }
}
