package com.damiongans.anchor.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.damiongans.anchor.model.AccentColorOption
import com.damiongans.anchor.model.ThemeMode
import com.damiongans.anchor.ui.platform.PlatformSystemBarsEffect
import com.damiongans.anchor.ui.platform.platformDynamicColorScheme

@Composable
fun MyApplicationTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    accentColor: AccentColorOption = AccentColorOption.SYSTEM,
    content: @Composable () -> Unit
) {
    val systemInDark = isSystemInDarkTheme()
    val isDark = when (themeMode) {
        ThemeMode.SYSTEM -> systemInDark
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
    }

    // OS dynamic accent color (Material You on Android 12+); Anchor cyan elsewhere.
    val platformScheme = platformDynamicColorScheme(isDark)
    val systemPhonePrimary = platformScheme?.primary ?: NeonCyan

    val primaryAccent = if (accentColor.isDynamic) systemPhonePrimary else accentColor.primaryColor

    val dynamicScheme = if (accentColor.isDynamic) platformScheme else null

    val colorScheme = if (isDark) {
        darkColorScheme(
            primary = primaryAccent,
            onPrimary = dynamicScheme?.onPrimary ?: BackgroundDark,
            primaryContainer = dynamicScheme?.primaryContainer ?: SurfaceVariantDark,
            onPrimaryContainer = dynamicScheme?.onPrimaryContainer ?: primaryAccent,
            secondary = dynamicScheme?.secondary ?: AmberGold,
            onSecondary = dynamicScheme?.onSecondary ?: BackgroundDark,
            secondaryContainer = dynamicScheme?.secondaryContainer ?: SurfaceVariantDark,
            onSecondaryContainer = dynamicScheme?.onSecondaryContainer ?: AmberGoldLight,
            tertiary = dynamicScheme?.tertiary ?: ElectricCoral,
            onTertiary = dynamicScheme?.onTertiary ?: BackgroundDark,
            background = BackgroundDark,
            onBackground = TextPrimaryDark,
            surface = SurfaceDark,
            onSurface = TextPrimaryDark,
            surfaceVariant = SurfaceVariantDark,
            onSurfaceVariant = TextSecondaryDark,
            outline = BorderDark
        )
    } else {
        lightColorScheme(
            primary = primaryAccent,
            onPrimary = dynamicScheme?.onPrimary ?: Color.White,
            primaryContainer = dynamicScheme?.primaryContainer ?: SurfaceVariantLight,
            onPrimaryContainer = dynamicScheme?.onPrimaryContainer ?: primaryAccent,
            secondary = dynamicScheme?.secondary ?: AmberGold,
            onSecondary = dynamicScheme?.onSecondary ?: Color.White,
            secondaryContainer = dynamicScheme?.secondaryContainer ?: SurfaceVariantLight,
            onSecondaryContainer = dynamicScheme?.onSecondaryContainer ?: AmberGoldLight,
            tertiary = dynamicScheme?.tertiary ?: ElectricCoral,
            onTertiary = dynamicScheme?.onTertiary ?: Color.White,
            background = BackgroundLight,
            onBackground = TextPrimaryLight,
            surface = SurfaceLight,
            onSurface = TextPrimaryLight,
            surfaceVariant = SurfaceVariantLight,
            onSurfaceVariant = TextSecondaryLight,
            outline = BorderLight
        )
    }

    PlatformSystemBarsEffect(isDark)

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
