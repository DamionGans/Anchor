package com.damiongans.anchor.repository

import java.util.prefs.Preferences

/**
 * Desktop [IKeyValueStore] backed by `java.util.prefs.Preferences` (Windows: the user registry
 * under `HKEY_CURRENT_USER\Software\JavaSoft\Prefs`). Used with the shared
 * [KeyValueSettingsRepository], so keys, defaults, and clamping match Android and iOS.
 */
class PreferencesKeyValueStore(
    private val prefs: Preferences = Preferences.userRoot().node(DEFAULT_NODE)
) : IKeyValueStore {

    override fun contains(key: String): Boolean = prefs.get(key, null) != null

    override fun getInt(key: String, default: Int): Int = prefs.getInt(key, default)

    override fun putInt(key: String, value: Int) = write { prefs.putInt(key, value) }

    override fun getFloat(key: String, default: Float): Float = prefs.getFloat(key, default)

    override fun putFloat(key: String, value: Float) = write { prefs.putFloat(key, value) }

    override fun getBoolean(key: String, default: Boolean): Boolean = prefs.getBoolean(key, default)

    override fun putBoolean(key: String, value: Boolean) = write { prefs.putBoolean(key, value) }

    override fun getString(key: String): String? = prefs.get(key, null)

    override fun putString(key: String, value: String) = write { prefs.put(key, value) }

    private inline fun write(block: () -> Unit) {
        block()
        try {
            prefs.flush()
        } catch (_: Exception) {
            // Preferences persist asynchronously as well; a failed flush is retried by the JVM.
        }
    }

    companion object {
        const val DEFAULT_NODE = "com/damiongans/anchor"
    }
}
