package com.damiongans.anchor

import com.damiongans.anchor.model.AccentColorOption
import com.damiongans.anchor.model.AudioDeviceType
import com.damiongans.anchor.model.BeatAccent
import com.damiongans.anchor.model.SoundSet
import com.damiongans.anchor.model.Subdivision
import com.damiongans.anchor.model.TapTempoStatus
import com.damiongans.anchor.model.TempoMarking
import com.damiongans.anchor.model.ThemeMode
import com.damiongans.anchor.model.TimeSignaturePreset
import com.damiongans.anchor.ui.theme.primaryColor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MetronomeUnitTest {

    @Test
    fun tempoMarking_forVariousBpm_returnsCorrectNames() {
        assertEquals("Larghissimo", TempoMarking.forBpm(30))
        assertEquals("Largo", TempoMarking.forBpm(55))
        assertEquals("Adagio", TempoMarking.forBpm(65))
        assertEquals("Andante", TempoMarking.forBpm(80))
        assertEquals("Moderato", TempoMarking.forBpm(100))
        assertEquals("Allegro", TempoMarking.forBpm(120))
        assertEquals("Presto", TempoMarking.forBpm(180))
        assertEquals("Prestissimo", TempoMarking.forBpm(220))
    }

    @Test
    fun timeSignaturePreset_hasValidConfigurations() {
        assertEquals(4, TimeSignaturePreset.FOUR_FOUR.beats)
        assertEquals(Subdivision.QUARTER, TimeSignaturePreset.FOUR_FOUR.subdivision)

        assertEquals(6, TimeSignaturePreset.SIX_EIGHT.beats)
        assertEquals(Subdivision.EIGHTH, TimeSignaturePreset.SIX_EIGHT.subdivision)
    }

    @Test
    fun subdivisions_havePositiveCounts() {
        Subdivision.values().forEach {
            assertNotNull(it.displayName)
            assert(it.count >= 1)
        }
    }

    @Test
    fun soundSets_areAllAvailable() {
        assertEquals(5, SoundSet.values().size)
    }

    @Test
    fun beatAccents_allHaveLabels() {
        BeatAccent.values().forEach {
            assertNotNull(it.label)
        }
    }

    @Test
    fun audioDeviceType_hasLatencyCategories() {
        AudioDeviceType.values().forEach {
            assertNotNull(it.displayName)
            assertNotNull(it.latencyCategory)
        }
        assertTrue(AudioDeviceType.BLUETOOTH.defaultCompensationMs > 0)
        assertEquals(0, AudioDeviceType.SPEAKER.defaultCompensationMs)
        assertEquals(0, AudioDeviceType.USB_AUDIO.defaultCompensationMs)
    }

    @Test
    fun tapTempoStatus_defaultValues() {
        val status = TapTempoStatus()
        assertEquals(0, status.tapCount)
        assertEquals(false, status.isHighCertainty)
    }

    @Test
    fun themeModes_andAccentColors_areAvailable() {
        assertEquals(3, ThemeMode.values().size)
        assertTrue(AccentColorOption.values().isNotEmpty())
        AccentColorOption.values().forEach {
            assertNotNull(it.displayName)
            assertNotNull(it.primaryColor)
        }
    }
}
