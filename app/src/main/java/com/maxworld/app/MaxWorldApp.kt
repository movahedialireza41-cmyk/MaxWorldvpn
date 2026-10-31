package com.maxworld.app

import android.app.Application
import libv2ray.Libv2ray
import java.io.File

class MaxWorldApp : Application() {
    override fun onCreate() {
        super.onCreate()
        ConfigStore.init(this)
        Subs.init(this)
        Adv.init(this)
        Shield.init(this)
        Design.init(this)
        Usage.init(this)
        AutoConnect.apply(this)
        Thread {
            Geo.installBundled(this)
            Geo.autoUpdate(this)
            if (Prefs.updateCheck(this)) Updater.check()
        }.start()
        try {
            // Folder where geoip.dat / geosite.dat will live in a later stage.
            val assets = File(filesDir, "assets").apply { mkdirs() }
            Libv2ray.initCoreEnv(assets.absolutePath, "")
        } catch (ignored: Throwable) {
            // The error is shown when the user tries to connect.
        }
    }
}
