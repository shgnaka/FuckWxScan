/*
 * SPDX-License-Identifier: Apache-2.0
 * New file added by contributors to shgnaka/FuckWxScan in 2026.
 */
package cn.liyuyu.fuckwxscan.capture

import android.accessibilityservice.AccessibilityService
import android.graphics.Bitmap
import android.os.Build
import android.view.Display
import androidx.annotation.RequiresApi
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

@RequiresApi(Build.VERSION_CODES.R)
class AccessibilityScreenCapture(
    private val service: AccessibilityService,
) : ScreenCapture {
    override suspend fun capture(): Result<Bitmap> = suspendCoroutine { continuation ->
        service.takeScreenshot(
            Display.DEFAULT_DISPLAY,
            service.mainExecutor,
            object : AccessibilityService.TakeScreenshotCallback {
                override fun onSuccess(screenshot: AccessibilityService.ScreenshotResult) {
                    val buffer = screenshot.hardwareBuffer
                    val result = try {
                        val hardwareBitmap = Bitmap.wrapHardwareBuffer(
                            buffer,
                            screenshot.colorSpace,
                        ) ?: throw IllegalStateException("Unable to wrap screenshot buffer")

                        val softwareBitmap = hardwareBitmap.copy(Bitmap.Config.ARGB_8888, false)
                            ?: throw IllegalStateException("Unable to copy screenshot bitmap")
                        hardwareBitmap.recycle()
                        Result.success(softwareBitmap)
                    } catch (error: Throwable) {
                        Result.failure(error)
                    } finally {
                        buffer.close()
                    }
                    continuation.resume(result)
                }

                override fun onFailure(errorCode: Int) {
                    continuation.resume(Result.failure(ScreenshotCaptureException(errorCode)))
                }
            },
        )
    }
}

class ScreenshotCaptureException(
    val errorCode: Int,
) : RuntimeException("Accessibility screenshot failed with error code $errorCode")
