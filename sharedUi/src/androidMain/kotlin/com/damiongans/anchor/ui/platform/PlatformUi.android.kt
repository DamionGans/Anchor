package com.damiongans.anchor.ui.platform

import android.app.Activity
import android.os.Build
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.damiongans.anchor.ui.theme.BackgroundDark
import com.damiongans.anchor.ui.theme.BackgroundLight

actual object PlatformUi {
    actual val systemThemeLabel: String = "Android Phone Theme"
    actual val systemThemeShortLabel: String = "Phone"
    actual val supportsVibration: Boolean = true
}

@Composable
actual fun platformDynamicColorScheme(isDark: Boolean): ColorScheme? {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return null
    val context = LocalContext.current
    return if (isDark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
}

@Composable
actual fun PlatformSystemBarsEffect(isDark: Boolean) {
    val view = LocalView.current
    if (view.isInEditMode) return
    SideEffect {
        val window = (view.context as? Activity)?.window ?: return@SideEffect
        val bgColor = if (isDark) BackgroundDark else BackgroundLight
        @Suppress("DEPRECATION")
        window.statusBarColor = bgColor.toArgb()
        @Suppress("DEPRECATION")
        window.navigationBarColor = bgColor.toArgb()
        val insetsController = WindowCompat.getInsetsController(window, view)
        insetsController.isAppearanceLightStatusBars = !isDark
        insetsController.isAppearanceLightNavigationBars = !isDark
    }
}
