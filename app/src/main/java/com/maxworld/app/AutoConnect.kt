package com.maxworld.app

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.VpnService

/** Connects by itself when the phone gets internet: 0 = off, 1 = on mobile data, 2 = on any network. */
object AutoConnect {
    private const val ACTION = "com.maxworld.app.NET"
    /** After a manual disconnect we leave the user alone for a while. */
    private const val PAUSE_MS = 10 * 60 * 1000L

    private fun pending(ctx: Context): PendingIntent =
        PendingIntent.getBroadcast(
            ctx, 77,
            Intent(ctx, NetReceiver::class.java).setAction(ACTION),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        )

    /** (Re)registers the system callback that wakes the app on network changes, or removes it when off. */
    fun apply(ctx: Context) {
        val app = ctx.applicationContext
        try {
            val cm = app.getSystemService(ConnectivityManager::class.java)
            val pi = pending(app)
            try {
                cm.unregisterNetworkCallback(pi)
            } catch (ignored: Exception) {
            }
            if (Prefs.autoMode(app) != 0) {
                val req = NetworkRequest.Builder()
                    .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                    .addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN)
                    .build()
                cm.registerNetworkCallback(req, pi)
            }
        } catch (ignored: Exception) {
        }
    }

    fun onNetwork(ctx: Context) {
        val mode = Prefs.autoMode(ctx)
        if (mode == 0) return
        if (VpnState.state != ConnState.DISCONNECTED) return
        if (System.currentTimeMillis() - Prefs.manualStopAt(ctx) < PAUSE_MS) return
        ConfigStore.init(ctx)
        if (ConfigStore.selected() == null) return
        if (VpnService.prepare(ctx) != null) return
        val cm = ctx.getSystemService(ConnectivityManager::class.java)
        val net = cm.activeNetwork ?: return
        val caps = cm.getNetworkCapabilities(net) ?: return
        if (caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) return
        if (!caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) return
        if (mode == 1 && !caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) return
        try {
            AppLog.add("Auto-connect started")
            MaxVpnService.start(ctx)
        } catch (e: Exception) {
            AppLog.add("Auto-connect was blocked by the system")
        }
    }
}

class NetReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        AutoConnect.onNetwork(context.applicationContext)
    }
}
