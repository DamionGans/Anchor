package com.damiongans.anchor.domain

import com.damiongans.anchor.model.TapTempoStatus
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Pure domain calculator for Tap Tempo calculations.
 * Features sliding window, debouncing, outlier rejection,
 * confidence scoring, and tempo normalization (quarter/eighth/half notes).
 */
class TapTempoCalculator(
    private val minTapIntervalMs: Long = 140L,
    private val maxTapIntervalMs: Long = 2300L,
    private val maxHistorySize: Int = 7
) {

    private val tapTimestamps = mutableListOf<Long>()
    private var lastGuessedBpm: Int = 120

    fun reset() {
        tapTimestamps.clear()
    }

    fun registerTap(timestampMs: Long): TapTempoResult? {
        // Reset if last tap was too long ago
        if (tapTimestamps.isNotEmpty() && (timestampMs - tapTimestamps.last()) > maxTapIntervalMs) {
            tapTimestamps.clear()
        }

        // Debounce accidental double taps
        if (tapTimestamps.isNotEmpty() && (timestampMs - tapTimestamps.last()) < minTapIntervalMs) {
            return null
        }

        tapTimestamps.add(timestampMs)
        if (tapTimestamps.size > maxHistorySize) {
            tapTimestamps.removeAt(0)
        }

        val tapCount = tapTimestamps.size
        if (tapCount < 2) {
            return TapTempoResult(
                status = TapTempoStatus(
                    tapCount = 1,
                    confidencePercent = 0,
                    estimatedBpm = lastGuessedBpm,
                    pulseDescription = "Tap again...",
                    isHighCertainty = false
                ),
                bpmToApply = null
            )
        }

        // Compute delta intervals
        val intervals = mutableListOf<Double>()
        for (i in 1 until tapTimestamps.size) {
            intervals.add((tapTimestamps[i] - tapTimestamps[i - 1]).toDouble())
        }

        // Trim outliers if we have at least 4 intervals
        val filteredIntervals = if (intervals.size >= 4) {
            val sorted = intervals.sorted()
            sorted.subList(1, sorted.size - 1)
        } else {
            intervals
        }

        val meanInterval = filteredIntervals.average()
        var varianceSum = 0.0
        for (iv in filteredIntervals) {
            varianceSum += (iv - meanInterval) * (iv - meanInterval)
        }
        val stdDev = sqrt(varianceSum / filteredIntervals.size)
        val cv = stdDev / meanInterval
        val confidence = ((1.0 - (cv / 0.14)).coerceIn(0.0, 1.0) * 100).toInt()

        val rawBpm = 60000.0 / meanInterval
        val (guessedBpm, pulseDesc) = when {
            rawBpm > 220.0 -> (rawBpm / 2.0).roundToInt() to "Eighth notes detected"
            rawBpm < 48.0 -> (rawBpm * 2.0).roundToInt() to "Half notes detected"
            else -> rawBpm.roundToInt() to "Quarter notes detected"
        }

        val finalBpm = guessedBpm.coerceIn(30, 280)
        lastGuessedBpm = finalBpm
        val isLocked = tapCount >= 3 && confidence >= 70

        return TapTempoResult(
            status = TapTempoStatus(
                tapCount = tapCount,
                confidencePercent = confidence,
                estimatedBpm = finalBpm,
                pulseDescription = pulseDesc,
                isHighCertainty = isLocked
            ),
            bpmToApply = finalBpm
        )
    }
}

data class TapTempoResult(
    val status: TapTempoStatus,
    val bpmToApply: Int?
)
