package com.damiongans.anchor.repository

import platform.Foundation.NSUserDefaults

/**
 * iOS `NSUserDefaults` implementation of the shared [IKeyValueStore].
 */
class NSUserDefaultsKeyValueStore(
    private val defaults: NSUserDefaults = NSUserDefaults.standardUserDefaults
) : IKeyValueStore {
    override fun contains(key: String): Boolean = defaults.objectForKey(key) != null

    override fun getInt(key: String, default: Int): Int =
        if (contains(key)) defaults.integerForKey(key).toInt() else default

    override fun putInt(key: String, value: Int) = defaults.setInteger(value.toLong(), forKey = key)

    override fun getFloat(key: String, default: Float): Float =
        if (contains(key)) defaults.floatForKey(key) else default

    override fun putFloat(key: String, value: Float) = defaults.setFloat(value, forKey = key)

    override fun getBoolean(key: String, default: Boolean): Boolean =
        if (contains(key)) defaults.boolForKey(key) else default

    override fun putBoolean(key: String, value: Boolean) = defaults.setBool(value, forKey = key)

    override fun getString(key: String): String? = defaults.stringForKey(key)

    override fun putString(key: String, value: String) = defaults.setObject(value, forKey = key)
}
