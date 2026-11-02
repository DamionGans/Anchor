package com.damiongans.anchor.haptics

/**
 * Platform-agnostic interface contract for haptic feedback.
 * Decouples core state management from platform haptics APIs (Android Vibrator,
 * iOS CoreHaptics, etc.) for multiplatform portability and unit testing.
 */
interface IHapticsManager {
    fun triggerTick(isAccent: Boolean)
    fun cancel()
}

/** For platforms without a vibration motor (desktop). */
class NoOpHapticsManager : IHapticsManager {
    override fun triggerTick(isAccent: Boolean) = Unit
    override fun cancel() = Unit
}
