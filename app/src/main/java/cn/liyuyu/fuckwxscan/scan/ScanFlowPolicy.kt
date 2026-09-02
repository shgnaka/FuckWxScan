/*
 * SPDX-License-Identifier: Apache-2.0
 * New file added by contributors to shgnaka/FuckWxScan in 2026.
 */
package cn.liyuyu.fuckwxscan.scan

enum class ScanAction {
    READ_QR,
    SAVE_SCREENSHOT,
}

sealed class DecodeDecision {
    object SaveScreenshot : DecodeDecision()

    data class ShowActionChoice(
        val qrCount: Int,
    ) : DecodeDecision()
}

sealed class ActionDecision {
    object SaveScreenshot : ActionDecision()
    object DispatchSingleQr : ActionDecision()
    object ShowQrSelection : ActionDecision()
}

object ScanFlowPolicy {
    fun afterDecode(qrCount: Int): DecodeDecision {
        require(qrCount >= 0) { "QR count must not be negative" }
        return if (qrCount == 0) {
            DecodeDecision.SaveScreenshot
        } else {
            DecodeDecision.ShowActionChoice(qrCount)
        }
    }

    fun afterAction(qrCount: Int, action: ScanAction): ActionDecision {
        require(qrCount > 0) { "An action choice requires at least one QR code" }
        return when (action) {
            ScanAction.READ_QR -> if (qrCount == 1) {
                ActionDecision.DispatchSingleQr
            } else {
                ActionDecision.ShowQrSelection
            }

            ScanAction.SAVE_SCREENSHOT -> ActionDecision.SaveScreenshot
        }
    }
}
