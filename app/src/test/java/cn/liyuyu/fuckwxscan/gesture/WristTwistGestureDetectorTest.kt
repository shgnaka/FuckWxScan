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

class WristTwistGestureDetectorTest {
    private val detector = WristTwistGestureDetector()

    @Test
    fun deliberateOutAndBackTwistTriggers() {
        assertFalse(detector.onSample(2f, 0f, 0f, 0L))
        assertFalse(detector.onSample(4f, 0f, 0f, 50L))
        assertFalse(detector.onSample(2f, 0f, 0f, 100L))
        assertFalse(detector.onSample(0f, 0f, 0f, 150L))
        assertFalse(detector.onSample(-2f, 0f, 0f, 200L))
        assertTrue(detector.onSample(-2f, 0f, 0f, 250L))

        assertEquals(
            WristTwistGestureDetector.DiagnosticStage.DETECTED,
            detector.lastDiagnosticEvent?.stage,
        )
    }

    @Test
    fun oneWayRotationDoesNotTrigger() {
        assertFalse(detector.onSample(2f, 0f, 0f, 0L))
        assertFalse(detector.onSample(4f, 0f, 0f, 50L))
        assertFalse(detector.onSample(2f, 0f, 0f, 100L))
        assertFalse(detector.onSample(0f, 0f, 0f, 150L))
        assertFalse(detector.onSample(2f, 0f, 0f, 250L))

        assertEquals(
            WristTwistGestureDetector.DiagnosticStage.DIRECTION_REJECTED,
            detector.lastDiagnosticEvent?.stage,
        )
    }

    @Test
    fun motionBelowStartThresholdDoesNotCreateCandidate() {
        assertFalse(detector.onSample(1.19f, 0f, 0f, 0L))

        assertNull(detector.lastDiagnosticEvent)
    }

    @Test
    fun tinyOutboundFlickIsRejected() {
        assertFalse(detector.onSample(2f, 0f, 0f, 0L))
        assertFalse(detector.onSample(0f, 0f, 0f, 20L))

        val event = detector.lastDiagnosticEvent
        assertEquals(
            WristTwistGestureDetector.DiagnosticStage.OUTBOUND_TOO_SHORT,
            event?.stage,
        )
        assertTrue((event?.outboundAngleRad ?: 1f) < 0.18f)
    }

    @Test
    fun separateReturnWindowAllowsRelaxedRoundTrip() {
        assertFalse(detector.onSample(2f, 0f, 0f, 0L))
        assertFalse(detector.onSample(2f, 0f, 0f, 100L))
        assertFalse(detector.onSample(2f, 0f, 0f, 500L))
        assertFalse(detector.onSample(0f, 0f, 0f, 650L))
        assertFalse(detector.onSample(-2f, 0f, 0f, 1_100L))

        assertTrue(detector.onSample(-2f, 0f, 0f, 1_150L))
    }

    @Test
    fun returnAfterSeparateWindowDoesNotTrigger() {
        assertFalse(detector.onSample(2f, 0f, 0f, 0L))
        assertFalse(detector.onSample(4f, 0f, 0f, 50L))
        assertFalse(detector.onSample(2f, 0f, 0f, 100L))
        assertFalse(detector.onSample(0f, 0f, 0f, 150L))
        assertFalse(detector.onSample(-2f, 0f, 0f, 751L))

        assertEquals(
            WristTwistGestureDetector.DiagnosticStage.RETURN_TIMEOUT,
            detector.lastDiagnosticEvent?.stage,
        )
    }

    @Test
    fun diagonalRotationCanReturnAlongSameAxis() {
        assertFalse(detector.onSample(2f, 2f, 0f, 0L))
        assertFalse(detector.onSample(3f, 3f, 0f, 50L))
        assertFalse(detector.onSample(2f, 2f, 0f, 100L))
        assertFalse(detector.onSample(0f, 0f, 0f, 150L))
        assertFalse(detector.onSample(-2f, -2f, 0f, 200L))

        assertTrue(detector.onSample(-2f, -2f, 0f, 250L))
    }

    @Test
    fun differentReturnAxisIsRejected() {
        assertFalse(detector.onSample(2f, 0f, 0f, 0L))
        assertFalse(detector.onSample(4f, 0f, 0f, 50L))
        assertFalse(detector.onSample(2f, 0f, 0f, 100L))
        assertFalse(detector.onSample(0f, 0f, 0f, 150L))
        assertFalse(detector.onSample(0f, -2f, 0f, 250L))

        val event = detector.lastDiagnosticEvent
        assertEquals(
            WristTwistGestureDetector.DiagnosticStage.DIRECTION_REJECTED,
            event?.stage,
        )
        assertEquals(0f, event?.cosine ?: 1f, 0.001f)
    }

    @Test
    fun cooldownBlocksImmediateSecondTwist() {
        completeTwist(startTimeMs = 0L)

        assertFalse(detector.onSample(2f, 0f, 0f, 400L))
        assertFalse(detector.onSample(0f, 0f, 0f, 500L))
        assertFalse(detector.onSample(-2f, 0f, 0f, 600L))
    }

    @Test
    fun detectorRearmsAfterCooldown() {
        completeTwist(startTimeMs = 0L)

        completeTwist(startTimeMs = 1_800L)
    }

    @Test
    fun timestampGoingBackResetsCandidate() {
        assertFalse(detector.onSample(2f, 0f, 0f, 100L))
        assertFalse(detector.onSample(4f, 0f, 0f, 150L))

        assertFalse(detector.onSample(-2f, 0f, 0f, 50L))
        assertEquals(
            WristTwistGestureDetector.DiagnosticStage.FIRST_PEAK,
            detector.lastDiagnosticEvent?.stage,
        )
    }

    private fun completeTwist(startTimeMs: Long) {
        assertFalse(detector.onSample(2f, 0f, 0f, startTimeMs))
        assertFalse(detector.onSample(4f, 0f, 0f, startTimeMs + 50L))
        assertFalse(detector.onSample(2f, 0f, 0f, startTimeMs + 100L))
        assertFalse(detector.onSample(0f, 0f, 0f, startTimeMs + 150L))
        assertFalse(detector.onSample(-2f, 0f, 0f, startTimeMs + 200L))
        assertTrue(detector.onSample(-2f, 0f, 0f, startTimeMs + 250L))
    }
}
