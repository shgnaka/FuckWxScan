package cn.liyuyu.fuckwxscan.screenshot

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScreenshotReadinessPolicyTest {
    @Test
    fun retryScheduleStartsWithShortDelays() {
        assertEquals(100L, ScreenshotReadinessPolicy.retryDelayMs(attempt = 0))
        assertEquals(250L, ScreenshotReadinessPolicy.retryDelayMs(attempt = 1))
        assertEquals(500L, ScreenshotReadinessPolicy.retryDelayMs(attempt = 2))
    }

    @Test
    fun retryScheduleUsesStableDelayAfterInitialAttempts() {
        assertEquals(500L, ScreenshotReadinessPolicy.retryDelayMs(attempt = 3))
        assertEquals(500L, ScreenshotReadinessPolicy.retryDelayMs(attempt = 10))
    }

    @Test
    fun retryStopsAtMaximumWait() {
        assertTrue(
            ScreenshotReadinessPolicy.shouldRetry(
                elapsedMs = ScreenshotReadinessPolicy.MAX_WAIT_MS - 1L,
            ),
        )
        assertFalse(
            ScreenshotReadinessPolicy.shouldRetry(
                elapsedMs = ScreenshotReadinessPolicy.MAX_WAIT_MS,
            ),
        )
        assertFalse(
            ScreenshotReadinessPolicy.shouldRetry(elapsedMs = -1L),
        )
    }
}
