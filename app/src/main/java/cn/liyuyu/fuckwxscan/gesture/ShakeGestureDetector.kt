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
    private val secondPeakThresholdMps2: Float = DEFAULT_SECOND_PEAK_THRESHOLD_MPS2,
    private val releaseThresholdMps2: Float = DEFAULT_RELEASE_THRESHOLD_MPS2,
    private val minPeakSeparationMs: Long = DEFAULT_MIN_PEAK_SEPARATION_MS,
    private val maxPeakSeparationMs: Long = DEFAULT_MAX_PEAK_SEPARATION_MS,
    private val maxOpposingCosine: Float = DEFAULT_MAX_OPPOSING_COSINE,
    private val cooldownMs: Long = DEFAULT_COOLDOWN_MS,
) {
    enum class DiagnosticStage {
        FIRST_PEAK,
        REARMED,
        EXPIRED,
        RESTARTED_AFTER_TIMEOUT,
        TOO_SOON,
        DIRECTION_REJECTED,
        DETECTED,
    }

    data class DiagnosticEvent(
        val sequence: Long,
        val stage: DiagnosticStage,
        val sampleMagnitudeMps2: Float,
        val firstPeakMagnitudeMps2: Float? = null,
        val secondPeakMagnitudeMps2: Float? = null,
        val separationMs: Long? = null,
        val cosine: Float? = null,
    )

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
    private var diagnosticSequence = 0L

    var lastDiagnosticEvent: DiagnosticEvent? = null
        private set

    init {
        require(peakThresholdMps2 > 0f)
        require(secondPeakThresholdMps2 > 0f)
        require(releaseThresholdMps2 >= 0f)
        require(releaseThresholdMps2 < secondPeakThresholdMps2)
        require(secondPeakThresholdMps2 <= peakThresholdMps2)
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
        val expiredPeak = existingPeak?.takeIf {
            eventTimeMs - it.timeMs > maxPeakSeparationMs
        }
        if (expiredPeak != null) {
            firstPeak = null
        }

        if (magnitude <= releaseThresholdMps2) {
            val wasArmed = peakArmed
            peakArmed = true
            when {
                expiredPeak != null -> recordDiagnostic(
                    stage = DiagnosticStage.EXPIRED,
                    sampleMagnitudeMps2 = magnitude,
                    firstPeak = expiredPeak,
                    separationMs = eventTimeMs - expiredPeak.timeMs,
                )
                !wasArmed && firstPeak != null -> recordDiagnostic(
                    stage = DiagnosticStage.REARMED,
                    sampleMagnitudeMps2 = magnitude,
                    firstPeak = firstPeak,
                )
            }
            return false
        }
        val requiredPeakThreshold = if (firstPeak == null) {
            peakThresholdMps2
        } else {
            secondPeakThresholdMps2
        }
        if (!peakArmed || magnitude < requiredPeakThreshold) {
            if (expiredPeak != null) {
                recordDiagnostic(
                    stage = DiagnosticStage.EXPIRED,
                    sampleMagnitudeMps2 = magnitude,
                    firstPeak = expiredPeak,
                    separationMs = eventTimeMs - expiredPeak.timeMs,
                )
            }
            return false
        }

        peakArmed = false
        val currentPeak = Peak(x, y, z, magnitude, eventTimeMs)
        val previousPeak = firstPeak
        if (previousPeak == null) {
            firstPeak = currentPeak
            recordDiagnostic(
                stage = if (expiredPeak == null) {
                    DiagnosticStage.FIRST_PEAK
                } else {
                    DiagnosticStage.RESTARTED_AFTER_TIMEOUT
                },
                sampleMagnitudeMps2 = magnitude,
                firstPeak = expiredPeak ?: currentPeak,
                secondPeak = currentPeak.takeIf { expiredPeak != null },
                separationMs = expiredPeak?.let { eventTimeMs - it.timeMs },
            )
            return false
        }

        val separationMs = eventTimeMs - previousPeak.timeMs
        if (separationMs < minPeakSeparationMs) {
            recordDiagnostic(
                stage = DiagnosticStage.TOO_SOON,
                sampleMagnitudeMps2 = magnitude,
                firstPeak = previousPeak,
                secondPeak = currentPeak,
                separationMs = separationMs,
                cosine = cosineBetween(previousPeak, currentPeak),
            )
            return false
        }

        val cosine = cosineBetween(previousPeak, currentPeak)
        if (separationMs <= maxPeakSeparationMs && cosine <= maxOpposingCosine
        ) {
            firstPeak = null
            cooldownUntilMs = eventTimeMs + cooldownMs
            recordDiagnostic(
                stage = DiagnosticStage.DETECTED,
                sampleMagnitudeMps2 = magnitude,
                firstPeak = previousPeak,
                secondPeak = currentPeak,
                separationMs = separationMs,
                cosine = cosine,
            )
            return true
        }

        firstPeak = currentPeak.takeIf { it.magnitude >= peakThresholdMps2 }
        recordDiagnostic(
            stage = DiagnosticStage.DIRECTION_REJECTED,
            sampleMagnitudeMps2 = magnitude,
            firstPeak = previousPeak,
            secondPeak = currentPeak,
            separationMs = separationMs,
            cosine = cosine,
        )
        return false
    }

    fun reset() {
        firstPeak = null
        peakArmed = true
        cooldownUntilMs = UNSET_TIME
        lastSampleTimeMs = UNSET_TIME
        lastDiagnosticEvent = null
    }

    private fun recordDiagnostic(
        stage: DiagnosticStage,
        sampleMagnitudeMps2: Float,
        firstPeak: Peak? = null,
        secondPeak: Peak? = null,
        separationMs: Long? = null,
        cosine: Float? = null,
    ) {
        diagnosticSequence += 1L
        lastDiagnosticEvent = DiagnosticEvent(
            sequence = diagnosticSequence,
            stage = stage,
            sampleMagnitudeMps2 = sampleMagnitudeMps2,
            firstPeakMagnitudeMps2 = firstPeak?.magnitude,
            secondPeakMagnitudeMps2 = secondPeak?.magnitude,
            separationMs = separationMs,
            cosine = cosine,
        )
    }

    private fun cosineBetween(first: Peak, second: Peak): Float {
        val dotProduct = first.x * second.x + first.y * second.y + first.z * second.z
        return dotProduct / (first.magnitude * second.magnitude)
    }

    private fun magnitude(x: Float, y: Float, z: Float): Float =
        sqrt(x * x + y * y + z * z)

    companion object {
        const val DEFAULT_PEAK_THRESHOLD_MPS2 = 12f
        const val DEFAULT_SECOND_PEAK_THRESHOLD_MPS2 = 7f
        const val DEFAULT_RELEASE_THRESHOLD_MPS2 = 4f
        const val DEFAULT_MIN_PEAK_SEPARATION_MS = 100L
        const val DEFAULT_MAX_PEAK_SEPARATION_MS = 600L
        const val DEFAULT_MAX_OPPOSING_COSINE = -0.25f
        const val DEFAULT_COOLDOWN_MS = 1_500L

        private const val UNSET_TIME = -1L
    }
}
