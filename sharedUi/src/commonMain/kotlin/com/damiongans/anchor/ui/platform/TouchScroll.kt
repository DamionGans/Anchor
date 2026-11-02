package com.damiongans.anchor.ui.platform

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.ScrollableDefaults
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Vertical scrolling that also follows a finger (or mouse) drag on platforms where touch arrives
 * as mouse input (Windows: Compose Desktop only scrolls with the wheel). On Android this is a plain
 * [verticalScroll], which already handles touch.
 */
@Composable
fun Modifier.touchVerticalScroll(state: ScrollState): Modifier {
    val scrolled = this.verticalScroll(state)
    if (!PlatformUi.needsDragToScroll) return scrolled
    val fling = ScrollableDefaults.flingBehavior()
    val dragState = rememberDraggableState { delta -> state.dispatchRawDelta(-delta) }
    return scrolled.draggable(
        state = dragState,
        orientation = Orientation.Vertical,
        onDragStopped = { velocity ->
            state.scroll { with(fling) { performFling(-velocity) } }
        }
    )
}

/**
 * Wraps scrollable [content] and overlays a scrollbar on desktop (no scrollbar on Android, which
 * shows its own overscroll/fade affordances).
 */
@Composable
fun ScrollableArea(
    state: ScrollState,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    Box(modifier = modifier) {
        content()
        // Overlay sized to the content (works inside unbounded-height parents such as menus).
        Box(modifier = Modifier.matchParentSize()) {
            PlatformVerticalScrollbar(
                state = state,
                modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight()
            )
        }
    }
}

/** End padding that keeps content clear of the desktop scrollbar (0 on Android). */
fun scrollbarGutter(): Dp = if (PlatformUi.needsDragToScroll) 14.dp else 0.dp
