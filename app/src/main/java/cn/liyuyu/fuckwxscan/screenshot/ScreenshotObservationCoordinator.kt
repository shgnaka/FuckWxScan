package cn.liyuyu.fuckwxscan.screenshot

enum class ScreenshotObservationAction {
    PROCESS,
    RETRY,
    IGNORE,
}

/**
 * Keeps the state that an Android ContentObserver must preserve across
 * callbacks, while leaving all timing and matching rules Android-free.
 */
class ScreenshotObservationCoordinator(
    monitorStartedAtMs: Long,
    recentWindowMs: Long = ScreenshotCandidatePolicy.DEFAULT_RECENT_WINDOW_MS,
) {
    private val policy = ScreenshotObservationPolicy(
        monitorStartedAtMs = monitorStartedAtMs,
        recentWindowMs = recentWindowMs,
    )
    private val processedKeys = mutableSetOf<String>()
    private val firstPendingAtMs = mutableMapOf<String, Long>()
    private val terminalKeys = mutableSetOf<String>()

    fun onMediaChanged(
        media: ScreenshotMediaMetadata,
        nowMs: Long,
        attempt: Int = 0,
    ): ScreenshotObservationAction {
        val key = media.stableKey
        if (key in terminalKeys) {
            return ScreenshotObservationAction.IGNORE
        }

        return when (policy.decide(media, nowMs, processedKeys)) {
            ScreenshotObservationDecision.IGNORE -> {
                ScreenshotObservationAction.IGNORE
            }

            ScreenshotObservationDecision.PROCESS -> {
                firstPendingAtMs.remove(key)
                processedKeys += key
                terminalKeys += key
                ScreenshotObservationAction.PROCESS
            }

            ScreenshotObservationDecision.RETRY -> {
                val firstPendingAt = firstPendingAtMs.getOrPut(key) { nowMs }
                if (ScreenshotReadinessPolicy.shouldRetry(nowMs - firstPendingAt)) {
                    ScreenshotObservationAction.RETRY
                } else {
                    firstPendingAtMs.remove(key)
                    terminalKeys += key
                    ScreenshotObservationAction.IGNORE
                }
            }
        }
    }
}
