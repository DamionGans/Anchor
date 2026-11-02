package com.damiongans.anchor.ui.dialogs

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.damiongans.anchor.model.AccentColorOption
import com.damiongans.anchor.model.ThemeMode
import com.damiongans.anchor.ui.icons.AnchorIcons
import com.damiongans.anchor.ui.platform.LocalAppVersionName
import com.damiongans.anchor.ui.platform.PlatformUi
import com.damiongans.anchor.ui.platform.platformDynamicColorScheme
import com.damiongans.anchor.ui.theme.NeonCyan
import com.damiongans.anchor.ui.theme.primaryColor
import com.damiongans.anchor.update.AppReleaseInfo

@Composable
internal fun ThemeSettingsTab(
    themeMode: ThemeMode,
    accentColor: AccentColorOption,
    onThemeModeSelect: (ThemeMode) -> Unit,
    onAccentColorSelect: (AccentColorOption) -> Unit
) {
    val systemInDark = isSystemInDarkTheme()
    val systemThemePrimary = platformDynamicColorScheme(systemInDark)?.primary ?: NeonCyan

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
                    text = PlatformUi.systemThemeLabel,
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
                    systemThemePrimary
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
                                contentDescription = PlatformUi.systemThemeLabel,
                                tint = if (previewColor.luminance() > 0.5f) Color.Black else Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = if (option.isDynamic) PlatformUi.systemThemeShortLabel else option.displayName.split(" ").last(),
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
internal fun AboutSettingsTab(
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
                    text = "Version ${LocalAppVersionName.current}",
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
