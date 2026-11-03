package com.damiongans.anchor.domain

import com.damiongans.anchor.model.BeatAccent
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.Test
import kotlin.math.abs

class DelayCompensationEngineTest {

    private val defaultAccents = listOf(
        BeatAccent.ACCENT,
        BeatAccent.REGULAR,
        BeatAccent.REGULAR,
        BeatAccent.REGULAR
    )

    /** SPEC-TIMING-001, SPEC-AUDIO-002 */
    @Test
    fun zeroOffsets_withWarmupPreRoll_holdsEventsUntilPlayheadReachesWarmupFrame() {
        val sampleRate = 48000
        val engine = DelayCompensationEngine(sampleRate = sampleRate)
        val warmupFrames = 2640L // 55ms at 48kHz
        val framesPerBeat = 24000.0 // 120 BPM (500ms)

        engine.reset(
            warmupFrames = warmupFrames,
            initialIsAccent = true,
            framesPerSubdivision = framesPerBeat
        )

        val visualEvents = mutableListOf<Triple<Int, Boolean, Int>>()
        val vibrationEvents = mutableListOf<Pair<Int, Boolean>>()
        var barCompletions = 0

        // During the 55ms warmup (playhead at 0, 1000, 2639), neither visual nor vibration should fire
        for (playhead in listOf(0L, 1000L, 2639L)) {
            val snap = engine.processPlayhead(
                playheadFrame = playhead,
                liveAccents = defaultAccents,
                totalBeats = 4,
                subdivisionCount = 1,
                currentFramesPerSubdivision = framesPerBeat,
                onVisualTick = { b, acc, s -> visualEvents.add(Triple(b, acc, s)) },
                onVibrationTick = { b, acc -> vibrationEvents.add(b to acc) },
                onBarCompleted = { barCompletions++ }
            )
            assertEquals(0f, snap.visualBeatPhase, 0.001f)
            assertEquals(24f, snap.pendulumAngle, 0.01f)
        }
        assertTrue(visualEvents.isEmpty())
        assertTrue(vibrationEvents.isEmpty())

        // At exact warmup boundary (playhead == 2640), Beat 1 fires on both channels simultaneously
        val snapAtBeat1 = engine.processPlayhead(
            playheadFrame = 2640L,
            liveAccents = defaultAccents,
            totalBeats = 4,
            subdivisionCount = 1,
            currentFramesPerSubdivision = framesPerBeat,
            onVisualTick = { b, acc, s -> visualEvents.add(Triple(b, acc, s)) },
            onVibrationTick = { b, acc -> vibrationEvents.add(b to acc) },
            onBarCompleted = { barCompletions++ }
        )

        assertEquals(1, visualEvents.size)
        assertEquals(Triple(0, true, 0), visualEvents[0])
        assertEquals(1, vibrationEvents.size)
        assertEquals(0 to true, vibrationEvents[0])
        assertEquals(0, barCompletions)
        assertEquals(0f, snapAtBeat1.visualBeatPhase, 0.001f)
        assertEquals(24f, snapAtBeat1.pendulumAngle, 0.01f)
    }

    /** SPEC-TIMING-002 */
    @Test
    fun negativeOffsets_advanceVibrationAndVisualsEarlierThanAudioWithoutDuplicatingOnSynthesis() {
        val sampleRate = 48000
        val engine = DelayCompensationEngine(sampleRate = sampleRate)
        val warmupFrames = 2640L // 55ms
        val framesPerBeat = 24000.0 // 500ms

        // Set vibration -25ms (-1200 frames) early, and visual 0ms
        engine.vibrationDelayMs = -25
        engine.visualDelayMs = 0

        engine.reset(
            warmupFrames = warmupFrames,
            initialIsAccent = true,
            framesPerSubdivision = framesPerBeat
        )

        val visualEvents = mutableListOf<Int>()
        val vibrationEvents = mutableListOf<Int>()

        // At playhead = 1439 (just before 2640 - 1200 = 1440), nothing fires yet
        engine.processPlayhead(
            playheadFrame = 1439L,
            liveAccents = defaultAccents,
            totalBeats = 4,
            subdivisionCount = 1,
            currentFramesPerSubdivision = framesPerBeat,
            onVisualTick = { b, _, _ -> visualEvents.add(b) },
            onVibrationTick = { b, _ -> vibrationEvents.add(b) },
            onBarCompleted = {}
        )
        assertTrue(vibrationEvents.isEmpty())
        assertTrue(visualEvents.isEmpty())

        // At playhead = 1440 (25ms BEFORE Beat 1 audio reaches playhead at 2640), Vibration fires!
        engine.processPlayhead(
            playheadFrame = 1440L,
            liveAccents = defaultAccents,
            totalBeats = 4,
            subdivisionCount = 1,
            currentFramesPerSubdivision = framesPerBeat,
            onVisualTick = { b, _, _ -> visualEvents.add(b) },
            onVibrationTick = { b, _ -> vibrationEvents.add(b) },
            onBarCompleted = {}
        )
        assertEquals(listOf(0), vibrationEvents)
        assertTrue(visualEvents.isEmpty())

        // At playhead = 2640, Visual fires, and Vibration does NOT double-fire
        engine.processPlayhead(
            playheadFrame = 2640L,
            liveAccents = defaultAccents,
            totalBeats = 4,
            subdivisionCount = 1,
            currentFramesPerSubdivision = framesPerBeat,
            onVisualTick = { b, _, _ -> visualEvents.add(b) },
            onVibrationTick = { b, _ -> vibrationEvents.add(b) },
            onBarCompleted = {}
        )
        assertEquals(listOf(0), vibrationEvents)
        assertEquals(listOf(0), visualEvents)

        // Now test large negative offset (-100ms = -4800 frames) on Beat 2 (audioFrame = 26640)
        // Beat 2 vibration should fire via lookahead at playhead = 26640 - 4800 = 21840,
        // BEFORE recordSynthesizedTick(seqId = 1) is even called!
        engine.vibrationDelayMs = -100
        engine.processPlayhead(
            playheadFrame = 21840L,
            liveAccents = defaultAccents,
            totalBeats = 4,
            subdivisionCount = 1,
            currentFramesPerSubdivision = framesPerBeat,
            onVisualTick = { b, _, _ -> visualEvents.add(b) },
            onVibrationTick = { b, _ -> vibrationEvents.add(b) },
            onBarCompleted = {}
        )
        assertEquals(listOf(0, 1), vibrationEvents)
        assertEquals(listOf(0), visualEvents)

        // When audio thread later synthesizes Beat 2 at frame 26640 and playhead reaches 26640:
        engine.recordSynthesizedTick(
            seqId = 1L,
            beatIndex = 1,
            subdivisionIndex = 0,
            beatSequenceNumber = 1L,
            isAccent = false,
            isBarWrap = false,
            audioFrame = 26640L,
            framesPerSubdivision = framesPerBeat
        )
        engine.processPlayhead(
            playheadFrame = 26640L,
            liveAccents = defaultAccents,
            totalBeats = 4,
            subdivisionCount = 1,
            currentFramesPerSubdivision = framesPerBeat,
            onVisualTick = { b, _, _ -> visualEvents.add(b) },
            onVibrationTick = { b, _ -> vibrationEvents.add(b) },
            onBarCompleted = {}
        )
        // Vibration must NOT duplicate Beat 2, and Visual now fires Beat 2!
        assertEquals(listOf(0, 1), vibrationEvents)
        assertEquals(listOf(0, 1), visualEvents)
    }

    /** SPEC-TIMING-002, SPEC-TIMING-003 */
    @Test
    fun positiveOffsets_andBluetoothCompensation_delayChannelsIndependently() {
        val sampleRate = 48000
        val engine = DelayCompensationEngine(sampleRate = sampleRate)
        val warmupFrames = 2400L // 50ms
        val framesPerBeat = 24000.0 // 500ms

        engine.visualDelayMs = 40 // +40ms (+1920 frames)
        engine.vibrationDelayMs = 15 // +15ms (+720 frames)
        engine.bluetoothOffsetMs = 100 // +100ms (+4800 frames)
        engine.isBluetoothActive = true

        // Total Visual = +140ms (+6720 frames -> trigger at 2400 + 6720 = 9120)
        // Total Vibration = +115ms (+5520 frames -> trigger at 2400 + 5520 = 7920)
        assertEquals(140, engine.effectiveVisualOffsetMs())
        assertEquals(115, engine.effectiveVibrationOffsetMs())

        engine.reset(
            warmupFrames = warmupFrames,
            initialIsAccent = true,
            framesPerSubdivision = framesPerBeat
        )

        val visualEvents = mutableListOf<Int>()
        val vibrationEvents = mutableListOf<Int>()

        // At playhead = 2400 (audio frame of Beat 1), neither has fired yet due to positive delay
        engine.processPlayhead(
            playheadFrame = 2400L,
            liveAccents = defaultAccents,
            totalBeats = 4,
            subdivisionCount = 1,
            currentFramesPerSubdivision = framesPerBeat,
            onVisualTick = { b, _, _ -> visualEvents.add(b) },
            onVibrationTick = { b, _ -> vibrationEvents.add(b) },
            onBarCompleted = {}
        )
        assertTrue(visualEvents.isEmpty())
        assertTrue(vibrationEvents.isEmpty())

        // At playhead = 7920, Vibration fires, Visual still waiting
        engine.processPlayhead(
            playheadFrame = 7920L,
            liveAccents = defaultAccents,
            totalBeats = 4,
            subdivisionCount = 1,
            currentFramesPerSubdivision = framesPerBeat,
            onVisualTick = { b, _, _ -> visualEvents.add(b) },
            onVibrationTick = { b, _ -> vibrationEvents.add(b) },
            onBarCompleted = {}
        )
        assertEquals(listOf(0), vibrationEvents)
        assertTrue(visualEvents.isEmpty())

        // At playhead = 9120, Visual fires and pendulum phase is 0.0 (start of Beat 1)
        val snap = engine.processPlayhead(
            playheadFrame = 9120L,
            liveAccents = defaultAccents,
            totalBeats = 4,
            subdivisionCount = 1,
            currentFramesPerSubdivision = framesPerBeat,
            onVisualTick = { b, _, _ -> visualEvents.add(b) },
            onVibrationTick = { b, _ -> vibrationEvents.add(b) },
            onBarCompleted = {}
        )
        assertEquals(listOf(0), visualEvents)
        assertEquals(0f, snap.visualBeatPhase, 0.001f)
    }

    @Test
    fun oddTimeSignature_pendulumAlternatesSmoothlyAcrossBarBoundary() {
        val engine = DelayCompensationEngine(sampleRate = 48000)
        // In 3/4 time:
        // Beat 0 (seq 0): swings +24 -> -24
        // Beat 1 (seq 1): swings -24 -> +24
        // Beat 2 (seq 2): swings +24 -> -24
        // Bar 2 Beat 0 (seq 3): must start at -24 and swing to +24 (no teleportation!)
        val endOfBar1Beat2 = engine.computePendulumAngle(beatSequenceNumber = 2L, beatPhase = 1.0f)
        val startOfBar2Beat0 = engine.computePendulumAngle(beatSequenceNumber = 3L, beatPhase = 0.0f)

        assertEquals(-24f, endOfBar1Beat2, 0.01f)
        assertEquals(-24f, startOfBar2Beat0, 0.01f)
        assertTrue(abs(endOfBar1Beat2 - startOfBar2Beat0) < 0.001f)
    }
}
