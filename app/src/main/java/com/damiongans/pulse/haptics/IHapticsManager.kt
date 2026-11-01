package com.damiongans.pulse.haptics

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

/**
 * Interface contract for haptic feedback.
 * Decouples ViewModel from the Android Vibrator system service for unit testing.
 */
interface IHapticsManager {
    fun triggerTick(isAccent: Boolean)
    fun cancel()
}

/**
 * Android system hardware implementation of IHapticsManager.
 * Uses hardware-accelerated click effects on Android 10+ (API 29+).
 */
class AndroidHapticsManager(context: Context) : IHapticsManager {

    private val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
        vibratorManager?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    }

    override fun triggerTick(isAccent: Boolean) {
        val vib = vibrator ?: return
        if (!vib.hasVibrator()) return

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val effect = if (isAccent) {
                    VibrationEffect.createPredefined(VibrationEffect.EFFECT_HEAVY_CLICK)
                } else {
                    VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK)
                }
                vib.vibrate(effect)
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val durationMs = if (isAccent) 20L else 10L
                val amplitude = if (isAccent) VibrationEffect.DEFAULT_AMPLITUDE else 120
                vib.vibrate(VibrationEffect.createOneShot(durationMs, amplitude))
            } else {
                @Suppress("DEPRECATION")
                vib.vibrate(if (isAccent) 20L else 10L)
            }
        } catch (_: Exception) {}
    }

    override fun cancel() {
        try {
            vibrator?.cancel()
        } catch (_: Exception) {}
    }
}
