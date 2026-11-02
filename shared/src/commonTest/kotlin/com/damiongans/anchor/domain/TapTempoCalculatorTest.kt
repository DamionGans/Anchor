package com.damiongans.anchor.domain

import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.BeforeTest
import kotlin.test.Test

class TapTempoCalculatorTest {

    private lateinit var calculator: TapTempoCalculator

    @BeforeTest
    fun setUp() {
        calculator = TapTempoCalculator()
    }

    @Test
    fun singleTap_doesNotCalculateBpmYet() {
        val result = calculator.registerTap(1000L)
        assertNotNull(result)
        assertEquals(1, result.status.tapCount)
        assertNull(result.bpmToApply)
    }

    @Test
    fun steadyTapsAt500ms_calculates120Bpm() {
        calculator.registerTap(1000L)
        val r2 = calculator.registerTap(1500L)
        val r3 = calculator.registerTap(2000L)
        val r4 = calculator.registerTap(2500L)

        assertNotNull(r4)
        assertEquals(4, r4.status.tapCount)
        assertEquals(120, r4.status.estimatedBpm)
        assertEquals(120, r4.bpmToApply)
        assertTrue(r4.status.confidencePercent >= 90)
        assertTrue(r4.status.isHighCertainty)
    }

    @Test
    fun rapidDoubleTap_isDebounced() {
        calculator.registerTap(1000L)
        // 50ms later -> debounced
        val debounced = calculator.registerTap(1050L)
        assertNull(debounced)

        // Valid tap 500ms later
        val valid = calculator.registerTap(1500L)
        assertNotNull(valid)
        assertEquals(2, valid.status.tapCount)
    }

    @Test
    fun idleTimeout_resetsTapHistory() {
        calculator.registerTap(1000L)
        calculator.registerTap(1500L)

        // 3000ms later (> maxTapIntervalMs 2300ms)
        val afterTimeout = calculator.registerTap(4500L)
        assertNotNull(afterTimeout)
        assertEquals(1, afterTimeout.status.tapCount)
        assertNull(afterTimeout.bpmToApply)
    }

    @Test
    fun fastCadence_normalizesToEighthNotes() {
        // Tapping at 200ms (~300 raw BPM)
        calculator.registerTap(1000L)
        calculator.registerTap(1200L)
        calculator.registerTap(1400L)
        val res = calculator.registerTap(1600L)

        assertNotNull(res)
        // 300 / 2 = 150 BPM
        assertEquals(150, res.status.estimatedBpm)
        assertEquals("Eighth notes detected", res.status.pulseDescription)
    }
}
