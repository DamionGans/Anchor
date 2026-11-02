package com.damiongans.anchor.repository

import com.damiongans.anchor.domain.DelayCompensationEngine
import com.damiongans.anchor.model.AccentColorOption
import com.damiongans.anchor.model.BeatAccent
import com.damiongans.anchor.model.ButtonTriggerMode
import com.damiongans.anchor.model.SoundSet
import com.damiongans.anchor.model.Subdivision
import com.damiongans.anchor.model.ThemeMode

/**
 * Minimal platform key-value persistence contract (Android `SharedPreferences`,
 * iOS `NSUserDefaults`, or an in-memory map for tests).
 */
interface IKeyValueStore {
    fun contains(key: String): Boolean
    fun getInt(key: String, default: Int): Int
    fun putInt(key: String, value: Int)
    fun getFloat(key: String, default: Float): Float
    fun putFloat(key: String, value: Float)
    fun getBoolean(key: String, default: Boolean): Boolean
    fun putBoolean(key: String, value: Boolean)
    fun getString(key: String): String?
    fun putString(key: String, value: String)
}

/**
 * Simple in-memory [IKeyValueStore] (unit tests, previews).
 */
class InMemoryKeyValueStore : IKeyValueStore {
    private val values = mutableMapOf<String, Any>()

    override fun contains(key: String): Boolean = values.containsKey(key)
    override fun getInt(key: String, default: Int): Int = values[key] as? Int ?: default
    override fun putInt(key: String, value: Int) { values[key] = value }
    override fun getFloat(key: String, default: Float): Float = values[key] as? Float ?: default
    override fun putFloat(key: String, value: Float) { values[key] = value }
    override fun getBoolean(key: String, default: Boolean): Boolean = values[key] as? Boolean ?: default
    override fun putBoolean(key: String, value: Boolean) { values[key] = value }
    override fun getString(key: String): String? = values[key] as? String
    override fun putString(key: String, value: String) { values[key] = value }
}

/**
 * Shared [ISettingsRepository] implementation: storage keys, defaults, clamping, legacy
 * fallbacks, and per-count accent memory live here once for every platform. Platforms only
 * provide an [IKeyValueStore].
 */
open class KeyValueSettingsRepository(private val store: IKeyValueStore) : ISettingsRepository {

    override fun getBpm(): Int = store.getInt(KEY_BPM, 120).coerceIn(20, 300)

    override fun setBpm(bpm: Int) = store.putInt(KEY_BPM, bpm.coerceIn(20, 300))

    override fun getTotalBeats(): Int =
        store.getInt(KEY_TOTAL_BEATS, 4).coerceIn(1, ISettingsRepository.MAX_BEATS)

    override fun setTotalBeats(beats: Int) =
        store.putInt(KEY_TOTAL_BEATS, beats.coerceIn(1, ISettingsRepository.MAX_BEATS))

    override fun getSubdivision(): Subdivision =
        enumOrDefault(store.getString(KEY_SUBDIVISION), Subdivision.QUARTER) { Subdivision.valueOf(it) }

    override fun setSubdivision(subdivision: Subdivision) = store.putString(KEY_SUBDIVISION, subdivision.name)

    override fun getRememberedAccents(): List<BeatAccent> {
        val base = ISettingsRepository.defaultAccentsForBeats(ISettingsRepository.MAX_BEATS).toMutableList()
        val raw = store.getString(KEY_REMEMBERED_ACCENTS) ?: store.getString(KEY_ACCENTS)
        if (!raw.isNullOrBlank()) {
            val parsed = raw.split(",").mapNotNull { token ->
                try {
                    BeatAccent.valueOf(token.trim())
                } catch (_: Exception) {
                    null
                }
            }
            for (i in 0 until minOf(parsed.size, ISettingsRepository.MAX_BEATS)) {
                base[i] = parsed[i]
            }
        }
        return base
    }

    override fun setRememberedAccents(accents: List<BeatAccent>) {
        store.putString(KEY_REMEMBERED_ACCENTS, serialize(mergeIntoRemembered(accents)))
    }

    override fun getAccents(totalBeats: Int): List<BeatAccent> =
        getRememberedAccents().take(totalBeats.coerceIn(1, ISettingsRepository.MAX_BEATS))

    override fun setAccents(accents: List<BeatAccent>) {
        val merged = mergeIntoRemembered(accents)
        store.putString(KEY_ACCENTS, serialize(accents))
        store.putString(KEY_REMEMBERED_ACCENTS, serialize(merged))
    }

    override fun getThemeMode(): ThemeMode =
        enumOrDefault(store.getString(KEY_THEME_MODE), ThemeMode.SYSTEM) { ThemeMode.valueOf(it) }

    override fun setThemeMode(mode: ThemeMode) = store.putString(KEY_THEME_MODE, mode.name)

    override fun getAccentColor(): AccentColorOption =
        enumOrDefault(store.getString(KEY_ACCENT_COLOR), AccentColorOption.SYSTEM) { AccentColorOption.valueOf(it) }

    override fun setAccentColor(accent: AccentColorOption) = store.putString(KEY_ACCENT_COLOR, accent.name)

    override fun getFlashDelayMs(): Int {
        val fallback = store.getInt(KEY_LEGACY_TIMING_OFFSET, 0)
        return store.getInt(KEY_FLASH_DELAY, fallback).coerceIn(
            DelayCompensationEngine.MIN_VISUAL_DELAY_MS,
            DelayCompensationEngine.MAX_VISUAL_DELAY_MS
        )
    }

    override fun setFlashDelayMs(delayMs: Int) = store.putInt(
        KEY_FLASH_DELAY,
        delayMs.coerceIn(DelayCompensationEngine.MIN_VISUAL_DELAY_MS, DelayCompensationEngine.MAX_VISUAL_DELAY_MS)
    )

    override fun getVibrationDelayMs(): Int {
        val fallback = store.getInt(KEY_LEGACY_TIMING_OFFSET, 0)
        return store.getInt(KEY_VIBRATION_DELAY, fallback).coerceIn(
            DelayCompensationEngine.MIN_VIBRATION_DELAY_MS,
            DelayCompensationEngine.MAX_VIBRATION_DELAY_MS
        )
    }

    override fun setVibrationDelayMs(delayMs: Int) = store.putInt(
        KEY_VIBRATION_DELAY,
        delayMs.coerceIn(DelayCompensationEngine.MIN_VIBRATION_DELAY_MS, DelayCompensationEngine.MAX_VIBRATION_DELAY_MS)
    )

    override fun getBluetoothCompensationMs(): Int = store.getInt(KEY_BLUETOOTH_COMP, 0).coerceIn(
        DelayCompensationEngine.MIN_BLUETOOTH_OFFSET_MS,
        DelayCompensationEngine.MAX_BLUETOOTH_OFFSET_MS
    )

    override fun setBluetoothCompensationMs(offsetMs: Int) = store.putInt(
        KEY_BLUETOOTH_COMP,
        offsetMs.coerceIn(DelayCompensationEngine.MIN_BLUETOOTH_OFFSET_MS, DelayCompensationEngine.MAX_BLUETOOTH_OFFSET_MS)
    )

    override fun isVibrationEnabled(): Boolean = store.getBoolean(KEY_VIBRATION_ENABLED, false)

    override fun setVibrationEnabled(enabled: Boolean) = store.putBoolean(KEY_VIBRATION_ENABLED, enabled)

    override fun isFlashEnabled(): Boolean = store.getBoolean(KEY_FLASH_ENABLED, false)

    override fun setFlashEnabled(enabled: Boolean) = store.putBoolean(KEY_FLASH_ENABLED, enabled)

    override fun getSoundSet(): SoundSet =
        enumOrDefault(store.getString(KEY_SOUND_SET), SoundSet.WOODBLOCK) { SoundSet.valueOf(it) }

    override fun setSoundSet(soundSet: SoundSet) = store.putString(KEY_SOUND_SET, soundSet.name)

    override fun getPitchMultiplier(): Float = store.getFloat(KEY_PITCH, 1.0f).coerceIn(0.5f, 2.0f)

    override fun setPitchMultiplier(pitch: Float) = store.putFloat(KEY_PITCH, pitch.coerceIn(0.5f, 2.0f))

    override fun getButtonTriggerMode(): ButtonTriggerMode =
        enumOrDefault(store.getString(KEY_BUTTON_TRIGGER_MODE), ButtonTriggerMode.ON_PRESS) { ButtonTriggerMode.valueOf(it) }

    override fun setButtonTriggerMode(mode: ButtonTriggerMode) = store.putString(KEY_BUTTON_TRIGGER_MODE, mode.name)

    override fun isUpdateDeclined(versionTag: String): Boolean =
        store.getBoolean(KEY_DECLINED_UPDATE_PREFIX + versionTag, false)

    override fun setUpdateDeclined(versionTag: String, declined: Boolean) =
        store.putBoolean(KEY_DECLINED_UPDATE_PREFIX + versionTag, declined)

    private fun mergeIntoRemembered(accents: List<BeatAccent>): List<BeatAccent> {
        val merged = getRememberedAccents().toMutableList()
        for (i in 0 until minOf(accents.size, ISettingsRepository.MAX_BEATS)) {
            merged[i] = accents[i]
        }
        return merged
    }

    private fun serialize(accents: List<BeatAccent>): String = accents.joinToString(",") { it.name }

    private inline fun <T> enumOrDefault(raw: String?, default: T, parse: (String) -> T): T {
        if (raw == null) return default
        return try {
            parse(raw)
        } catch (_: Exception) {
            default
        }
    }

    companion object {
        const val KEY_BPM = "bpm"
        const val KEY_TOTAL_BEATS = "total_beats"
        const val KEY_SUBDIVISION = "subdivision"
        const val KEY_ACCENTS = "accents"
        const val KEY_REMEMBERED_ACCENTS = "remembered_accents"
        const val KEY_THEME_MODE = "theme_mode"
        const val KEY_ACCENT_COLOR = "accent_color"
        const val KEY_LEGACY_TIMING_OFFSET = "timing_offset_ms"
        const val KEY_FLASH_DELAY = "flash_delay_ms"
        const val KEY_VIBRATION_DELAY = "vibration_delay_ms"
        const val KEY_BLUETOOTH_COMP = "bluetooth_compensation_ms"
        const val KEY_VIBRATION_ENABLED = "vibration_enabled"
        const val KEY_FLASH_ENABLED = "flash_enabled"
        const val KEY_SOUND_SET = "sound_set"
        const val KEY_PITCH = "pitch_multiplier"
        const val KEY_BUTTON_TRIGGER_MODE = "button_trigger_mode"
        const val KEY_DECLINED_UPDATE_PREFIX = "declined_update_"
    }
}
