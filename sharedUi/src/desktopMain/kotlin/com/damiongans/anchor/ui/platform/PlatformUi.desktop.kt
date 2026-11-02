package com.damiongans.anchor.ui.platform

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.defaultScrollbarStyle
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

actual object PlatformUi {
    actual val systemThemeLabel: String = "Default Theme"
    actual val systemThemeShortLabel: String = "Default"
    actual val supportsVibration: Boolean = false
    actual val needsDragToScroll: Boolean = true
}

/** Windows has no Material You palette; the default Anchor accent is used. */
@Composable
actual fun platformDynamicColorScheme(isDark: Boolean): ColorScheme? = null

@Composable
actual fun PlatformSystemBarsEffect(isDark: Boolean) {
}

/** Wide (touch-friendly) scrollbar that is always visible while content overflows. */
@Composable
actual fun PlatformVerticalScrollbar(state: ScrollState, modifier: Modifier) {
    if (state.maxValue <= 0) return
    VerticalScrollbar(
        adapter = rememberScrollbarAdapter(state),
        modifier = modifier.padding(vertical = 4.dp),
        style = defaultScrollbarStyle().copy(
            thickness = 10.dp,
            minimalHeight = 32.dp,
            shape = RoundedCornerShape(5.dp),
            unhoverColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f),
            hoverColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
        )
    )
}
