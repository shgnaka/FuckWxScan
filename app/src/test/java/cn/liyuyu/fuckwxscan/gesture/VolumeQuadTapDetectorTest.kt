/*
 * SPDX-License-Identifier: Apache-2.0
 * New file added by contributors to shgnaka/FuckWxScan in 2026.
 */
package cn.liyuyu.fuckwxscan.gesture

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VolumeQuadTapDetectorTest {
    private val detector = VolumeQuadTapDetector()

    @Test
    fun fourFastCompletedTapsTriggerOnce() {
        assertFalse(tap(0))
        assertFalse(tap(90))
        assertFalse(tap(180))
        assertTrue(tap(270))
        assertFalse(tap(360))
    }

    @Test
    fun threeTapsDoNotTrigger() {
        assertFalse(tap(0))
        assertFalse(tap(90))
        assertFalse(tap(180))
    }

    @Test
    fun exactTimingBoundariesAreAccepted() {
        assertFalse(tap(0, 10))
        assertFalse(tap(100, 110))
        assertFalse(tap(200, 210))
        assertTrue(tap(290, 300))
    }

    @Test
    fun interTapTimeoutStartsANewCandidate() {
        assertFalse(tap(0))
        assertFalse(tap(101))
        assertFalse(tap(191))
        assertFalse(tap(281))
        assertTrue(tap(371))
    }

    @Test
    fun totalTimeoutRejectsFourthTap() {
        assertFalse(tap(0, 1))
        assertFalse(tap(100, 101))
        assertFalse(tap(200, 201))
        assertFalse(tap(300, 301))
    }

    @Test
    fun repeatedDownResetsCandidate() {
        assertFalse(tap(0))
        assertFalse(
            detector.onEvent(
                isVolumeUp = true,
                action = VolumeQuadTapDetector.Action.DOWN,
                repeatCount = 1,
                eventTimeMs = 40,
            ),
        )
        assertFalse(tap(100))
        assertFalse(tap(190))
        assertFalse(tap(280))
    }

    @Test
    fun anotherKeyResetsCandidate() {
        assertFalse(tap(0))
        assertFalse(
            detector.onEvent(
                isVolumeUp = false,
                action = VolumeQuadTapDetector.Action.DOWN,
                repeatCount = 0,
                eventTimeMs = 40,
            ),
        )
        assertFalse(tap(100))
        assertFalse(tap(190))
        assertFalse(tap(280))
    }

    @Test
    fun invalidDownUpOrderResetsCandidate() {
        assertFalse(
            detector.onEvent(
                isVolumeUp = true,
                action = VolumeQuadTapDetector.Action.UP,
                repeatCount = 0,
                eventTimeMs = 0,
            ),
        )
        assertFalse(tap(10))
        assertFalse(
            detector.onEvent(
                isVolumeUp = true,
                action = VolumeQuadTapDetector.Action.DOWN,
                repeatCount = 0,
                eventTimeMs = 50,
            ),
        )
        assertFalse(
            detector.onEvent(
                isVolumeUp = true,
                action = VolumeQuadTapDetector.Action.DOWN,
                repeatCount = 0,
                eventTimeMs = 60,
            ),
        )
    }

    private fun tap(downTimeMs: Long, upTimeMs: Long = downTimeMs + 10): Boolean {
        assertFalse(
            detector.onEvent(
                isVolumeUp = true,
                action = VolumeQuadTapDetector.Action.DOWN,
                repeatCount = 0,
                eventTimeMs = downTimeMs,
            ),
        )
        return detector.onEvent(
            isVolumeUp = true,
            action = VolumeQuadTapDetector.Action.UP,
            repeatCount = 0,
            eventTimeMs = upTimeMs,
        )
    }
}
