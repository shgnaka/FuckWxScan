/*
 * SPDX-License-Identifier: Apache-2.0
 * New file added by contributors to shgnaka/FuckWxScan in 2026.
 */
package cn.liyuyu.fuckwxscan.settings

import android.content.Context

object AppPreferences {
    private const val PREFERENCES_NAME = "qr_volume_gesture_preferences"
    private const val KEY_AUTO_COPY = "auto_copy"

    // This is a conservative MVP default, not a final product decision.
    const val DEFAULT_AUTO_COPY = false

    fun isAutoCopyEnabled(context: Context): Boolean {
        return preferences(context).getBoolean(KEY_AUTO_COPY, DEFAULT_AUTO_COPY)
    }

    fun setAutoCopyEnabled(context: Context, enabled: Boolean) {
        preferences(context).edit().putBoolean(KEY_AUTO_COPY, enabled).apply()
    }

    private fun preferences(context: Context) = context.getSharedPreferences(
        PREFERENCES_NAME,
        Context.MODE_PRIVATE,
    )
}
