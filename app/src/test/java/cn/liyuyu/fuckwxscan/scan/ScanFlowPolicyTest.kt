/*
 * SPDX-License-Identifier: Apache-2.0
 * New tests added by contributors to shgnaka/FuckWxScan in 2026.
 */
package cn.liyuyu.fuckwxscan.scan

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class ScanFlowPolicyTest {
    @Test
    fun detectedQrCodesOfferReadAndSaveOnly() {
        assertEquals(
            listOf(ScanAction.READ_QR, ScanAction.SAVE_SCREENSHOT),
            ScanFlowPolicy.actionChoices,
        )
    }

    @Test
    fun noQrCodesSaveTheCapturedScreenshotWithoutShowingChoice() {
        assertEquals(
            DecodeDecision.SaveScreenshot,
            ScanFlowPolicy.afterDecode(qrCount = 0),
        )
    }

    @Test
    fun oneQrCodeShowsTheTwoActionChoice() {
        assertEquals(
            DecodeDecision.ShowActionChoice(qrCount = 1),
            ScanFlowPolicy.afterDecode(qrCount = 1),
        )
    }

    @Test
    fun multipleQrCodesShowTheTwoActionChoiceWithTheDetectedCount() {
        assertEquals(
            DecodeDecision.ShowActionChoice(qrCount = 3),
            ScanFlowPolicy.afterDecode(qrCount = 3),
        )
    }

    @Test
    fun readingOneQrCodeDispatchesItDirectly() {
        assertEquals(
            ActionDecision.DispatchSingleQr,
            ScanFlowPolicy.afterAction(
                qrCount = 1,
                action = ScanAction.READ_QR,
            ),
        )
    }

    @Test
    fun readingMultipleQrCodesOpensTheExistingSelectionFlow() {
        assertEquals(
            ActionDecision.ShowQrSelection,
            ScanFlowPolicy.afterAction(
                qrCount = 2,
                action = ScanAction.READ_QR,
            ),
        )
    }

    @Test
    fun savingAfterOneQrCodeSavesTheOriginalScreenshot() {
        assertEquals(
            ActionDecision.SaveScreenshot,
            ScanFlowPolicy.afterAction(
                qrCount = 1,
                action = ScanAction.SAVE_SCREENSHOT,
            ),
        )
    }

    @Test
    fun savingAfterMultipleQrCodesSavesTheOriginalScreenshot() {
        assertEquals(
            ActionDecision.SaveScreenshot,
            ScanFlowPolicy.afterAction(
                qrCount = 4,
                action = ScanAction.SAVE_SCREENSHOT,
            ),
        )
    }

    @Test
    fun negativeQrCountIsRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            ScanFlowPolicy.afterDecode(qrCount = -1)
        }
    }

    @Test
    fun choosingAnActionWithoutDetectedQrIsRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            ScanFlowPolicy.afterAction(
                qrCount = 0,
                action = ScanAction.READ_QR,
            )
        }
    }
}
