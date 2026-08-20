/*
 * SPDX-License-Identifier: Apache-2.0
 * New file added by contributors to shgnaka/FuckWxScan in 2026.
 */
package cn.liyuyu.fuckwxscan.gesture

import kotlin.math.sqrt

/**
 * Detects one deliberate back-and-forth shake without Android framework dependencies.
 *
 * Samples must contain linear acceleration in metres per second squared. Timestamps must use
 * one monotonic clock, such as [android.hardware.SensorEvent.timestamp] converted to milliseconds.
 */
class ShakeGestureDetector(
    private val peakThresholdMps2: Float = DEFAULT_PEAK_THRESHOLD_MPS2,
    private val releaseThresholdMps2: Float = DEFAULT_RELEASE_THRESHOLD_MPS2,
    private val minPeakSeparationMs: Long = DEFAULT_MIN_PEAK_SEPARATION_MS,
    private val maxPeakSeparationMs: Long = DEFAULT_MAX_PEAK_SEPARATION_MS,
    private val maxOpposingCosine: Float = DEFAULT_MAX_OPPOSING_COSINE,
    private val cooldownMs: Long = DEFAULT_COOLDOWN_MS,
) {
    private data class Peak(
        val x: Float,
        val y: Float,
        val z: Float,
        val magnitude: Float,
        val timeMs: Long,
    )

    private var firstPeak: Peak? = null
    private var peakArmed = true
    private var cooldownUntilMs = UNSET_TIME
    private var lastSampleTimeMs = UNSET_TIME

    init {
        require(peakThresholdMps2 > 0f)
        require(releaseThresholdMps2 >= 0f)
        require(releaseThresholdMps2 < peakThresholdMps2)
        require(minPeakSeparationMs >= 0L)
        require(maxPeakSeparationMs >= minPeakSeparationMs)
        require(maxOpposingCosine in -1f..1f)
        require(cooldownMs >= 0L)
    }

    /** Returns true exactly when a valid back-and-forth shake completes. */
    fun onSample(x: Float, y: Float, z: Float, eventTimeMs: Long): Boolean {
        if (lastSampleTimeMs != UNSET_TIME && eventTimeMs < lastSampleTimeMs) {
            reset()
        }
        lastSampleTimeMs = eventTimeMs

        val magnitude = magnitude(x, y, z)
        if (cooldownUntilMs != UNSET_TIME && eventTimeMs < cooldownUntilMs) {
            if (magnitude <= releaseThresholdMps2) {
                peakArmed = true
            }
            return false
        }

        val existingPeak = firstPeak
        if (existingPeak != null && eventTimeMs - existingPeak.timeMs > maxPeakSeparationMs) {
            firstPeak = null
        }

        if (magnitude <= releaseThresholdMps2) {
            peakArmed = true
            return false
        }
        if (!peakArmed || magnitude < peakThresholdMps2) {
            return false
        }

        peakArmed = false
        val currentPeak = Peak(x, y, z, magnitude, eventTimeMs)
        val previousPeak = firstPeak
        if (previousPeak == null) {
            firstPeak = currentPeak
            return false
        }

        val separationMs = eventTimeMs - previousPeak.timeMs
        if (separationMs < minPeakSeparationMs) {
            return false
        }

        if (separationMs <= maxPeakSeparationMs &&
            cosineBetween(previousPeak, currentPeak) <= maxOpposingCosine
        ) {
            firstPeak = null
            cooldownUntilMs = eventTimeMs + cooldownMs
            return true
        }

        firstPeak = currentPeak
        return false
    }

    fun reset() {
        firstPeak = null
        peakArmed = true
        cooldownUntilMs = UNSET_TIME
        lastSampleTimeMs = UNSET_TIME
    }

    private fun cosineBetween(first: Peak, second: Peak): Float {
        val dotProduct = first.x * second.x + first.y * second.y + first.z * second.z
        return dotProduct / (first.magnitude * second.magnitude)
    }

    private fun magnitude(x: Float, y: Float, z: Float): Float =
        sqrt(x * x + y * y + z * z)

    companion object {
        const val DEFAULT_PEAK_THRESHOLD_MPS2 = 12f
        const val DEFAULT_RELEASE_THRESHOLD_MPS2 = 4f
        const val DEFAULT_MIN_PEAK_SEPARATION_MS = 60L
        const val DEFAULT_MAX_PEAK_SEPARATION_MS = 350L
        const val DEFAULT_MAX_OPPOSING_COSINE = -0.25f
        const val DEFAULT_COOLDOWN_MS = 1_500L

        private const val UNSET_TIME = -1L
    }
}
