/*
 * Modified from li-yu/FuckWxScan by contributors to shgnaka/FuckWxScan in 2026.
 * Changes: setup UI, AccessibilityService onboarding, auto-copy setting, and legacy capture flow.
 * SPDX-License-Identifier: Apache-2.0
 */
package cn.liyuyu.fuckwxscan.ui

import android.accessibilityservice.AccessibilityServiceInfo
import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Button
import androidx.compose.material.Divider
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.Switch
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import cn.liyuyu.fuckwxscan.App
import cn.liyuyu.fuckwxscan.R
import cn.liyuyu.fuckwxscan.service.CaptureService
import cn.liyuyu.fuckwxscan.service.QrAccessibilityService
import cn.liyuyu.fuckwxscan.settings.AppPreferences
import cn.liyuyu.fuckwxscan.ui.theme.FuckWxScanTheme
import cn.liyuyu.fuckwxscan.utils.showToast

class MainActivity : ComponentActivity() {
    companion object {
        const val ACTION_REQUEST_LEGACY_CAPTURE =
            "cn.liyuyu.fuckwxscan.action.REQUEST_LEGACY_CAPTURE"
    }

    private var accessibilityEnabled by mutableStateOf(false)
    private var autoCopyEnabled by mutableStateOf(false)
    private var legacyProjectionReady by mutableStateOf(false)
    private var captureAfterProjectionGrant = false

    private val projectionPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        val permissionData = result.data
        if (result.resultCode == Activity.RESULT_OK && permissionData != null) {
            App.screenCaptureIntentResult = permissionData
            legacyProjectionReady = true
            showToast(getString(R.string.legacy_capture_permission_granted))
            if (captureAfterProjectionGrant) {
                startLegacyCapture()
                finish()
            }
        } else {
            App.screenCaptureIntentResult = null
            legacyProjectionReady = false
            showToast(getString(R.string.legacy_capture_permission_denied))
            if (captureAfterProjectionGrant) {
                finish()
            }
        }
        captureAfterProjectionGrant = false
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, true)
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                finish()
            }
        })

        if (intent.action == ACTION_REQUEST_LEGACY_CAPTURE) {
            if (App.screenCaptureIntentResult != null) {
                startLegacyCapture()
                finish()
            } else {
                requestLegacyProjection(startCaptureAfterGrant = true)
            }
            return
        }

        autoCopyEnabled = AppPreferences.isAutoCopyEnabled(this)
        legacyProjectionReady = App.screenCaptureIntentResult != null
        showSetupScreen()
    }

    override fun onResume() {
        super.onResume()
        accessibilityEnabled = isAccessibilityServiceEnabled()
        legacyProjectionReady = App.screenCaptureIntentResult != null
    }

    private fun showSetupScreen() {
        setContent {
            FuckWxScanTheme {
                SetupScreen(
                    accessibilityEnabled = accessibilityEnabled,
                    autoCopyEnabled = autoCopyEnabled,
                    legacyProjectionReady = legacyProjectionReady,
                    onOpenAccessibilitySettings = {
                        startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                    },
                    onAutoCopyChanged = { enabled ->
                        autoCopyEnabled = enabled
                        AppPreferences.setAutoCopyEnabled(this, enabled)
                    },
                    onPrepareLegacyCapture = {
                        requestLegacyProjection(startCaptureAfterGrant = false)
                    },
                )
            }
        }
    }

    private fun requestLegacyProjection(startCaptureAfterGrant: Boolean) {
        captureAfterProjectionGrant = startCaptureAfterGrant
        val manager = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        projectionPermissionLauncher.launch(manager.createScreenCaptureIntent())
    }

    private fun startLegacyCapture() {
        ContextCompat.startForegroundService(this, Intent(this, CaptureService::class.java))
    }

    private fun isAccessibilityServiceEnabled(): Boolean {
        val manager = getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
        val component = ComponentName(this, QrAccessibilityService::class.java)
        return manager.getEnabledAccessibilityServiceList(
            AccessibilityServiceInfo.FEEDBACK_ALL_MASK,
        ).any { info ->
            val serviceInfo = info.resolveInfo.serviceInfo
            serviceInfo.packageName == component.packageName &&
                serviceInfo.name == component.className
        }
    }

}

@Composable
private fun SetupScreen(
    accessibilityEnabled: Boolean,
    autoCopyEnabled: Boolean,
    legacyProjectionReady: Boolean,
    onOpenAccessibilitySettings: () -> Unit,
    onAutoCopyChanged: (Boolean) -> Unit,
    onPrepareLegacyCapture: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colors.background,
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = stringResource(R.string.setup_title),
                style = MaterialTheme.typography.h5,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = stringResource(R.string.setup_description),
                style = MaterialTheme.typography.body1,
            )

            Text(
                text = stringResource(
                    if (accessibilityEnabled) {
                        R.string.accessibility_status_enabled
                    } else {
                        R.string.accessibility_status_disabled
                    },
                ),
                color = if (accessibilityEnabled) {
                    MaterialTheme.colors.primary
                } else {
                    MaterialTheme.colors.error
                },
                fontWeight = FontWeight.Bold,
            )
            Button(
                onClick = onOpenAccessibilitySettings,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.open_accessibility_settings))
            }

            Divider()

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.auto_copy_label),
                    modifier = Modifier.weight(1f),
                )
                Switch(
                    checked = autoCopyEnabled,
                    onCheckedChange = onAutoCopyChanged,
                )
            }
            Text(
                text = stringResource(R.string.auto_copy_description),
                style = MaterialTheme.typography.body2,
            )

            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
                Divider()
                Text(
                    text = stringResource(
                        if (legacyProjectionReady) {
                            R.string.legacy_capture_ready
                        } else {
                            R.string.legacy_capture_not_ready
                        },
                    ),
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = stringResource(R.string.legacy_capture_explanation),
                    style = MaterialTheme.typography.body2,
                )
                Button(
                    onClick = onPrepareLegacyCapture,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.prepare_legacy_capture))
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}
