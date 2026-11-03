package com.damiongans.anchor.model

import com.damiongans.anchor.playback.PlaybackSessionState
import com.damiongans.anchor.repository.ISettingsRepository
import com.damiongans.anchor.viewmodel.MetronomeUiState
import kotlin.test.Test
import kotlin.test.assertEquals

/** Pure model rules from `docs/SPEC.md` that need no controller or platform. */
class ModelSpecTest {

    /** SPEC-TEMPO-003 */
    @Test
    fun tempoMarkings_coverEveryBpmWithTheSpecifiedRanges() {
        val expected = listOf(
            20 to "Larghissimo", 39 to "Larghissimo", 40 to "Grave", 49 to "Grave", 50 to "Largo",
            59 to "Largo", 60 to "Adagio", 71 to "Adagio", 72 to "Andante", 87 to "Andante",
            88 to "Moderato", 107 to "Moderato", 108 to "Allegretto", 119 to "Allegretto",
            120 to "Allegro", 155 to "Allegro", 156 to "Vivace", 175 to "Vivace", 176 to "Presto",
            199 to "Presto", 200 to "Prestissimo", 300 to "Prestissimo"
        )
        expected.forEach { (bpm, name) -> assertEquals(name, TempoMarking.forBpm(bpm), "bpm $bpm") }
        // No gaps or overlaps between consecutive ranges.
        TempoMarking.ALL.zipWithNext().forEach { (a, b) -> assertEquals(a.maxBpm + 1, b.minBpm) }
    }

    /** SPEC-METER-002 */
    @Test
    fun subdivisions_areQuarterEighthTripletSixteenth() {
        assertEquals(
            listOf("QUARTER" to 1, "EIGHTH" to 2, "TRIPLET" to 3, "SIXTEENTH" to 4),
            Subdivision.entries.map { it.name to it.count }
        )
    }

    /** SPEC-METER-003 */
    @Test
    fun meterDisplay_usesEighthsOnlyForEighthSubdivision() {
        assertEquals(8, MetronomeUiState(totalBeats = 6, currentSubdivision = Subdivision.EIGHTH).timeSignatureDenominator)
        assertEquals(4, MetronomeUiState(totalBeats = 4, currentSubdivision = Subdivision.SIXTEENTH).timeSignatureDenominator)
        assertEquals("7/8", Song("s", "x", 120, 7, Subdivision.EIGHTH, emptyList()).timeSignatureDisplay)
        assertEquals("3/4", Song("s", "x", 120, 3, Subdivision.TRIPLET, emptyList()).timeSignatureDisplay)
        assertEquals(
            listOf("1/4", "2/4", "3/4", "4/4", "5/4", "6/8", "7/8", "9/8", "12/8"),
            TimeSignaturePreset.entries.map { it.displayName }
        )
        TimeSignaturePreset.entries.forEach { preset ->
            val denominator = if (preset.subdivision == Subdivision.EIGHTH) 8 else 4
            assertEquals("${preset.beats}/$denominator", preset.displayName)
        }
    }

    /** SPEC-ACCENT-001 */
    @Test
    fun accents_cycleThroughAllFourLevelsAndDefaultToDownbeatAccent() {
        assertEquals(
            listOf(BeatAccent.REGULAR, BeatAccent.SUB, BeatAccent.MUTE, BeatAccent.ACCENT),
            BeatAccent.entries.map { it.next() }
        )
        assertEquals(
            listOf(BeatAccent.ACCENT, BeatAccent.REGULAR, BeatAccent.REGULAR),
            ISettingsRepository.defaultAccentsForBeats(3)
        )
        assertEquals(ISettingsRepository.MAX_BEATS, ISettingsRepository.defaultAccentsForBeats(99).size)
    }

    /** SPEC-AUDIO-001 */
    @Test
    fun soundSets_areTheFiveSynthesizedKits() {
        assertEquals(
            listOf(SoundSet.WOODBLOCK, SoundSet.MECHANICAL, SoundSet.DIGITAL, SoundSet.RIMSHOT, SoundSet.ELECTRO_808),
            SoundSet.entries
        )
        assertEquals(SoundSet.WOODBLOCK, MetronomeUiState().currentSoundSet)
    }

    /** SPEC-TIMING-003 */
    @Test
    fun bluetoothOffset_appliesOnlyWhileTheActiveOutputIsBluetooth() {
        val base = MetronomeUiState(flashDelayMs = 20, vibrationDelayMs = -10, bluetoothCompensationMs = 150)
        assertEquals(20, base.effectiveVisualDelayMs)
        assertEquals(-10, base.effectiveVibrationDelayMs)

        val bluetooth = base.copy(audioInfo = AudioOutputInfo(deviceType = AudioDeviceType.BLUETOOTH))
        assertEquals(170, bluetooth.effectiveVisualDelayMs)
        assertEquals(140, bluetooth.effectiveVibrationDelayMs)
    }

    /** SPEC-SESSION-001 */
    @Test
    fun mediaSessionText_showsTempoMarkingAndTransportState() {
        val playing = PlaybackSessionState(bpm = 96, beats = 6, denominator = 8, isPlaying = true)
        assertEquals("96 BPM • Moderato", playing.title)
        assertEquals("Playing • 6/8 Time", playing.subtitle)
        assertEquals("Paused • 6/8 Time", playing.copy(isPlaying = false).subtitle)
    }
}
