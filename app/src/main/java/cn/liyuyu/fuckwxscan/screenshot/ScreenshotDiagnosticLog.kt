package cn.liyuyu.fuckwxscan.screenshot

import android.util.Log

/**
 * Centralized diagnostics for the screenshot-to-QR pipeline.
 * QR payloads are intentionally never written to the log.
 */
object ScreenshotDiagnosticLog {
    const val TAG = "ScreenshotQr"

    fun debug(message: String) {
        Log.d(TAG, message)
    }

    fun info(message: String) {
        Log.i(TAG, message)
    }

    fun warn(message: String, throwable: Throwable? = null) {
        if (throwable == null) {
            Log.w(TAG, message)
        } else {
            Log.w(TAG, message, throwable)
        }
    }

    fun error(message: String, throwable: Throwable? = null) {
        if (throwable == null) {
            Log.e(TAG, message)
        } else {
            Log.e(TAG, message, throwable)
        }
    }
}
