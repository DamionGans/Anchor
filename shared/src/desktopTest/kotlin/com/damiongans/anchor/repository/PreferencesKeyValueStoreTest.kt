package com.damiongans.anchor.repository

import com.damiongans.anchor.model.SoundSet
import java.util.prefs.Preferences
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PreferencesKeyValueStoreTest {

    private lateinit var node: Preferences

    @BeforeTest
    fun setUp() {
        node = Preferences.userRoot().node("com/damiongans/anchor-test-${System.nanoTime()}")
    }

    @AfterTest
    fun tearDown() {
        node.removeNode()
    }

    @Test
    fun roundTripsAllValueTypes() {
        val store = PreferencesKeyValueStore(node)
        assertFalse(store.contains("bpm"))
        assertEquals(7, store.getInt("bpm", 7))
        assertNull(store.getString("sound_set"))

        store.putInt("bpm", 144)
        store.putFloat("pitch", 1.25f)
        store.putBoolean("flash", true)
        store.putString("sound_set", "RIMSHOT")

        assertTrue(store.contains("bpm"))
        assertEquals(144, store.getInt("bpm", 0))
        assertEquals(1.25f, store.getFloat("pitch", 0f))
        assertTrue(store.getBoolean("flash", false))
        assertEquals("RIMSHOT", store.getString("sound_set"))
    }

    @Test
    fun sharedSettingsRepositoryPersistsAcrossInstances() {
        KeyValueSettingsRepository(PreferencesKeyValueStore(node)).apply {
            setBpm(999)
            setSoundSet(SoundSet.ELECTRO_808)
        }
        val restored = KeyValueSettingsRepository(PreferencesKeyValueStore(node))
        assertEquals(300, restored.getBpm()) // shared clamping applies
        assertEquals(SoundSet.ELECTRO_808, restored.getSoundSet())
    }
}
