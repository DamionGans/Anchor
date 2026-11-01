package com.damiongans.pulse.repository

import android.content.Context
import com.damiongans.pulse.model.AccentColorOption
import com.damiongans.pulse.model.BeatAccent
import com.damiongans.pulse.model.ButtonTriggerMode
import com.damiongans.pulse.model.SoundSet
import com.damiongans.pulse.model.Subdivision
import com.damiongans.pulse.model.ThemeMode

/**
 * Interface contract for persisting and retrieving user preferences.
 * Separates data persistence from ViewModel, allowing testing without Android context.
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
}

/**
 * Android SharedPreferences implementation of ISettingsRepository.
 */
class SharedPreferencesSettingsRepository(context: Context) : ISettingsRepository {

    private val prefs = context.getSharedPreferences("pulse_metronome_prefs", Context.MODE_PRIVATE)

    override fun getBpm(): Int {
        return prefs.getInt("bpm", 120).coerceIn(20, 300)
    }

    override fun setBpm(bpm: Int) {
        prefs.edit().putInt("bpm", bpm.coerceIn(20, 300)).apply()
    }

    override fun getTotalBeats(): Int {
        return prefs.getInt("total_beats", 4).coerceIn(1, MAX_BEATS)
    }

    override fun setTotalBeats(beats: Int) {
        prefs.edit().putInt("total_beats", beats.coerceIn(1, MAX_BEATS)).apply()
    }

    override fun getSubdivision(): Subdivision {
        val name = prefs.getString("subdivision", Subdivision.QUARTER.name) ?: Subdivision.QUARTER.name
        return try {
            Subdivision.valueOf(name)
        } catch (_: Exception) {
            Subdivision.QUARTER
        }
    }

    override fun setSubdivision(subdivision: Subdivision) {
        prefs.edit().putString("subdivision", subdivision.name).apply()
    }

    override fun getRememberedAccents(): List<BeatAccent> {
        val base = defaultAccentsForBeats(MAX_BEATS).toMutableList()
        val raw = prefs.getString("remembered_accents", null) ?: prefs.getString("accents", null)
        if (!raw.isNullOrBlank()) {
            val parsed = raw.split(",").mapNotNull { token ->
                try {
                    BeatAccent.valueOf(token.trim())
                } catch (_: Exception) {
                    null
                }
            }
            for (i in 0 until minOf(parsed.size, MAX_BEATS)) {
                base[i] = parsed[i]
            }
        }
        return base
    }

    override fun setRememberedAccents(accents: List<BeatAccent>) {
        val merged = getRememberedAccents().toMutableList()
        for (i in 0 until minOf(accents.size, MAX_BEATS)) {
            merged[i] = accents[i]
        }
        val serialized = merged.joinToString(",") { it.name }
        prefs.edit().putString("remembered_accents", serialized).apply()
    }

    override fun getAccents(totalBeats: Int): List<BeatAccent> {
        val clampedBeats = totalBeats.coerceIn(1, MAX_BEATS)
        return getRememberedAccents().take(clampedBeats)
    }

    override fun setAccents(accents: List<BeatAccent>) {
        val serialized = accents.joinToString(",") { it.name }
        val merged = getRememberedAccents().toMutableList()
        for (i in 0 until minOf(accents.size, MAX_BEATS)) {
            merged[i] = accents[i]
        }
        prefs.edit()
            .putString("accents", serialized)
            .putString("remembered_accents", merged.joinToString(",") { it.name })
            .apply()
    }

    override fun getThemeMode(): ThemeMode {
        val name = prefs.getString("theme_mode", ThemeMode.SYSTEM.name) ?: ThemeMode.SYSTEM.name
        return try {
            ThemeMode.valueOf(name)
        } catch (_: Exception) {
            ThemeMode.SYSTEM
        }
    }

    override fun setThemeMode(mode: ThemeMode) {
        prefs.edit().putString("theme_mode", mode.name).apply()
    }

    override fun getAccentColor(): AccentColorOption {
        val name = prefs.getString("accent_color", AccentColorOption.SYSTEM.name) ?: AccentColorOption.SYSTEM.name
        return try {
            AccentColorOption.valueOf(name)
        } catch (_: Exception) {
            AccentColorOption.SYSTEM
        }
    }

    override fun setAccentColor(accent: AccentColorOption) {
        prefs.edit().putString("accent_color", accent.name).apply()
    }

    override fun getFlashDelayMs(): Int {
        // Fallback to legacy timing_offset_ms if flash_delay_ms not yet set
        val fallback = prefs.getInt("timing_offset_ms", 0)
        return prefs.getInt("flash_delay_ms", fallback).coerceIn(-200, 300)
    }

    override fun setFlashDelayMs(delayMs: Int) {
        prefs.edit().putInt("flash_delay_ms", delayMs.coerceIn(-200, 300)).apply()
    }

    override fun getVibrationDelayMs(): Int {
        val fallback = prefs.getInt("timing_offset_ms", 0)
        return prefs.getInt("vibration_delay_ms", fallback).coerceIn(-200, 300)
    }

    override fun setVibrationDelayMs(delayMs: Int) {
        prefs.edit().putInt("vibration_delay_ms", delayMs.coerceIn(-200, 300)).apply()
    }

    override fun getBluetoothCompensationMs(): Int {
        return prefs.getInt("bluetooth_compensation_ms", 0).coerceIn(-100, 400)
    }

    override fun setBluetoothCompensationMs(offsetMs: Int) {
        prefs.edit().putInt("bluetooth_compensation_ms", offsetMs.coerceIn(-100, 400)).apply()
    }

    override fun isVibrationEnabled(): Boolean {
        return prefs.getBoolean("vibration_enabled", false)
    }

    override fun setVibrationEnabled(enabled: Boolean) {
        prefs.edit().putBoolean("vibration_enabled", enabled).apply()
    }

    override fun isFlashEnabled(): Boolean {
        return prefs.getBoolean("flash_enabled", false)
    }

    override fun setFlashEnabled(enabled: Boolean) {
        prefs.edit().putBoolean("flash_enabled", enabled).apply()
    }

    override fun getSoundSet(): SoundSet {
        val name = prefs.getString("sound_set", SoundSet.WOODBLOCK.name) ?: SoundSet.WOODBLOCK.name
        return try {
            SoundSet.valueOf(name)
        } catch (_: Exception) {
            SoundSet.WOODBLOCK
        }
    }

    override fun setSoundSet(soundSet: SoundSet) {
        prefs.edit().putString("sound_set", soundSet.name).apply()
    }

    override fun getPitchMultiplier(): Float {
        return prefs.getFloat("pitch_multiplier", 1.0f).coerceIn(0.5f, 2.0f)
    }

    override fun setPitchMultiplier(pitch: Float) {
        prefs.edit().putFloat("pitch_multiplier", pitch.coerceIn(0.5f, 2.0f)).apply()
    }

    override fun getButtonTriggerMode(): ButtonTriggerMode {
        val name = prefs.getString("button_trigger_mode", ButtonTriggerMode.ON_PRESS.name)
            ?: ButtonTriggerMode.ON_PRESS.name
        return try {
            ButtonTriggerMode.valueOf(name)
        } catch (_: Exception) {
            ButtonTriggerMode.ON_PRESS
        }
    }

    override fun setButtonTriggerMode(mode: ButtonTriggerMode) {
        prefs.edit().putString("button_trigger_mode", mode.name).apply()
    }

    override fun isUpdateDeclined(versionTag: String): Boolean {
        return prefs.getBoolean("declined_update_$versionTag", false)
    }

    override fun setUpdateDeclined(versionTag: String, declined: Boolean) {
        prefs.edit().putBoolean("declined_update_$versionTag", declined).apply()
    }

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
