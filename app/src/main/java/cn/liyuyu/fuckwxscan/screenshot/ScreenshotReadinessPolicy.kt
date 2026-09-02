package cn.liyuyu.fuckwxscan.screenshot

/**
 * Bounded retry timing for a screenshot row that is visible but still pending.
 */
object ScreenshotReadinessPolicy {
    const val MAX_WAIT_MS = 3_000L

    fun retryDelayMs(attempt: Int): Long =
        when {
            attempt <= 0 -> 100L
            attempt == 1 -> 250L
            else -> 500L
        }

    fun shouldRetry(elapsedMs: Long): Boolean =
        elapsedMs >= 0L && elapsedMs < MAX_WAIT_MS
}
