/*
 * SPDX-License-Identifier: Apache-2.0
 * New file added by contributors to shgnaka/FuckWxScan in 2026.
 */
package cn.liyuyu.fuckwxscan.ui

import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.core.view.WindowCompat
import androidx.lifecycle.lifecycleScope
import cn.liyuyu.fuckwxscan.R
import cn.liyuyu.fuckwxscan.capture.MediaStoreScreenshotSaver
import cn.liyuyu.fuckwxscan.capture.TemporaryScreenshotStore
import cn.liyuyu.fuckwxscan.data.BarcodeResult
import cn.liyuyu.fuckwxscan.data.ResultType
import cn.liyuyu.fuckwxscan.result.ResultHandler
import cn.liyuyu.fuckwxscan.scan.ActionDecision
import cn.liyuyu.fuckwxscan.scan.ScanAction
import cn.liyuyu.fuckwxscan.scan.ScanFlowPolicy
import cn.liyuyu.fuckwxscan.utils.BarcodeUtil
import cn.liyuyu.fuckwxscan.utils.parcelable
import cn.liyuyu.fuckwxscan.utils.parcelableArrayList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ScreenshotActionActivity : ComponentActivity() {
    private lateinit var results: ArrayList<BarcodeResult>
    private lateinit var screenshotUri: Uri
    private lateinit var temporaryScreenshotStore: TemporaryScreenshotStore
    private var actionInProgress = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        isShowing = true
        temporaryScreenshotStore = TemporaryScreenshotStore(this)

        results = intent.parcelableArrayList<BarcodeResult>(EXTRA_BARCODE_RESULTS)
            ?: return closeWithoutAction()
        screenshotUri = intent.parcelable<Uri>(EXTRA_SCREENSHOT_URI)
            ?: return closeWithoutAction()
        if (results.isEmpty()) {
            return closeWithoutAction()
        }

        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.TRANSPARENT
        WindowCompat.setDecorFitsSystemWindows(window, false)
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (!actionInProgress) {
                    cancelAction()
                }
            }
        })

        setContentView(createContentView())
    }

    override fun onDestroy() {
        isShowing = false
        super.onDestroy()
    }

    private fun createContentView(): View {
        val root = FrameLayout(this).apply {
            setBackgroundColor(Color.TRANSPARENT)
        }
        root.addView(
            View(this).apply {
                setBackgroundColor(MASK_COLOR)
                isClickable = true
                setOnClickListener { cancelAction() }
            },
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            ),
        )

        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            isClickable = true
            setPadding(dp(20), dp(16), dp(20), dp(16))
            background = GradientDrawable().apply {
                setColor(PANEL_COLOR)
                cornerRadius = dp(20).toFloat()
            }
            elevation = dp(8).toFloat()
        }
        panel.addView(
            TextView(this).apply {
                text = getString(R.string.screenshot_action_choice_title, results.size)
                setTextColor(Color.WHITE)
                textSize = 16f
                typeface = Typeface.DEFAULT_BOLD
                setPadding(0, 0, 0, dp(12))
            },
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ),
        )
        panel.addView(
            TextView(this).apply {
                text = getString(R.string.screenshot_action_choice_description)
                setTextColor(0xFFDDDDDD.toInt())
                textSize = 14f
            },
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ),
        )

        for (action in ScanFlowPolicy.actionChoices) {
            val button = createActionButton(
                when (action) {
                    ScanAction.READ_QR -> R.string.screenshot_action_read_qr
                    ScanAction.SAVE_SCREENSHOT -> R.string.screenshot_action_save
                },
            )
            button.setOnClickListener {
                when (action) {
                    ScanAction.READ_QR -> readQr()
                    ScanAction.SAVE_SCREENSHOT -> saveScreenshot()
                }
            }
            panel.addView(button, actionLayoutParams())
        }

        root.addView(
            panel,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM,
            ).apply {
                leftMargin = dp(12)
                rightMargin = dp(12)
                bottomMargin = dp(12)
            },
        )
        return root
    }

    private fun readQr() {
        if (actionInProgress) {
            return
        }
        actionInProgress = true
        try {
            when (ScanFlowPolicy.afterAction(results.size, ScanAction.READ_QR)) {
                ActionDecision.DispatchSingleQr ->
                    dispatchResult(results.single())

                ActionDecision.ShowQrSelection -> {
                    startActivity(
                        QrSelectionActivity.createIntent(
                            context = this,
                            results = results,
                            screenshotUri = screenshotUri,
                            deleteScreenshotOnFinish = true,
                        ),
                    )
                }

                ActionDecision.SaveScreenshot ->
                    error("Reading QR cannot result in screenshot saving")
            }
            finishAndRemoveTask()
        } catch (error: Throwable) {
            actionInProgress = false
            android.util.Log.e(TAG, "Unable to dispatch QR result", error)
            showToast(R.string.screenshot_action_read_failed)
        }
    }

    private fun saveScreenshot() {
        if (actionInProgress) {
            return
        }
        actionInProgress = true
        check(
            ScanFlowPolicy.afterAction(
                qrCount = results.size,
                action = ScanAction.SAVE_SCREENSHOT,
            ) == ActionDecision.SaveScreenshot,
        )
        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                MediaStoreScreenshotSaver(this@ScreenshotActionActivity)
                    .save(screenshotUri)
            }
            if (result.isSuccess) {
                temporaryScreenshotStore.delete()
                showToast(R.string.screenshot_saved)
                finishAndRemoveTask()
            } else {
                actionInProgress = false
                showToast(R.string.screenshot_save_failed)
            }
        }
    }

    private fun cancelAction() {
        if (actionInProgress) {
            return
        }
        temporaryScreenshotStore.delete()
        finishAndRemoveTask()
    }

    private fun dispatchResult(result: BarcodeResult) {
        ResultHandler(this).handle(result.text, screenshotUri)
        if (BarcodeUtil.getResultType(result.text) != ResultType.AlipayUrl) {
            temporaryScreenshotStore.delete()
        }
    }

    private fun closeWithoutAction() {
        if (::temporaryScreenshotStore.isInitialized) {
            temporaryScreenshotStore.delete()
        }
        isShowing = false
        finishAndRemoveTask()
    }

    private fun createActionButton(textResId: Int): Button {
        return Button(this).apply {
            text = getString(textResId)
            setTextColor(Color.WHITE)
            textSize = 16f
            isAllCaps = false
            minHeight = dp(52)
            background = GradientDrawable().apply {
                setColor(BUTTON_COLOR)
                cornerRadius = dp(12).toFloat()
            }
        }
    }

    private fun actionLayoutParams(): LinearLayout.LayoutParams =
        LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        ).apply {
            topMargin = dp(8)
        }

    private fun showToast(messageResId: Int) {
        android.widget.Toast.makeText(
            this,
            messageResId,
            android.widget.Toast.LENGTH_SHORT,
        ).show()
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    companion object {
        const val EXTRA_BARCODE_RESULTS = "extra_action_barcode_results"
        const val EXTRA_SCREENSHOT_URI = "extra_action_screenshot_uri"

        @Volatile
        var isShowing: Boolean = false

        private const val TAG = "ScreenshotAction"
        private const val MASK_COLOR = 0x66000000
        private const val PANEL_COLOR = 0xF02B2B2B.toInt()
        private const val BUTTON_COLOR = 0xFF4A4A4A.toInt()

        fun createIntent(
            context: android.content.Context,
            results: List<BarcodeResult>,
            screenshotUri: Uri,
        ): android.content.Intent {
            return android.content.Intent(context, ScreenshotActionActivity::class.java).apply {
                putParcelableArrayListExtra(
                    EXTRA_BARCODE_RESULTS,
                    ArrayList(results),
                )
                putExtra(EXTRA_SCREENSHOT_URI, screenshotUri)
                addFlags(
                    android.content.Intent.FLAG_ACTIVITY_NEW_TASK or
                        android.content.Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS or
                        android.content.Intent.FLAG_ACTIVITY_NO_ANIMATION or
                        android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
            }
        }
    }
}
