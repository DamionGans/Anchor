package com.damiongans.anchor.desktop

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.damiongans.anchor.ui.MetronomeScreen
import com.damiongans.anchor.ui.platform.LocalAppVersionName
import com.damiongans.anchor.ui.theme.MyApplicationTheme
import com.damiongans.anchor.update.DistributionChannel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.jetbrains.skia.Image
import java.awt.Dimension

/**
 * Windows desktop entry point. All app logic comes from the shared [com.damiongans.anchor.viewmodel.MetronomeController]
 * (wired by [AnchorDesktop]) and all UI from the shared Compose UI in `:sharedUi`.
 */
fun main() {
    val versionName = System.getProperty("anchor.version")?.takeIf { it.isNotBlank() } ?: DEV_VERSION
    // Main-thread (Swing EDT) scope for the controller's state updates and update checks.
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    val controller = AnchorDesktop.createController(scope, versionName, DistributionChannel.GITHUB)
    // Keep the audio output ready while the app is open (instant first beat where supported).
    controller.setOutputStandby(true)

    val appIcon = loadAppIcon()

    application {
        val state by controller.uiState.collectAsState()
        val windowState = rememberWindowState(size = DpSize(900.dp, 620.dp))

        Window(
            onCloseRequest = {
                controller.release()
                scope.cancel()
                exitApplication()
            },
            title = "Anchor Metronome",
            icon = appIcon,
            state = windowState,
            onPreviewKeyEvent = { event ->
                event.type == KeyEventType.KeyDown && KeyboardShortcuts.handle(event, controller)
            }
        ) {
            window.minimumSize = Dimension(380, 560)
            MyApplicationTheme(themeMode = state.themeMode, accentColor = state.accentColor) {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    CompositionLocalProvider(LocalAppVersionName provides versionName) {
                        MetronomeScreen(controller = controller)
                    }
                }
            }
        }
    }
}

private fun loadAppIcon(): BitmapPainter? = try {
    Thread.currentThread().contextClassLoader.getResourceAsStream("anchor-icon.png")?.use { stream ->
        BitmapPainter(Image.makeFromEncoded(stream.readBytes()).toComposeImageBitmap())
    }
} catch (_: Exception) {
    null
}

private const val DEV_VERSION = "1.7.0"
