package cn.liyuyu.fuckwxscan.screenshot

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScreenshotCandidatePolicyTest {
    private val nowMs = 1_756_800_000_000L

    @Test
    fun screenshotFolderIsAccepted() {
        assertTrue(
            ScreenshotCandidatePolicy.isCandidate(
                media(
                    displayName = "IMG_20260902_120000.png",
                    relativePath = "Pictures/Screenshots/",
                ),
                nowMs = nowMs,
            ),
        )
    }

    @Test
    fun japaneseScreenshotNameIsAccepted() {
        assertTrue(
            ScreenshotCandidatePolicy.isCandidate(
                media(
                    displayName = "スクリーンショット_20260902-120000.png",
                    relativePath = null,
                ),
                nowMs = nowMs,
            ),
        )
    }

    @Test
    fun screenshotNameIsAcceptedWithoutRelativePath() {
        assertTrue(
            ScreenshotCandidatePolicy.isCandidate(
                media(
                    displayName = "Screenshot_20260902-120000.jpg",
                    relativePath = null,
                ),
                nowMs = nowMs,
            ),
        )
    }

    @Test
    fun cameraImageIsRejected() {
        assertFalse(
            ScreenshotCandidatePolicy.isCandidate(
                media(
                    displayName = "IMG_20260902_120000.jpg",
                    relativePath = "DCIM/Camera/",
                ),
                nowMs = nowMs,
            ),
        )
    }

    @Test
    fun downloadImageNamedScreenshotIsRejected() {
        assertFalse(
            ScreenshotCandidatePolicy.isCandidate(
                media(
                    displayName = "Screenshot_20260902.png",
                    relativePath = "Download/",
                ),
                nowMs = nowMs,
            ),
        )
    }

    @Test
    fun nonImageMediaIsRejected() {
        assertFalse(
            ScreenshotCandidatePolicy.isCandidate(
                media(
                    displayName = "Screenshot_20260902.mp4",
                    relativePath = "Pictures/Screenshots/",
                    mimeType = "video/mp4",
                ),
                nowMs = nowMs,
            ),
        )
    }

    @Test
    fun oldScreenshotIsRejected() {
        assertFalse(
            ScreenshotCandidatePolicy.isCandidate(
                media(
                    displayName = "Screenshot_20260902.png",
                    relativePath = "Pictures/Screenshots/",
                    dateAddedMs = nowMs - ScreenshotCandidatePolicy.DEFAULT_RECENT_WINDOW_MS - 1L,
                ),
                nowMs = nowMs,
            ),
        )
    }

    private fun media(
        displayName: String?,
        relativePath: String?,
        mimeType: String = "image/png",
        dateAddedMs: Long = nowMs,
        uri: String = "content://media/external/images/media/1",
        id: Long = 1L,
        isPending: Boolean = false,
    ) = ScreenshotMediaMetadata(
        id = id,
        uri = uri,
        displayName = displayName,
        relativePath = relativePath,
        mimeType = mimeType,
        dateAddedMs = dateAddedMs,
        isPending = isPending,
    )
}
