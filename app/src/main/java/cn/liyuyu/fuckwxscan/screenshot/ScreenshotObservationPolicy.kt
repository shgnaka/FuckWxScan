package cn.liyuyu.fuckwxscan.screenshot

enum class ScreenshotObservationDecision {
    PROCESS,
    RETRY,
    IGNORE,
}

/**
 * Applies the lifecycle rules around MediaStore observer callbacks.
 */
class ScreenshotObservationPolicy(
    private val monitorStartedAtMs: Long,
    private val recentWindowMs: Long = ScreenshotCandidatePolicy.DEFAULT_RECENT_WINDOW_MS,
) {
    fun decide(
        media: ScreenshotMediaMetadata,
        nowMs: Long,
        processedKeys: Set<String> = emptySet(),
    ): ScreenshotObservationDecision {
        if (media.dateAddedMs < monitorStartedAtMs) {
            return ScreenshotObservationDecision.IGNORE
        }
        if (media.stableKey in processedKeys) {
            return ScreenshotObservationDecision.IGNORE
        }
        if (!ScreenshotCandidatePolicy.isCandidate(media, nowMs, recentWindowMs)) {
            return ScreenshotObservationDecision.IGNORE
        }
        return if (media.isPending) {
            ScreenshotObservationDecision.RETRY
        } else {
            ScreenshotObservationDecision.PROCESS
        }
    }
}
