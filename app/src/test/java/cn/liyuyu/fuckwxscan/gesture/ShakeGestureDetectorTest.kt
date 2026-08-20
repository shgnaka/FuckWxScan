/*
 * SPDX-License-Identifier: Apache-2.0
 * New file added by contributors to shgnaka/FuckWxScan in 2026.
 */
package cn.liyuyu.fuckwxscan.gesture

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ShakeGestureDetectorTest {
    private val detector = ShakeGestureDetector()

    @Test
    fun opposingPeaksWithinWindowTriggerOnce() {
        assertFalse(detector.onSample(13f, 0f, 0f, 0L))
        assertFalse(detector.onSample(0f, 0f, 0f, 80L))
        assertTrue(detector.onSample(-13f, 0f, 0f, 180L))
    }

    @Test
    fun singlePeakDoesNotTrigger() {
        assertFalse(detector.onSample(13f, 0f, 0f, 0L))
        assertFalse(detector.onSample(0f, 0f, 0f, 80L))
    }

    @Test
    fun samplesFromOnePeakDoNotCountAsTwoPeaks() {
        assertFalse(detector.onSample(13f, 0f, 0f, 0L))
        assertFalse(detector.onSample(-13f, 0f, 0f, 80L))
        assertFalse(detector.onSample(0f, 0f, 0f, 120L))
    }

    @Test
    fun peaksInSameDirectionDoNotTrigger() {
        assertFalse(detector.onSample(13f, 0f, 0f, 0L))
        assertFalse(detector.onSample(0f, 0f, 0f, 80L))
        assertFalse(detector.onSample(13f, 0f, 0f, 180L))
    }

    @Test
    fun peaksOutsideWindowDoNotTrigger() {
        assertFalse(detector.onSample(13f, 0f, 0f, 0L))
        assertFalse(detector.onSample(0f, 0f, 0f, 80L))
        assertFalse(detector.onSample(-13f, 0f, 0f, 351L))
    }

    @Test
    fun cooldownBlocksImmediateSecondShake() {
        assertFalse(detector.onSample(13f, 0f, 0f, 0L))
        assertFalse(detector.onSample(0f, 0f, 0f, 80L))
        assertTrue(detector.onSample(-13f, 0f, 0f, 180L))

        assertFalse(detector.onSample(0f, 0f, 0f, 260L))
        assertFalse(detector.onSample(13f, 0f, 0f, 400L))
        assertFalse(detector.onSample(0f, 0f, 0f, 480L))
        assertFalse(detector.onSample(-13f, 0f, 0f, 580L))
    }

    @Test
    fun detectorRearmsAfterCooldown() {
        assertFalse(detector.onSample(13f, 0f, 0f, 0L))
        assertFalse(detector.onSample(0f, 0f, 0f, 80L))
        assertTrue(detector.onSample(-13f, 0f, 0f, 180L))

        assertFalse(detector.onSample(0f, 0f, 0f, 1_700L))
        assertFalse(detector.onSample(13f, 0f, 0f, 1_800L))
        assertFalse(detector.onSample(0f, 0f, 0f, 1_880L))
        assertTrue(detector.onSample(-13f, 0f, 0f, 1_980L))
    }

    @Test
    fun diagonalPeaksMustBeOppositeEnough() {
        assertFalse(detector.onSample(10f, 8f, 0f, 0L))
        assertFalse(detector.onSample(0f, 0f, 0f, 80L))
        assertTrue(detector.onSample(-10f, -8f, 0f, 180L))
    }

    @Test
    fun belowThresholdDoesNotCreateCandidate() {
        assertFalse(detector.onSample(5f, 0f, 0f, 0L))

        assertNull(detector.lastDiagnosticEvent)
    }

    @Test
    fun successfulShakeReportsEachCandidateStage() {
        assertFalse(detector.onSample(13f, 0f, 0f, 0L))
        assertEquals(
            ShakeGestureDetector.DiagnosticStage.FIRST_PEAK,
            detector.lastDiagnosticEvent?.stage,
        )

        assertFalse(detector.onSample(0f, 0f, 0f, 80L))
        assertEquals(
            ShakeGestureDetector.DiagnosticStage.REARMED,
            detector.lastDiagnosticEvent?.stage,
        )

        assertTrue(detector.onSample(-14f, 0f, 0f, 180L))
        val event = detector.lastDiagnosticEvent
        assertEquals(ShakeGestureDetector.DiagnosticStage.DETECTED, event?.stage)
        assertEquals(13f, event?.firstPeakMagnitudeMps2 ?: 0f, 0.001f)
        assertEquals(14f, event?.secondPeakMagnitudeMps2 ?: 0f, 0.001f)
        assertEquals(180L, event?.separationMs)
        assertEquals(-1f, event?.cosine ?: 0f, 0.001f)
    }

    @Test
    fun expiredCandidateReportsElapsedTime() {
        assertFalse(detector.onSample(13f, 0f, 0f, 0L))
        assertFalse(detector.onSample(0f, 0f, 0f, 80L))
        assertFalse(detector.onSample(0f, 0f, 0f, 351L))

        val event = detector.lastDiagnosticEvent
        assertEquals(ShakeGestureDetector.DiagnosticStage.EXPIRED, event?.stage)
        assertEquals(351L, event?.separationMs)
    }

    @Test
    fun directionRejectionReportsCosine() {
        assertFalse(detector.onSample(13f, 0f, 0f, 0L))
        assertFalse(detector.onSample(0f, 0f, 0f, 80L))
        assertFalse(detector.onSample(13f, 0f, 0f, 180L))

        val event = detector.lastDiagnosticEvent
        assertEquals(
            ShakeGestureDetector.DiagnosticStage.DIRECTION_REJECTED,
            event?.stage,
        )
        assertEquals(180L, event?.separationMs)
        assertEquals(1f, event?.cosine ?: 0f, 0.001f)
    }

    @Test
    fun earlySecondPeakReportsMinimumIntervalFailure() {
        assertFalse(detector.onSample(13f, 0f, 0f, 0L))
        assertFalse(detector.onSample(0f, 0f, 0f, 20L))
        assertFalse(detector.onSample(-13f, 0f, 0f, 50L))

        val event = detector.lastDiagnosticEvent
        assertEquals(ShakeGestureDetector.DiagnosticStage.TOO_SOON, event?.stage)
        assertEquals(50L, event?.separationMs)
    }

    @Test
    fun strongPeakAfterTimeoutReportsNewCandidate() {
        assertFalse(detector.onSample(13f, 0f, 0f, 0L))
        assertFalse(detector.onSample(0f, 0f, 0f, 80L))
        assertFalse(detector.onSample(-14f, 0f, 0f, 351L))

        val event = detector.lastDiagnosticEvent
        assertEquals(
            ShakeGestureDetector.DiagnosticStage.RESTARTED_AFTER_TIMEOUT,
            event?.stage,
        )
        assertEquals(351L, event?.separationMs)
        assertEquals(13f, event?.firstPeakMagnitudeMps2 ?: 0f, 0.001f)
        assertEquals(14f, event?.secondPeakMagnitudeMps2 ?: 0f, 0.001f)
    }
}
