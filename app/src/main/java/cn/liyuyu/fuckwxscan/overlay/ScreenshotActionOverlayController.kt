package cn.liyuyu.fuckwxscan.overlay

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.view.Gravity
import android.view.KeyEvent
import android.view.WindowManager
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import cn.liyuyu.fuckwxscan.screenshot.ScreenshotDiagnosticLog

/**
 * Displays the two actions above the current app without requesting the
 * general draw-over-other-apps permission. The accessibility service owns this
 * window type.
 */
class ScreenshotActionOverlayController(
    private val service: AccessibilityService,
) {
    private val windowManager =
        service.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private var root: FrameLayout? = null

    fun show(
        onReadQr: () -> Unit,
        onKeepScreenshot: () -> Unit,
    ): Boolean {
        ScreenshotDiagnosticLog.info("choice overlay show requested")
        dismiss()

        val overlay = FrameLayout(service).apply {
            setBackgroundColor(Color.argb(120, 0, 0, 0))
            isClickable = true
            isFocusableInTouchMode = true
            setOnClickListener { dismiss() }
            setOnKeyListener { _, keyCode, event ->
                if (keyCode == KeyEvent.KEYCODE_BACK &&
                    event.action == KeyEvent.ACTION_UP
                ) {
                    dismiss()
                    true
                } else {
                    false
                }
            }
        }

        val panel = LinearLayout(service).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(Color.WHITE)
            isClickable = true
            isFocusable = true
            elevation = dp(8)
            setPadding(dp(12).toInt(), dp(12).toInt(), dp(12).toInt(), dp(12).toInt())
            setOnClickListener { }
        }

        val readButton = Button(service).apply {
            text = "QR を読み取る"
            minHeight = dp(48).toInt()
            setOnClickListener {
                ScreenshotDiagnosticLog.info("choice button clicked=READ_QR")
                dismiss()
                onReadQr()
            }
        }
        val keepButton = Button(service).apply {
            text = "スクショを保存"
            minHeight = dp(48).toInt()
            setOnClickListener {
                ScreenshotDiagnosticLog.info("choice button clicked=KEEP_SCREENSHOT")
                dismiss()
                onKeepScreenshot()
            }
        }
        panel.addView(
            readButton,
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f),
        )
        panel.addView(
            keepButton,
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f),
        )

        overlay.addView(
            panel,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM,
            ),
        )

        val layoutParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
        }

        root = overlay
        try {
            windowManager.addView(overlay, layoutParams)
            overlay.requestFocus()
            ScreenshotDiagnosticLog.info("choice overlay attached")
            return true
        } catch (e: WindowManager.BadTokenException) {
            ScreenshotDiagnosticLog.error("choice overlay BadTokenException", e)
            root = null
            return false
        } catch (e: SecurityException) {
            ScreenshotDiagnosticLog.error("choice overlay SecurityException", e)
            root = null
            return false
        }
    }

    fun dismiss() {
        val overlay = root ?: return
        root = null
        if (overlay.isAttachedToWindow) {
            try {
                windowManager.removeView(overlay)
                ScreenshotDiagnosticLog.debug("choice overlay removed")
            } catch (e: IllegalArgumentException) {
                ScreenshotDiagnosticLog.warn("choice overlay was already removed", e)
            }
        }
    }

    private fun dp(value: Int): Float =
        value * service.resources.displayMetrics.density
}
