package com.damiongans.anchor.audio

import com.damiongans.anchor.model.BeatAccent
import com.damiongans.anchor.model.SoundSet
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin

/**
 * Pre-synthesized 16-bit mono PCM click waveforms for one sound set / pitch / sample rate.
 */
class ClickWaveforms(
    val accent: ShortArray,
    val regular: ShortArray,
    val sub: ShortArray
) {
    /** Returns the waveform for a main-beat accent level, or `null` for a muted beat. */
    fun forAccent(accentType: BeatAccent): ShortArray? = when (accentType) {
        BeatAccent.ACCENT -> accent
        BeatAccent.REGULAR -> regular
        BeatAccent.SUB -> sub
        BeatAccent.MUTE -> null
    }

    companion object {
        val EMPTY = ClickWaveforms(ShortArray(0), ShortArray(0), ShortArray(0))
    }
}

/**
 * Platform-agnostic click synthesis shared by every platform audio engine.
 * Produces 25 ms waveforms with a 3 ms linear fade-out so buffers end at zero (no DC clicks).
 * All synthesis happens up front so the real-time render path performs zero allocations.
 */
object ClickSynthesizer {

    const val CLICK_DURATION_SECONDS = 0.025
    const val FADE_OUT_SECONDS = 0.003

    fun synthesize(soundSet: SoundSet, pitchMultiplier: Float, sampleRate: Int): ClickWaveforms {
        val pitch = pitchMultiplier.coerceIn(0.5f, 2.0f).toDouble()
        val safeRate = sampleRate.coerceAtLeast(8000)
        val sr = safeRate.toDouble()

        val numSamples = (safeRate * CLICK_DURATION_SECONDS).toInt()
        val fadeSamples = (safeRate * FADE_OUT_SECONDS).toInt().coerceAtLeast(1)

        val accentWave = ShortArray(numSamples)
        val regularWave = ShortArray(numSamples)
        val subWave = ShortArray(numSamples)

        for (i in 0 until numSamples) {
            val t = i / sr
            val fadeOut = if (i >= numSamples - fadeSamples) {
                (numSamples - 1 - i).toDouble() / fadeSamples
            } else {
                1.0
            }

            when (soundSet) {
                SoundSet.WOODBLOCK -> {
                    val env = exp(-120.0 * t) * fadeOut
                    val fAcc = (1450.0 * pitch * exp(-150.0 * t) + 680.0 * pitch)
                    val sAcc = (sin(2.0 * PI * fAcc * t) * 0.75 + sin(4.0 * PI * fAcc * t) * 0.25) * env
                    accentWave[i] = toPcm(sAcc, 32000.0)

                    val fReg = (1100.0 * pitch * exp(-150.0 * t) + 500.0 * pitch)
                    val sReg = (sin(2.0 * PI * fReg * t) * 0.75 + sin(4.0 * PI * fReg * t) * 0.25) * env * 0.82
                    regularWave[i] = toPcm(sReg, 32000.0)

                    val fSub = (780.0 * pitch * exp(-150.0 * t) + 390.0 * pitch)
                    val sSub = (sin(2.0 * PI * fSub * t) * 0.8) * env * 0.52
                    subWave[i] = toPcm(sSub, 32000.0)
                }

                SoundSet.MECHANICAL -> {
                    val clickEnv = exp(-420.0 * t) * fadeOut
                    val bodyEnv = exp(-65.0 * t) * fadeOut
                    val clickF = 2700.0 * pitch
                    val bodyF = 600.0 * pitch

                    val sAcc = (sin(2.0 * PI * clickF * t) * clickEnv * 0.7 + sin(2.0 * PI * bodyF * t) * bodyEnv * 0.5)
                    accentWave[i] = toPcm(sAcc, 32000.0)

                    val sReg = (sin(2.0 * PI * (clickF * 0.85) * t) * clickEnv * 0.6 + sin(2.0 * PI * (bodyF * 0.85) * t) * bodyEnv * 0.4)
                    regularWave[i] = toPcm(sReg, 32000.0)

                    val sSub = (sin(2.0 * PI * (clickF * 0.7) * t) * clickEnv * 0.4)
                    subWave[i] = toPcm(sSub, 32000.0)
                }

                SoundSet.DIGITAL -> {
                    val env = exp(-90.0 * t) * fadeOut
                    val sAcc = sin(2.0 * PI * (1760.0 * pitch) * t) * env
                    accentWave[i] = toPcm(sAcc, 31000.0)

                    val sReg = sin(2.0 * PI * (880.0 * pitch) * t) * env * 0.8
                    regularWave[i] = toPcm(sReg, 31000.0)

                    val sSub = sin(2.0 * PI * (440.0 * pitch) * t) * env * 0.5
                    subWave[i] = toPcm(sSub, 31000.0)
                }

                SoundSet.RIMSHOT -> {
                    val snapEnv = exp(-360.0 * t) * fadeOut
                    val shellEnv = exp(-80.0 * t) * fadeOut
                    val snapF = 3500.0 * pitch
                    val shellF = 840.0 * pitch

                    val sAcc = (sin(2.0 * PI * snapF * t) * snapEnv * 0.7 + sin(2.0 * PI * shellF * t) * shellEnv * 0.4)
                    accentWave[i] = toPcm(sAcc, 32000.0)

                    val sReg = (sin(2.0 * PI * (snapF * 0.8) * t) * snapEnv * 0.55 + sin(2.0 * PI * (shellF * 0.8) * t) * shellEnv * 0.35)
                    regularWave[i] = toPcm(sReg, 32000.0)

                    val sSub = (sin(2.0 * PI * (snapF * 0.6) * t) * snapEnv * 0.35)
                    subWave[i] = toPcm(sSub, 32000.0)
                }

                SoundSet.ELECTRO_808 -> {
                    val env = exp(-95.0 * t) * fadeOut
                    val f1 = 587.0 * pitch
                    val f2 = 845.0 * pitch

                    val sAcc = (sin(2.0 * PI * f1 * t) * 0.6 + sin(2.0 * PI * f2 * t) * 0.4) * env
                    accentWave[i] = toPcm(sAcc, 32000.0)

                    val sReg = (sin(2.0 * PI * (f1 * 0.8) * t) * 0.5 + sin(2.0 * PI * (f2 * 0.8) * t) * 0.35) * env * 0.85
                    regularWave[i] = toPcm(sReg, 32000.0)

                    val sSub = sin(2.0 * PI * (f1 * 0.6) * t) * env * 0.45
                    subWave[i] = toPcm(sSub, 32000.0)
                }
            }
        }

        return ClickWaveforms(accentWave, regularWave, subWave)
    }

    private fun toPcm(sample: Double, gain: Double): Short =
        (sample * gain).toInt().coerceIn(-32767, 32767).toShort()
}
