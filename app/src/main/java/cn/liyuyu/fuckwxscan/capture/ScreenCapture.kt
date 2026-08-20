/*
 * SPDX-License-Identifier: Apache-2.0
 * New file added by contributors to shgnaka/FuckWxScan in 2026.
 */
package cn.liyuyu.fuckwxscan.capture

import android.graphics.Bitmap

interface ScreenCapture {
    suspend fun capture(): Result<Bitmap>
}

class LegacyMediaProjectionRequiredException : UnsupportedOperationException(
    "MediaProjection is required below Android 11",
)
