package com.damiongans.anchor.ui.platform

import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Composable

actual object PlatformUi {
    actual val systemThemeLabel: String = "Default Theme"
    actual val systemThemeShortLabel: String = "Default"
    actual val supportsVibration: Boolean = false
}

/** Windows has no Material You palette; the default Anchor accent is used. */
@Composable
actual fun platformDynamicColorScheme(isDark: Boolean): ColorScheme? = null

@Composable
actual fun PlatformSystemBarsEffect(isDark: Boolean) {
}
