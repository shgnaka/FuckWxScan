package cn.liyuyu.fuckwxscan.scan

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScreenshotQrFlowPolicyTest {
    @Test
    fun zeroQrDoesNotShowActionUi() {
        assertEquals(
            ScreenshotScanDecision.NO_UI,
            ScreenshotQrFlowPolicy.afterDecode(qrCount = 0),
        )
        assertTrue(
            ScreenshotQrFlowPolicy.actionsFor(qrCount = 0).isEmpty(),
        )
    }

    @Test
    fun oneQrShowsReadOrKeepChoicesAndUsesSingleResultFlow() {
        assertEquals(
            ScreenshotScanDecision.SHOW_ACTION_CHOICE,
            ScreenshotQrFlowPolicy.afterDecode(qrCount = 1),
        )
        assertEquals(
            listOf(
                ScreenshotAction.READ_QR,
                ScreenshotAction.KEEP_SCREENSHOT,
            ),
            ScreenshotQrFlowPolicy.actionsFor(qrCount = 1),
        )
        assertEquals(
            QrReadRoute.SINGLE_RESULT,
            ScreenshotQrFlowPolicy.routeForReadAction(qrCount = 1),
        )
    }

    @Test
    fun multipleQrShowsReadOrKeepChoicesAndUsesSelectionFlow() {
        assertEquals(
            ScreenshotScanDecision.SHOW_ACTION_CHOICE,
            ScreenshotQrFlowPolicy.afterDecode(qrCount = 2),
        )
        assertEquals(
            listOf(
                ScreenshotAction.READ_QR,
                ScreenshotAction.KEEP_SCREENSHOT,
            ),
            ScreenshotQrFlowPolicy.actionsFor(qrCount = 2),
        )
        assertEquals(
            QrReadRoute.MULTI_SELECTION,
            ScreenshotQrFlowPolicy.routeForReadAction(qrCount = 2),
        )
    }

    @Test
    fun readActionKeepsOriginalScreenshot() {
        assertFalse(
            ScreenshotQrFlowPolicy.shouldDeleteOriginalScreenshot(
                action = ScreenshotAction.READ_QR,
            ),
        )
    }

    @Test
    fun keepActionDoesNotCreateAnotherScreenshot() {
        assertEquals(
            ScreenshotActionEffect.KEEP_EXISTING_SCREENSHOT,
            ScreenshotQrFlowPolicy.effectOf(
                action = ScreenshotAction.KEEP_SCREENSHOT,
            ),
        )
        assertFalse(
            ScreenshotQrFlowPolicy.shouldDeleteOriginalScreenshot(
                action = ScreenshotAction.KEEP_SCREENSHOT,
            ),
        )
    }

    @Test
    fun negativeQrCountIsHandledLikeNoQr() {
        assertEquals(
            ScreenshotScanDecision.NO_UI,
            ScreenshotQrFlowPolicy.afterDecode(qrCount = -1),
        )
        assertEquals(
            QrReadRoute.NONE,
            ScreenshotQrFlowPolicy.routeForReadAction(qrCount = -1),
        )
    }
}
