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
import cn.liyuyu.fuckwxscan.ui.QrSelectionActivity
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
        val intent = Intent(context, QrSelectionActivity::class.java).apply {
            putParcelableArrayListExtra(
                QrSelectionActivity.EXTRA_BARCODE_RESULTS,
                ArrayList(results),
            )
            putExtra(QrSelectionActivity.EXTRA_BARCODE_BITMAP, screenshotUri)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_CLEAR_TASK or
                Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS
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
