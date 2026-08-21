/*
 * SPDX-License-Identifier: Apache-2.0
 * New file added by contributors to shgnaka/FuckWxScan in 2026.
 */
package cn.liyuyu.fuckwxscan.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.PowerManager
import android.util.Log
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import android.widget.Toast
import androidx.core.content.ContextCompat
import cn.liyuyu.fuckwxscan.App
import cn.liyuyu.fuckwxscan.R
import cn.liyuyu.fuckwxscan.capture.ScreenCaptureFacade
import cn.liyuyu.fuckwxscan.diagnostics.DiagnosticStore
import cn.liyuyu.fuckwxscan.gesture.ShakeGestureDetector
import cn.liyuyu.fuckwxscan.gesture.VolumeQuadTapDetector
import cn.liyuyu.fuckwxscan.gesture.WristTwistGestureDetector
import cn.liyuyu.fuckwxscan.overlay.MultiQrOverlayController
import cn.liyuyu.fuckwxscan.qr.QrDecoder
import cn.liyuyu.fuckwxscan.result.QrResultDispatcher
import cn.liyuyu.fuckwxscan.result.ResultHandler
import cn.liyuyu.fuckwxscan.settings.AppPreferences
import cn.liyuyu.fuckwxscan.ui.MainActivity
import cn.liyuyu.fuckwxscan.utils.BarcodeUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.math.sqrt

class QrAccessibilityService : AccessibilityService(), SensorEventListener,
    SharedPreferences.OnSharedPreferenceChangeListener {
    private val detector = VolumeQuadTapDetector()
    private val shakeDetector = ShakeGestureDetector()
    private val wristTwistDetector = WristTwistGestureDetector()
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val captureFacade by lazy { ScreenCaptureFacade(this) }
    private val multiQrOverlayControllerDelegate = lazy { MultiQrOverlayController(this) }
    private val multiQrOverlayController by multiQrOverlayControllerDelegate
    private val appPreferences by lazy { AppPreferences.sharedPreferences(this) }
    private val sensorManager by lazy {
        getSystemService(Context.SENSOR_SERVICE) as SensorManager
    }
    private val powerManager by lazy {
        getSystemService(Context.POWER_SERVICE) as PowerManager
    }
    private val keyguardManager by lazy {
        getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
    }

    private var scanInProgress = false
    private var accessibilityConnected = false
    private var shakeSensor: Sensor? = null
    private var shakeSensorRegistered = false
    private var twistSensor: Sensor? = null
    private var twistSensorRegistered = false
    private var gravityInitialized = false
    private val gravity = FloatArray(3)
    private var lastAccelerometerTimestampNs = UNSET_TIME
    private var sensorDiagnosticSamplePending = true
    private var lastMotionDiagnosticTimeMs = UNSET_TIME
    private var maxObservedShakeAccelerationMps2 = 0f
    private var lastRecordedShakeDiagnosticSequence = 0L
    private var twistSensorDiagnosticSamplePending = true
    private var lastTwistDiagnosticTimeMs = UNSET_TIME
    private var maxObservedTwistAngularSpeedRadPerSecond = 0f
    private var lastRecordedTwistDiagnosticSequence = 0L

    override fun onCreate() {
        DiagnosticStore.markServiceStarting(this, "onCreate 開始")
        try {
            super.onCreate()
            appPreferences.registerOnSharedPreferenceChangeListener(this)
            DiagnosticStore.recordStage(this, "AccessibilityService.onCreate 完了")
        } catch (error: Throwable) {
            DiagnosticStore.recordError(this, "AccessibilityService.onCreate", error)
            throw error
        }
    }

    override fun onServiceConnected() {
        DiagnosticStore.markServiceStarting(this, "onServiceConnected 開始")
        try {
            super.onServiceConnected()
            DiagnosticStore.recordStage(this, "serviceInfo 更新開始")
            serviceInfo = serviceInfo.apply {
                flags = flags or AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS
            }
            accessibilityConnected = true
            DiagnosticStore.markServiceConnected(this)
            configureShakeSensor()
            configureTwistSensor()
        } catch (error: Throwable) {
            accessibilityConnected = false
            DiagnosticStore.recordError(this, "AccessibilityService.onServiceConnected", error)
            throw error
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit

    override fun onInterrupt() {
        detector.reset()
        shakeDetector.reset()
        wristTwistDetector.reset()
        if (multiQrOverlayControllerDelegate.isInitialized()) {
            multiQrOverlayController.dismiss()
        }
        DiagnosticStore.recordStage(this, "AccessibilityService.onInterrupt")
    }

    override fun onKeyEvent(event: KeyEvent): Boolean {
        if (event.keyCode == KeyEvent.KEYCODE_VOLUME_UP) {
            val actionName = when (event.action) {
                KeyEvent.ACTION_DOWN -> "DOWN"
                KeyEvent.ACTION_UP -> "UP"
                else -> "OTHER(${event.action})"
            }
            DiagnosticStore.recordKeyEvent(
                this,
                "VOLUME_UP $actionName repeat=${event.repeatCount} eventTime=${event.eventTime}",
            )
        }
        val action = when (event.action) {
            KeyEvent.ACTION_DOWN -> VolumeQuadTapDetector.Action.DOWN
            KeyEvent.ACTION_UP -> VolumeQuadTapDetector.Action.UP
            else -> VolumeQuadTapDetector.Action.OTHER
        }
        val detected = detector.onEvent(
            isVolumeUp = event.keyCode == KeyEvent.KEYCODE_VOLUME_UP,
            action = action,
            repeatCount = event.repeatCount,
            eventTimeMs = event.eventTime,
        )
        if (detected) {
            Log.i(TAG, "Volume Up quad tap detected")
            DiagnosticStore.recordQuadTap(this)
            scanCurrentScreen()
        }

        // Keep the normal Android volume behavior for the MVP.
        return false
    }

    override fun onSharedPreferenceChanged(
        sharedPreferences: SharedPreferences?,
        key: String?,
    ) {
        serviceScope.launch {
            when (key) {
                AppPreferences.KEY_SHAKE_TRIGGER -> configureShakeSensor()
                AppPreferences.KEY_WRIST_TWIST_TRIGGER -> configureTwistSensor()
            }
        }
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (event.values.size < 3) {
            return
        }

        when {
            shakeSensorRegistered && event.sensor == shakeSensor -> handleShakeSensorEvent(event)
            twistSensorRegistered && event.sensor == twistSensor -> handleTwistSensorEvent(event)
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        val activeSensor = sensor ?: return
        val accuracyName = when (accuracy) {
            SensorManager.SENSOR_STATUS_ACCURACY_HIGH -> "HIGH"
            SensorManager.SENSOR_STATUS_ACCURACY_MEDIUM -> "MEDIUM"
            SensorManager.SENSOR_STATUS_ACCURACY_LOW -> "LOW"
            SensorManager.SENSOR_STATUS_UNRELIABLE -> "UNRELIABLE"
            else -> "UNKNOWN($accuracy)"
        }
        when {
            shakeSensorRegistered && activeSensor == shakeSensor ->
                DiagnosticStore.recordSensorState(
                    this,
                    "監視中：${describeShakeSensor(activeSensor)}、accuracy=$accuracyName",
                )
            twistSensorRegistered && activeSensor == twistSensor ->
                DiagnosticStore.recordTwistSensorState(
                    this,
                    "監視中：${describeTwistSensor(activeSensor)}、accuracy=$accuracyName",
                )
        }
    }

    override fun onDestroy() {
        accessibilityConnected = false
        unregisterShakeSensor()
        unregisterTwistSensor()
        appPreferences.unregisterOnSharedPreferenceChangeListener(this)
        if (multiQrOverlayControllerDelegate.isInitialized()) {
            multiQrOverlayController.dismiss()
        }
        DiagnosticStore.markServiceStopped(this, "サービス破棄（onDestroy）")
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun handleShakeSensorEvent(event: SensorEvent) {
        val linearAcceleration = extractLinearAcceleration(event) ?: return
        val eventTimeMs = event.timestamp / NANOS_PER_MILLISECOND
        val magnitude = accelerationMagnitude(linearAcceleration)
        recordMotionDiagnosticIfNeeded(
            event.sensor,
            linearAcceleration,
            magnitude,
            eventTimeMs,
        )

        if (!powerManager.isInteractive || keyguardManager.isKeyguardLocked) {
            shakeDetector.reset()
            return
        }

        recordShakeMaximumIfNeeded(magnitude, eventTimeMs)
        val detected = shakeDetector.onSample(
            x = linearAcceleration[0],
            y = linearAcceleration[1],
            z = linearAcceleration[2],
            eventTimeMs = eventTimeMs,
        )
        recordShakeDiagnosticIfChanged()
        if (detected) {
            Log.i(TAG, "Shake gesture detected")
            DiagnosticStore.recordShake(this)
            scanCurrentScreen()
        }
    }

    private fun handleTwistSensorEvent(event: SensorEvent) {
        val angularVelocity = event.values
        val eventTimeMs = event.timestamp / NANOS_PER_MILLISECOND
        val angularSpeed = vectorMagnitude(angularVelocity)
        recordTwistSensorDiagnosticIfNeeded(
            event.sensor,
            angularVelocity,
            angularSpeed,
            eventTimeMs,
        )

        if (!powerManager.isInteractive || keyguardManager.isKeyguardLocked) {
            wristTwistDetector.reset()
            return
        }

        recordTwistMaximumIfNeeded(angularSpeed, eventTimeMs)
        val detected = wristTwistDetector.onSample(
            x = angularVelocity[0],
            y = angularVelocity[1],
            z = angularVelocity[2],
            eventTimeMs = eventTimeMs,
        )
        recordTwistDiagnosticIfChanged()
        if (detected) {
            Log.i(TAG, "Wrist twist gesture detected")
            DiagnosticStore.recordWristTwist(this)
            scanCurrentScreen()
        }
    }

    private fun configureShakeSensor() {
        if (!accessibilityConnected) {
            return
        }
        if (!AppPreferences.isShakeTriggerEnabled(this)) {
            unregisterShakeSensor()
            DiagnosticStore.recordSensorState(this, "無効：設定が OFF")
            return
        }
        if (shakeSensorRegistered) {
            return
        }

        val sensor = sensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION)
            ?: sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        if (sensor == null) {
            DiagnosticStore.recordSensorState(this, "利用不可：加速度センサーなし")
            return
        }

        try {
            resetSensorState()
            shakeSensor = sensor
            shakeSensorRegistered = sensorManager.registerListener(
                this,
                sensor,
                SensorManager.SENSOR_DELAY_GAME,
            )
            if (!shakeSensorRegistered) {
                shakeSensor = null
                DiagnosticStore.recordSensorState(this, "登録失敗：${sensor.name}")
                return
            }

            DiagnosticStore.resetShakeDetectorDiagnostics(this)
            DiagnosticStore.recordSensorState(
                this,
                "監視中：${describeShakeSensor(sensor)}",
            )
        } catch (error: Throwable) {
            shakeSensorRegistered = false
            shakeSensor = null
            DiagnosticStore.recordSensorState(this, "登録例外：${sensor.name}")
            DiagnosticStore.recordError(this, "振動センサー登録", error)
        }
    }

    private fun unregisterShakeSensor() {
        if (shakeSensorRegistered) {
            try {
                shakeSensor?.let { sensorManager.unregisterListener(this, it) }
            } catch (error: Throwable) {
                DiagnosticStore.recordError(this, "振動センサー解除", error)
            }
        }
        shakeSensorRegistered = false
        shakeSensor = null
        resetSensorState()
    }

    private fun configureTwistSensor() {
        if (!accessibilityConnected) {
            return
        }
        if (!AppPreferences.isWristTwistTriggerEnabled(this)) {
            unregisterTwistSensor()
            DiagnosticStore.recordTwistSensorState(this, "無効：設定が OFF")
            return
        }
        if (twistSensorRegistered) {
            return
        }

        val sensor = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
        if (sensor == null) {
            DiagnosticStore.recordTwistSensorState(this, "利用不可：ジャイロセンサーなし")
            return
        }

        try {
            resetTwistSensorState()
            twistSensor = sensor
            twistSensorRegistered = sensorManager.registerListener(
                this,
                sensor,
                SensorManager.SENSOR_DELAY_GAME,
            )
            if (!twistSensorRegistered) {
                twistSensor = null
                DiagnosticStore.recordTwistSensorState(this, "登録失敗：${sensor.name}")
                return
            }

            DiagnosticStore.resetTwistDetectorDiagnostics(this)
            DiagnosticStore.recordTwistSensorState(
                this,
                "監視中：${describeTwistSensor(sensor)}",
            )
        } catch (error: Throwable) {
            twistSensorRegistered = false
            twistSensor = null
            DiagnosticStore.recordTwistSensorState(this, "登録例外：${sensor.name}")
            DiagnosticStore.recordError(this, "ひねりセンサー登録", error)
        }
    }

    private fun unregisterTwistSensor() {
        if (twistSensorRegistered) {
            try {
                twistSensor?.let { sensorManager.unregisterListener(this, it) }
            } catch (error: Throwable) {
                DiagnosticStore.recordError(this, "ひねりセンサー解除", error)
            }
        }
        twistSensorRegistered = false
        twistSensor = null
        resetTwistSensorState()
    }

    private fun resetSensorState() {
        shakeDetector.reset()
        gravityInitialized = false
        gravity.fill(0f)
        lastAccelerometerTimestampNs = UNSET_TIME
        sensorDiagnosticSamplePending = true
        lastMotionDiagnosticTimeMs = UNSET_TIME
        maxObservedShakeAccelerationMps2 = 0f
        lastRecordedShakeDiagnosticSequence = 0L
    }

    private fun resetTwistSensorState() {
        wristTwistDetector.reset()
        twistSensorDiagnosticSamplePending = true
        lastTwistDiagnosticTimeMs = UNSET_TIME
        maxObservedTwistAngularSpeedRadPerSecond = 0f
        lastRecordedTwistDiagnosticSequence = 0L
    }

    private fun describeShakeSensor(sensor: Sensor): String {
        val source = if (sensor.type == Sensor.TYPE_LINEAR_ACCELERATION) {
            "線形加速度"
        } else {
            "加速度（重力補正）"
        }
        return "${sensor.name}（$source、wakeUp=${sensor.isWakeUpSensor}）"
    }

    private fun describeTwistSensor(sensor: Sensor): String =
        "${sensor.name}（ジャイロ、wakeUp=${sensor.isWakeUpSensor}）"

    private fun extractLinearAcceleration(event: SensorEvent): FloatArray? {
        if (event.sensor.type == Sensor.TYPE_LINEAR_ACCELERATION) {
            return floatArrayOf(event.values[0], event.values[1], event.values[2])
        }
        if (event.sensor.type != Sensor.TYPE_ACCELEROMETER) {
            return null
        }

        if (!gravityInitialized || event.timestamp <= lastAccelerometerTimestampNs) {
            gravity[0] = event.values[0]
            gravity[1] = event.values[1]
            gravity[2] = event.values[2]
            gravityInitialized = true
            lastAccelerometerTimestampNs = event.timestamp
            return null
        }

        val elapsedSeconds = ((event.timestamp - lastAccelerometerTimestampNs) / NANOS_PER_SECOND)
            .toFloat()
            .coerceIn(0f, MAX_FILTER_INTERVAL_SECONDS)
        lastAccelerometerTimestampNs = event.timestamp
        val alpha = GRAVITY_FILTER_TIME_CONSTANT_SECONDS /
            (GRAVITY_FILTER_TIME_CONSTANT_SECONDS + elapsedSeconds)
        for (axis in gravity.indices) {
            gravity[axis] = alpha * gravity[axis] + (1f - alpha) * event.values[axis]
        }
        return floatArrayOf(
            event.values[0] - gravity[0],
            event.values[1] - gravity[1],
            event.values[2] - gravity[2],
        )
    }

    private fun recordMotionDiagnosticIfNeeded(
        sensor: Sensor,
        acceleration: FloatArray,
        magnitude: Float,
        eventTimeMs: Long,
    ) {
        val strongMotion = magnitude >= ShakeGestureDetector.DEFAULT_RELEASE_THRESHOLD_MPS2
        val diagnosticIntervalElapsed = lastMotionDiagnosticTimeMs == UNSET_TIME ||
            eventTimeMs - lastMotionDiagnosticTimeMs >= MOTION_DIAGNOSTIC_INTERVAL_MS
        if (!sensorDiagnosticSamplePending && !(strongMotion && diagnosticIntervalElapsed)) {
            return
        }

        sensorDiagnosticSamplePending = false
        lastMotionDiagnosticTimeMs = eventTimeMs
        DiagnosticStore.recordSensorSample(
            this,
            String.format(
                Locale.US,
                "%s linear=(%.1f, %.1f, %.1f) magnitude=%.1f eventTime=%d",
                sensor.name,
                acceleration[0],
                acceleration[1],
                acceleration[2],
                magnitude,
                eventTimeMs,
            ),
        )
    }

    private fun accelerationMagnitude(acceleration: FloatArray): Float = sqrt(
        acceleration[0] * acceleration[0] +
            acceleration[1] * acceleration[1] +
            acceleration[2] * acceleration[2],
    )

    private fun vectorMagnitude(vector: FloatArray): Float = sqrt(
        vector[0] * vector[0] +
            vector[1] * vector[1] +
            vector[2] * vector[2],
    )

    private fun recordTwistSensorDiagnosticIfNeeded(
        sensor: Sensor,
        angularVelocity: FloatArray,
        angularSpeed: Float,
        eventTimeMs: Long,
    ) {
        val deliberateRotation =
            angularSpeed >= WristTwistGestureDetector.DEFAULT_RELEASE_THRESHOLD_RAD_PER_SECOND
        val diagnosticIntervalElapsed = lastTwistDiagnosticTimeMs == UNSET_TIME ||
            eventTimeMs - lastTwistDiagnosticTimeMs >= MOTION_DIAGNOSTIC_INTERVAL_MS
        if (
            !twistSensorDiagnosticSamplePending &&
            !(deliberateRotation && diagnosticIntervalElapsed)
        ) {
            return
        }

        twistSensorDiagnosticSamplePending = false
        lastTwistDiagnosticTimeMs = eventTimeMs
        DiagnosticStore.recordTwistSensorSample(
            this,
            String.format(
                Locale.US,
                "%s angular=(%.2f, %.2f, %.2f) speed=%.2f rad/s eventTime=%d",
                sensor.name,
                angularVelocity[0],
                angularVelocity[1],
                angularVelocity[2],
                angularSpeed,
                eventTimeMs,
            ),
        )
    }

    private fun recordShakeMaximumIfNeeded(magnitude: Float, eventTimeMs: Long) {
        if (magnitude < maxObservedShakeAccelerationMps2 + MAXIMUM_RECORD_STEP_MPS2) {
            return
        }
        maxObservedShakeAccelerationMps2 = magnitude
        DiagnosticStore.recordShakeMaximum(
            this,
            String.format(
                Locale.US,
                "maximum=%.1f m/s² threshold=%.1f m/s² eventTime=%d",
                magnitude,
                ShakeGestureDetector.DEFAULT_PEAK_THRESHOLD_MPS2,
                eventTimeMs,
            ),
        )
    }

    private fun recordTwistMaximumIfNeeded(angularSpeed: Float, eventTimeMs: Long) {
        if (
            angularSpeed <
            maxObservedTwistAngularSpeedRadPerSecond + MAXIMUM_TWIST_RECORD_STEP_RAD_PER_SECOND
        ) {
            return
        }
        maxObservedTwistAngularSpeedRadPerSecond = angularSpeed
        DiagnosticStore.recordTwistMaximum(
            this,
            String.format(
                Locale.US,
                "maximum=%.2f rad/s threshold=%.2f rad/s eventTime=%d",
                angularSpeed,
                WristTwistGestureDetector.DEFAULT_FIRST_PEAK_THRESHOLD_RAD_PER_SECOND,
                eventTimeMs,
            ),
        )
    }

    private fun recordShakeDiagnosticIfChanged() {
        val event = shakeDetector.lastDiagnosticEvent ?: return
        if (event.sequence == lastRecordedShakeDiagnosticSequence) {
            return
        }
        lastRecordedShakeDiagnosticSequence = event.sequence
        DiagnosticStore.recordShakeDecision(this, formatShakeDiagnostic(event))
    }

    private fun recordTwistDiagnosticIfChanged() {
        val event = wristTwistDetector.lastDiagnosticEvent ?: return
        if (event.sequence == lastRecordedTwistDiagnosticSequence) {
            return
        }
        lastRecordedTwistDiagnosticSequence = event.sequence
        DiagnosticStore.recordTwistDecision(this, formatTwistDiagnostic(event))
    }

    private fun formatShakeDiagnostic(event: ShakeGestureDetector.DiagnosticEvent): String {
        val first = formatMagnitude(event.firstPeakMagnitudeMps2)
        val second = formatMagnitude(event.secondPeakMagnitudeMps2)
        val sample = formatMagnitude(event.sampleMagnitudeMps2)
        val interval = event.separationMs?.toString() ?: "none"
        val cosine = event.cosine?.let { String.format(Locale.US, "%.2f", it) } ?: "none"
        val requiredCosine = event.requiredCosine?.let {
            String.format(Locale.US, "%.2f", it)
        } ?: "none"
        val peakThreshold = formatMagnitude(ShakeGestureDetector.DEFAULT_PEAK_THRESHOLD_MPS2)
        val strongPeakThreshold = formatMagnitude(
            ShakeGestureDetector.DEFAULT_STRONG_PEAK_THRESHOLD_MPS2,
        )
        val secondPeakThreshold = formatMagnitude(
            ShakeGestureDetector.DEFAULT_SECOND_PEAK_THRESHOLD_MPS2,
        )
        val releaseThreshold = formatMagnitude(
            ShakeGestureDetector.DEFAULT_RELEASE_THRESHOLD_MPS2,
        )
        val minimumInterval = ShakeGestureDetector.DEFAULT_MIN_PEAK_SEPARATION_MS
        val maximumInterval = ShakeGestureDetector.DEFAULT_MAX_PEAK_SEPARATION_MS
        val detail = when (event.stage) {
            ShakeGestureDetector.DiagnosticStage.FIRST_PEAK ->
                "第1ピーク受付：first=$first minimum=$peakThreshold " +
                    "strong=$strongPeakThreshold"
            ShakeGestureDetector.DiagnosticStage.REARMED ->
                "第1ピーク後に再待機：first=$first releaseSample=$sample " +
                    "required<=$releaseThreshold secondThreshold=$secondPeakThreshold"
            ShakeGestureDetector.DiagnosticStage.EXPIRED ->
                "不成立：第2ピーク待ち時間切れ first=$first " +
                    "elapsed=${interval}ms max=${maximumInterval}ms"
            ShakeGestureDetector.DiagnosticStage.RESTARTED_AFTER_TIMEOUT ->
                "時間切れ後に新候補：previous=$first current=$second elapsed=${interval}ms"
            ShakeGestureDetector.DiagnosticStage.TOO_SOON ->
                "第2ピークを棄却：早すぎる first=$first second=$second " +
                    "interval=${interval}ms min=${minimumInterval}ms cosine=$cosine"
            ShakeGestureDetector.DiagnosticStage.DIRECTION_REJECTED ->
                "不成立：方向反転不足 first=$first second=$second " +
                    "interval=${interval}ms cosine=$cosine required<=$requiredCosine"
            ShakeGestureDetector.DiagnosticStage.DETECTED ->
                "成立：first=$first second=$second interval=${interval}ms " +
                    "cosine=$cosine required<=$requiredCosine"
        }
        return "seq=${event.sequence} $detail"
    }

    private fun formatMagnitude(value: Float?): String =
        value?.let { String.format(Locale.US, "%.1f", it) } ?: "none"

    private fun formatTwistDiagnostic(
        event: WristTwistGestureDetector.DiagnosticEvent,
    ): String {
        val sample = formatAngularSpeed(event.sampleSpeedRadPerSecond)
        val first = formatAngularSpeed(event.firstPeakRadPerSecond)
        val returning = formatAngularSpeed(event.returnPeakRadPerSecond)
        val outboundAngle = formatAngle(event.outboundAngleRad)
        val returnAngle = formatAngle(event.returnAngleRad)
        val elapsed = event.elapsedMs?.toString() ?: "none"
        val cosine = event.cosine?.let { String.format(Locale.US, "%.2f", it) } ?: "none"
        val firstThreshold = formatAngularSpeed(
            WristTwistGestureDetector.DEFAULT_FIRST_PEAK_THRESHOLD_RAD_PER_SECOND,
        )
        val returnThreshold = formatAngularSpeed(
            WristTwistGestureDetector.DEFAULT_RETURN_PEAK_THRESHOLD_RAD_PER_SECOND,
        )
        val releaseThreshold = formatAngularSpeed(
            WristTwistGestureDetector.DEFAULT_RELEASE_THRESHOLD_RAD_PER_SECOND,
        )
        val minimumOutboundAngle = formatAngle(
            WristTwistGestureDetector.DEFAULT_MINIMUM_OUTBOUND_ANGLE_RAD,
        )
        val minimumReturnAngle = formatAngle(
            WristTwistGestureDetector.DEFAULT_MINIMUM_RETURN_ANGLE_RAD,
        )
        val maximumOutboundDuration =
            WristTwistGestureDetector.DEFAULT_MAXIMUM_OUTBOUND_DURATION_MS
        val maximumReturnDuration = WristTwistGestureDetector.DEFAULT_MAXIMUM_RETURN_DURATION_MS
        val requiredCosine = String.format(
            Locale.US,
            "%.2f",
            WristTwistGestureDetector.DEFAULT_MAXIMUM_OPPOSING_COSINE,
        )
        val detail = when (event.stage) {
            WristTwistGestureDetector.DiagnosticStage.FIRST_PEAK ->
                "第1回転受付：first=$first threshold=$firstThreshold rad/s"
            WristTwistGestureDetector.DiagnosticStage.OUTBOUND_RELEASED ->
                "往路完了：first=$first angle=$outboundAngle rad " +
                    "required>=$minimumOutboundAngle releaseSample=$sample " +
                    "required<=$releaseThreshold rad/s"
            WristTwistGestureDetector.DiagnosticStage.OUTBOUND_TOO_SHORT ->
                "不成立：往路角度不足 first=$first angle=$outboundAngle rad " +
                    "required>=$minimumOutboundAngle"
            WristTwistGestureDetector.DiagnosticStage.OUTBOUND_TIMEOUT ->
                "不成立：往路時間切れ first=$first elapsed=${elapsed}ms " +
                    "max=${maximumOutboundDuration}ms"
            WristTwistGestureDetector.DiagnosticStage.RETURN_TIMEOUT ->
                "不成立：復路時間切れ first=$first return=$returning " +
                    "returnAngle=$returnAngle rad required>=$minimumReturnAngle " +
                    "elapsed=${elapsed}ms max=${maximumReturnDuration}ms"
            WristTwistGestureDetector.DiagnosticStage.DIRECTION_REJECTED ->
                "復路候補を棄却：方向反転不足 first=$first return=$returning " +
                    "returnThreshold=$returnThreshold cosine=$cosine " +
                    "required<=$requiredCosine"
            WristTwistGestureDetector.DiagnosticStage.DETECTED ->
                "成立：first=$first return=$returning outboundAngle=$outboundAngle rad " +
                    "returnAngle=$returnAngle rad elapsed=${elapsed}ms cosine=$cosine " +
                    "required<=$requiredCosine"
        }
        return "seq=${event.sequence} $detail"
    }

    private fun formatAngularSpeed(value: Float?): String =
        value?.let { String.format(Locale.US, "%.2f", it) } ?: "none"

    private fun formatAngle(value: Float?): String =
        value?.let { String.format(Locale.US, "%.2f", it) } ?: "none"

    private fun scanCurrentScreen() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            DiagnosticStore.recordStage(this, "旧方式の画面取得を開始")
            triggerLegacyCapture()
            return
        }
        if (scanInProgress) {
            DiagnosticStore.recordStage(this, "画面取得を省略：処理中")
            return
        }
        if (multiQrOverlayController.isShowing) {
            DiagnosticStore.recordStage(this, "画面取得を省略：選択画面表示中")
            return
        }
        scanInProgress = true
        DiagnosticStore.recordStage(this, "AccessibilityService.takeScreenshot 開始")

        serviceScope.launch {
            try {
                val bitmap = captureFacade.capture().getOrElse { error ->
                    Log.e(TAG, "Unable to capture current screen", error)
                    DiagnosticStore.recordError(this@QrAccessibilityService, "画面取得", error)
                    Toast.makeText(
                        this@QrAccessibilityService,
                        R.string.screenshot_failed,
                        Toast.LENGTH_SHORT,
                    ).show()
                    return@launch
                }
                DiagnosticStore.recordStage(this@QrAccessibilityService, "画面取得成功")

                try {
                    val results = withContext(Dispatchers.Default) {
                        QrDecoder.decode(bitmap)
                    }
                    DiagnosticStore.recordStage(
                        this@QrAccessibilityService,
                        "QR デコード完了：${results.size} 件",
                    )
                    val screenshotUri = if (QrResultDispatcher.needsScreenshotFile(results)) {
                        withContext(Dispatchers.IO) {
                            BarcodeUtil.getBitmapUri(bitmap, this@QrAccessibilityService)
                        }
                    } else {
                        null
                    }
                    if (results.size > 1) {
                        val overlayShown = multiQrOverlayController.show(
                            sourceWidth = bitmap.width,
                            sourceHeight = bitmap.height,
                            results = results,
                            onSelected = { result ->
                                DiagnosticStore.recordStage(
                                    this@QrAccessibilityService,
                                    "複数 QR 選択完了",
                                )
                                ResultHandler(this@QrAccessibilityService).handle(
                                    result.text,
                                    screenshotUri,
                                )
                            },
                        )
                        if (!overlayShown) {
                            Toast.makeText(
                                this@QrAccessibilityService,
                                R.string.qr_selection_overlay_failed,
                                Toast.LENGTH_SHORT,
                            ).show()
                            DiagnosticStore.recordStage(
                                this@QrAccessibilityService,
                                "複数 QR 選択オーバーレイ表示失敗",
                            )
                        } else {
                            DiagnosticStore.recordStage(
                                this@QrAccessibilityService,
                                "複数 QR 選択オーバーレイ表示：${results.size} 件",
                            )
                        }
                    } else {
                        QrResultDispatcher(this@QrAccessibilityService).dispatch(
                            results,
                            screenshotUri,
                        )
                    }
                    DiagnosticStore.recordStage(this@QrAccessibilityService, "結果処理完了")
                } finally {
                    if (!bitmap.isRecycled) {
                        bitmap.recycle()
                    }
                }
            } catch (error: Throwable) {
                DiagnosticStore.recordError(this@QrAccessibilityService, "画面読み取り処理", error)
                throw error
            } finally {
                scanInProgress = false
            }
        }
    }

    private fun triggerLegacyCapture() {
        if (scanInProgress) {
            return
        }
        if (App.screenCaptureIntentResult != null) {
            ContextCompat.startForegroundService(
                this,
                Intent(this, CaptureService::class.java),
            )
            return
        }

        Toast.makeText(this, R.string.legacy_capture_permission_needed, Toast.LENGTH_LONG).show()
        startActivity(
            Intent(this, MainActivity::class.java).apply {
                action = MainActivity.ACTION_REQUEST_LEGACY_CAPTURE
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            },
        )
    }

    companion object {
        private const val TAG = "QrAccessibility"
        private const val UNSET_TIME = -1L
        private const val NANOS_PER_MILLISECOND = 1_000_000L
        private const val NANOS_PER_SECOND = 1_000_000_000.0
        private const val GRAVITY_FILTER_TIME_CONSTANT_SECONDS = 0.8f
        private const val MAX_FILTER_INTERVAL_SECONDS = 1f
        private const val MOTION_DIAGNOSTIC_INTERVAL_MS = 500L
        private const val MAXIMUM_RECORD_STEP_MPS2 = 0.1f
        private const val MAXIMUM_TWIST_RECORD_STEP_RAD_PER_SECOND = 0.05f
    }
}
