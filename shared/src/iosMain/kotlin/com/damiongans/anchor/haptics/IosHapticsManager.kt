package com.damiongans.anchor.haptics

import platform.UIKit.UIImpactFeedbackGenerator
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue

/**
 * iOS implementation of [IHapticsManager] using the Taptic Engine (`UIImpactFeedbackGenerator`).
 * Ticks arrive on the audio loop thread (already playhead-compensated) and are forwarded to the
 * main thread as UIKit requires. Accented beats use full intensity, regular beats a lighter tap.
 * Must be created on the main thread.
 */
class IosHapticsManager : IHapticsManager {

    private val generator = UIImpactFeedbackGenerator()

    init {
        generator.prepare()
    }

    override fun triggerTick(isAccent: Boolean) {
        dispatch_async(dispatch_get_main_queue()) {
            generator.impactOccurredWithIntensity(if (isAccent) ACCENT_INTENSITY else REGULAR_INTENSITY)
            generator.prepare()
        }
    }

    override fun cancel() {
        // Impact feedback is a single transient event; there is nothing to cancel.
    }

    private companion object {
        const val ACCENT_INTENSITY = 1.0
        const val REGULAR_INTENSITY = 0.55
    }
}
