/*
 * SPDX-License-Identifier: Apache-2.0
 * New file added by contributors to shgnaka/FuckWxScan in 2026.
 */
package cn.liyuyu.fuckwxscan.gesture

/**
 * Detects four completed Volume Up taps without depending on Android framework classes.
 *
 * A tap is a non-repeated DOWN followed by UP. All timestamps must use the same monotonic
 * clock, such as [android.view.KeyEvent.getEventTime].
 */
class VolumeQuadTapDetector(
    private val requiredTapCount: Int = DEFAULT_REQUIRED_TAP_COUNT,
    private val maxInterTapIntervalMs: Long = DEFAULT_MAX_INTER_TAP_INTERVAL_MS,
    private val maxGestureDurationMs: Long = DEFAULT_MAX_GESTURE_DURATION_MS,
) {
    enum class Action {
        DOWN,
        UP,
        OTHER,
    }

    private var completedTapCount = 0
    private var firstDownTimeMs = UNSET_TIME
    private var lastDownTimeMs = UNSET_TIME
    private var pendingDownTimeMs = UNSET_TIME

    init {
        require(requiredTapCount > 0)
        require(maxInterTapIntervalMs >= 0)
        require(maxGestureDurationMs >= 0)
    }

    /** Returns true exactly when a valid gesture completes. */
    fun onEvent(
        isVolumeUp: Boolean,
        action: Action,
        repeatCount: Int,
        eventTimeMs: Long,
    ): Boolean {
        if (!isVolumeUp || action == Action.OTHER || repeatCount > 0) {
            reset()
            return false
        }

        return when (action) {
            Action.DOWN -> onDown(eventTimeMs)
            Action.UP -> onUp(eventTimeMs)
            Action.OTHER -> false
        }
    }

    fun reset() {
        completedTapCount = 0
        firstDownTimeMs = UNSET_TIME
        lastDownTimeMs = UNSET_TIME
        pendingDownTimeMs = UNSET_TIME
    }

    private fun onDown(eventTimeMs: Long): Boolean {
        if (pendingDownTimeMs != UNSET_TIME) {
            reset()
            return false
        }

        if (completedTapCount == 0) {
            startNewGesture(eventTimeMs)
            return false
        }

        val interTapInterval = eventTimeMs - lastDownTimeMs
        val gestureDuration = eventTimeMs - firstDownTimeMs
        if (interTapInterval < 0 ||
            interTapInterval > maxInterTapIntervalMs ||
            gestureDuration < 0 ||
            gestureDuration > maxGestureDurationMs
        ) {
            reset()
            startNewGesture(eventTimeMs)
            return false
        }

        lastDownTimeMs = eventTimeMs
        pendingDownTimeMs = eventTimeMs
        return false
    }

    private fun onUp(eventTimeMs: Long): Boolean {
        if (pendingDownTimeMs == UNSET_TIME || eventTimeMs < pendingDownTimeMs) {
            reset()
            return false
        }

        if (eventTimeMs - firstDownTimeMs > maxGestureDurationMs) {
            reset()
            return false
        }

        pendingDownTimeMs = UNSET_TIME
        completedTapCount += 1
        if (completedTapCount != requiredTapCount) {
            return false
        }

        reset()
        return true
    }

    private fun startNewGesture(eventTimeMs: Long) {
        firstDownTimeMs = eventTimeMs
        lastDownTimeMs = eventTimeMs
        pendingDownTimeMs = eventTimeMs
    }

    companion object {
        const val DEFAULT_REQUIRED_TAP_COUNT = 4
        const val DEFAULT_MAX_INTER_TAP_INTERVAL_MS = 220L
        const val DEFAULT_MAX_GESTURE_DURATION_MS = 700L

        private const val UNSET_TIME = -1L
    }
}
