package com.damiongans.anchor.ui.platform

import androidx.compose.foundation.ScrollState
import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * The few UI details that differ per platform. Everything else in the shared Compose UI is
 * identical on Android and Windows.
 */
expect object PlatformUi {
    /** Label of the accent option that follows the OS color theme (e.g. Material You on Android). */
    val systemThemeLabel: String

    /** Short label of that option in the accent color picker. */
    val systemThemeShortLabel: String

    /** Whether the device has a vibration motor (hides vibration settings when false). */
    val supportsVibration: Boolean

    /**
     * Whether scroll containers must follow pointer drags themselves (Windows delivers touch as
     * mouse input, which Compose Desktop does not drag-scroll). See [touchVerticalScroll].
     */
    val needsDragToScroll: Boolean
}

/** A visible, touch-sized scrollbar on desktop; nothing on Android. */
@Composable
expect fun PlatformVerticalScrollbar(state: ScrollState, modifier: Modifier = Modifier)

/** The OS-provided dynamic color scheme (Android 12+ Material You), or null when unavailable. */
@Composable
expect fun platformDynamicColorScheme(isDark: Boolean): ColorScheme?

/** Applies light/dark system bar styling (Android status/navigation bars); no-op elsewhere. */
@Composable
expect fun PlatformSystemBarsEffect(isDark: Boolean)

/** App version shown in Settings and the release notes; provided by each platform host. */
val LocalAppVersionName = staticCompositionLocalOf { "" }
