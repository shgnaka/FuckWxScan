package cn.liyuyu.fuckwxscan.ui

import android.Manifest
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import cn.liyuyu.fuckwxscan.R
import cn.liyuyu.fuckwxscan.data.BarcodeResult
import cn.liyuyu.fuckwxscan.data.ResultType
import cn.liyuyu.fuckwxscan.service.ScreenshotAccessibilityService
import cn.liyuyu.fuckwxscan.screenshot.ScreenshotDiagnosticLog
import cn.liyuyu.fuckwxscan.ui.theme.FuckWxScanTheme
import cn.liyuyu.fuckwxscan.ui.theme.HintMask
import cn.liyuyu.fuckwxscan.utils.BarcodeUtil
import cn.liyuyu.fuckwxscan.utils.ScreenUtil
import cn.liyuyu.fuckwxscan.utils.parcelable
import cn.liyuyu.fuckwxscan.utils.parcelableArrayList
import cn.liyuyu.fuckwxscan.utils.showToast

class MainActivity : ComponentActivity() {
    companion object {
        const val EXTRA_BARCODE_RESULTS = "extra_barcode_results"
        const val EXTRA_BARCODE_BITMAP = "extra_barcode_bitmap"
    }

    private val imagePermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { grants ->
        val granted = grants.values.all { it }
        ScreenshotDiagnosticLog.info("image permission result granted=$granted")
        if (granted) {
            continueMonitorSetup()
        } else {
            showToast("写真と動画の読み取りを許可してください")
            finish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ScreenshotDiagnosticLog.info("MainActivity created")
        WindowCompat.setDecorFitsSystemWindows(window, false)
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                finish()
            }
        })
        val results = intent.parcelableArrayList<BarcodeResult>(EXTRA_BARCODE_RESULTS)
        if (results != null && results.isNotEmpty()) {
            if (results.size == 1) {
                handleText(results[0].text)
                finish()
            } else {
                showHints(results)
            }
            return
        }
        startMonitorSetup()
    }

    private fun startMonitorSetup() {
        val imagePermissionGranted = hasImageReadPermission()
        ScreenshotDiagnosticLog.info(
            "monitor setup imagePermissionGranted=$imagePermissionGranted",
        )
        if (!imagePermissionGranted) {
            ScreenshotDiagnosticLog.info("requesting image read permission")
            imagePermissionLauncher.launch(requiredImagePermissions())
            return
        }
        continueMonitorSetup()
    }

    private fun continueMonitorSetup() {
        val accessibilityEnabled = isAccessibilityServiceEnabled()
        ScreenshotDiagnosticLog.info(
            "monitor setup accessibilityEnabled=$accessibilityEnabled",
        )
        if (!accessibilityEnabled) {
            try {
                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                showToast("ユーザー補助設定でスクショ後 QR 読み取りを有効にしてください")
            } catch (_: Exception) {
                showToast("ユーザー補助設定を開けませんでした")
            }
        } else {
            showToast("スクショ後に QR コードを確認します")
        }
        finish()
    }

    private fun requiredImagePermissions(): Array<String> =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            arrayOf(Manifest.permission.READ_MEDIA_IMAGES)
        } else {
            arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        }

    private fun hasImageReadPermission(): Boolean =
        ContextCompat.checkSelfPermission(
            this,
            requiredImagePermissions().first(),
        ) == PackageManager.PERMISSION_GRANTED

    private fun isAccessibilityServiceEnabled(): Boolean {
        val expected = ComponentName(
            this,
            ScreenshotAccessibilityService::class.java,
        ).flattenToString()
        val enabled = Settings.Secure.getString(
            contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
        ).orEmpty()
        val serviceEnabled = enabled.split(':').any {
            it.equals(expected, ignoreCase = true)
        }
        ScreenshotDiagnosticLog.debug(
            "accessibility service state enabled=$serviceEnabled",
        )
        return serviceEnabled
    }

    private fun handleText(text: String) {
        ScreenshotDiagnosticLog.info(
            "handling QR result type=${BarcodeUtil.getResultType(text)} " +
                "hasBitmapUri=${intent.parcelable<Uri>(EXTRA_BARCODE_BITMAP) != null}",
        )
        showToast(text)
        val resultType = BarcodeUtil.getResultType(text)
        val bitmapUri = intent.parcelable<Uri>(EXTRA_BARCODE_BITMAP)
        if (resultType == ResultType.AlipayUrl) {
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "image/*"
                bitmapUri?.let {
                    putExtra(Intent.EXTRA_STREAM, it)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                setClassName(
                    "com.eg.android.AlipayGphone",
                    "com.alipay.mobile.quinox.splash.ShareScanQRDispenseActivity"
                )
            }
            if (intent.resolveActivity(packageManager) != null) {
                startActivity(intent)
            } else {
                showToast("没有找到[支付宝]哎！")
            }
        } else if (resultType == ResultType.WeChatUrl) {
            val intent = packageManager.getLaunchIntentForPackage("com.tencent.mm")?.apply {
                putExtra("LauncherUI.From.Scaner.Shortcut", true)
            }
            if (intent?.resolveActivity(packageManager) != null) {
                startActivity(intent)
            } else {
                showToast("没有找到[微信]哎！")
            }
        } else if (resultType == ResultType.CommonUrl) {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(text))
            if (intent.resolveActivity(packageManager) != null) {
                startActivity(intent)
            } else {
                showToast("没有找到可处理的应用哎！")
            }
        }
    }

    private fun showHints(results: List<BarcodeResult>?) {
        setContent {
            FuckWxScanTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = HintMask,
                ) {
                    Box {
                        val transition = rememberInfiniteTransition()
                        val currentSize by transition.animateValue(
                            28.dp, 40.dp, Dp.VectorConverter, infiniteRepeatable(
                                animation = tween(
                                    durationMillis = 400, easing = LinearEasing
                                ), repeatMode = RepeatMode.Reverse
                            )
                        )
                        Text(
                            text = "取消",
                            color = Color.White,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .offset(x = (-16).dp, y = 16.dp + with(LocalDensity.current) {
                                    ScreenUtil
                                        .getStatusBarHeight(
                                            this@MainActivity
                                        )
                                        .toDp()
                                })
                                .clickable {
                                    finish()
                                },
                        )
                        results?.let {
                            for (result in results) {
                                Box(
                                    modifier = Modifier
                                        .offset(
                                            with(LocalDensity.current) {
                                                result.centerX.toDp() - 18.dp
                                            },
                                            with(LocalDensity.current) {
                                                result.centerY.toDp() - 18.dp
                                            },
                                        )
                                        .size(36.dp)
                                        .clickable {
                                            handleText(result.text)
                                            finish()
                                        },
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Image(
                                        painter = painterResource(id = R.drawable.ic_wait_click),
                                        contentDescription = null,
                                        modifier = Modifier
                                            .clip(CircleShape)
                                            .background(Color.White)
                                            .size(currentSize),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
