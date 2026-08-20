/*
 * SPDX-License-Identifier: Apache-2.0
 * New file added by contributors to shgnaka/FuckWxScan in 2026.
 */
package cn.liyuyu.fuckwxscan.overlay

import android.accessibilityservice.AccessibilityService
import android.animation.ValueAnimator
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.util.Log
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.animation.LinearInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.annotation.MainThread
import cn.liyuyu.fuckwxscan.R
import cn.liyuyu.fuckwxscan.data.BarcodeResult
import cn.liyuyu.fuckwxscan.utils.ScreenUtil

class MultiQrOverlayController(
    private val service: AccessibilityService,
) {
    private val windowManager = service.getSystemService(AccessibilityService.WINDOW_SERVICE)
        as WindowManager

    private var activeOverlay: ActiveOverlay? = null

    val isShowing: Boolean
        get() = activeOverlay != null

    @MainThread
    fun show(
        screenshot: Bitmap,
        results: List<BarcodeResult>,
        onSelected: (BarcodeResult) -> Unit,
    ): Boolean {
        if (screenshot.isRecycled || results.size < 2) {
            return false
        }

        dismiss()

        lateinit var view: MultiQrOverlayView
        view = MultiQrOverlayView(
            context = service,
            screenshot = screenshot,
            results = results,
            onCancel = ::dismiss,
            onSelected = { result ->
                dismiss()
                onSelected(result)
            },
        )
        val overlay = ActiveOverlay(view, screenshot)

        return try {
            windowManager.addView(view, createLayoutParams())
            activeOverlay = overlay
            true
        } catch (error: RuntimeException) {
            Log.e(TAG, "Unable to show multiple QR accessibility overlay", error)
            view.release()
            false
        }
    }

    @MainThread
    fun dismiss() {
        val overlay = activeOverlay ?: return
        activeOverlay = null

        try {
            windowManager.removeViewImmediate(overlay.view)
        } catch (error: RuntimeException) {
            Log.w(TAG, "Unable to remove multiple QR accessibility overlay", error)
        } finally {
            overlay.view.release()
            if (!overlay.screenshot.isRecycled) {
                overlay.screenshot.recycle()
            }
        }
    }

    private fun createLayoutParams(): WindowManager.LayoutParams {
        return WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            title = "QR Volume Scanner selection"
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }
    }

    private data class ActiveOverlay(
        val view: MultiQrOverlayView,
        val screenshot: Bitmap,
    )

    companion object {
        private const val TAG = "MultiQrOverlay"
    }
}

private class MultiQrOverlayView(
    context: android.content.Context,
    private val screenshot: Bitmap,
    private val results: List<BarcodeResult>,
    private val onCancel: () -> Unit,
    private val onSelected: (BarcodeResult) -> Unit,
) : FrameLayout(context) {
    private val density = resources.displayMetrics.density
    private val markerSize = dp(36)
    private val screenshotView = ImageView(context)
    private val markers = mutableListOf<ImageView>()
    private val pulseAnimator = ValueAnimator.ofFloat(28f / 36f, 40f / 36f).apply {
        duration = 400L
        interpolator = LinearInterpolator()
        repeatCount = ValueAnimator.INFINITE
        repeatMode = ValueAnimator.REVERSE
        addUpdateListener { animation ->
            val scale = animation.animatedValue as Float
            markers.forEach { marker ->
                marker.scaleX = scale
                marker.scaleY = scale
            }
        }
    }

    init {
        isClickable = true
        isFocusableInTouchMode = true
        contentDescription = context.getString(R.string.qr_selection_overlay_description)
        @Suppress("DEPRECATION")
        systemUiVisibility = View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
            View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
            View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION

        screenshotView.apply {
            scaleType = ImageView.ScaleType.FIT_XY
            setImageBitmap(screenshot)
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }
        addView(
            screenshotView,
            LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            ),
        )

        addView(
            View(context).apply {
                setBackgroundColor(MASK_COLOR)
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            },
            LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            ),
        )

        results.forEachIndexed { index, result ->
            val marker = createMarker(index, result)
            markers += marker
            addView(marker, LayoutParams(markerSize, markerSize))
        }

        addView(createCancelView(), createCancelLayoutParams())
        setOnKeyListener { _, keyCode, event ->
            if (keyCode == KeyEvent.KEYCODE_BACK && event.action == KeyEvent.ACTION_UP) {
                onCancel()
                true
            } else {
                false
            }
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        requestFocus()
        pulseAnimator.start()
    }

    override fun onDetachedFromWindow() {
        pulseAnimator.cancel()
        super.onDetachedFromWindow()
    }

    override fun onSizeChanged(width: Int, height: Int, oldWidth: Int, oldHeight: Int) {
        super.onSizeChanged(width, height, oldWidth, oldHeight)
        if (width <= 0 || height <= 0) {
            return
        }

        markers.forEachIndexed { index, marker ->
            val result = results[index]
            val position = OverlayGeometry.markerTopLeft(
                centerX = result.centerX,
                centerY = result.centerY,
                sourceWidth = screenshot.width,
                sourceHeight = screenshot.height,
                targetWidth = width,
                targetHeight = height,
                markerSize = markerSize,
            )
            (marker.layoutParams as LayoutParams).apply {
                leftMargin = position.left
                topMargin = position.top
                gravity = Gravity.TOP or Gravity.START
            }.also(marker::setLayoutParams)
        }
    }

    fun release() {
        pulseAnimator.cancel()
        screenshotView.setImageDrawable(null)
        markers.forEach { marker ->
            marker.setOnClickListener(null)
        }
        setOnKeyListener(null)
        setOnClickListener(null)
    }

    private fun createMarker(index: Int, result: BarcodeResult): ImageView {
        return ImageView(context).apply {
            setImageResource(R.drawable.ic_wait_click)
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.WHITE)
            }
            contentDescription = context.getString(
                R.string.qr_selection_marker_description,
                index + 1,
            )
            isClickable = true
            isFocusable = true
            setOnClickListener { onSelected(result) }
        }
    }

    private fun createCancelView(): TextView {
        return TextView(context).apply {
            text = context.getString(R.string.cancel)
            setTextColor(Color.WHITE)
            textSize = 16f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setPadding(dp(12), dp(8), dp(12), dp(8))
            isClickable = true
            isFocusable = true
            setOnClickListener { onCancel() }
        }
    }

    private fun createCancelLayoutParams(): LayoutParams {
        return LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            Gravity.TOP or Gravity.END,
        ).apply {
            topMargin = ScreenUtil.getStatusBarHeight(context) + dp(8)
            marginEnd = dp(4)
        }
    }

    private fun dp(value: Int): Int = (value * density).toInt()

    companion object {
        private const val MASK_COLOR = 0x66000000
    }
}
