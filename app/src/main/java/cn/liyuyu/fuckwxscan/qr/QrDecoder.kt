/*
 * SPDX-License-Identifier: Apache-2.0
 * New file added by contributors to shgnaka/FuckWxScan in 2026.
 */
package cn.liyuyu.fuckwxscan.qr

import android.graphics.Bitmap
import cn.liyuyu.fuckwxscan.data.BarcodeResult
import cn.liyuyu.fuckwxscan.utils.BarcodeUtil
import cn.liyuyu.fuckwxscan.utils.toBarcodeResult

object QrDecoder {
    fun decode(bitmap: Bitmap): List<BarcodeResult> {
        return BarcodeUtil.decodeQRCode(bitmap)
            .orEmpty()
            .map { it.toBarcodeResult() }
    }
}
