/*
 * SPDX-License-Identifier: Apache-2.0
 * New file added by contributors to shgnaka/FuckWxScan in 2026.
 */
package cn.liyuyu.fuckwxscan.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Intent
import android.os.Build
import android.util.Log
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import android.widget.Toast
import androidx.core.content.ContextCompat
import cn.liyuyu.fuckwxscan.App
import cn.liyuyu.fuckwxscan.R
import cn.liyuyu.fuckwxscan.capture.ScreenCaptureFacade
import cn.liyuyu.fuckwxscan.gesture.VolumeQuadTapDetector
import cn.liyuyu.fuckwxscan.overlay.MultiQrOverlayController
import cn.liyuyu.fuckwxscan.qr.QrDecoder
import cn.liyuyu.fuckwxscan.result.QrResultDispatcher
import cn.liyuyu.fuckwxscan.result.ResultHandler
import cn.liyuyu.fuckwxscan.ui.MainActivity
import cn.liyuyu.fuckwxscan.utils.BarcodeUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class QrAccessibilityService : AccessibilityService() {
    private val detector = VolumeQuadTapDetector()
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val captureFacade by lazy { ScreenCaptureFacade(this) }
    private val multiQrOverlayControllerDelegate = lazy { MultiQrOverlayController(this) }
    private val multiQrOverlayController by multiQrOverlayControllerDelegate

    private var scanInProgress = false

    override fun onServiceConnected() {
        super.onServiceConnected()
        serviceInfo = serviceInfo.apply {
            flags = flags or AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit

    override fun onInterrupt() {
        detector.reset()
        if (multiQrOverlayControllerDelegate.isInitialized()) {
            multiQrOverlayController.dismiss()
        }
    }

    override fun onKeyEvent(event: KeyEvent): Boolean {
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
            scanCurrentScreen()
        }

        // Keep the normal Android volume behavior for the MVP.
        return false
    }

    override fun onDestroy() {
        if (multiQrOverlayControllerDelegate.isInitialized()) {
            multiQrOverlayController.dismiss()
        }
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun scanCurrentScreen() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            triggerLegacyCapture()
            return
        }
        if (scanInProgress) {
            return
        }
        if (multiQrOverlayController.isShowing) {
            return
        }
        scanInProgress = true

        serviceScope.launch {
            try {
                val bitmap = captureFacade.capture().getOrElse { error ->
                    Log.e(TAG, "Unable to capture current screen", error)
                    Toast.makeText(
                        this@QrAccessibilityService,
                        R.string.screenshot_failed,
                        Toast.LENGTH_SHORT,
                    ).show()
                    return@launch
                }

                var overlayOwnsBitmap = false
                try {
                    val results = withContext(Dispatchers.Default) {
                        QrDecoder.decode(bitmap)
                    }
                    val screenshotUri = if (QrResultDispatcher.needsScreenshotFile(results)) {
                        withContext(Dispatchers.IO) {
                            BarcodeUtil.getBitmapUri(bitmap, this@QrAccessibilityService)
                        }
                    } else {
                        null
                    }
                    if (results.size > 1) {
                        overlayOwnsBitmap = multiQrOverlayController.show(
                            screenshot = bitmap,
                            results = results,
                            onSelected = { result ->
                                ResultHandler(this@QrAccessibilityService).handle(
                                    result.text,
                                    screenshotUri,
                                )
                            },
                        )
                        if (!overlayOwnsBitmap) {
                            Toast.makeText(
                                this@QrAccessibilityService,
                                R.string.qr_selection_overlay_failed,
                                Toast.LENGTH_SHORT,
                            ).show()
                        }
                    } else {
                        QrResultDispatcher(this@QrAccessibilityService).dispatch(
                            results,
                            screenshotUri,
                        )
                    }
                } finally {
                    if (!overlayOwnsBitmap) {
                        bitmap.recycle()
                    }
                }
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
    }
}
