/*
 * SPDX-License-Identifier: Apache-2.0
 * New file added by contributors to shgnaka/FuckWxScan in 2026.
 */
package cn.liyuyu.fuckwxscan.overlay

import org.junit.Assert.assertEquals
import org.junit.Test

class OverlayGeometryTest {
    @Test
    fun sameSizedOverlayKeepsQrCenterAligned() {
        val position = OverlayGeometry.markerTopLeft(
            centerX = 500f,
            centerY = 900f,
            sourceWidth = 1080,
            sourceHeight = 2400,
            targetWidth = 1080,
            targetHeight = 2400,
            markerSize = 36,
        )

        assertEquals(MarkerPosition(left = 482, top = 882), position)
    }

    @Test
    fun differentlySizedOverlayScalesEachAxis() {
        val position = OverlayGeometry.markerTopLeft(
            centerX = 270f,
            centerY = 600f,
            sourceWidth = 1080,
            sourceHeight = 2400,
            targetWidth = 720,
            targetHeight = 1280,
            markerSize = 40,
        )

        assertEquals(MarkerPosition(left = 160, top = 300), position)
    }
}
