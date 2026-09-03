package cn.liyuyu.fuckwxscan

import android.app.Application
import android.content.Intent
import cn.liyuyu.fuckwxscan.screenshot.ScreenshotDiagnosticLog

/**
 * Created by frank on 2022/10/17.
 */
class App : Application() {

    companion object {
        var screenCaptureIntentResult: Intent? = null
    }

    override fun onCreate() {
        super.onCreate()
        ScreenshotDiagnosticLog.init(this)
    }
}
