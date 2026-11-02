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
}
