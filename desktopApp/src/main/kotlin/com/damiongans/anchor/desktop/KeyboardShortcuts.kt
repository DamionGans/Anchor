package com.damiongans.anchor.desktop

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import com.damiongans.anchor.viewmodel.MetronomeController

/**
 * Desktop keyboard shortcuts (key-down events, window-wide) (`SPEC-UI-004`):
 * - Space: play / pause
 * - Up / Down (or + / -): tempo ±1 BPM; with Shift: ±5 BPM
 * - T: tap tempo
 * - PageUp / PageDown (or [ / ]): previous / next song in active playlist
 */
internal object KeyboardShortcuts {

    fun handle(event: KeyEvent, controller: MetronomeController): Boolean {
        return handleKey(
            key = event.key,
            isShiftPressed = event.isShiftPressed,
            isCtrlPressed = event.isCtrlPressed,
            controller = controller
        )
    }

    internal fun handleKey(
        key: Key,
        isShiftPressed: Boolean = false,
        isCtrlPressed: Boolean = false,
        controller: MetronomeController
    ): Boolean {
        if (isCtrlPressed) return false
        val step = if (isShiftPressed) 5 else 1
        return when (key) {
            Key.Spacebar -> {
                controller.togglePlayPause(); true
            }
            Key.DirectionUp, Key.Plus, Key.NumPadAdd, Key.Equals -> {
                controller.adjustBpm(step); true
            }
            Key.DirectionDown, Key.Minus, Key.NumPadSubtract -> {
                controller.adjustBpm(-step); true
            }
            Key.T -> {
                controller.tapTempo(); true
            }
            Key.PageUp, Key.LeftBracket -> {
                controller.previousSong(); true
            }
            Key.PageDown, Key.RightBracket -> {
                controller.nextSong(); true
            }
            else -> false
        }
    }
}
