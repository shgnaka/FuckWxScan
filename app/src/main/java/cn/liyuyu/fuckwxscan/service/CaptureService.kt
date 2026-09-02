/*
 * Modified from li-yu/FuckWxScan by contributors to shgnaka/FuckWxScan in 2026.
 * Changes: legacy-only capture path and shared QR decode/result dispatching.
 * SPDX-License-Identifier: Apache-2.0
 */
package cn.liyuyu.fuckwxscan.service

import android.annotation.SuppressLint
import android.app.*
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.IBinder
import android.util.Log
import android.widget.Toast
import androidx.core.app.NotificationCompat
import cn.liyuyu.fuckwxscan.App
import cn.liyuyu.fuckwxscan.R
import cn.liyuyu.fuckwxscan.capture.MediaStoreScreenshotSaver
import cn.liyuyu.fuckwxscan.capture.TemporaryScreenshotStore
import cn.liyuyu.fuckwxscan.qr.QrDecoder
import cn.liyuyu.fuckwxscan.scan.DecodeDecision
import cn.liyuyu.fuckwxscan.scan.ScanFlowPolicy
import cn.liyuyu.fuckwxscan.ui.MainActivity
import cn.liyuyu.fuckwxscan.ui.ScreenshotActionActivity
import cn.liyuyu.fuckwxscan.utils.BarcodeUtil
import cn.liyuyu.fuckwxscan.utils.ScreenUtil
import kotlinx.coroutines.*


/**
 * Created by frank on 2022/10/17.
 */
@SuppressLint("WrongConstant")
class CaptureService : Service(), CoroutineScope by MainScope() {

    private val mediaProjectionManager: MediaProjectionManager by lazy {
        getSystemService(
            MEDIA_PROJECTION_SERVICE
        ) as MediaProjectionManager
    }
    private val imageReaderDelegate = lazy {
        val (width, height) = ScreenUtil.getScreenSize(this)
        ImageReader.newInstance(
            width,
            height,
            PixelFormat.RGBA_8888,
            1
        )
    }
    private val imageReader by imageReaderDelegate
    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var captureInProgress = false

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    private val foregroundService: ForegroundNotification by lazy {
        ForegroundNotification(this)
    }

    private fun startCapture() {
        if (captureInProgress) {
            return
        }

        val permissionData = App.screenCaptureIntentResult
        if (permissionData == null) {
            requestProjectionPermission()
            return
        }
        if (mediaProjection == null) {
            mediaProjection = runCatching {
                mediaProjectionManager.getMediaProjection(
                    Activity.RESULT_OK,
                    permissionData,
                )
            }.onFailure { error ->
                Log.e(TAG, "Unable to restore MediaProjection", error)
            }.getOrNull()
        }
        val projection = mediaProjection
        if (projection == null) {
            requestProjectionPermission()
            return
        }
        val (screenWidth, screenHeight) = ScreenUtil.getScreenSize(this)
        virtualDisplay = runCatching {
            projection.createVirtualDisplay(
                "ScreenCapture",
                screenWidth, screenHeight, ScreenUtil.getScreenDensityDpi(),
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                imageReader.surface, null, null
            )
        }.onFailure { error ->
            Log.e(TAG, "Unable to create MediaProjection display", error)
        }.getOrNull()
        if (virtualDisplay == null) {
            requestProjectionPermission()
            return
        }

        captureInProgress = true
        launch(Dispatchers.IO) {
            try {
                delay(200)
                val image = withTimeoutOrNull(1000) {
                    var latestImage = imageReader.acquireLatestImage()
                    while (latestImage == null) {
                        delay(16)
                        latestImage = imageReader.acquireLatestImage()
                    }
                    latestImage
                }
                if (image == null) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(
                            this@CaptureService,
                            R.string.screenshot_failed,
                            Toast.LENGTH_SHORT,
                        ).show()
                    }
                    return@launch
                }
                val bitmap = BarcodeUtil.imageToBitmap(image)
                try {
                    val results = withTimeoutOrNull(2000) { QrDecoder.decode(bitmap) }.orEmpty()
                    when (val decision = ScanFlowPolicy.afterDecode(results.size)) {
                        DecodeDecision.SaveScreenshot -> saveScreenshot(bitmap)
                        is DecodeDecision.ShowActionChoice -> {
                            val temporaryScreenshotResult = TemporaryScreenshotStore(
                                this@CaptureService,
                            ).create(bitmap)
                            if (temporaryScreenshotResult.isFailure) {
                                val error = temporaryScreenshotResult.exceptionOrNull()
                                    ?: IllegalStateException("Unable to store temporary screenshot")
                                Log.e(TAG, "Unable to store temporary screenshot", error)
                                saveScreenshot(bitmap)
                                return@launch
                            }
                            val screenshotUri = temporaryScreenshotResult.getOrThrow()
                            withContext(Dispatchers.Main) {
                                startActivity(
                                    ScreenshotActionActivity.createIntent(
                                        context = this@CaptureService,
                                        results = results,
                                        screenshotUri = screenshotUri,
                                    ),
                                )
                            }
                            Log.i(TAG, "QR action choice shown for ${decision.qrCount} result(s)")
                        }
                    }
                } finally {
                    bitmap.recycle()
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                Log.e(TAG, "Legacy screen capture failed", error)
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        this@CaptureService,
                        R.string.screenshot_failed,
                        Toast.LENGTH_SHORT,
                    ).show()
                }
            } finally {
                virtualDisplay?.release()
                virtualDisplay = null
                captureInProgress = false
                this@CaptureService.stopSelf()
            }
        }
    }

    private suspend fun saveScreenshot(bitmap: android.graphics.Bitmap) {
        val result = MediaStoreScreenshotSaver(this).save(bitmap)
        withContext(Dispatchers.Main) {
            Toast.makeText(
                this@CaptureService,
                if (result.isSuccess) {
                    R.string.screenshot_saved
                } else {
                    R.string.screenshot_save_failed
                },
                Toast.LENGTH_SHORT,
            ).show()
        }
    }

    private fun requestProjectionPermission() {
        App.screenCaptureIntentResult = null
        Toast.makeText(this, R.string.legacy_capture_permission_needed, Toast.LENGTH_LONG).show()
        runCatching {
            startActivity(
                Intent(this, MainActivity::class.java).apply {
                    action = MainActivity.ACTION_REQUEST_LEGACY_CAPTURE
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                },
            )
        }.onFailure { error ->
            Log.e(TAG, "Unable to request MediaProjection permission", error)
        }
        stopSelf()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (null == intent) {
            return START_NOT_STICKY
        }
        foregroundService.startForegroundNotification()
        startCapture()
        return super.onStartCommand(intent, flags, startId)
    }

    override fun onDestroy() {
        cancel()
        virtualDisplay?.release()
        virtualDisplay = null
        runCatching { mediaProjection?.stop() }
        mediaProjection = null
        if (imageReaderDelegate.isInitialized()) {
            imageReader.close()
        }
        foregroundService.stopForegroundNotification()
        super.onDestroy()
    }

    companion object {
        private const val TAG = "CaptureService"
    }
}

class ForegroundNotification(private val service: CaptureService) :
    ContextWrapper(service) {

    companion object {
        private const val START_ID = 2141
        private const val CHANNEL_ID = "fuck_wx_foreground_service"
        private const val CHANNEL_NAME = "扫你码服务"
    }

    private var mNotificationManager: NotificationManager? = null

    private var mCompatBuilder: NotificationCompat.Builder? = null

    private val compatBuilder: NotificationCompat.Builder?
        get() {
            if (mCompatBuilder == null) {
                val notificationIntent = Intent(this, MainActivity::class.java)
                notificationIntent.action = Intent.ACTION_MAIN
                notificationIntent.addCategory(Intent.CATEGORY_LAUNCHER)
                notificationIntent.flags =
                    Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED
                val pendingIntent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    PendingIntent.getActivity(
                        this,
                        2140,
                        notificationIntent,
                        PendingIntent.FLAG_MUTABLE
                    )
                } else {
                    PendingIntent.getActivity(
                        this,
                        2140,
                        notificationIntent,
                        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                    )
                }

                val notificationBuilder: NotificationCompat.Builder =
                    NotificationCompat.Builder(this, CHANNEL_ID)
                notificationBuilder.setContentTitle(getString(R.string.notification_title))
                notificationBuilder.setContentText(getString(R.string.notification_sub_title))
                notificationBuilder.setSmallIcon(R.mipmap.ic_launcher)
                notificationBuilder.setContentIntent(pendingIntent)
                mCompatBuilder = notificationBuilder
            }
            return mCompatBuilder
        }

    init {
        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        mNotificationManager =
            getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_LOW
            )
            channel.lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            channel.setShowBadge(false)
            mNotificationManager?.createNotificationChannel(channel)
        }
    }

    fun startForegroundNotification() {
        service.startForeground(START_ID, compatBuilder?.build())
    }

    fun stopForegroundNotification() {
        mNotificationManager?.cancelAll()
        service.stopForeground(Service.STOP_FOREGROUND_REMOVE)
    }
}
