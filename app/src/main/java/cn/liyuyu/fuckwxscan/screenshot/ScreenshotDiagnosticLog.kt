package cn.liyuyu.fuckwxscan.screenshot

import android.content.Context
import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Centralized diagnostics for the screenshot-to-QR pipeline.
 * QR payloads are intentionally never written to the log.
 */
object ScreenshotDiagnosticLog {
    const val TAG = "ScreenshotQr"

    private const val FILE_NAME = "screenshot-qr-diagnostics.log"
    private const val MAX_LINES = 1000

    @Volatile
    private var applicationContext: Context? = null
    private val fileLock = Any()
    private val timestampFormat = SimpleDateFormat(
        "yyyy-MM-dd HH:mm:ss.SSS",
        Locale.US,
    )

    fun init(context: Context) {
        applicationContext = context.applicationContext
    }

    fun debug(message: String) {
        write("D", message) { Log.d(TAG, message) }
    }

    fun info(message: String) {
        write("I", message) { Log.i(TAG, message) }
    }

    fun warn(message: String, throwable: Throwable? = null) {
        write("W", message) {
            if (throwable == null) Log.w(TAG, message) else Log.w(TAG, message, throwable)
        }
    }

    fun error(message: String, throwable: Throwable? = null) {
        write("E", message) {
            if (throwable == null) Log.e(TAG, message) else Log.e(TAG, message, throwable)
        }
    }

    fun read(context: Context): String {
        val file = diagnosticFile(context)
        if (!file.exists()) return "診断ログはまだありません。"
        return runCatching {
            file.readText().ifBlank { "診断ログはまだありません。" }
        }.getOrElse { "診断ログを読み込めませんでした: ${it.javaClass.simpleName}" }
    }

    fun clear(context: Context) {
        synchronized(fileLock) {
            diagnosticFile(context).delete()
        }
    }

    private fun write(level: String, message: String, logAction: () -> Unit) {
        logAction()
        val context = applicationContext ?: return
        val line = synchronized(timestampFormat) {
            "${timestampFormat.format(Date())} $level/$TAG: $message"
        }
        runCatching {
            synchronized(fileLock) {
                val file = diagnosticFile(context)
                file.appendText("$line\n")
                if (file.readLines().size > MAX_LINES) {
                    val retained = file.readLines().takeLast(MAX_LINES)
                    file.writeText(retained.joinToString("\n") + "\n")
                }
            }
        }
    }

    private fun diagnosticFile(context: Context): File =
        File(context.applicationContext.filesDir, FILE_NAME)
}
