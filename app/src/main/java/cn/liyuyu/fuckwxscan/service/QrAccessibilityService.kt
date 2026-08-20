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
import cn.liyuyu.fuckwxscan.diagnostics.DiagnosticStore
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

    override fun onCreate() {
        DiagnosticStore.markServiceStarting(this, "onCreate 開始")
        try {
            super.onCreate()
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
            DiagnosticStore.markServiceConnected(this)
        } catch (error: Throwable) {
            DiagnosticStore.recordError(this, "AccessibilityService.onServiceConnected", error)
            throw error
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit

    override fun onInterrupt() {
        detector.reset()
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

    override fun onDestroy() {
        if (multiQrOverlayControllerDelegate.isInitialized()) {
            multiQrOverlayController.dismiss()
        }
        DiagnosticStore.markServiceStopped(this, "サービス破棄（onDestroy）")
        serviceScope.cancel()
        super.onDestroy()
    }

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

                var overlayOwnsBitmap = false
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
                        overlayOwnsBitmap = multiQrOverlayController.show(
                            screenshot = bitmap,
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
                        if (!overlayOwnsBitmap) {
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
                    if (!overlayOwnsBitmap) {
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
    }
}
