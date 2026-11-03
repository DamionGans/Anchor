package com.damiongans.anchor.audio

import com.damiongans.anchor.model.SoundSet
import com.damiongans.anchor.model.Subdivision
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class WarmupAndSlewTest {

    /** SPEC-AUDIO-002 */
    @Test
    fun warmupDither_coversAtLeast55msForEveryRealisticChunkSize() {
        for (sampleRate in listOf(44_100, 48_000, 96_000)) {
            for (chunk in listOf(64, 96, 128, 192, 240, 256, 441, 480, 512, 1024)) {
                val frames = MetronomeSequencer.warmupChunkCount(sampleRate, chunk).toLong() * chunk
                val ms = frames * 1000.0 / sampleRate
                assertTrue(ms >= 55.0, "$sampleRate Hz / $chunk frames gives only $ms ms of warmup")
            }
        }
        assertEquals(3, MetronomeSequencer.warmupChunkCount(48_000, 10_000, minimumChunks = 3))
    }

    /** SPEC-AUDIO-002 */
    @Test
    fun warmupDither_isPlusMinusOneLsb() {
        val buffer = ShortArray(8) { 1234 }
        MetronomeSequencer.fillWarmupDither(buffer, 6)
        assertEquals(listOf<Short>(1, -1, 1, -1, 1, -1, 1234, 1234), buffer.toList())
    }

    /** SPEC-PULSE-001 */
    @Test
    fun bpmChanges_slewByAtMostTheChunkLimit() {
        val sampleRate = 48_000
        val sequencer = MetronomeSequencer().apply {
            bpm = 60.0
            totalBeats = 4
            subdivision = Subdivision.QUARTER
            waveforms = ClickSynthesizer.synthesize(SoundSet.DIGITAL, 1.0f, sampleRate)
            prepare(sampleRate, 0)
        }
        val buffer = ShortArray(256)
        sequencer.bpm = 100.0
        var previous = sequencer.smoothedBpm
        var chunks = 0
        while (sequencer.smoothedBpm != 100.0) {
            sequencer.renderChunk(buffer, buffer.size)
            val step = sequencer.smoothedBpm - previous
            assertTrue(step > 0 && step <= MetronomeSequencer.MAX_BPM_STEP_PER_CHUNK + 1e-9, "step $step")
            previous = sequencer.smoothedBpm
            chunks++
            assertTrue(chunks < 100, "smoothed BPM never reached the target")
        }
        assertEquals(20, chunks)
    }
}
