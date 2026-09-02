package cn.liyuyu.fuckwxscan.service

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.net.Uri
import cn.liyuyu.fuckwxscan.data.BarcodeResult
import cn.liyuyu.fuckwxscan.overlay.ScreenshotActionOverlayController
import cn.liyuyu.fuckwxscan.scan.QrReadRoute
import cn.liyuyu.fuckwxscan.scan.ScreenshotQrFlowPolicy
import cn.liyuyu.fuckwxscan.scan.ScreenshotScanDecision
import cn.liyuyu.fuckwxscan.screenshot.ScreenshotMediaMetadata
import cn.liyuyu.fuckwxscan.screenshot.ScreenshotMediaObserver
import cn.liyuyu.fuckwxscan.ui.MainActivity
import cn.liyuyu.fuckwxscan.utils.BarcodeUtil
import cn.liyuyu.fuckwxscan.utils.toBarcodeResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Observes system screenshots through MediaStore while the user has enabled
 * this accessibility service.
 */
class ScreenshotAccessibilityService : AccessibilityService() {
    private val serviceScope = CoroutineScope(
        SupervisorJob() + Dispatchers.Main.immediate,
    )
    private var mediaObserver: ScreenshotMediaObserver? = null
    private var actionOverlay: ScreenshotActionOverlayController? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        mediaObserver?.stop()
        actionOverlay?.dismiss()
        actionOverlay = ScreenshotActionOverlayController(this)
        mediaObserver = ScreenshotMediaObserver(
            context = this,
            scope = serviceScope,
            onScreenshotReady = ::decodeAndShowChoice,
        ).also { it.start() }
    }

    override fun onAccessibilityEvent(event: android.view.accessibility.AccessibilityEvent?) {
        // The service uses MediaStore only; it does not inspect or intercept
        // the foreground application's view hierarchy.
    }

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        mediaObserver?.stop()
        mediaObserver = null
        actionOverlay?.dismiss()
        actionOverlay = null
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun decodeAndShowChoice(
        media: ScreenshotMediaMetadata,
        bitmap: android.graphics.Bitmap,
    ) {
        serviceScope.launch(Dispatchers.Default) {
            val decoded = try {
                BarcodeUtil.decodeQRCode(bitmap)
            } catch (_: Exception) {
                null
            }
            val results = decoded?.map { it.toBarcodeResult() }.orEmpty()
            if (!bitmap.isRecycled) {
                bitmap.recycle()
            }
            if (ScreenshotQrFlowPolicy.afterDecode(results.size) !=
                ScreenshotScanDecision.SHOW_ACTION_CHOICE
            ) {
                return@launch
            }

            withContext(Dispatchers.Main.immediate) {
                actionOverlay?.show(
                    onReadQr = {
                        startQrReadFlow(media, results)
                    },
                    onKeepScreenshot = {
                        // The system has already saved this screenshot. There
                        // is no second capture and no duplicate save operation.
                    },
                )
            }
        }
    }

    private fun startQrReadFlow(
        media: ScreenshotMediaMetadata,
        results: List<BarcodeResult>,
    ) {
        when (ScreenshotQrFlowPolicy.routeForReadAction(results.size)) {
            QrReadRoute.NONE -> return
            QrReadRoute.SINGLE_RESULT,
            QrReadRoute.MULTI_SELECTION,
            -> {
                val intent = Intent(this, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP
                    putParcelableArrayListExtra(
                        MainActivity.EXTRA_BARCODE_RESULTS,
                        ArrayList(results),
                    )
                    putExtra(
                        MainActivity.EXTRA_BARCODE_BITMAP,
                        Uri.parse(media.uri),
                    )
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                try {
                    startActivity(intent)
                } catch (_: Exception) {
                    android.widget.Toast.makeText(
                        this,
                        "QR 読み取り画面を開けませんでした",
                        android.widget.Toast.LENGTH_SHORT,
                    ).show()
                }
            }
        }
    }
}
