package com.maxworld.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.VpnService

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        if (!Prefs.connectOnBoot(context)) return
        ConfigStore.init(context)
        if (ConfigStore.selected() == null) return
        // Without a previously granted VPN consent we cannot connect silently.
        if (VpnService.prepare(context) != null) return
        try {
            MaxVpnService.start(context)
        } catch (ignored: Exception) {
        }
    }
}
