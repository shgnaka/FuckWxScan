package cn.liyuyu.fuckwxscan.scan

enum class ScreenshotScanDecision {
    NO_UI,
    SHOW_ACTION_CHOICE,
}

enum class ScreenshotAction {
    READ_QR,
    KEEP_SCREENSHOT,
}

enum class QrReadRoute {
    NONE,
    SINGLE_RESULT,
    MULTI_SELECTION,
}

enum class ScreenshotActionEffect {
    READ_QR,
    KEEP_EXISTING_SCREENSHOT,
}

/**
 * Describes what happens after the already-saved system screenshot is decoded.
 */
object ScreenshotQrFlowPolicy {
    fun afterDecode(qrCount: Int): ScreenshotScanDecision =
        if (qrCount > 0) {
            ScreenshotScanDecision.SHOW_ACTION_CHOICE
        } else {
            ScreenshotScanDecision.NO_UI
        }

    fun actionsFor(qrCount: Int): List<ScreenshotAction> =
        if (qrCount > 0) {
            listOf(
                ScreenshotAction.READ_QR,
                ScreenshotAction.KEEP_SCREENSHOT,
            )
        } else {
            emptyList()
        }

    fun routeForReadAction(qrCount: Int): QrReadRoute =
        when {
            qrCount == 1 -> QrReadRoute.SINGLE_RESULT
            qrCount > 1 -> QrReadRoute.MULTI_SELECTION
            else -> QrReadRoute.NONE
        }

    fun effectOf(action: ScreenshotAction): ScreenshotActionEffect =
        when (action) {
            ScreenshotAction.READ_QR -> ScreenshotActionEffect.READ_QR
            ScreenshotAction.KEEP_SCREENSHOT -> {
                ScreenshotActionEffect.KEEP_EXISTING_SCREENSHOT
            }
        }

    fun shouldDeleteOriginalScreenshot(action: ScreenshotAction): Boolean =
        when (action) {
            ScreenshotAction.READ_QR -> false
            ScreenshotAction.KEEP_SCREENSHOT -> false
        }
}
