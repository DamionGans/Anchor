package com.damiongans.anchor.audio

import com.damiongans.anchor.model.AudioDeviceType
import com.damiongans.anchor.model.AudioOutputDevice
import com.damiongans.anchor.model.BeatAccent
import com.damiongans.anchor.model.SoundSet
import com.damiongans.anchor.model.Subdivision
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MetronomeSequencerTest {

    private val sampleRate = 48000

    /** SPEC-AUDIO-001 */
    @Test
    fun clickSynthesizer_producesFadedWaveformsForEverySoundSet() {
        for (soundSet in SoundSet.entries) {
            val waves = ClickSynthesizer.synthesize(soundSet, 1.0f, sampleRate)
            val expectedSize = (sampleRate * ClickSynthesizer.CLICK_DURATION_SECONDS).toInt()
            assertEquals(expectedSize, waves.accent.size)
            assertEquals(0, waves.accent.last().toInt(), "Waveform must end at zero for $soundSet")
            assertTrue(waves.accent.any { it.toInt() != 0 })
            assertEquals(null, waves.forAccent(BeatAccent.MUTE))
        }
    }

    /** SPEC-PULSE-001, SPEC-TIMING-001, SPEC-AUDIO-002 */
    @Test
    fun sequencer_placesBeatsAtExactFramesAndDispatchesOnPlayhead() {
        val sequencer = MetronomeSequencer()
        sequencer.bpm = 120.0
        sequencer.totalBeats = 4
        sequencer.subdivision = Subdivision.QUARTER
        sequencer.waveforms = ClickSynthesizer.synthesize(SoundSet.DIGITAL, 1.0f, sampleRate)

        val visual = mutableListOf<Int>()
        var bars = 0
        sequencer.onVisualTick = { beat, _, _ -> visual.add(beat) }
        sequencer.onBarCompleted = { bars++ }

        val chunk = 480
        val warmupChunks = MetronomeSequencer.warmupChunkCount(sampleRate, chunk)
        val warmupFrames = warmupChunks.toLong() * chunk
        sequencer.prepare(sampleRate, warmupFrames)

        val buffer = ShortArray(chunk)
        repeat(warmupChunks) {
            MetronomeSequencer.fillWarmupDither(buffer, chunk)
            assertEquals(1, buffer[0].toInt())
            assertEquals(-1, buffer[1].toInt())
            sequencer.advanceWarmup(chunk)
        }
        assertEquals(warmupFrames, sequencer.totalFramesWritten)

        sequencer.startFirstBeat()
        // 120 BPM quarter notes = 24000 frames per beat; render 2.5 bars (5 seconds).
        val framesToRender = sampleRate * 5
        var rendered = 0
        var firstChunkHasClick = false
        while (rendered < framesToRender) {
            sequencer.renderChunk(buffer, chunk)
            if (rendered == 0) firstChunkHasClick = buffer.any { it.toInt() != 0 }
            rendered += chunk
        }
        assertTrue(firstChunkHasClick, "Beat 1 must sound immediately after the warmup")

        // Nothing is due before Beat 1 reaches the playhead (0 ms offset, no magic constants).
        sequencer.processPlayhead(warmupFrames - 1)
        assertTrue(visual.isEmpty())

        sequencer.processPlayhead(warmupFrames)
        assertEquals(listOf(0), visual)

        sequencer.processPlayhead(warmupFrames + 24000L * 4)
        assertEquals(listOf(0, 1, 2, 3, 0), visual)
        assertEquals(1, bars)
    }

    /** SPEC-AUDIO-003 */
    @Test
    fun warmStart_onRunningOutput_placesBeat1AtTheNextFrameOfTheOutputTimeline() {
        val sequencer = MetronomeSequencer()
        sequencer.bpm = 120.0
        sequencer.totalBeats = 4
        sequencer.subdivision = Subdivision.QUARTER
        sequencer.waveforms = ClickSynthesizer.synthesize(SoundSet.DIGITAL, 1.0f, sampleRate)
        val visual = mutableListOf<Int>()
        sequencer.onVisualTick = { beat, _, _ -> visual.add(beat) }

        // The output has been running in standby for ~3 hours (beyond any Int-sized warmup count).
        val outputFrame = sampleRate.toLong() * 60 * 60 * 3 + 123
        repeat(2) { // a second run on the same output restarts cleanly at Beat 1
            visual.clear()
            sequencer.prepare(sampleRate, outputFrame)
            sequencer.alignToOutputFrame(outputFrame)
            assertEquals(outputFrame, sequencer.totalFramesWritten)
            sequencer.startFirstBeat()

            val chunk = ShortArray(256)
            sequencer.renderChunk(chunk, chunk.size)
            assertTrue(chunk.any { it.toInt() != 0 }, "Beat 1 must sound in the first chunk")

            sequencer.processPlayhead(outputFrame - 1)
            assertTrue(visual.isEmpty())
            sequencer.processPlayhead(outputFrame)
            assertEquals(listOf(0), visual)
        }
    }

    /** SPEC-TIMING-001 */
    @Test
    fun playheadEstimator_interpolatesMonotonicallyAndCapsAtWritten() {
        val estimator = PlayheadEstimator(sampleRate = 48000, maxInterpolationFrames = 512)
        estimator.reset(nowNanos = 0L)
        assertEquals(480L, estimator.estimate(hardwareFrames = 0L, nowNanos = 10_000_000L, totalWritten = 10_000L))
        assertEquals(1000L, estimator.estimate(1000L, 20_000_000L, 10_000L))
        // 1 ms later without a hardware update -> +48 frames interpolation
        assertEquals(1048L, estimator.estimate(1000L, 21_000_000L, 10_000L))
        // Never moves backwards, never exceeds written frames
        assertEquals(1048L, estimator.estimate(900L, 21_000_000L, 10_000L))
        assertEquals(1100L, estimator.estimate(1100L, 22_000_000L, 1100L))
    }

    @Test
    fun wrappingFrameCounter_unwraps32BitCounters() {
        val counter = WrappingFrameCounter()
        assertEquals(0xFFFFFFF0L, counter.unwrap(0xFFFFFFF0L))
        assertEquals(0x100000010L, counter.unwrap(0x10L))
    }

    /** SPEC-AUDIO-004 */
    @Test
    fun audioRouteSelector_alwaysListsSpeakerAndPrefersExternalOutputs() {
        val bt = AudioOutputDevice(7, AudioDeviceType.BLUETOOTH, "Buds")
        val usb = AudioOutputDevice(3, AudioDeviceType.USB_AUDIO, "DAC")
        val sorted = AudioRouteSelector.normalize(listOf(bt, usb, bt.copy(id = 8)))
        assertEquals(listOf(AudioDeviceType.SPEAKER, AudioDeviceType.USB_AUDIO, AudioDeviceType.BLUETOOTH), sorted.map { it.deviceType })

        assertEquals(usb, AudioRouteSelector.selectActive(sorted, preferredDeviceId = null, routedDevice = null))
        assertEquals(bt, AudioRouteSelector.selectActive(sorted, preferredDeviceId = 7, routedDevice = null))
        assertEquals(AudioRouteSelector.BUILT_IN_SPEAKER, AudioRouteSelector.selectActive(sorted, 99, AudioRouteSelector.BUILT_IN_SPEAKER))
        assertEquals(listOf(AudioRouteSelector.BUILT_IN_SPEAKER), AudioRouteSelector.normalize(emptyList()))
    }

    /**
     * Renders audio in small chunks and advances the visual cursor one frame at a time, recording
     * the exact frame every visual tick fires and the largest pendulum step between frames.
     */
    private class Harness(subdivision: Subdivision = Subdivision.QUARTER, bpm: Double = 120.0) {
        val sequencer = MetronomeSequencer()
        val ticks = mutableListOf<Triple<Long, Int, Int>>() // frame, beatIndex, subdivisionIndex
        var bars = 0
        var maxAngleStepPerFrame = 0f
        private var cursor = WARMUP
        private var lastAngle: Float? = null
        private val chunk = ShortArray(64)

        init {
            sequencer.bpm = bpm
            sequencer.totalBeats = 4
            sequencer.subdivision = subdivision
            sequencer.waveforms = ClickSynthesizer.synthesize(SoundSet.DIGITAL, 1.0f, RATE)
            sequencer.onVisualTick = { beat, _, sub -> ticks.add(Triple(cursor, beat, sub)) }
            sequencer.onBarCompleted = { bars++ }
            sequencer.prepare(RATE, WARMUP)
            sequencer.advanceWarmup(WARMUP.toInt())
            sequencer.startFirstBeat()
        }

        /** Renders until the beat-1 relative frame [untilFrame] has been processed. */
        fun runUntil(untilFrame: Long) {
            val target = WARMUP + untilFrame
            while (cursor < target) {
                if (cursor >= sequencer.totalFramesWritten) sequencer.renderChunk(chunk, chunk.size)
                val angle = sequencer.processPlayhead(cursor).pendulumAngle
                lastAngle?.let { maxAngleStepPerFrame = maxOf(maxAngleStepPerFrame, kotlin.math.abs(angle - it)) }
                lastAngle = angle
                cursor++
            }
        }

        fun mainBeatFrames(): List<Long> = ticks.filter { it.third == 0 }.map { it.first - WARMUP }

        companion object {
            const val RATE = 48000
            const val WARMUP = 2560L
        }
    }

    /** SPEC-PULSE-002 */
    @Test
    fun subdivisionChangeMidBeat_keepsDownbeatsOnTheGridAndNeverFiresLateSubTicks() {
        val h = Harness() // 120 BPM quarter notes: one beat every 24000 frames
        h.runUntil(12000)  // halfway through beat 1
        h.sequencer.subdivision = Subdivision.SIXTEENTH
        h.runUntil(48001)

        assertEquals(listOf(0L, 24000L, 48000L), h.mainBeatFrames())
        // After the switch at phase 0.5, the next 16th boundary is phase 0.75 (frame 18000);
        // the already-passed 0.25 / 0.5 boundaries are not fired late.
        val subTicks = h.ticks.filter { it.third > 0 }.map { (it.first - Harness.WARMUP) to it.third }
        assertEquals(18000L to 3, subTicks.first())
        assertEquals(listOf(30000L to 1, 36000L to 2, 42000L to 3), subTicks.drop(1).take(3))
    }

    /** SPEC-PULSE-003 */
    @Test
    fun timeSignatureChangeMidBar_keepsThePulseAndStartsTheNewBarOnTheNextBeat() {
        val h = Harness()
        h.runUntil(72001)                // beats 1-4 of a 4/4 bar (indices 0..3)
        h.sequencer.totalBeats = 3       // switch to 3/4 while on beat index 3
        h.sequencer.subdivision = Subdivision.EIGHTH // e.g. a 6/8-style preset change
        h.runUntil(144001)

        assertEquals((0..6).map { it * 24000L }, h.mainBeatFrames())
        val beatIndices = h.ticks.filter { it.third == 0 }.map { it.second }
        assertEquals(listOf(0, 1, 2, 3, 0, 1, 2), beatIndices)
        assertEquals(1, h.bars)
    }

    /** SPEC-PULSE-004 */
    @Test
    fun pendulumStaysContinuous_acrossTempoSubdivisionAndMeterChanges() {
        val h = Harness()
        h.runUntil(10000)
        h.sequencer.bpm = 240.0                       // fast jump up
        h.runUntil(30000)
        h.sequencer.subdivision = Subdivision.TRIPLET // mid-beat subdivision change
        h.runUntil(45000)
        h.sequencer.totalBeats = 7
        h.sequencer.bpm = 60.0                        // fast jump down
        h.runUntil(90000)
        h.sequencer.subdivision = Subdivision.QUARTER
        h.sequencer.bpm = 300.0
        h.runUntil(130000)

        // Peak pendulum speed at 300 BPM is ~0.01 degrees per frame; any reset or snap would be
        // many degrees in a single frame.
        assertTrue(h.maxAngleStepPerFrame < 0.05f, "pendulum jumped ${h.maxAngleStepPerFrame} degrees in one frame")
        // Beats keep firing (no stall) and their spacing never shrinks below the 300 BPM interval.
        val beats = h.mainBeatFrames()
        assertTrue(beats.size >= 8)
        assertTrue(beats.zipWithNext { a, b -> b - a }.all { it >= 9600 - 1 })
    }
}
