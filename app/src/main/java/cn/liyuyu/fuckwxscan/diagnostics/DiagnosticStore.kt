/*
 * SPDX-License-Identifier: Apache-2.0
 * New file added by contributors to shgnaka/FuckWxScan in 2026.
 */
package cn.liyuyu.fuckwxscan.diagnostics

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.os.Process
import android.util.Log
import cn.liyuyu.fuckwxscan.BuildConfig
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class DiagnosticSnapshot(
    val serviceConnected: Boolean,
    val serviceState: String,
    val sensorState: String,
    val lastSensorEvent: String,
    val maxShakeAcceleration: String,
    val lastShakeDecision: String,
    val lastShake: String,
    val twistSensorState: String,
    val lastTwistSensorEvent: String,
    val maxTwistAngularSpeed: String,
    val lastTwistDecision: String,
    val lastTwist: String,
    val lastKeyEvent: String,
    val lastQuadTap: String,
    val lastError: String,
    val recentEvents: String,
    val report: String,
) {
    companion object {
        fun empty() = DiagnosticSnapshot(
            serviceConnected = false,
            serviceState = NOT_RECORDED,
            sensorState = NOT_RECORDED,
            lastSensorEvent = NOT_RECORDED,
            maxShakeAcceleration = NOT_RECORDED,
            lastShakeDecision = NOT_RECORDED,
            lastShake = NOT_RECORDED,
            twistSensorState = NOT_RECORDED,
            lastTwistSensorEvent = NOT_RECORDED,
            maxTwistAngularSpeed = NOT_RECORDED,
            lastTwistDecision = NOT_RECORDED,
            lastTwist = NOT_RECORDED,
            lastKeyEvent = NOT_RECORDED,
            lastQuadTap = NOT_RECORDED,
            lastError = NOT_RECORDED,
            recentEvents = NOT_RECORDED,
            report = NOT_RECORDED,
        )

        private const val NOT_RECORDED = "記録なし"
    }
}

object DiagnosticStore {
    private const val TAG = "QrDiagnostics"
    private const val PREFERENCES_NAME = "accessibility_diagnostics"
    private const val KEY_CONNECTED_SESSION = "connected_session"
    private const val KEY_SERVICE_STATE = "service_state"
    private const val KEY_SERVICE_STATE_AT = "service_state_at"
    private const val KEY_SENSOR_STATE = "sensor_state"
    private const val KEY_SENSOR_STATE_AT = "sensor_state_at"
    private const val KEY_LAST_SENSOR_EVENT = "last_sensor_event"
    private const val KEY_LAST_SENSOR_EVENT_AT = "last_sensor_event_at"
    private const val KEY_MAX_SHAKE_ACCELERATION = "max_shake_acceleration"
    private const val KEY_MAX_SHAKE_ACCELERATION_AT = "max_shake_acceleration_at"
    private const val KEY_LAST_SHAKE_DECISION = "last_shake_decision"
    private const val KEY_LAST_SHAKE_DECISION_AT = "last_shake_decision_at"
    private const val KEY_LAST_SHAKE = "last_shake"
    private const val KEY_LAST_SHAKE_AT = "last_shake_at"
    private const val KEY_TWIST_SENSOR_STATE = "twist_sensor_state"
    private const val KEY_TWIST_SENSOR_STATE_AT = "twist_sensor_state_at"
    private const val KEY_LAST_TWIST_SENSOR_EVENT = "last_twist_sensor_event"
    private const val KEY_LAST_TWIST_SENSOR_EVENT_AT = "last_twist_sensor_event_at"
    private const val KEY_MAX_TWIST_ANGULAR_SPEED = "max_twist_angular_speed"
    private const val KEY_MAX_TWIST_ANGULAR_SPEED_AT = "max_twist_angular_speed_at"
    private const val KEY_LAST_TWIST_DECISION = "last_twist_decision"
    private const val KEY_LAST_TWIST_DECISION_AT = "last_twist_decision_at"
    private const val KEY_LAST_TWIST = "last_twist"
    private const val KEY_LAST_TWIST_AT = "last_twist_at"
    private const val KEY_LAST_KEY_EVENT = "last_key_event"
    private const val KEY_LAST_KEY_EVENT_AT = "last_key_event_at"
    private const val KEY_LAST_QUAD_TAP = "last_quad_tap"
    private const val KEY_LAST_QUAD_TAP_AT = "last_quad_tap_at"
    private const val KEY_LAST_ERROR = "last_error"
    private const val KEY_LAST_ERROR_AT = "last_error_at"
    private const val KEY_EVENTS = "events"
    private const val NOT_RECORDED = "記録なし"
    private const val MAX_EVENTS = 60
    private const val MAX_ERROR_CHARS = 12_000

    @Volatile
    private var currentSessionId = "not-initialized"

    @Volatile
    private var crashHandlerInstalled = false

    @Synchronized
    fun initialize(context: Context) {
        if (currentSessionId != "not-initialized") {
            return
        }

        currentSessionId = "${System.currentTimeMillis()}-${Process.myPid()}"
        installCrashHandler(context.applicationContext)
        recordEvent(
            context,
            category = "APP",
            message = "Application process started; session=$currentSessionId",
        )
    }

    fun markServiceStarting(context: Context, stage: String) {
        updateServiceState(context, "起動処理中：$stage", connected = false)
    }

    fun markServiceConnected(context: Context) {
        updateServiceState(context, "接続完了（onServiceConnected）", connected = true)
    }

    fun markServiceStopped(context: Context, state: String) {
        updateServiceState(context, state, connected = false)
    }

    fun recordKeyEvent(context: Context, message: String) {
        safeRecord {
            val now = System.currentTimeMillis()
            preferences(context).edit()
                .putString(KEY_LAST_KEY_EVENT, message)
                .putLong(KEY_LAST_KEY_EVENT_AT, now)
                .apply()
            appendEvent(context, now, "KEY", message, synchronous = false)
            Log.d(TAG, message)
        }
    }

    fun recordQuadTap(context: Context) {
        safeRecord {
            val now = System.currentTimeMillis()
            val message = "音量上ボタンの 4 連打を検出"
            preferences(context).edit()
                .putString(KEY_LAST_QUAD_TAP, message)
                .putLong(KEY_LAST_QUAD_TAP_AT, now)
                .apply()
            appendEvent(context, now, "GESTURE", message, synchronous = false)
            Log.i(TAG, message)
        }
    }

    fun recordSensorState(context: Context, state: String) {
        safeRecord {
            val now = System.currentTimeMillis()
            preferences(context).edit()
                .putString(KEY_SENSOR_STATE, state)
                .putLong(KEY_SENSOR_STATE_AT, now)
                .apply()
            appendEvent(context, now, "SENSOR", state, synchronous = false)
            Log.i(TAG, state)
        }
    }

    fun recordSensorSample(context: Context, sample: String) {
        safeRecord {
            val now = System.currentTimeMillis()
            preferences(context).edit()
                .putString(KEY_LAST_SENSOR_EVENT, sample)
                .putLong(KEY_LAST_SENSOR_EVENT_AT, now)
                .apply()
            Log.d(TAG, sample)
        }
    }

    fun resetShakeDetectorDiagnostics(context: Context) {
        safeRecord {
            val now = System.currentTimeMillis()
            preferences(context).edit()
                .putString(KEY_MAX_SHAKE_ACCELERATION, "0.0 m/s²（監視開始）")
                .putLong(KEY_MAX_SHAKE_ACCELERATION_AT, now)
                .putString(KEY_LAST_SHAKE_DECISION, "候補なし")
                .putLong(KEY_LAST_SHAKE_DECISION_AT, now)
                .apply()
        }
    }

    fun recordShakeMaximum(context: Context, message: String) {
        safeRecord {
            val now = System.currentTimeMillis()
            preferences(context).edit()
                .putString(KEY_MAX_SHAKE_ACCELERATION, message)
                .putLong(KEY_MAX_SHAKE_ACCELERATION_AT, now)
                .apply()
            Log.d(TAG, message)
        }
    }

    fun recordShakeDecision(context: Context, message: String) {
        safeRecord {
            val now = System.currentTimeMillis()
            preferences(context).edit()
                .putString(KEY_LAST_SHAKE_DECISION, message)
                .putLong(KEY_LAST_SHAKE_DECISION_AT, now)
                .apply()
            appendEvent(context, now, "SHAKE", message, synchronous = false)
            Log.i(TAG, message)
        }
    }

    fun recordShake(context: Context) {
        safeRecord {
            val now = System.currentTimeMillis()
            val message = "端末を振る操作を検出"
            preferences(context).edit()
                .putString(KEY_LAST_SHAKE, message)
                .putLong(KEY_LAST_SHAKE_AT, now)
                .apply()
            appendEvent(context, now, "GESTURE", message, synchronous = false)
            Log.i(TAG, message)
        }
    }

    fun recordTwistSensorState(context: Context, state: String) {
        safeRecord {
            val now = System.currentTimeMillis()
            preferences(context).edit()
                .putString(KEY_TWIST_SENSOR_STATE, state)
                .putLong(KEY_TWIST_SENSOR_STATE_AT, now)
                .apply()
            appendEvent(context, now, "TWIST_SENSOR", state, synchronous = false)
            Log.i(TAG, state)
        }
    }

    fun recordTwistSensorSample(context: Context, sample: String) {
        safeRecord {
            val now = System.currentTimeMillis()
            preferences(context).edit()
                .putString(KEY_LAST_TWIST_SENSOR_EVENT, sample)
                .putLong(KEY_LAST_TWIST_SENSOR_EVENT_AT, now)
                .apply()
            Log.d(TAG, sample)
        }
    }

    fun resetTwistDetectorDiagnostics(context: Context) {
        safeRecord {
            val now = System.currentTimeMillis()
            preferences(context).edit()
                .putString(KEY_MAX_TWIST_ANGULAR_SPEED, "0.0 rad/s（監視開始）")
                .putLong(KEY_MAX_TWIST_ANGULAR_SPEED_AT, now)
                .putString(KEY_LAST_TWIST_DECISION, "候補なし")
                .putLong(KEY_LAST_TWIST_DECISION_AT, now)
                .apply()
        }
    }

    fun recordTwistMaximum(context: Context, message: String) {
        safeRecord {
            val now = System.currentTimeMillis()
            preferences(context).edit()
                .putString(KEY_MAX_TWIST_ANGULAR_SPEED, message)
                .putLong(KEY_MAX_TWIST_ANGULAR_SPEED_AT, now)
                .apply()
            Log.d(TAG, message)
        }
    }

    fun recordTwistDecision(context: Context, message: String) {
        safeRecord {
            val now = System.currentTimeMillis()
            preferences(context).edit()
                .putString(KEY_LAST_TWIST_DECISION, message)
                .putLong(KEY_LAST_TWIST_DECISION_AT, now)
                .apply()
            appendEvent(context, now, "TWIST", message, synchronous = false)
            Log.i(TAG, message)
        }
    }

    fun recordWristTwist(context: Context) {
        safeRecord {
            val now = System.currentTimeMillis()
            val message = "手首をひねって戻す操作を検出"
            preferences(context).edit()
                .putString(KEY_LAST_TWIST, message)
                .putLong(KEY_LAST_TWIST_AT, now)
                .apply()
            appendEvent(context, now, "GESTURE", message, synchronous = false)
            Log.i(TAG, message)
        }
    }

    fun recordStage(context: Context, stage: String) {
        recordEvent(context, "STAGE", stage)
    }

    @SuppressLint("ApplySharedPref")
    fun recordError(context: Context, stage: String, error: Throwable) {
        safeRecord {
            val now = System.currentTimeMillis()
            val summary = buildString {
                append(stage)
                append(": ")
                append(error.javaClass.name)
                error.message?.takeIf { it.isNotBlank() }?.let {
                    append(": ")
                    append(it)
                }
            }
            val detail = "$summary\n${Log.getStackTraceString(error)}".take(MAX_ERROR_CHARS)
            preferences(context).edit()
                .putString(KEY_LAST_ERROR, detail)
                .putLong(KEY_LAST_ERROR_AT, now)
                .commit()
            appendEvent(context, now, "ERROR", summary, synchronous = true)
            Log.e(TAG, summary, error)
        }
    }

    fun snapshot(context: Context): DiagnosticSnapshot = try {
        val prefs = preferences(context)
        val connectedSession = prefs.getString(KEY_CONNECTED_SESSION, null)
        val connected = connectedSession == currentSessionId
        val serviceState = timedValue(
            prefs,
            KEY_SERVICE_STATE,
            KEY_SERVICE_STATE_AT,
        )
        val sensorState = timedValue(
            prefs,
            KEY_SENSOR_STATE,
            KEY_SENSOR_STATE_AT,
        )
        val lastSensorEvent = timedValue(
            prefs,
            KEY_LAST_SENSOR_EVENT,
            KEY_LAST_SENSOR_EVENT_AT,
        )
        val maxShakeAcceleration = timedValue(
            prefs,
            KEY_MAX_SHAKE_ACCELERATION,
            KEY_MAX_SHAKE_ACCELERATION_AT,
        )
        val lastShakeDecision = timedValue(
            prefs,
            KEY_LAST_SHAKE_DECISION,
            KEY_LAST_SHAKE_DECISION_AT,
        )
        val lastShake = timedValue(
            prefs,
            KEY_LAST_SHAKE,
            KEY_LAST_SHAKE_AT,
        )
        val twistSensorState = timedValue(
            prefs,
            KEY_TWIST_SENSOR_STATE,
            KEY_TWIST_SENSOR_STATE_AT,
        )
        val lastTwistSensorEvent = timedValue(
            prefs,
            KEY_LAST_TWIST_SENSOR_EVENT,
            KEY_LAST_TWIST_SENSOR_EVENT_AT,
        )
        val maxTwistAngularSpeed = timedValue(
            prefs,
            KEY_MAX_TWIST_ANGULAR_SPEED,
            KEY_MAX_TWIST_ANGULAR_SPEED_AT,
        )
        val lastTwistDecision = timedValue(
            prefs,
            KEY_LAST_TWIST_DECISION,
            KEY_LAST_TWIST_DECISION_AT,
        )
        val lastTwist = timedValue(
            prefs,
            KEY_LAST_TWIST,
            KEY_LAST_TWIST_AT,
        )
        val lastKeyEvent = timedValue(
            prefs,
            KEY_LAST_KEY_EVENT,
            KEY_LAST_KEY_EVENT_AT,
        )
        val lastQuadTap = timedValue(
            prefs,
            KEY_LAST_QUAD_TAP,
            KEY_LAST_QUAD_TAP_AT,
        )
        val lastError = timedValue(
            prefs,
            KEY_LAST_ERROR,
            KEY_LAST_ERROR_AT,
        )
        val recentEvents = prefs.getString(KEY_EVENTS, null)
            ?.takeIf { it.isNotBlank() }
            ?: NOT_RECORDED
        val report = buildReport(
            context = context,
            connected = connected,
            connectedSession = connectedSession,
            serviceState = serviceState,
            sensorState = sensorState,
            lastSensorEvent = lastSensorEvent,
            maxShakeAcceleration = maxShakeAcceleration,
            lastShakeDecision = lastShakeDecision,
            lastShake = lastShake,
            twistSensorState = twistSensorState,
            lastTwistSensorEvent = lastTwistSensorEvent,
            maxTwistAngularSpeed = maxTwistAngularSpeed,
            lastTwistDecision = lastTwistDecision,
            lastTwist = lastTwist,
            lastKeyEvent = lastKeyEvent,
            lastQuadTap = lastQuadTap,
            lastError = lastError,
            recentEvents = recentEvents,
        )

        DiagnosticSnapshot(
            serviceConnected = connected,
            serviceState = serviceState,
            sensorState = sensorState,
            lastSensorEvent = lastSensorEvent,
            maxShakeAcceleration = maxShakeAcceleration,
            lastShakeDecision = lastShakeDecision,
            lastShake = lastShake,
            twistSensorState = twistSensorState,
            lastTwistSensorEvent = lastTwistSensorEvent,
            maxTwistAngularSpeed = maxTwistAngularSpeed,
            lastTwistDecision = lastTwistDecision,
            lastTwist = lastTwist,
            lastKeyEvent = lastKeyEvent,
            lastQuadTap = lastQuadTap,
            lastError = lastError,
            recentEvents = recentEvents,
            report = report,
        )
    } catch (error: Throwable) {
        val detail = "診断情報の読み込み失敗：${error.javaClass.name}: ${error.message.orEmpty()}"
        DiagnosticSnapshot(
            serviceConnected = false,
            serviceState = detail,
            sensorState = NOT_RECORDED,
            lastSensorEvent = NOT_RECORDED,
            maxShakeAcceleration = NOT_RECORDED,
            lastShakeDecision = NOT_RECORDED,
            lastShake = NOT_RECORDED,
            twistSensorState = NOT_RECORDED,
            lastTwistSensorEvent = NOT_RECORDED,
            maxTwistAngularSpeed = NOT_RECORDED,
            lastTwistDecision = NOT_RECORDED,
            lastTwist = NOT_RECORDED,
            lastKeyEvent = NOT_RECORDED,
            lastQuadTap = NOT_RECORDED,
            lastError = detail,
            recentEvents = NOT_RECORDED,
            report = detail,
        )
    }

    private fun installCrashHandler(context: Context) {
        if (crashHandlerInstalled) {
            return
        }
        crashHandlerInstalled = true
        val previousHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            recordError(
                context,
                "未処理例外（thread=${thread.name}）",
                error,
            )
            previousHandler?.uncaughtException(thread, error)
        }
    }

    private fun updateServiceState(context: Context, state: String, connected: Boolean) {
        safeRecord {
            val now = System.currentTimeMillis()
            val editor = preferences(context).edit()
                .putString(KEY_SERVICE_STATE, state)
                .putLong(KEY_SERVICE_STATE_AT, now)
            if (connected) {
                editor.putString(KEY_CONNECTED_SESSION, currentSessionId)
            } else {
                editor.remove(KEY_CONNECTED_SESSION)
            }
            editor.apply()
            appendEvent(context, now, "SERVICE", state, synchronous = false)
            Log.i(TAG, state)
        }
    }

    private fun recordEvent(context: Context, category: String, message: String) {
        safeRecord {
            val now = System.currentTimeMillis()
            appendEvent(context, now, category, message, synchronous = false)
            Log.i(TAG, "[$category] $message")
        }
    }

    @Synchronized
    @SuppressLint("ApplySharedPref")
    private fun appendEvent(
        context: Context,
        timestamp: Long,
        category: String,
        message: String,
        synchronous: Boolean,
    ) {
        val prefs = preferences(context)
        val current = prefs.getString(KEY_EVENTS, null)
            ?.lineSequence()
            ?.filter { it.isNotBlank() }
            ?.toList()
            .orEmpty()
        val line = "${formatTimestamp(timestamp)} [$category] ${singleLine(message)}"
        val updated = (current + line).takeLast(MAX_EVENTS).joinToString("\n")
        val editor = prefs.edit().putString(KEY_EVENTS, updated)
        if (synchronous) {
            editor.commit()
        } else {
            editor.apply()
        }
    }

    private fun timedValue(
        prefs: SharedPreferences,
        valueKey: String,
        timestampKey: String,
    ): String {
        val value = prefs.getString(valueKey, null)?.takeIf { it.isNotBlank() }
            ?: return NOT_RECORDED
        val timestamp = prefs.getLong(timestampKey, 0L)
        return if (timestamp > 0L) {
            "${formatTimestamp(timestamp)}\n$value"
        } else {
            value
        }
    }

    private fun buildReport(
        context: Context,
        connected: Boolean,
        connectedSession: String?,
        serviceState: String,
        sensorState: String,
        lastSensorEvent: String,
        maxShakeAcceleration: String,
        lastShakeDecision: String,
        lastShake: String,
        twistSensorState: String,
        lastTwistSensorEvent: String,
        maxTwistAngularSpeed: String,
        lastTwistDecision: String,
        lastTwist: String,
        lastKeyEvent: String,
        lastQuadTap: String,
        lastError: String,
        recentEvents: String,
    ): String = buildString {
        appendLine("QR Volume Scanner diagnostic report")
        appendLine("generated=${formatTimestamp(System.currentTimeMillis())}")
        appendLine("package=${context.packageName}")
        appendLine("version=${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
        appendLine("device=${Build.MANUFACTURER} ${Build.MODEL}")
        appendLine("android=${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})")
        appendLine("processSession=$currentSessionId")
        appendLine("connectedSession=${connectedSession ?: "none"}")
        appendLine("runtimeConnected=$connected")
        appendLine()
        appendLine("serviceState:")
        appendLine(serviceState)
        appendLine()
        appendLine("sensorState:")
        appendLine(sensorState)
        appendLine()
        appendLine("lastSensorEvent:")
        appendLine(lastSensorEvent)
        appendLine()
        appendLine("maxShakeAcceleration:")
        appendLine(maxShakeAcceleration)
        appendLine()
        appendLine("lastShakeDecision:")
        appendLine(lastShakeDecision)
        appendLine()
        appendLine("lastShake:")
        appendLine(lastShake)
        appendLine()
        appendLine("twistSensorState:")
        appendLine(twistSensorState)
        appendLine()
        appendLine("lastTwistSensorEvent:")
        appendLine(lastTwistSensorEvent)
        appendLine()
        appendLine("maxTwistAngularSpeed:")
        appendLine(maxTwistAngularSpeed)
        appendLine()
        appendLine("lastTwistDecision:")
        appendLine(lastTwistDecision)
        appendLine()
        appendLine("lastTwist:")
        appendLine(lastTwist)
        appendLine()
        appendLine("lastKeyEvent:")
        appendLine(lastKeyEvent)
        appendLine()
        appendLine("lastQuadTap:")
        appendLine(lastQuadTap)
        appendLine()
        appendLine("lastError:")
        appendLine(lastError)
        appendLine()
        appendLine("recentEvents:")
        append(recentEvents)
    }

    private fun preferences(context: Context): SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    private fun formatTimestamp(timestamp: Long): String =
        SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.JAPAN).format(Date(timestamp))

    private fun singleLine(value: String): String =
        value.replace('\n', ' ').replace('\r', ' ')

    private inline fun safeRecord(block: () -> Unit) {
        try {
            block()
        } catch (error: Throwable) {
            Log.e(TAG, "Failed to persist diagnostics", error)
        }
    }
}
