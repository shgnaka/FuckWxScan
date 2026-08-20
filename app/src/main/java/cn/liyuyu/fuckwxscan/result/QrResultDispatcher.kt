/*
 * SPDX-License-Identifier: Apache-2.0
 * New file added by contributors to shgnaka/FuckWxScan in 2026.
 */
package cn.liyuyu.fuckwxscan.result

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import cn.liyuyu.fuckwxscan.R
import cn.liyuyu.fuckwxscan.data.BarcodeResult
import cn.liyuyu.fuckwxscan.data.ResultType
import cn.liyuyu.fuckwxscan.ui.MainActivity
import cn.liyuyu.fuckwxscan.utils.BarcodeUtil

class QrResultDispatcher(
    private val context: Context,
) {
    fun dispatch(results: List<BarcodeResult>, screenshotUri: Uri?) {
        when (results.size) {
            0 -> Toast.makeText(context, R.string.qr_not_found, Toast.LENGTH_SHORT).show()
            1 -> ResultHandler(context).handle(results.single().text, screenshotUri)
            else -> showSelection(results, screenshotUri)
        }
    }

    private fun showSelection(results: List<BarcodeResult>, screenshotUri: Uri?) {
        val intent = Intent(context, MainActivity::class.java).apply {
            putParcelableArrayListExtra(
                MainActivity.EXTRA_BARCODE_RESULTS,
                ArrayList(results),
            )
            putExtra(MainActivity.EXTRA_BARCODE_BITMAP, screenshotUri)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    companion object {
        fun needsScreenshotFile(results: List<BarcodeResult>): Boolean {
            return results.size > 1 || results.singleOrNull()?.let {
                BarcodeUtil.getResultType(it.text) == ResultType.AlipayUrl
            } == true
        }
    }
}
