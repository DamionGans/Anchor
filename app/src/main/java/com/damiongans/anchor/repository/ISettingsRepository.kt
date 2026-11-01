package com.damiongans.anchor.repository

import com.damiongans.anchor.model.AccentColorOption
import com.damiongans.anchor.model.BeatAccent
import com.damiongans.anchor.model.ButtonTriggerMode
import com.damiongans.anchor.model.SoundSet
import com.damiongans.anchor.model.Subdivision
import com.damiongans.anchor.model.ThemeMode

/**
 * Platform-agnostic interface contract for persisting and retrieving user preferences.
 * Separates data persistence from ViewModel/domain logic so implementations can be provided
 * via Android SharedPreferences, iOS NSUserDefaults, or Desktop preferences, as well as
 * in-memory fakes for unit testing.
 */
interface ISettingsRepository {
    fun getBpm(): Int
    fun setBpm(bpm: Int)

    fun getTotalBeats(): Int
    fun setTotalBeats(beats: Int)

    fun getSubdivision(): Subdivision
    fun setSubdivision(subdivision: Subdivision)

    fun getAccents(totalBeats: Int): List<BeatAccent>
    fun setAccents(accents: List<BeatAccent>)

    fun getRememberedAccents(): List<BeatAccent>
    fun setRememberedAccents(accents: List<BeatAccent>)

    fun getThemeMode(): ThemeMode
    fun setThemeMode(mode: ThemeMode)

    fun getAccentColor(): AccentColorOption
    fun setAccentColor(accent: AccentColorOption)

    fun getFlashDelayMs(): Int
    fun setFlashDelayMs(delayMs: Int)

    fun getVibrationDelayMs(): Int
    fun setVibrationDelayMs(delayMs: Int)

    fun getBluetoothCompensationMs(): Int
    fun setBluetoothCompensationMs(offsetMs: Int)

    fun isVibrationEnabled(): Boolean
    fun setVibrationEnabled(enabled: Boolean)

    fun isFlashEnabled(): Boolean
    fun setFlashEnabled(enabled: Boolean)

    fun getSoundSet(): SoundSet
    fun setSoundSet(soundSet: SoundSet)

    fun getPitchMultiplier(): Float
    fun setPitchMultiplier(pitch: Float)

    fun getButtonTriggerMode(): ButtonTriggerMode
    fun setButtonTriggerMode(mode: ButtonTriggerMode)

    fun isUpdateDeclined(versionTag: String): Boolean
    fun setUpdateDeclined(versionTag: String, declined: Boolean)

    companion object {
        const val MAX_BEATS = 16

        fun defaultAccentsForBeats(beats: Int): List<BeatAccent> {
            val count = beats.coerceIn(1, MAX_BEATS)
            return (0 until count).map { idx ->
                if (idx == 0) BeatAccent.ACCENT else BeatAccent.REGULAR
            }
        }
    }
}
