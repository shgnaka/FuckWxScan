/*
 * SPDX-License-Identifier: Apache-2.0
 * New file added by contributors to shgnaka/FuckWxScan in 2026.
 */
package cn.liyuyu.fuckwxscan.overlay

import kotlin.math.roundToInt

internal data class MarkerPosition(
    val left: Int,
    val top: Int,
)

internal object OverlayGeometry {
    fun markerTopLeft(
        centerX: Float,
        centerY: Float,
        sourceWidth: Int,
        sourceHeight: Int,
        targetWidth: Int,
        targetHeight: Int,
        markerSize: Int,
    ): MarkerPosition {
        require(sourceWidth > 0)
        require(sourceHeight > 0)
        require(targetWidth > 0)
        require(targetHeight > 0)
        require(markerSize > 0)

        val scaledCenterX = centerX * targetWidth / sourceWidth
        val scaledCenterY = centerY * targetHeight / sourceHeight
        return MarkerPosition(
            left = (scaledCenterX - markerSize / 2f).roundToInt(),
            top = (scaledCenterY - markerSize / 2f).roundToInt(),
        )
    }
}
