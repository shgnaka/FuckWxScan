/*
 * SPDX-License-Identifier: Apache-2.0
 * New file added by contributors to shgnaka/FuckWxScan in 2026.
 */
package cn.liyuyu.fuckwxscan.gesture

import org.junit.Assert.assertFalse
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
}
