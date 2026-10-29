package com.maxworld.app

import android.content.Context
import android.net.VpnService
import androidx.annotation.StringRes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.Socket
import java.net.URL

/** One line of the "why won't it connect?" check. */
class Check(@StringRes val title: Int, val state: Int, @StringRes val hint: Int = 0, val extra: String = "") {
    companion object {
        const val OK = 0
        const val BAD = 1
        const val SKIP = 2
    }
}

object Doctor {

    /** Runs the checks one by one; [onStep] gets the list so far so the screen can fill in live. */
    suspend fun run(ctx: Context, onStep: (List<Check>) -> Unit) {
        val out = ArrayList<Check>()
        fun add(c: Check) {
            out.add(c)
            onStep(out.toList())
        }

        // 1. phone internet (the app itself is outside the VPN, so this tests the real connection)
        val net = withContext(Dispatchers.IO) { directInternet() }
        add(if (net) Check(R.string.dr_net, Check.OK) else Check(R.string.dr_net, Check.BAD, R.string.dr_net_fix))

        // 2. a server exists
        val cfg = ConfigStore.selected()
        if (cfg == null) {
            add(Check(R.string.dr_server, Check.BAD, R.string.dr_server_fix))
        } else {
            add(Check(R.string.dr_server, Check.OK, extra = cfg.displayName()))
        }

        // 3. VPN permission
        val perm = VpnService.prepare(ctx) == null
        add(if (perm) Check(R.string.dr_perm, Check.OK) else Check(R.string.dr_perm, Check.BAD, R.string.dr_perm_fix))

        // 4. server port reachable
        if (cfg != null && !cfg.isGroup() && cfg.rawJson.isEmpty() && cfg.address.isNotBlank() && cfg.protocol != "wireguard" && cfg.protocol != "hysteria2") {
            val reach = withContext(Dispatchers.IO) { tcp(cfg.address, cfg.port) }
            add(if (reach) Check(R.string.dr_reach, Check.OK) else Check(R.string.dr_reach, Check.BAD, R.string.dr_reach_fix))
        } else {
            add(Check(R.string.dr_reach, Check.SKIP))
        }

        // 5. real delay through the server
        if (cfg != null) {
            val ms = Tester.delay(cfg)
            add(
                if (ms > 0) Check(R.string.dr_delay, Check.OK, extra = "$ms ms")
                else Check(R.string.dr_delay, Check.BAD, R.string.dr_delay_fix)
            )
        } else {
            add(Check(R.string.dr_delay, Check.SKIP))
        }

        // 6. background survival
        val battery = try {
            Keepalive.isIgnoringBattery(ctx)
        } catch (e: Exception) {
            true
        }
        add(if (battery) Check(R.string.dr_battery, Check.OK) else Check(R.string.dr_battery, Check.BAD, R.string.dr_battery_fix))

        // 7. notifications
        add(
            if (notifGranted(ctx)) Check(R.string.dr_notif, Check.OK)
            else Check(R.string.dr_notif, Check.BAD, R.string.dr_notif_fix)
        )

        // 8. geo files when a rule needs them
        if (Adv.routePreset == 1 || Adv.blockAds) {
            add(if (Geo.ready()) Check(R.string.dr_geo, Check.OK) else Check(R.string.dr_geo, Check.BAD, R.string.dr_geo_fix))
        }
    }

    private fun directInternet(): Boolean = try {
        val c = URL("https://www.gstatic.com/generate_204").openConnection(Proxy.NO_PROXY) as HttpURLConnection
        c.connectTimeout = 5000
        c.readTimeout = 5000
        c.useCaches = false
        try {
            c.responseCode in 200..399
        } finally {
            c.disconnect()
        }
    } catch (e: Exception) {
        false
    }

    private fun tcp(host: String, port: Int): Boolean = try {
        Socket().use { it.connect(InetSocketAddress(host, port), 5000) }
        true
    } catch (e: Exception) {
        false
    }
}
