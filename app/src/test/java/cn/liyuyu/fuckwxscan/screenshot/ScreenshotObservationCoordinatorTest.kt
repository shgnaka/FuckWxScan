package cn.liyuyu.fuckwxscan.screenshot

import org.junit.Assert.assertEquals
import org.junit.Test

class ScreenshotObservationCoordinatorTest {
    private val monitorStartedAtMs = 1_756_800_000_000L

    @Test
    fun readyScreenshotIsProcessedOnlyOnce() {
        val coordinator = ScreenshotObservationCoordinator(
            monitorStartedAtMs = monitorStartedAtMs,
        )
        val screenshot = screenshot()

        assertEquals(
            ScreenshotObservationAction.PROCESS,
            coordinator.onMediaChanged(
                media = screenshot,
                nowMs = monitorStartedAtMs + 200L,
            ),
        )
        assertEquals(
            ScreenshotObservationAction.IGNORE,
            coordinator.onMediaChanged(
                media = screenshot,
                nowMs = monitorStartedAtMs + 300L,
            ),
        )
    }

    @Test
    fun pendingScreenshotUsesBoundedRetrySchedule() {
        val coordinator = ScreenshotObservationCoordinator(
            monitorStartedAtMs = monitorStartedAtMs,
        )
        val screenshot = screenshot(isPending = true)

        assertEquals(
            ScreenshotObservationAction.RETRY,
            coordinator.onMediaChanged(
                media = screenshot,
                nowMs = monitorStartedAtMs + 200L,
                attempt = 0,
            ),
        )
        assertEquals(
            ScreenshotObservationAction.RETRY,
            coordinator.onMediaChanged(
                media = screenshot,
                nowMs = monitorStartedAtMs + 300L,
                attempt = 1,
            ),
        )
        assertEquals(
            ScreenshotObservationAction.RETRY,
            coordinator.onMediaChanged(
                media = screenshot,
                nowMs = monitorStartedAtMs + 550L,
                attempt = 2,
            ),
        )
    }

    @Test
    fun pendingScreenshotIsIgnoredAfterMaximumWait() {
        val coordinator = ScreenshotObservationCoordinator(
            monitorStartedAtMs = monitorStartedAtMs,
        )
        val screenshot = screenshot(isPending = true)

        assertEquals(
            ScreenshotObservationAction.RETRY,
            coordinator.onMediaChanged(
                media = screenshot,
                nowMs = monitorStartedAtMs + 100L,
            ),
        )
        assertEquals(
            ScreenshotObservationAction.IGNORE,
            coordinator.onMediaChanged(
                media = screenshot,
                nowMs = monitorStartedAtMs + 3_100L,
                attempt = 3,
            ),
        )
    }

    @Test
    fun mediaAddedBeforeMonitoringIsIgnored() {
        val coordinator = ScreenshotObservationCoordinator(
            monitorStartedAtMs = monitorStartedAtMs,
        )

        assertEquals(
            ScreenshotObservationAction.IGNORE,
            coordinator.onMediaChanged(
                media = screenshot(
                    dateAddedMs = monitorStartedAtMs - 1L,
                ),
                nowMs = monitorStartedAtMs + 100L,
            ),
        )
    }

    private fun screenshot(
        uri: String = "content://media/external/images/media/10",
        dateAddedMs: Long = monitorStartedAtMs + 100L,
        isPending: Boolean = false,
    ) = ScreenshotMediaMetadata(
        id = 10L,
        uri = uri,
        displayName = "Screenshot_20260902-120000.png",
        relativePath = "Pictures/Screenshots/",
        mimeType = "image/png",
        dateAddedMs = dateAddedMs,
        isPending = isPending,
    )
}
