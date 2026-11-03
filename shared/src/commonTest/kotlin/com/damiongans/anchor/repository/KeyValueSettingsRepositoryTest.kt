package com.damiongans.anchor.repository

import com.damiongans.anchor.model.BeatAccent
import com.damiongans.anchor.model.SoundSet
import com.damiongans.anchor.model.Subdivision
import com.damiongans.anchor.model.ThemeMode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class KeyValueSettingsRepositoryTest {

    /** SPEC-PERSIST-001, SPEC-TEMPO-001, SPEC-METER-001 */
    @Test
    fun defaultsAndClamping() {
        val repo = KeyValueSettingsRepository(InMemoryKeyValueStore())
        assertEquals(120, repo.getBpm())
        assertEquals(4, repo.getTotalBeats())
        assertEquals(Subdivision.QUARTER, repo.getSubdivision())
        assertEquals(ThemeMode.SYSTEM, repo.getThemeMode())
        assertEquals(SoundSet.WOODBLOCK, repo.getSoundSet())

        repo.setBpm(1000)
        assertEquals(300, repo.getBpm())
        repo.setFlashDelayMs(-999)
        assertEquals(-200, repo.getFlashDelayMs())
        repo.setBluetoothCompensationMs(999)
        assertEquals(400, repo.getBluetoothCompensationMs())
        repo.setPitchMultiplier(0.1f)
        assertEquals(0.5f, repo.getPitchMultiplier())
    }

    /** SPEC-ACCENT-002 */
    @Test
    fun accentsAreRememberedPerBeatCount() {
        val repo = KeyValueSettingsRepository(InMemoryKeyValueStore())
        repo.setAccents(listOf(BeatAccent.ACCENT, BeatAccent.SUB, BeatAccent.REGULAR, BeatAccent.MUTE))
        repo.setAccents(listOf(BeatAccent.REGULAR, BeatAccent.SUB, BeatAccent.REGULAR))
        val remembered = repo.getRememberedAccents()
        assertEquals(16, remembered.size)
        assertEquals(BeatAccent.REGULAR, remembered[0])
        assertEquals(BeatAccent.MUTE, remembered[3])
        assertEquals(remembered.take(6), repo.getAccents(6))
    }

    /** SPEC-PERSIST-001 */
    @Test
    fun legacyKeysAndInvalidValuesFallBack() {
        val store = InMemoryKeyValueStore()
        store.putInt(KeyValueSettingsRepository.KEY_LEGACY_TIMING_OFFSET, -40)
        store.putString(KeyValueSettingsRepository.KEY_SOUND_SET, "NOT_A_SOUND")
        val repo = KeyValueSettingsRepository(store)
        assertEquals(-40, repo.getFlashDelayMs())
        assertEquals(-40, repo.getVibrationDelayMs())
        assertEquals(SoundSet.WOODBLOCK, repo.getSoundSet())

        assertFalse(repo.isUpdateDeclined("v9.9.9"))
        repo.setUpdateDeclined("v9.9.9", true)
        assertTrue(repo.isUpdateDeclined("v9.9.9"))
    }
}
