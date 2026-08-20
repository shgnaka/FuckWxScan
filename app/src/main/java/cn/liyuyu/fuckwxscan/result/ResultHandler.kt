/*
 * SPDX-License-Identifier: Apache-2.0
 * New file added by contributors to shgnaka/FuckWxScan in 2026.
 * Result routing is adapted from li-yu/FuckWxScan's MainActivity.
 */
package cn.liyuyu.fuckwxscan.result

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import cn.liyuyu.fuckwxscan.R
import cn.liyuyu.fuckwxscan.data.ResultType
import cn.liyuyu.fuckwxscan.settings.AppPreferences
import cn.liyuyu.fuckwxscan.utils.BarcodeUtil

class ResultHandler(
    private val context: Context,
) {
    fun handle(text: String, screenshotUri: Uri? = null) {
        if (AppPreferences.isAutoCopyEnabled(context)) {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText(context.getString(R.string.clip_label), text))
        }

        Toast.makeText(context, text, Toast.LENGTH_LONG).show()

        when (BarcodeUtil.getResultType(text)) {
            ResultType.AlipayUrl -> openAlipay(screenshotUri, text)
            ResultType.WeChatUrl -> openWeChat(text)
            ResultType.CommonUrl -> openUrl(text)
            ResultType.PlainText -> Unit
        }
    }

    private fun openAlipay(screenshotUri: Uri?, fallbackText: String) {
        if (screenshotUri == null) {
            openUrl(fallbackText)
            return
        }

        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "image/*"
            putExtra(Intent.EXTRA_STREAM, screenshotUri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            setClassName(
                "com.eg.android.AlipayGphone",
                "com.alipay.mobile.quinox.splash.ShareScanQRDispenseActivity",
            )
        }
        startIfResolvable(intent, R.string.no_alipay_handler)
    }

    private fun openWeChat(fallbackText: String) {
        val intent = context.packageManager.getLaunchIntentForPackage("com.tencent.mm")?.apply {
            putExtra("LauncherUI.From.Scaner.Shortcut", true)
        }
        if (intent == null || intent.resolveActivity(context.packageManager) == null) {
            openUrl(fallbackText)
            return
        }
        start(intent)
    }

    private fun openUrl(text: String) {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(text))
        startIfResolvable(intent, R.string.no_url_handler)
    }

    private fun startIfResolvable(intent: Intent, missingHandlerMessage: Int) {
        if (intent.resolveActivity(context.packageManager) == null) {
            Toast.makeText(context, missingHandlerMessage, Toast.LENGTH_SHORT).show()
            return
        }
        start(intent)
    }

    private fun start(intent: Intent) {
        if (context !is Activity) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }
}
