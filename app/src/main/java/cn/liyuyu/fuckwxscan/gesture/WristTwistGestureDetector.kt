/*
 * SPDX-License-Identifier: Apache-2.0
 * New file added by contributors to shgnaka/FuckWxScan in 2026.
 */
package cn.liyuyu.fuckwxscan.gesture

import kotlin.math.sqrt

/**
 * Detects one deliberate wrist twist out and back without Android framework dependencies.
 *
 * Samples must contain angular velocity in radians per second. Timestamps must use one monotonic
 * clock, such as [android.hardware.SensorEvent.timestamp] converted to milliseconds.
 */
class WristTwistGestureDetector(
    private val firstPeakThresholdRadPerSecond: Float =
        DEFAULT_FIRST_PEAK_THRESHOLD_RAD_PER_SECOND,
    private val returnPeakThresholdRadPerSecond: Float =
        DEFAULT_RETURN_PEAK_THRESHOLD_RAD_PER_SECOND,
    private val releaseThresholdRadPerSecond: Float = DEFAULT_RELEASE_THRESHOLD_RAD_PER_SECOND,
    private val minimumOutboundAngleRad: Float = DEFAULT_MINIMUM_OUTBOUND_ANGLE_RAD,
    private val minimumReturnAngleRad: Float = DEFAULT_MINIMUM_RETURN_ANGLE_RAD,
    private val maximumOutboundDurationMs: Long = DEFAULT_MAXIMUM_OUTBOUND_DURATION_MS,
    private val maximumReturnDurationMs: Long = DEFAULT_MAXIMUM_RETURN_DURATION_MS,
    private val maximumOpposingCosine: Float = DEFAULT_MAXIMUM_OPPOSING_COSINE,
    private val cooldownMs: Long = DEFAULT_COOLDOWN_MS,
) {
    enum class DiagnosticStage {
        FIRST_PEAK,
        OUTBOUND_RELEASED,
        OUTBOUND_TOO_SHORT,
        OUTBOUND_TIMEOUT,
        RETURN_TIMEOUT,
        DIRECTION_REJECTED,
        DETECTED,
    }

    data class DiagnosticEvent(
        val sequence: Long,
        val stage: DiagnosticStage,
        val sampleSpeedRadPerSecond: Float,
        val firstPeakRadPerSecond: Float? = null,
        val returnPeakRadPerSecond: Float? = null,
        val outboundAngleRad: Float? = null,
        val returnAngleRad: Float? = null,
        val elapsedMs: Long? = null,
        val cosine: Float? = null,
    )

    private enum class Phase {
        IDLE,
        OUTBOUND,
        RETURN,
    }

    private data class Peak(
        val x: Float,
        val y: Float,
        val z: Float,
        val magnitude: Float,
    )

    private var phase = Phase.IDLE
    private var firstPeak: Peak? = null
    private var returnPeak: Peak? = null
    private var candidateStartedAtMs = UNSET_TIME
    private var returnStartedAtMs = UNSET_TIME
    private var cooldownUntilMs = UNSET_TIME
    private var lastSampleTimeMs = UNSET_TIME
    private var outboundAngleRad = 0f
    private var returnAngleRad = 0f
    private var diagnosticSequence = 0L

    var lastDiagnosticEvent: DiagnosticEvent? = null
        private set

    init {
        require(firstPeakThresholdRadPerSecond > 0f)
        require(returnPeakThresholdRadPerSecond > 0f)
        require(releaseThresholdRadPerSecond >= 0f)
        require(releaseThresholdRadPerSecond < returnPeakThresholdRadPerSecond)
        require(returnPeakThresholdRadPerSecond <= firstPeakThresholdRadPerSecond)
        require(minimumOutboundAngleRad > 0f)
        require(minimumReturnAngleRad > 0f)
        require(maximumOutboundDurationMs > 0L)
        require(maximumReturnDurationMs > 0L)
        require(maximumOpposingCosine in -1f..0f)
        require(cooldownMs >= 0L)
    }

    /** Returns true exactly when a valid out-and-back wrist twist completes. */
    fun onSample(x: Float, y: Float, z: Float, eventTimeMs: Long): Boolean {
        if (lastSampleTimeMs != UNSET_TIME && eventTimeMs < lastSampleTimeMs) {
            reset()
        }

        val elapsedSinceLastSampleMs = if (lastSampleTimeMs == UNSET_TIME) {
            0L
        } else {
            (eventTimeMs - lastSampleTimeMs).coerceIn(0L, MAXIMUM_INTEGRATION_STEP_MS)
        }
        lastSampleTimeMs = eventTimeMs

        val speed = magnitude(x, y, z)
        if (cooldownUntilMs != UNSET_TIME && eventTimeMs < cooldownUntilMs) {
            return false
        }
        if (cooldownUntilMs != UNSET_TIME) {
            cooldownUntilMs = UNSET_TIME
            clearCandidate()
        }

        return when (phase) {
            Phase.IDLE -> {
                if (speed >= firstPeakThresholdRadPerSecond) {
                    startCandidate(x, y, z, speed, eventTimeMs)
                }
                false
            }
            Phase.OUTBOUND -> onOutboundSample(
                x = x,
                y = y,
                z = z,
                speed = speed,
                eventTimeMs = eventTimeMs,
                elapsedSinceLastSampleMs = elapsedSinceLastSampleMs,
            )
            Phase.RETURN -> onReturnSample(
                x = x,
                y = y,
                z = z,
                speed = speed,
                eventTimeMs = eventTimeMs,
                elapsedSinceLastSampleMs = elapsedSinceLastSampleMs,
            )
        }
    }

    fun reset() {
        clearCandidate()
        cooldownUntilMs = UNSET_TIME
        lastSampleTimeMs = UNSET_TIME
        lastDiagnosticEvent = null
    }

    private fun onOutboundSample(
        x: Float,
        y: Float,
        z: Float,
        speed: Float,
        eventTimeMs: Long,
        elapsedSinceLastSampleMs: Long,
    ): Boolean {
        val elapsedMs = eventTimeMs - candidateStartedAtMs
        if (elapsedMs > maximumOutboundDurationMs) {
            recordDiagnostic(
                stage = DiagnosticStage.OUTBOUND_TIMEOUT,
                sampleSpeedRadPerSecond = speed,
                firstPeak = firstPeak,
                outboundAngleRad = outboundAngleRad,
                elapsedMs = elapsedMs,
            )
            clearCandidate()
            if (speed >= firstPeakThresholdRadPerSecond) {
                startCandidate(x, y, z, speed, eventTimeMs)
            }
            return false
        }

        outboundAngleRad += speed * elapsedSinceLastSampleMs / MILLIS_PER_SECOND
        val currentPeak = firstPeak
        if (currentPeak == null || speed > currentPeak.magnitude) {
            firstPeak = Peak(x, y, z, speed)
        }

        if (speed > releaseThresholdRadPerSecond) {
            return false
        }

        if (outboundAngleRad < minimumOutboundAngleRad) {
            recordDiagnostic(
                stage = DiagnosticStage.OUTBOUND_TOO_SHORT,
                sampleSpeedRadPerSecond = speed,
                firstPeak = firstPeak,
                outboundAngleRad = outboundAngleRad,
                elapsedMs = elapsedMs,
            )
            clearCandidate()
            return false
        }

        phase = Phase.RETURN
        returnStartedAtMs = eventTimeMs
        recordDiagnostic(
            stage = DiagnosticStage.OUTBOUND_RELEASED,
            sampleSpeedRadPerSecond = speed,
            firstPeak = firstPeak,
            outboundAngleRad = outboundAngleRad,
            elapsedMs = elapsedMs,
        )
        return false
    }

    private fun onReturnSample(
        x: Float,
        y: Float,
        z: Float,
        speed: Float,
        eventTimeMs: Long,
        elapsedSinceLastSampleMs: Long,
    ): Boolean {
        val elapsedMs = eventTimeMs - returnStartedAtMs
        if (elapsedMs > maximumReturnDurationMs) {
            recordDiagnostic(
                stage = DiagnosticStage.RETURN_TIMEOUT,
                sampleSpeedRadPerSecond = speed,
                firstPeak = firstPeak,
                returnPeak = returnPeak,
                outboundAngleRad = outboundAngleRad,
                returnAngleRad = returnAngleRad,
                elapsedMs = elapsedMs,
                cosine = returnPeak?.let { peak -> cosineBetween(requireNotNull(firstPeak), peak) },
            )
            clearCandidate()
            return false
        }

        val first = firstPeak ?: run {
            clearCandidate()
            return false
        }
        if (speed <= releaseThresholdRadPerSecond) {
            return false
        }

        val currentPeak = Peak(x, y, z, speed)
        val cosine = cosineBetween(first, currentPeak)
        if (cosine > maximumOpposingCosine) {
            if (speed >= returnPeakThresholdRadPerSecond) {
                recordDiagnostic(
                    stage = DiagnosticStage.DIRECTION_REJECTED,
                    sampleSpeedRadPerSecond = speed,
                    firstPeak = first,
                    returnPeak = currentPeak,
                    outboundAngleRad = outboundAngleRad,
                    returnAngleRad = returnAngleRad,
                    elapsedMs = elapsedMs,
                    cosine = cosine,
                )
            }
            return false
        }

        returnAngleRad += speed * elapsedSinceLastSampleMs / MILLIS_PER_SECOND
        val strongestReturnPeak = returnPeak
        if (strongestReturnPeak == null || speed > strongestReturnPeak.magnitude) {
            returnPeak = currentPeak
        }
        if (
            requireNotNull(returnPeak).magnitude < returnPeakThresholdRadPerSecond ||
            returnAngleRad < minimumReturnAngleRad
        ) {
            return false
        }

        recordDiagnostic(
            stage = DiagnosticStage.DETECTED,
            sampleSpeedRadPerSecond = speed,
            firstPeak = first,
            returnPeak = returnPeak,
            outboundAngleRad = outboundAngleRad,
            returnAngleRad = returnAngleRad,
            elapsedMs = elapsedMs,
            cosine = cosineBetween(first, requireNotNull(returnPeak)),
        )
        clearCandidate()
        cooldownUntilMs = eventTimeMs + cooldownMs
        return true
    }

    private fun startCandidate(
        x: Float,
        y: Float,
        z: Float,
        speed: Float,
        eventTimeMs: Long,
    ) {
        phase = Phase.OUTBOUND
        firstPeak = Peak(x, y, z, speed)
        returnPeak = null
        candidateStartedAtMs = eventTimeMs
        returnStartedAtMs = UNSET_TIME
        outboundAngleRad = 0f
        returnAngleRad = 0f
        recordDiagnostic(
            stage = DiagnosticStage.FIRST_PEAK,
            sampleSpeedRadPerSecond = speed,
            firstPeak = firstPeak,
            outboundAngleRad = outboundAngleRad,
            elapsedMs = 0L,
        )
    }

    private fun clearCandidate() {
        phase = Phase.IDLE
        firstPeak = null
        returnPeak = null
        candidateStartedAtMs = UNSET_TIME
        returnStartedAtMs = UNSET_TIME
        outboundAngleRad = 0f
        returnAngleRad = 0f
    }

    private fun recordDiagnostic(
        stage: DiagnosticStage,
        sampleSpeedRadPerSecond: Float,
        firstPeak: Peak? = null,
        returnPeak: Peak? = null,
        outboundAngleRad: Float? = null,
        returnAngleRad: Float? = null,
        elapsedMs: Long? = null,
        cosine: Float? = null,
    ) {
        diagnosticSequence += 1L
        lastDiagnosticEvent = DiagnosticEvent(
            sequence = diagnosticSequence,
            stage = stage,
            sampleSpeedRadPerSecond = sampleSpeedRadPerSecond,
            firstPeakRadPerSecond = firstPeak?.magnitude,
            returnPeakRadPerSecond = returnPeak?.magnitude,
            outboundAngleRad = outboundAngleRad,
            returnAngleRad = returnAngleRad,
            elapsedMs = elapsedMs,
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
        const val DEFAULT_FIRST_PEAK_THRESHOLD_RAD_PER_SECOND = 1.2f
        const val DEFAULT_RETURN_PEAK_THRESHOLD_RAD_PER_SECOND = 0.9f
        const val DEFAULT_RELEASE_THRESHOLD_RAD_PER_SECOND = 0.5f
        const val DEFAULT_MINIMUM_OUTBOUND_ANGLE_RAD = 0.18f
        const val DEFAULT_MINIMUM_RETURN_ANGLE_RAD = 0.14f
        const val DEFAULT_MAXIMUM_OUTBOUND_DURATION_MS = 700L
        const val DEFAULT_MAXIMUM_RETURN_DURATION_MS = 600L
        const val DEFAULT_MAXIMUM_OPPOSING_COSINE = -0.65f
        const val DEFAULT_COOLDOWN_MS = 1_500L

        private const val MILLIS_PER_SECOND = 1_000f
        private const val MAXIMUM_INTEGRATION_STEP_MS = 50L
        private const val UNSET_TIME = -1L
    }
}
