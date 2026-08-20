/*
 * SPDX-License-Identifier: Apache-2.0
 * New file added by contributors to shgnaka/FuckWxScan in 2026.
 */
package cn.liyuyu.fuckwxscan.capture

import android.accessibilityservice.AccessibilityService
import android.graphics.Bitmap
import android.os.Build

class ScreenCaptureFacade(
    private val accessibilityService: AccessibilityService,
) {
    suspend fun capture(): Result<Bitmap> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            AccessibilityScreenCapture(accessibilityService).capture()
        } else {
            Result.failure(LegacyMediaProjectionRequiredException())
        }
    }
}
