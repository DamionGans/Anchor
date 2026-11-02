package com.damiongans.anchor.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.damiongans.anchor.model.AccentColorOption
import com.damiongans.anchor.model.ThemeMode

@Composable
fun MyApplicationTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    accentColor: AccentColorOption = AccentColorOption.SYSTEM,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val systemInDark = isSystemInDarkTheme()
    val isDark = when (themeMode) {
        ThemeMode.SYSTEM -> systemInDark
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
    }

    // Resolve system dynamic accent color from Android phone theme on Android 12+ (API 31+)
    val systemPhonePrimary = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        if (isDark) dynamicDarkColorScheme(context).primary else dynamicLightColorScheme(context).primary
    } else {
        NeonCyan
    }

    val primaryAccent = if (accentColor.isDynamic) systemPhonePrimary else accentColor.primaryColor

    val dynamicScheme = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && accentColor.isDynamic) {
        if (isDark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    } else null

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

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            val bgColor = if (isDark) BackgroundDark else BackgroundLight
            window.statusBarColor = bgColor.toArgb()
            window.navigationBarColor = bgColor.toArgb()
            val insetsController = WindowCompat.getInsetsController(window, view)
            insetsController.isAppearanceLightStatusBars = !isDark
            insetsController.isAppearanceLightNavigationBars = !isDark
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
