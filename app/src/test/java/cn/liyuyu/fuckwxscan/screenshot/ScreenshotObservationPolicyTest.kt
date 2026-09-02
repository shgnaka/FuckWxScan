package cn.liyuyu.fuckwxscan.screenshot

import org.junit.Assert.assertEquals
import org.junit.Test

class ScreenshotObservationPolicyTest {
    private val monitorStartedAtMs = 1_756_800_000_000L
    private val policy = ScreenshotObservationPolicy(
        monitorStartedAtMs = monitorStartedAtMs,
    )

    @Test
    fun newReadyScreenshotIsProcessed() {
        val screenshot = screenshot(
            dateAddedMs = monitorStartedAtMs + 100L,
        )

        assertEquals(
            ScreenshotObservationDecision.PROCESS,
            policy.decide(
                media = screenshot,
                nowMs = monitorStartedAtMs + 200L,
            ),
        )
    }

    @Test
    fun screenshotAddedBeforeMonitoringIsIgnored() {
        val screenshot = screenshot(
            dateAddedMs = monitorStartedAtMs - 1L,
        )

        assertEquals(
            ScreenshotObservationDecision.IGNORE,
            policy.decide(
                media = screenshot,
                nowMs = monitorStartedAtMs + 100L,
            ),
        )
    }

    @Test
    fun pendingScreenshotRequestsRetry() {
        val screenshot = screenshot(
            dateAddedMs = monitorStartedAtMs + 100L,
            isPending = true,
        )

        assertEquals(
            ScreenshotObservationDecision.RETRY,
            policy.decide(
                media = screenshot,
                nowMs = monitorStartedAtMs + 200L,
            ),
        )
    }

    @Test
    fun duplicateUriIsIgnored() {
        val screenshot = screenshot(
            uri = "content://media/external/images/media/10",
        )

        assertEquals(
            ScreenshotObservationDecision.IGNORE,
            policy.decide(
                media = screenshot,
                nowMs = monitorStartedAtMs + 200L,
                processedKeys = setOf(screenshot.uri),
            ),
        )
    }

    @Test
    fun differentUriIsProcessed() {
        val screenshot = screenshot(
            uri = "content://media/external/images/media/11",
        )

        assertEquals(
            ScreenshotObservationDecision.PROCESS,
            policy.decide(
                media = screenshot,
                nowMs = monitorStartedAtMs + 200L,
                processedKeys = setOf(
                    "content://media/external/images/media/10",
                ),
            ),
        )
    }

    @Test
    fun nonScreenshotMediaIsIgnored() {
        val cameraImage = screenshot(
            displayName = "IMG_20260902_120000.jpg",
            relativePath = "DCIM/Camera/",
        )

        assertEquals(
            ScreenshotObservationDecision.IGNORE,
            policy.decide(
                media = cameraImage,
                nowMs = monitorStartedAtMs + 200L,
            ),
        )
    }

    @Test
    fun oldPendingMediaIsIgnoredInsteadOfRetried() {
        val oldScreenshot = screenshot(
            dateAddedMs = monitorStartedAtMs - 1L,
            isPending = true,
        )

        assertEquals(
            ScreenshotObservationDecision.IGNORE,
            policy.decide(
                media = oldScreenshot,
                nowMs = monitorStartedAtMs + 200L,
            ),
        )
    }

    private fun screenshot(
        uri: String = "content://media/external/images/media/10",
        displayName: String = "Screenshot_20260902-120000.png",
        relativePath: String? = "Pictures/Screenshots/",
        dateAddedMs: Long = monitorStartedAtMs + 100L,
        isPending: Boolean = false,
    ) = ScreenshotMediaMetadata(
        id = uri.substringAfterLast('/').toLong(),
        uri = uri,
        displayName = displayName,
        relativePath = relativePath,
        mimeType = "image/png",
        dateAddedMs = dateAddedMs,
        isPending = isPending,
    )
}
