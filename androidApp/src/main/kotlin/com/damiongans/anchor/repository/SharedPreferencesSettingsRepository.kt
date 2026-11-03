package com.damiongans.anchor.repository

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import com.damiongans.anchor.model.BeatAccent

/**
 * Android `SharedPreferences` implementation of the shared [IKeyValueStore].
 */
class SharedPreferencesKeyValueStore(private val prefs: SharedPreferences) : IKeyValueStore {
    override fun contains(key: String): Boolean = prefs.contains(key)
    override fun getInt(key: String, default: Int): Int = prefs.getInt(key, default)
    override fun putInt(key: String, value: Int) = prefs.edit { putInt(key, value) }
    override fun getFloat(key: String, default: Float): Float = prefs.getFloat(key, default)
    override fun putFloat(key: String, value: Float) = prefs.edit { putFloat(key, value) }
    override fun getBoolean(key: String, default: Boolean): Boolean = prefs.getBoolean(key, default)
    override fun putBoolean(key: String, value: Boolean) = prefs.edit { putBoolean(key, value) }
    override fun getString(key: String): String? = prefs.getString(key, null)
    override fun putString(key: String, value: String) = prefs.edit { putString(key, value) }
}

/**
 * Android settings repository: the shared [KeyValueSettingsRepository] backed by
 * `SharedPreferences`. Automatically migrates existing preferences from the legacy
 * `pulse_metronome_prefs` file if present.
 */
class SharedPreferencesSettingsRepository private constructor(
    store: SharedPreferencesKeyValueStore
) : KeyValueSettingsRepository(store) {

    constructor(context: Context) : this(SharedPreferencesKeyValueStore(openMigratedPrefs(context)))

    companion object {
        const val PREFS_NAME = "anchor_metronome_prefs"
        private const val LEGACY_PREFS_NAME = "pulse_metronome_prefs"
        const val MAX_BEATS = ISettingsRepository.MAX_BEATS

        fun defaultAccentsForBeats(beats: Int): List<BeatAccent> =
            ISettingsRepository.defaultAccentsForBeats(beats)

        private fun openMigratedPrefs(context: Context): SharedPreferences {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            if (prefs.all.isEmpty()) {
                val legacyEntries = context.getSharedPreferences(LEGACY_PREFS_NAME, Context.MODE_PRIVATE).all
                if (legacyEntries.isNotEmpty()) {
                    val editor = prefs.edit()
                    for ((key, value) in legacyEntries) {
                        when (value) {
                            is Int -> editor.putInt(key, value)
                            is Boolean -> editor.putBoolean(key, value)
                            is Float -> editor.putFloat(key, value)
                            is Long -> editor.putLong(key, value)
                            is String -> editor.putString(key, value)
                        }
                    }
                    editor.apply()
                }
            }
            return prefs
        }
    }
}
