/*
 * SPDX-License-Identifier: Apache-2.0
 * Selection UI extracted from li-yu/FuckWxScan's MainActivity by contributors
 * to shgnaka/FuckWxScan in 2026.
 */
package cn.liyuyu.fuckwxscan.ui

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.animateValue
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import cn.liyuyu.fuckwxscan.R
import cn.liyuyu.fuckwxscan.data.BarcodeResult
import cn.liyuyu.fuckwxscan.result.ResultHandler
import cn.liyuyu.fuckwxscan.ui.theme.FuckWxScanTheme
import cn.liyuyu.fuckwxscan.ui.theme.HintMask
import cn.liyuyu.fuckwxscan.utils.ScreenUtil
import cn.liyuyu.fuckwxscan.utils.parcelable
import cn.liyuyu.fuckwxscan.utils.parcelableArrayList

class QrSelectionActivity : ComponentActivity() {
    companion object {
        const val EXTRA_BARCODE_RESULTS = "extra_barcode_results"
        const val EXTRA_BARCODE_BITMAP = "extra_barcode_bitmap"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                closeSelection()
            }
        })

        val results = intent.parcelableArrayList<BarcodeResult>(EXTRA_BARCODE_RESULTS)
        if (results == null || results.size < 2) {
            closeSelection()
            return
        }

        val screenshotUri = intent.parcelable<Uri>(EXTRA_BARCODE_BITMAP)
        setContent {
            QrSelectionScreen(
                results = results,
                onCancel = ::closeSelection,
                onSelected = { result ->
                    ResultHandler(this).handle(result.text, screenshotUri)
                    closeSelection()
                },
            )
        }
    }

    private fun closeSelection() {
        finishAndRemoveTask()
    }
}

@Composable
private fun QrSelectionScreen(
    results: List<BarcodeResult>,
    onCancel: () -> Unit,
    onSelected: (BarcodeResult) -> Unit,
) {
    FuckWxScanTheme {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = HintMask,
        ) {
            Box {
                val context = LocalContext.current
                val transition = rememberInfiniteTransition()
                val currentSize by transition.animateValue(
                    28.dp,
                    40.dp,
                    Dp.VectorConverter,
                    infiniteRepeatable(
                        animation = tween(
                            durationMillis = 400,
                            easing = LinearEasing,
                        ),
                        repeatMode = RepeatMode.Reverse,
                    ),
                )
                Text(
                    text = stringResource(R.string.cancel),
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .offset(
                            x = (-16).dp,
                            y = 16.dp + with(LocalDensity.current) {
                                ScreenUtil.getStatusBarHeight(context).toDp()
                            },
                        )
                        .clickable(onClick = onCancel),
                )
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
                            .clickable { onSelected(result) },
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
