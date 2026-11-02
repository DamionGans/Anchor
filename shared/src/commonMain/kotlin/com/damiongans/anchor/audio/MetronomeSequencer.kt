package com.damiongans.anchor.audio

import com.damiongans.anchor.domain.DelayCompensationEngine
import com.damiongans.anchor.model.BeatAccent
import com.damiongans.anchor.model.Subdivision
import kotlin.concurrent.Volatile
import kotlin.math.abs

/**
 * Platform-agnostic, allocation-free metronome sequencer shared by every platform audio engine.
 *
 * Responsibilities (identical on Android and iOS):
 * - Hardware warmup dither (±1 LSB) written before Beat 1 so DACs / mixers leave standby.
 * - Sample-accurate click synthesis into caller-provided 16-bit PCM chunks, including
 *   bar/beat/subdivision bookkeeping and slew-limited BPM changes.
 * - Recording every synthesized tick into the [DelayCompensationEngine] timeline and
 *   dispatching playhead-anchored visual / vibration / bar / phase callbacks.
 *
 * Platform engines only own the I/O: pushing the rendered chunks to the platform audio
 * API (Android `AudioTrack`, iOS `AVAudioPlayerNode`) and reporting the hardware playhead.
 * All render and dispatch calls must come from one thread (the platform's audio loop).
 */
class MetronomeSequencer(
    val compensator: DelayCompensationEngine = DelayCompensationEngine()
) {
    companion object {
        /** Duration of the sub-LSB hardware warmup dither written before Beat 1. */
        const val WARMUP_SECONDS = 0.055

        /** Maximum BPM change applied per rendered chunk (smooth tempo transitions). */
        const val MAX_BPM_STEP_PER_CHUNK = 2.0

        fun framesPerSubdivision(bpm: Double, subdivisionCount: Int, sampleRate: Int): Double {
            val safeBpm = bpm.coerceAtLeast(1.0)
            val safeSub = subdivisionCount.coerceAtLeast(1)
            return (60.0 * sampleRate.toDouble()) / (safeBpm * safeSub)
        }

        /** Number of whole [chunkFrames]-sized chunks covering [WARMUP_SECONDS] (at least [minimumChunks]). */
        fun warmupChunkCount(sampleRate: Int, chunkFrames: Int, minimumChunks: Int = 1): Int {
            val safeChunk = chunkFrames.coerceAtLeast(1)
            return maxOf(minimumChunks, ((sampleRate * WARMUP_SECONDS) / safeChunk).toInt())
        }

        /** Fills [buffer] with alternating ±1 PCM dither (about -90 dBFS, inaudible). */
        fun fillWarmupDither(buffer: ShortArray, frames: Int) {
            for (i in 0 until frames.coerceAtMost(buffer.size)) {
                buffer[i] = if ((i and 1) == 0) 1 else -1
            }
        }
    }

    // Live parameters (written from the UI thread, read by the audio loop).
    @Volatile var bpm: Double = 120.0
    @Volatile var totalBeats: Int = 4
    @Volatile var subdivision: Subdivision = Subdivision.QUARTER
    @Volatile var accents: List<BeatAccent> = listOf(
        BeatAccent.ACCENT,
        BeatAccent.REGULAR,
        BeatAccent.REGULAR,
        BeatAccent.REGULAR
    )
    @Volatile var waveforms: ClickWaveforms = ClickWaveforms.EMPTY

    // Playhead-anchored callbacks (invoked on the audio loop thread).
    var onVisualTick: ((beatIndex: Int, isAccent: Boolean, subdivisionIndex: Int) -> Unit)? = null
    var onVibrationTick: ((beatIndex: Int, isAccent: Boolean) -> Unit)? = null
    var onBarCompleted: (() -> Unit)? = null
    var onPhaseUpdate: ((beatSequenceNumber: Int, phase: Float) -> Unit)? = null

    var sampleRate: Int = 48000
        private set

    /** Total PCM frames handed to the platform output so far (warmup included). */
    var totalFramesWritten: Long = 0L
        private set

    /** Duration of one subdivision (in frames) at the currently smoothed tempo. */
    var currentFramesPerSubdivision: Double = framesPerSubdivision(120.0, 1, 48000)
        private set

    private var currentBeat = 0
    private var currentSubdivisionIdx = 0
    private var tickSeqId = 0L
    private var beatSequenceNumber = 0L
    private var phase = 0.0
    private var activeSmoothedBpm = 120.0
    private var activeWave: ShortArray? = null
    private var activeWavePos = 0

    // Pre-allocated dispatch lambdas (no allocations on the audio thread).
    private val visualDispatch: (Int, Boolean, Int) -> Unit = { b, a, s -> onVisualTick?.invoke(b, a, s) }
    private val vibrationDispatch: (Int, Boolean) -> Unit = { b, a -> onVibrationTick?.invoke(b, a) }
    private val barDispatch: () -> Unit = { onBarCompleted?.invoke() }

    /**
     * Resets the render state and the delay compensation timeline for a new playback run.
     *
     * @param warmupFrames Number of warmup dither frames that will be written before Beat 1.
     */
    fun prepare(sampleRate: Int, warmupFrames: Long) {
        this.sampleRate = sampleRate.coerceAtLeast(8000)
        compensator.sampleRate = this.sampleRate

        totalFramesWritten = 0L
        currentBeat = 0
        currentSubdivisionIdx = 0
        tickSeqId = 0L
        beatSequenceNumber = 0L
        phase = 0.0
        activeSmoothedBpm = bpm
        activeWave = null
        activeWavePos = 0
        currentFramesPerSubdivision = framesPerSubdivision(activeSmoothedBpm, subdivision.count, this.sampleRate)

        val initialAccent = accents.firstOrNull() ?: BeatAccent.ACCENT
        compensator.reset(
            warmupFrames = warmupFrames,
            initialIsAccent = initialAccent == BeatAccent.ACCENT,
            framesPerSubdivision = currentFramesPerSubdivision
        )
    }

    /** Records that [frames] warmup dither frames were delivered to the output. */
    fun advanceWarmup(frames: Int) {
        if (frames > 0) totalFramesWritten += frames
    }

    /**
     * Anchors Beat 1 (seqId 0) at the current write position, right after the warmup.
     */
    fun startFirstBeat() {
        val initialAccent = accents.firstOrNull() ?: BeatAccent.ACCENT
        activeWave = waveforms.forAccent(initialAccent)
        activeWavePos = 0
        phase = 0.0
        compensator.recordSynthesizedTick(
            seqId = 0L,
            beatIndex = 0,
            subdivisionIndex = 0,
            beatSequenceNumber = 0L,
            isAccent = initialAccent == BeatAccent.ACCENT,
            isBarWrap = false,
            audioFrame = totalFramesWritten,
            framesPerSubdivision = currentFramesPerSubdivision
        )
    }

    /**
     * Synthesizes the next [frames] PCM frames into [buffer] at unity gain and records every
     * tick onset into the delay compensation timeline. Advances [totalFramesWritten].
     */
    fun renderChunk(buffer: ShortArray, frames: Int) {
        val count = frames.coerceAtMost(buffer.size)
        val targetBpm = bpm
        val subCount = subdivision.count.coerceAtLeast(1)
        val beats = totalBeats.coerceAtLeast(1)
        val liveAccents = accents

        // Smooth BPM transition: slew-rate limit toward the target BPM.
        val bpmDiff = targetBpm - activeSmoothedBpm
        if (abs(bpmDiff) > 0.05) {
            activeSmoothedBpm += bpmDiff.coerceIn(-MAX_BPM_STEP_PER_CHUNK, MAX_BPM_STEP_PER_CHUNK)
        } else {
            activeSmoothedBpm = targetBpm
        }

        val phaseIncrement = (activeSmoothedBpm * subCount) / (60.0 * sampleRate)
        currentFramesPerSubdivision = 1.0 / phaseIncrement

        for (i in 0 until count) {
            if (phase >= 1.0) {
                phase -= 1.0
                tickSeqId++

                var isBarWrap = false
                currentSubdivisionIdx++
                if (currentSubdivisionIdx >= subCount) {
                    currentSubdivisionIdx = 0
                    beatSequenceNumber++
                    currentBeat++
                    if (currentBeat >= beats) {
                        currentBeat = 0
                        isBarWrap = true
                    }
                }

                val isTickAccent: Boolean
                if (currentSubdivisionIdx == 0) {
                    val accentType = liveAccents.getOrNull(currentBeat) ?: BeatAccent.REGULAR
                    isTickAccent = accentType == BeatAccent.ACCENT
                    activeWave = waveforms.forAccent(accentType)
                    activeWavePos = 0
                } else {
                    isTickAccent = false
                    activeWave = waveforms.sub
                    activeWavePos = 0
                }

                compensator.recordSynthesizedTick(
                    seqId = tickSeqId,
                    beatIndex = currentBeat,
                    subdivisionIndex = currentSubdivisionIdx,
                    beatSequenceNumber = beatSequenceNumber,
                    isAccent = isTickAccent,
                    isBarWrap = isBarWrap,
                    audioFrame = totalFramesWritten + i,
                    framesPerSubdivision = currentFramesPerSubdivision
                )
            }

            var sampleVal = 0
            val wave = activeWave
            if (wave != null) {
                if (activeWavePos < wave.size) {
                    sampleVal = wave[activeWavePos].toInt()
                    activeWavePos++
                } else {
                    activeWave = null
                }
            }

            buffer[i] = sampleVal.coerceIn(-32767, 32767).toShort()
            phase += phaseIncrement
        }

        totalFramesWritten += count
    }

    /**
     * Dispatches all visual / vibration / bar events due at the hardware [playheadFrame]
     * and (optionally) reports the continuous pendulum phase.
     */
    fun processPlayhead(playheadFrame: Long, emitPhase: Boolean = true): DelayCompensationEngine.SyncSnapshot {
        val snapshot = compensator.processPlayhead(
            playheadFrame = playheadFrame,
            liveAccents = accents,
            totalBeats = totalBeats.coerceAtLeast(1),
            subdivisionCount = subdivision.count.coerceAtLeast(1),
            currentFramesPerSubdivision = currentFramesPerSubdivision,
            onVisualTick = visualDispatch,
            onVibrationTick = vibrationDispatch,
            onBarCompleted = barDispatch
        )
        if (emitPhase) {
            onPhaseUpdate?.invoke(
                (snapshot.visualBeatSequenceNumber and 0x7FFFFFFFL).toInt(),
                snapshot.visualBeatPhase
            )
        }
        return snapshot
    }
}

/**
 * Converts a coarse hardware playhead (which typically advances in whole I/O bursts) into a
 * smooth, monotonic frame position by interpolating with a monotonic clock between bursts.
 * Shared by the Android (`AudioTrack.getPlaybackHeadPosition`) and iOS (`AVAudioPlayerNode`
 * player sample time) engines.
 */
class PlayheadEstimator(
    var sampleRate: Int,
    var maxInterpolationFrames: Long
) {
    private var lastHardwareFrames = 0L
    private var lastAdvanceNanos = 0L
    private var playStartNanos = 0L
    private var monotonicFrame = 0L

    fun reset(nowNanos: Long) {
        lastHardwareFrames = 0L
        lastAdvanceNanos = nowNanos
        playStartNanos = nowNanos
        monotonicFrame = 0L
    }

    /**
     * @param hardwareFrames Frames reported as played by the platform (already unwrapped, 64-bit).
     * @param nowNanos Monotonic clock in nanoseconds.
     * @param totalWritten Frames handed to the output so far (upper bound for the estimate).
     */
    fun estimate(hardwareFrames: Long, nowNanos: Long, totalWritten: Long): Long {
        val estimated = if (hardwareFrames > 0L) {
            if (hardwareFrames != lastHardwareFrames) {
                lastHardwareFrames = hardwareFrames
                lastAdvanceNanos = nowNanos
            }
            val deltaNs = (nowNanos - lastAdvanceNanos).coerceAtLeast(0L)
            val subBurstFrames = ((deltaNs * sampleRate) / 1_000_000_000L)
                .coerceIn(0L, maxInterpolationFrames.coerceAtLeast(0L))
            (hardwareFrames + subBurstFrames).coerceAtMost(totalWritten)
        } else {
            val elapsedNs = (nowNanos - playStartNanos).coerceAtLeast(0L)
            ((elapsedNs * sampleRate) / 1_000_000_000L).coerceAtMost(totalWritten)
        }
        if (estimated > monotonicFrame) {
            monotonicFrame = estimated
        }
        return monotonicFrame
    }
}

/**
 * Unwraps an unsigned 32-bit hardware frame counter (e.g. Android `getPlaybackHeadPosition`)
 * into a monotonic 64-bit frame count.
 */
class WrappingFrameCounter {
    private var lastRaw = 0L
    private var wraps = 0L

    fun reset() {
        lastRaw = 0L
        wraps = 0L
    }

    fun unwrap(raw32: Long): Long {
        val raw = raw32 and 0xFFFFFFFFL
        if (raw < lastRaw) wraps++
        lastRaw = raw
        return (wraps shl 32) + raw
    }
}
