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
        assertFalse(detector.onSample(-13f, 0f, 0f, 701L))
    }

    @Test
    fun deviceRoundTripAt475MillisecondsTriggers() {
        assertFalse(detector.onSample(14.3f, 0f, 0f, 0L))
        assertFalse(detector.onSample(3.2f, 0f, 0f, 342L))

        assertTrue(detector.onSample(-14.2f, 0f, 0f, 475L))
    }

    @Test
    fun weakerOpposingReturnPeakTriggers() {
        assertFalse(detector.onSample(13f, 0f, 0f, 0L))
        assertFalse(detector.onSample(0f, 0f, 0f, 200L))

        assertTrue(detector.onSample(-6f, 0f, 0f, 400L))
    }

    @Test
    fun returnPeakBelowSecondThresholdDoesNotTrigger() {
        assertFalse(detector.onSample(13f, 0f, 0f, 0L))
        assertFalse(detector.onSample(0f, 0f, 0f, 200L))

        assertFalse(detector.onSample(-5.9f, 0f, 0f, 400L))
    }

    @Test
    fun gentleCleanOpposingPeaksTrigger() {
        assertFalse(detector.onSample(8f, 0f, 0f, 0L))
        assertFalse(detector.onSample(0f, 0f, 0f, 200L))

        assertTrue(detector.onSample(-6f, 0f, 0f, 450L))
        assertEquals(-0.60f, detector.lastDiagnosticEvent?.requiredCosine ?: 0f, 0.001f)
    }

    @Test
    fun gentleRoundTripAtWindowBoundaryTriggers() {
        assertFalse(detector.onSample(8f, 0f, 0f, 0L))
        assertFalse(detector.onSample(0f, 0f, 0f, 350L))

        assertTrue(detector.onSample(-6f, 0f, 0f, 700L))
    }

    @Test
    fun gentlePeaksNeedClearerDirectionReversal() {
        assertFalse(detector.onSample(8f, 0f, 0f, 0L))
        assertFalse(detector.onSample(0f, 0f, 0f, 200L))

        assertFalse(detector.onSample(-3f, 5.2f, 0f, 450L))
        assertEquals(
            ShakeGestureDetector.DiagnosticStage.DIRECTION_REJECTED,
            detector.lastDiagnosticEvent?.stage,
        )
    }

    @Test
    fun strongPeakKeepsOriginalDirectionTolerance() {
        assertFalse(detector.onSample(13f, 0f, 0f, 0L))
        assertFalse(detector.onSample(0f, 0f, 0f, 200L))

        assertTrue(detector.onSample(-2f, 6f, 0f, 450L))
        assertEquals(-0.25f, detector.lastDiagnosticEvent?.requiredCosine ?: 0f, 0.001f)
    }

    @Test
    fun strongReturnPeakKeepsOriginalDirectionTolerance() {
        assertFalse(detector.onSample(8f, 0f, 0f, 0L))
        assertFalse(detector.onSample(0f, 0f, 0f, 200L))

        assertTrue(detector.onSample(-4f, 12f, 0f, 450L))
        assertEquals(-0.25f, detector.lastDiagnosticEvent?.requiredCosine ?: 0f, 0.001f)
    }

    @Test
    fun strongestSampleDefinesFirstPeakProfile() {
        assertFalse(detector.onSample(8f, 0f, 0f, 0L))
        assertFalse(detector.onSample(13f, 0f, 0f, 30L))
        assertFalse(detector.onSample(0f, 0f, 0f, 200L))

        assertTrue(detector.onSample(-2f, 6f, 0f, 450L))
        assertEquals(13f, detector.lastDiagnosticEvent?.firstPeakMagnitudeMps2 ?: 0f, 0.001f)
    }

    @Test
    fun weakRejectedReturnPeakDoesNotBecomeANewFirstPeak() {
        assertFalse(detector.onSample(13f, 0f, 0f, 0L))
        assertFalse(detector.onSample(0f, 0f, 0f, 150L))
        assertFalse(detector.onSample(7f, 0f, 0f, 300L))
        assertFalse(detector.onSample(0f, 0f, 0f, 350L))

        assertFalse(detector.onSample(-13f, 0f, 0f, 500L))
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
        assertFalse(detector.onSample(7.9f, 0f, 0f, 0L))

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
        assertFalse(detector.onSample(0f, 0f, 0f, 701L))

        val event = detector.lastDiagnosticEvent
        assertEquals(ShakeGestureDetector.DiagnosticStage.EXPIRED, event?.stage)
        assertEquals(701L, event?.separationMs)
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
        assertFalse(detector.onSample(-14f, 0f, 0f, 701L))

        val event = detector.lastDiagnosticEvent
        assertEquals(
            ShakeGestureDetector.DiagnosticStage.RESTARTED_AFTER_TIMEOUT,
            event?.stage,
        )
        assertEquals(701L, event?.separationMs)
        assertEquals(13f, event?.firstPeakMagnitudeMps2 ?: 0f, 0.001f)
        assertEquals(14f, event?.secondPeakMagnitudeMps2 ?: 0f, 0.001f)
    }
}
