package com.damiongans.anchor.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
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
import com.damiongans.anchor.ui.icons.AnchorIcons
import com.damiongans.anchor.ui.platform.ScrollableArea
import com.damiongans.anchor.ui.platform.scrollbarGutter
import com.damiongans.anchor.ui.platform.touchVerticalScroll

@Composable
fun TopBar(
    audioInfo: AudioOutputInfo,
    hasActiveSetlist: Boolean,
    onSelectAudioOutput: (Int?) -> Unit,
    onPlaylistsClick: () -> Unit,
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

                DropdownMenu(
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
                                DropdownMenuItem(
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

            // Playlists & Song Library Button
            IconButton(
                onClick = onPlaylistsClick,
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(
                        if (hasActiveSetlist) {
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.22f)
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant
                        }
                    )
                    .border(
                        width = 1.dp,
                        color = if (hasActiveSetlist) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                        shape = CircleShape
                    )
                    .testTag("top_playlists_button")
            ) {
                Icon(
                    imageVector = AnchorIcons.QueueMusic,
                    contentDescription = "Playlists & Song Library",
                    tint = if (hasActiveSetlist) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(20.dp)
                )
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
