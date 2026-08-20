/*
 * SPDX-License-Identifier: Apache-2.0
 * New file added by contributors to shgnaka/FuckWxScan in 2026.
 */
package cn.liyuyu.fuckwxscan.settings

import android.content.Context
import android.content.SharedPreferences

object AppPreferences {
    private const val PREFERENCES_NAME = "qr_volume_gesture_preferences"
    private const val KEY_AUTO_COPY = "auto_copy"
    const val KEY_SHAKE_TRIGGER = "shake_trigger"

    // This is a conservative MVP default, not a final product decision.
    const val DEFAULT_AUTO_COPY = false
    const val DEFAULT_SHAKE_TRIGGER = false

    fun isAutoCopyEnabled(context: Context): Boolean {
        return sharedPreferences(context).getBoolean(KEY_AUTO_COPY, DEFAULT_AUTO_COPY)
    }

    fun setAutoCopyEnabled(context: Context, enabled: Boolean) {
        sharedPreferences(context).edit().putBoolean(KEY_AUTO_COPY, enabled).apply()
    }

    fun isShakeTriggerEnabled(context: Context): Boolean {
        return sharedPreferences(context).getBoolean(
            KEY_SHAKE_TRIGGER,
            DEFAULT_SHAKE_TRIGGER,
        )
    }

    fun setShakeTriggerEnabled(context: Context, enabled: Boolean) {
        sharedPreferences(context).edit().putBoolean(KEY_SHAKE_TRIGGER, enabled).apply()
    }

    fun sharedPreferences(context: Context): SharedPreferences =
        context.applicationContext.getSharedPreferences(
            PREFERENCES_NAME,
            Context.MODE_PRIVATE,
        )
}
