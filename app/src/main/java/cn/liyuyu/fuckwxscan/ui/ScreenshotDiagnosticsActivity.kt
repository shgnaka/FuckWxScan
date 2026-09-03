package cn.liyuyu.fuckwxscan.ui

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Button
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import cn.liyuyu.fuckwxscan.R
import cn.liyuyu.fuckwxscan.screenshot.ScreenshotDiagnosticLog
import cn.liyuyu.fuckwxscan.service.ScreenshotAccessibilityService
import cn.liyuyu.fuckwxscan.ui.theme.FuckWxScanTheme

class ScreenshotDiagnosticsActivity : ComponentActivity() {
    private var logText by mutableStateOf("診断ログを読み込んでいます。")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        refreshLogs()
        setContent {
            FuckWxScanTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    DiagnosticsContent(
                        logText = logText,
                        accessibilityEnabled = isAccessibilityServiceEnabled(),
                        imagePermissionGranted = hasImageReadPermission(),
                        onRefresh = { refreshLogs() },
                        onClear = {
                            ScreenshotDiagnosticLog.clear(this)
                            refreshLogs()
                        },
                        onCopy = { copyLogs() },
                        onOpenAccessibilitySettings = { openAccessibilitySettings() },
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        refreshLogs()
    }

    private fun refreshLogs() {
        logText = ScreenshotDiagnosticLog.read(this)
    }

    private fun copyLogs() {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("ScreenshotQr", logText))
        Toast.makeText(this, "診断ログをコピーしました", Toast.LENGTH_SHORT).show()
    }

    private fun openAccessibilitySettings() {
        startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
    }

    private fun hasImageReadPermission(): Boolean {
        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_IMAGES
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }
        return ContextCompat.checkSelfPermission(this, permission) ==
            PackageManager.PERMISSION_GRANTED
    }

    private fun isAccessibilityServiceEnabled(): Boolean {
        val expected = ComponentName(
            this,
            ScreenshotAccessibilityService::class.java,
        ).flattenToString()
        val enabled = Settings.Secure.getString(
            contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
        ).orEmpty()
        return enabled.split(':').any { it.equals(expected, ignoreCase = true) }
    }
}

@androidx.compose.runtime.Composable
private fun DiagnosticsContent(
    logText: String,
    accessibilityEnabled: Boolean,
    imagePermissionGranted: Boolean,
    onRefresh: () -> Unit,
    onClear: () -> Unit,
    onCopy: () -> Unit,
    onOpenAccessibilitySettings: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
    ) {
        Text(
            text = "スクショ QR 診断",
            style = MaterialTheme.typography.h6,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text("この画面は端末だけで診断ログを確認するためのものです。")
        Spacer(modifier = Modifier.height(12.dp))
        Text("ユーザー補助サービス: ${if (accessibilityEnabled) "有効" else "無効"}")
        Text("写真と動画の読み取り: ${if (imagePermissionGranted) "許可済み" else "未許可"}")
        Spacer(modifier = Modifier.height(12.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Button(onClick = onRefresh, modifier = Modifier.weight(1f)) {
                Text("更新")
            }
            Button(onClick = onCopy, modifier = Modifier.weight(1f)) {
                Text("コピー")
            }
            Button(onClick = onClear, modifier = Modifier.weight(1f)) {
                Text("消去")
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Button(
            onClick = onOpenAccessibilitySettings,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("ユーザー補助設定を開く")
        }
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = logText,
            fontSize = 11.sp,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(rememberScrollState()),
        )
    }
}
