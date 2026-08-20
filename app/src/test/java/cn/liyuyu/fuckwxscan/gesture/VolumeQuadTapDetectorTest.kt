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
        assertFalse(tap(200))
        assertFalse(tap(400))
        assertTrue(tap(600))
        assertFalse(tap(800))
    }

    @Test
    fun threeTapsDoNotTrigger() {
        assertFalse(tap(0))
        assertFalse(tap(200))
        assertFalse(tap(400))
    }

    @Test
    fun exactTimingBoundariesAreAccepted() {
        assertFalse(tap(0, 10))
        assertFalse(tap(220, 230))
        assertFalse(tap(440, 450))
        assertTrue(tap(660, 700))
    }

    @Test
    fun interTapTimeoutStartsANewCandidate() {
        assertFalse(tap(0))
        assertFalse(tap(221))
        assertFalse(tap(421))
        assertFalse(tap(621))
        assertTrue(tap(821))
    }

    @Test
    fun totalTimeoutRejectsFourthTap() {
        assertFalse(tap(0, 10))
        assertFalse(tap(220, 230))
        assertFalse(tap(440, 450))
        assertFalse(tap(660, 701))
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
        assertFalse(tap(220))
        assertFalse(tap(420))
        assertFalse(tap(620))
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
        assertFalse(tap(220))
        assertFalse(tap(420))
        assertFalse(tap(620))
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
