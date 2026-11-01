package com.damiongans.anchor.domain

import com.damiongans.anchor.model.BeatAccent
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToLong

/**
 * Vendor-agnostic, sample-frame-accurate delay compensation and multi-sensory
 * synchronization engine for Anchor.
 *
 * Design principles:
 * 1. Playhead-Anchored Timeline:
 *    All visual and haptic events are referenced against the actual hardware
 *    AudioTrack playback cursor (`playheadFrame`) rather than the CPU synthesis
 *    write cursor (`writeFrame`). This automatically eliminates discrepancies
 *    caused by device-specific `AudioTrack.getMinBufferSize()` capacities and
 *    the 55ms DAC/mixer warmup pre-roll.
 * 2. Bidirectional User Control (Early / Negative & Late / Positive Offsets):
 *    - Visual Offset (`visualDelayMs`): Shifts BeatBar, Screen Flash, and Pendulum
 *      together relative to the audible beat (-200ms to +300ms).
 *    - Vibration Offset (`vibrationDelayMs`): Shifts haptic actuator triggers
 *      independently relative to the audible beat (-200ms to +300ms).
 *    - Bluetooth Offset (`bluetoothOffsetMs`): Applied when Bluetooth audio routing
 *      is active (-100ms to +400ms), with zero hidden magic offsets.
 * 3. Lookahead Projection for Negative Offsets:
 *    Supports firing visuals or vibrations up to 200ms *before* the audio beat
 *    reaches the speaker by projecting upcoming beat frame boundaries from the
 *    active tempo and tracking monotonic tick sequence IDs so no beat is ever
 *    duplicated or skipped.
 */
class DelayCompensationEngine(
    sampleRate: Int = 48000
) {
    companion object {
        const val MIN_VISUAL_DELAY_MS = -200
        const val MAX_VISUAL_DELAY_MS = 300
        const val MIN_VIBRATION_DELAY_MS = -200
        const val MAX_VIBRATION_DELAY_MS = 300
        const val MIN_BLUETOOTH_OFFSET_MS = -100
        const val MAX_BLUETOOTH_OFFSET_MS = 400
        const val MAX_SWING_DEGREES = 24f
    }

    data class ScheduledTick(
        val seqId: Long,
        var beatIndex: Int,
        var subdivisionIndex: Int,
        var beatSequenceNumber: Long,
        var isAccent: Boolean,
        var isBarWrap: Boolean,
        var audioFrame: Long,
        var framesPerSubdivision: Double
    )

    data class SyncSnapshot(
        val visualBeatIndex: Int,
        val visualSubdivisionIndex: Int,
        val visualBeatSequenceNumber: Long,
        val visualBeatPhase: Float,
        val pendulumAngle: Float
    )

    @Volatile
    var sampleRate: Int = sampleRate.coerceAtLeast(8000)
        set(value) {
            field = value.coerceAtLeast(8000)
        }

    @Volatile
    var visualDelayMs: Int = 0
        set(value) {
            field = value.coerceIn(MIN_VISUAL_DELAY_MS, MAX_VISUAL_DELAY_MS)
        }

    @Volatile
    var vibrationDelayMs: Int = 0
        set(value) {
            field = value.coerceIn(MIN_VIBRATION_DELAY_MS, MAX_VIBRATION_DELAY_MS)
        }

    @Volatile
    var bluetoothOffsetMs: Int = 0
        set(value) {
            field = value.coerceIn(MIN_BLUETOOTH_OFFSET_MS, MAX_BLUETOOTH_OFFSET_MS)
        }

    @Volatile
    var isBluetoothActive: Boolean = false

    private val timeline = ArrayDeque<ScheduledTick>(32)
    private var lastVisualTriggeredSeq: Long = -1L
    private var lastVibrationTriggeredSeq: Long = -1L
    private var firstBeatFrame: Long = 0L

    /**
     * Effective visual delay in milliseconds (Visual Offset + active Bluetooth Offset).
     * Negative values advance visuals earlier than audio; positive values delay visuals later.
     */
    fun effectiveVisualOffsetMs(): Int {
        val bt = if (isBluetoothActive) bluetoothOffsetMs else 0
        return visualDelayMs + bt
    }

    /**
     * Effective vibration delay in milliseconds (Vibration Offset + active Bluetooth Offset).
     * Negative values fire the vibration motor earlier than audio; positive values delay it later.
     */
    fun effectiveVibrationOffsetMs(): Int {
        val bt = if (isBluetoothActive) bluetoothOffsetMs else 0
        return vibrationDelayMs + bt
    }

    /**
     * Converts a millisecond offset into signed PCM frames at the current sample rate.
     */
    fun msToFrames(offsetMs: Int): Long {
        return ((offsetMs.toDouble() * sampleRate) / 1000.0).roundToLong()
    }

    fun effectiveVisualOffsetFrames(): Long = msToFrames(effectiveVisualOffsetMs())

    fun effectiveVibrationOffsetFrames(): Long = msToFrames(effectiveVibrationOffsetMs())

    /**
     * Initializes the timeline at the start of playback right after the hardware
     * warmup pre-roll has been queued.
     *
     * @param warmupFrames Number of warmup pre-roll frames written prior to Beat 1 sample 0.
     * @param initialIsAccent Whether Beat 1 (index 0) is accented.
     * @param framesPerSubdivision Initial duration of one subdivision in PCM frames.
     */
    @Synchronized
    fun reset(
        warmupFrames: Long,
        initialIsAccent: Boolean,
        framesPerSubdivision: Double
    ) {
        timeline.clear()
        firstBeatFrame = warmupFrames.coerceAtLeast(0L)
        lastVisualTriggeredSeq = -1L
        lastVibrationTriggeredSeq = -1L

        val safeFramesPerSub = framesPerSubdivision.coerceAtLeast(1.0)
        timeline.addLast(
            ScheduledTick(
                seqId = 0L,
                beatIndex = 0,
                subdivisionIndex = 0,
                beatSequenceNumber = 0L,
                isAccent = initialIsAccent,
                isBarWrap = false,
                audioFrame = firstBeatFrame,
                framesPerSubdivision = safeFramesPerSub
            )
        )
    }

    /**
     * Records or updates an exact tick synthesized by the audio engine at [audioFrame].
     * If a lookahead projection already created an entry for [seqId], its frame position
     * and metadata are updated in place without re-triggering already-fired callbacks.
     */
    @Synchronized
    fun recordSynthesizedTick(
        seqId: Long,
        beatIndex: Int,
        subdivisionIndex: Int,
        beatSequenceNumber: Long,
        isAccent: Boolean,
        isBarWrap: Boolean,
        audioFrame: Long,
        framesPerSubdivision: Double
    ) {
        val safeFramesPerSub = framesPerSubdivision.coerceAtLeast(1.0)
        val existing = timeline.firstOrNull { it.seqId == seqId }
        if (existing != null) {
            existing.beatIndex = beatIndex
            existing.subdivisionIndex = subdivisionIndex
            existing.beatSequenceNumber = beatSequenceNumber
            existing.isAccent = isAccent
            existing.isBarWrap = isBarWrap
            existing.audioFrame = audioFrame
            existing.framesPerSubdivision = safeFramesPerSub
        } else {
            timeline.addLast(
                ScheduledTick(
                    seqId = seqId,
                    beatIndex = beatIndex,
                    subdivisionIndex = subdivisionIndex,
                    beatSequenceNumber = beatSequenceNumber,
                    isAccent = isAccent,
                    isBarWrap = isBarWrap,
                    audioFrame = audioFrame,
                    framesPerSubdivision = safeFramesPerSub
                )
            )
        }

        pruneOldEntries()
    }

    /**
     * Evaluates the current hardware [playheadFrame], projects lookahead ticks if a
     * negative offset requires firing before synthesis, dispatches due visual and
     * vibration events, and computes the synchronized visual pendulum state.
     */
    @Synchronized
    fun processPlayhead(
        playheadFrame: Long,
        liveAccents: List<BeatAccent>,
        totalBeats: Int,
        subdivisionCount: Int,
        currentFramesPerSubdivision: Double,
        onVisualTick: (beatIndex: Int, isAccent: Boolean, subdivisionIndex: Int) -> Unit,
        onVibrationTick: (beatIndex: Int, isAccent: Boolean) -> Unit,
        onBarCompleted: () -> Unit
    ): SyncSnapshot {
        val safeBeats = totalBeats.coerceAtLeast(1)
        val safeSubCount = subdivisionCount.coerceAtLeast(1)
        val safeFramesPerSub = currentFramesPerSubdivision.coerceAtLeast(1.0)

        val visualCursorFrame = playheadFrame - effectiveVisualOffsetFrames()
        val vibrationCursorFrame = playheadFrame - effectiveVibrationOffsetFrames()
        val maxCursorFrame = maxOf(visualCursorFrame, vibrationCursorFrame)

        // Ensure lookahead ticks exist up to maxCursorFrame so negative offsets
        // (early triggers) fire seamlessly even before the audio write loop reaches them.
        ensureLookaheadTicks(
            targetCursorFrame = maxCursorFrame,
            liveAccents = liveAccents,
            totalBeats = safeBeats,
            subdivisionCount = safeSubCount,
            framesPerSubdivision = safeFramesPerSub
        )

        // 1. Dispatch Vibration ticks due at vibrationCursorFrame (main beats only: subIdx == 0)
        for (tick in timeline) {
            if (tick.seqId > lastVibrationTriggeredSeq && tick.audioFrame <= vibrationCursorFrame) {
                lastVibrationTriggeredSeq = tick.seqId
                if (tick.subdivisionIndex == 0) {
                    val liveAccent = (liveAccents.getOrNull(tick.beatIndex) ?: BeatAccent.REGULAR) == BeatAccent.ACCENT
                    onVibrationTick(tick.beatIndex, liveAccent)
                }
            }
        }

        // 2. Dispatch Visual & Screen Flash ticks due at visualCursorFrame
        for (tick in timeline) {
            if (tick.seqId > lastVisualTriggeredSeq && tick.audioFrame <= visualCursorFrame) {
                lastVisualTriggeredSeq = tick.seqId
                val liveAccent = if (tick.subdivisionIndex == 0) {
                    (liveAccents.getOrNull(tick.beatIndex) ?: BeatAccent.REGULAR) == BeatAccent.ACCENT
                } else {
                    false
                }
                if (tick.isBarWrap) {
                    onBarCompleted()
                }
                onVisualTick(tick.beatIndex, liveAccent, tick.subdivisionIndex)
            }
        }

        // 3. Compute continuous visual phase & pendulum angle at visualCursorFrame
        val activeTick = timeline.lastOrNull { it.audioFrame <= visualCursorFrame }
        if (activeTick == null) {
            // Still in pre-Beat-1 warmup window (or positive visual delay before Beat 1)
            return SyncSnapshot(
                visualBeatIndex = 0,
                visualSubdivisionIndex = 0,
                visualBeatSequenceNumber = 0L,
                visualBeatPhase = 0f,
                pendulumAngle = MAX_SWING_DEGREES
            )
        }

        val subElapsedFrames = (visualCursorFrame - activeTick.audioFrame).coerceAtLeast(0L)
        val subPhase = (subElapsedFrames.toDouble() / activeTick.framesPerSubdivision).coerceIn(0.0, 1.0)
        val beatPhase = ((activeTick.subdivisionIndex + subPhase) / safeSubCount.toDouble())
            .coerceIn(0.0, 1.0)
            .toFloat()

        val angle = computePendulumAngle(
            beatSequenceNumber = activeTick.beatSequenceNumber,
            beatPhase = beatPhase
        )

        pruneOldEntries()

        return SyncSnapshot(
            visualBeatIndex = activeTick.beatIndex,
            visualSubdivisionIndex = activeTick.subdivisionIndex,
            visualBeatSequenceNumber = activeTick.beatSequenceNumber,
            visualBeatPhase = beatPhase,
            pendulumAngle = angle
        )
    }

    /**
     * Computes physical pendulum angle in degrees [-24f..+24f] for a given monotonic
     * beat sequence number and fractional beat phase [0f..1f].
     *
     * Uses a monotonic beat counter so odd time signatures (3/4, 5/4, 7/8) always
     * alternate swing direction cleanly at bar boundaries without teleporting.
     * Blends harmonic motion (65%) with linear escapement velocity (35%) so direction
     * reversal at the beat boundary (phase = 0.0) is visually immediate.
     */
    fun computePendulumAngle(beatSequenceNumber: Long, beatPhase: Float): Float {
        val clampedPhase = beatPhase.coerceIn(0f, 1f)
        val direction = if (beatSequenceNumber.mod(2L) == 0L) 1f else -1f
        val harmonic = cos(clampedPhase.toDouble() * PI).toFloat()
        val linear = 1f - 2f * clampedPhase
        val blended = 0.65f * harmonic + 0.35f * linear
        return blended * MAX_SWING_DEGREES * direction
    }

    private fun ensureLookaheadTicks(
        targetCursorFrame: Long,
        liveAccents: List<BeatAccent>,
        totalBeats: Int,
        subdivisionCount: Int,
        framesPerSubdivision: Double
    ) {
        var last = timeline.lastOrNull() ?: return
        var guard = 0
        while (last.audioFrame <= targetCursorFrame && guard < 16) {
            guard++
            val nextSeqId = last.seqId + 1L
            var nextSubIdx = last.subdivisionIndex + 1
            var nextBeatIdx = last.beatIndex
            var nextBeatSeq = last.beatSequenceNumber
            var isBarWrap = false

            if (nextSubIdx >= subdivisionCount) {
                nextSubIdx = 0
                nextBeatSeq++
                nextBeatIdx++
                if (nextBeatIdx >= totalBeats) {
                    nextBeatIdx = 0
                    isBarWrap = true
                }
            }

            val isAccent = if (nextSubIdx == 0) {
                (liveAccents.getOrNull(nextBeatIdx) ?: BeatAccent.REGULAR) == BeatAccent.ACCENT
            } else {
                false
            }
            val nextAudioFrame = last.audioFrame + last.framesPerSubdivision.roundToLong().coerceAtLeast(1L)

            val projected = ScheduledTick(
                seqId = nextSeqId,
                beatIndex = nextBeatIdx,
                subdivisionIndex = nextSubIdx,
                beatSequenceNumber = nextBeatSeq,
                isAccent = isAccent,
                isBarWrap = isBarWrap,
                audioFrame = nextAudioFrame,
                framesPerSubdivision = framesPerSubdivision
            )
            timeline.addLast(projected)
            last = projected
        }
    }

    private fun pruneOldEntries() {
        val minKeepSeq = minOf(lastVisualTriggeredSeq, lastVibrationTriggeredSeq) - 2L
        while (timeline.size > 4 && timeline.first().seqId < minKeepSeq) {
            timeline.removeFirst()
        }
    }
}
