package cn.liyuyu.fuckwxscan

import android.app.Application
import android.content.Intent
import cn.liyuyu.fuckwxscan.diagnostics.DiagnosticStore

/**
 * Created by frank on 2022/10/17.
 */
class App : Application() {

    override fun onCreate() {
        super.onCreate()
        DiagnosticStore.initialize(this)
    }

    companion object {
        var screenCaptureIntentResult: Intent? = null
    }
}
