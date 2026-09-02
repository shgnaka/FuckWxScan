package cn.liyuyu.fuckwxscan.service

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.net.Uri
import android.os.SystemClock
import cn.liyuyu.fuckwxscan.data.BarcodeResult
import cn.liyuyu.fuckwxscan.overlay.ScreenshotActionOverlayController
import cn.liyuyu.fuckwxscan.scan.QrReadRoute
import cn.liyuyu.fuckwxscan.scan.ScreenshotQrFlowPolicy
import cn.liyuyu.fuckwxscan.scan.ScreenshotScanDecision
import cn.liyuyu.fuckwxscan.screenshot.ScreenshotDiagnosticLog
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
        ScreenshotDiagnosticLog.info("Accessibility service connected")
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

    override fun onInterrupt() {
        ScreenshotDiagnosticLog.warn("Accessibility service interrupted")
    }

    override fun onDestroy() {
        ScreenshotDiagnosticLog.info("Accessibility service destroyed")
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
        val startedAtMs = SystemClock.elapsedRealtime()
        ScreenshotDiagnosticLog.info(
            "QR decode started mediaId=${media.id} uri=${media.uri}",
        )
        serviceScope.launch(Dispatchers.Default) {
            val decoded = try {
                BarcodeUtil.decodeQRCode(bitmap)
            } catch (e: Exception) {
                ScreenshotDiagnosticLog.error(
                    "QR decode threw an exception mediaId=${media.id}",
                    e,
                )
                null
            }
            val results = decoded?.map { it.toBarcodeResult() }.orEmpty()
            val elapsedMs = SystemClock.elapsedRealtime() - startedAtMs
            ScreenshotDiagnosticLog.info(
                "QR decode finished mediaId=${media.id} qrCount=${results.size} " +
                    "elapsedMs=$elapsedMs",
            )
            if (!bitmap.isRecycled) {
                bitmap.recycle()
            }
            if (ScreenshotQrFlowPolicy.afterDecode(results.size) !=
                ScreenshotScanDecision.SHOW_ACTION_CHOICE
            ) {
                ScreenshotDiagnosticLog.info(
                    "no QR detected; choice overlay not shown mediaId=${media.id}",
                )
                return@launch
            }

            withContext(Dispatchers.Main.immediate) {
                val shown = actionOverlay?.show(
                    onReadQr = {
                        ScreenshotDiagnosticLog.info(
                            "choice selected=READ_QR mediaId=${media.id} " +
                                "qrCount=${results.size}",
                        )
                        startQrReadFlow(media, results)
                    },
                    onKeepScreenshot = {
                        ScreenshotDiagnosticLog.info(
                            "choice selected=KEEP_SCREENSHOT mediaId=${media.id}",
                        )
                        // The system has already saved this screenshot. There
                        // is no second capture and no duplicate save operation.
                    },
                ) == true
                ScreenshotDiagnosticLog.info(
                    "choice overlay result=$shown mediaId=${media.id}",
                )
            }
        }
    }

    private fun startQrReadFlow(
        media: ScreenshotMediaMetadata,
        results: List<BarcodeResult>,
    ) {
        val route = ScreenshotQrFlowPolicy.routeForReadAction(results.size)
        ScreenshotDiagnosticLog.info(
            "routing READ_QR mediaId=${media.id} qrCount=${results.size} route=$route",
        )
        when (route) {
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
                    ScreenshotDiagnosticLog.debug("starting MainActivity for QR result")
                    startActivity(intent)
                } catch (e: Exception) {
                    ScreenshotDiagnosticLog.error(
                        "QR result activity could not be started mediaId=${media.id}",
                        e,
                    )
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
