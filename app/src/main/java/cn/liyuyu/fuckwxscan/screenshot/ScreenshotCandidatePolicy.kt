package cn.liyuyu.fuckwxscan.screenshot

import java.util.Locale

/**
 * Identifies a recently-created image that is likely to be a system
 * screenshot, without depending on a particular device's filename convention.
 */
object ScreenshotCandidatePolicy {
    const val DEFAULT_RECENT_WINDOW_MS = 5_000L

    private val blockedFolderNames = setOf(
        "camera",
        "download",
        "downloads",
    )
    private val screenshotFolderNames = setOf(
        "screenshot",
        "screenshots",
        "スクリーンショット",
    )
    private val screenshotNameTokens = listOf(
        "screenshot",
        "screen_shot",
        "screen-shot",
        "screencap",
        "スクリーンショット",
    )

    fun isCandidate(
        media: ScreenshotMediaMetadata,
        nowMs: Long,
        recentWindowMs: Long = DEFAULT_RECENT_WINDOW_MS,
    ): Boolean {
        if (!media.mimeType.orEmpty().lowercase(Locale.ROOT).startsWith("image/")) {
            return false
        }
        if (recentWindowMs < 0L) {
            return false
        }

        val ageMs = nowMs - media.dateAddedMs
        if (ageMs < 0L || ageMs > recentWindowMs) {
            return false
        }

        val pathParts = normalizedParts(media.relativePath)
        if (pathParts.any { it in blockedFolderNames }) {
            return false
        }

        val normalizedName = normalize(media.displayName)
        return pathParts.any { it in screenshotFolderNames } ||
            screenshotNameTokens.any { normalizedName.contains(it) }
    }

    private fun normalizedParts(value: String?): List<String> =
        normalize(value)
            .split('/')
            .filter { it.isNotBlank() }

    private fun normalize(value: String?): String =
        value.orEmpty()
            .replace('\\', '/')
            .lowercase(Locale.ROOT)
}
