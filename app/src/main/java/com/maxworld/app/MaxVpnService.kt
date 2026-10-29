package com.maxworld.app

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import hev.htproxy.TProxyService
import libv2ray.CoreCallbackHandler
import libv2ray.CoreController
import libv2ray.Libv2ray
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

/**
 * The VPN. Traffic path:
 *   apps -> TUN -> hev-socks5-tunnel -> Xray SOCKS inbound (127.0.0.1) -> server
 * MaxWorld itself is excluded from the VPN so Xray's own connections don't loop back.
 */
class MaxVpnService : VpnService() {

    companion object {
        const val ACTION_CONNECT = "com.maxworld.app.CONNECT"
        const val ACTION_DISCONNECT = "com.maxworld.app.DISCONNECT"

        private const val HEALTH_URL = "https://www.gstatic.com/generate_204"

        private val worker = Executors.newSingleThreadExecutor()
        private val scheduler = Executors.newSingleThreadScheduledExecutor()

        fun start(ctx: Context) {
            val i = Intent(ctx, MaxVpnService::class.java).setAction(ACTION_CONNECT)
            ctx.startForegroundService(i)
        }

        fun stop(ctx: Context) {
            // Plain startService is fine while we are alive; if not, nothing to stop.
            Prefs.setManualStopAt(ctx.applicationContext, System.currentTimeMillis())
            try {
                ctx.startService(Intent(ctx, MaxVpnService::class.java).setAction(ACTION_DISCONNECT))
            } catch (ignored: Exception) {
                VpnState.disconnected()
                Surfaces.refresh(ctx)
            }
        }
    }

    private var core: CoreController? = null
    private var tun: ParcelFileDescriptor? = null
    private var tunnelRunning = false
    private var hold: ParcelFileDescriptor? = null
    private var everConnected = false

    private var watchdog: ScheduledFuture<*>? = null
    private var statsTask: ScheduledFuture<*>? = null
    private var pingTask: ScheduledFuture<*>? = null
    private var statsTick = 0
    private val failTried = HashSet<String>()
    private var failures = 0
    @Volatile private var generation = 0
    @Volatile private var lastStartId = 0
    private var netCallback: ConnectivityManager.NetworkCallback? = null

    private val callback = object : CoreCallbackHandler {
        override fun startup(): Long = 0
        override fun shutdown(): Long = 0
        override fun onEmitStatus(code: Long, msg: String?): Long = 0
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        lastStartId = startId
        when (intent?.action) {
            ACTION_DISCONNECT -> {
                Prefs.setManualStopAt(applicationContext, System.currentTimeMillis())
                worker.execute {
                    teardown()
                    releaseHold()
                    everConnected = false
                    VpnState.disconnected()
                    surfaces()
                    stopForegroundCompat()
                    // Only stops if no newer start command arrived in the meantime.
                    stopSelf(startId)
                }
                return Service.START_NOT_STICKY
            }
            else -> {
                // Also reached when Android starts us as "Always-on VPN".
                promoteToForeground()
                failTried.clear()
                VpnState.connecting()
                surfaces()
                worker.execute { connect(startId) }
            }
        }
        return Service.START_NOT_STICKY
    }

    private fun surfaces() = Surfaces.refresh(applicationContext)

    private fun promoteToForeground() {
        try {
            val n = Notifs.vpn(this)
            if (Build.VERSION.SDK_INT >= 34) {
                startForeground(Notifs.ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
            } else {
                startForeground(Notifs.ID, n)
            }
        } catch (ignored: Exception) {
            // Keep going; the VPN itself still works without the notification.
        }
    }

    private fun stopForegroundCompat() {
        try {
            stopForeground(Service.STOP_FOREGROUND_REMOVE)
        } catch (ignored: Exception) {
        }
    }

    /** Split tunneling choices, shared by the real tunnel and the kill-switch blocker. */
    private fun applyApps(builder: Builder) {
        // Xray's own sockets must bypass the tunnel (libv2ray offers no protect() hook), so this
        // app is never routed through the VPN.
        var selectedOnly = false
        if (Adv.splitMode == 1) {
            for (pkg in Adv.splitApps) {
                if (pkg == packageName) continue
                try {
                    builder.addAllowedApplication(pkg)
                    selectedOnly = true
                } catch (ignored: Exception) {
                    // app no longer installed
                }
            }
        }
        if (!selectedOnly) {
            builder.addDisallowedApplication(packageName)
            if (Adv.splitMode == 2) {
                for (pkg in Adv.splitApps) {
                    try {
                        builder.addDisallowedApplication(pkg)
                    } catch (ignored: Exception) {
                    }
                }
            }
        }
    }

    /**
     * Kill switch: an interface that takes all traffic but has nothing behind it, so packets go
     * nowhere. It replaces the real tunnel without a gap and stays until we reconnect or the user stops.
     */
    private fun holdTraffic() {
        try {
            val b = Builder()
                .setSession(getString(R.string.app_name))
                .setMtu(1500)
                .addAddress("10.10.14.1", 30)
                .addRoute("0.0.0.0", 0)
                .addAddress("fd00:10:14::1", 126)
                .addRoute("::", 0)
            applyApps(b)
            val old = hold
            hold = b.establish()
            try {
                old?.close()
            } catch (ignored: Exception) {
            }
            if (hold != null) {
                VpnState.markBlocking(true)
                AppLog.add("Kill switch: internet blocked until the VPN is back")
            }
        } catch (ignored: Exception) {
        }
    }

    private fun releaseHold() {
        try {
            hold?.close()
        } catch (ignored: Exception) {
        }
        hold = null
        VpnState.markBlocking(false)
    }

    private fun connect(startId: Int) {
        try {
            if (everConnected && Prefs.killSwitch(applicationContext)) holdTraffic()
            teardown()

            val cfg = ConfigStore.selected() ?: throw IllegalStateException(getString(R.string.err_no_config))
            AppLog.add("Server: " + cfg.displayName() + " (" + cfg.protocol + "/" + cfg.network + "/" + cfg.security + ")")
            val json = XrayConfig.build(cfg)

            val controller = Libv2ray.newCoreController(callback)
            core = controller
            controller.startLoop(json, 0)

            val builder = Builder()
                .setSession(getString(R.string.app_name))
                .setMtu(1500)
                .addAddress("10.10.14.1", 30)
                .addRoute("0.0.0.0", 0)
            Adv.dnsIps().forEach { builder.addDnsServer(it) }
            if (Adv.ipv6) {
                builder.addAddress("fd00:10:14::1", 126)
                builder.addRoute("::", 0)
            }

            applyApps(builder)
            if (Build.VERSION.SDK_INT >= 29) builder.setMetered(false)

            val pfd = builder.establish() ?: throw IllegalStateException(getString(R.string.err_vpn_denied))
            tun = pfd
            releaseHold()

            val yaml = File(filesDir, "hev.yaml")
            yaml.writeText(
                """
                tunnel:
                  mtu: 1500
                  ipv4: 198.18.0.1
                ${if (Adv.ipv6) "  ipv6: 'fc00::1'" else ""}
                socks5:
                  port: ${XrayConfig.SOCKS_PORT}
                  address: 127.0.0.1
                  udp: 'udp'
                misc:
                  task-stack-size: 81920
                  log-level: warn
                """.trimIndent()
            )

            if (!TProxyService.TProxyStartService(yaml.absolutePath, pfd.fd)) {
                throw IllegalStateException("tunnel start failed")
            }
            tunnelRunning = true

            everConnected = true
            Traffic.reset()
            Usage.resetBase()
            VpnState.connected()
            surfaces()
            startWatchdog()
        } catch (t: Throwable) {
            teardown()
            if (switchToNext()) {
                VpnState.connecting()
                surfaces()
                worker.execute { connect(startId) }
                return
            }
            VpnState.failed(getString(R.string.err_conn_failed, t.message ?: t.javaClass.simpleName))
            if (everConnected && Prefs.killSwitch(applicationContext)) {
                if (hold == null) holdTraffic()
                if (hold != null) {
                    VpnState.markBlocking(true)
                    surfaces()
                    try {
                        getSystemService(android.app.NotificationManager::class.java)
                            .notify(Notifs.ID, Notifs.vpn(this, blocked = true))
                    } catch (ignored: Exception) {
                    }
                    // Keep trying in the background while the internet stays closed.
                    if (Prefs.autoReconnect(applicationContext)) {
                        scheduler.schedule({
                            if (hold != null && VpnState.state == ConnState.DISCONNECTED) {
                                VpnState.connecting()
                                surfaces()
                                val sid = lastStartId
                                worker.execute { connect(sid) }
                            }
                        }, 15, TimeUnit.SECONDS)
                    }
                    return
                }
            }
            releaseHold()
            everConnected = false
            surfaces()
            stopForegroundCompat()
            stopSelf(startId)
        }
    }

    // ----------------------------------------------------------------- auto reconnect

    private fun startWatchdog() {
        stopWatchdog()
        failures = 0
        statsTick = 0
        statsTask = scheduler.scheduleWithFixedDelay({ statsTick() }, 1, 1, TimeUnit.SECONDS)
        pingTask = scheduler.scheduleWithFixedDelay({ pingTick() }, 3, 5, TimeUnit.SECONDS)
        watchdog = scheduler.scheduleWithFixedDelay({ healthCheck(strict = false) }, 40, 40, TimeUnit.SECONDS)

        try {
            val cm = getSystemService(ConnectivityManager::class.java)
            val request = NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN)
                .build()
            val cb = object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    // The phone switched between Wi-Fi and mobile data: verify shortly after.
                    scheduler.schedule({ healthCheck(strict = true) }, 4, TimeUnit.SECONDS)
                }
            }
            cm.registerNetworkCallback(request, cb)
            netCallback = cb
        } catch (ignored: Exception) {
        }
    }

    private fun stopWatchdog() {
        watchdog?.cancel(false)
        watchdog = null
        statsTask?.cancel(false)
        statsTask = null
        pingTask?.cancel(false)
        pingTask = null
        netCallback?.let {
            try {
                getSystemService(ConnectivityManager::class.java).unregisterNetworkCallback(it)
            } catch (ignored: Exception) {
            }
        }
        netCallback = null
    }

    private fun statsTick() {
        if (VpnState.state != ConnState.CONNECTED) return
        Traffic.sample()
        statsTick++
        if (statsTick % 2 == 0 && Prefs.notifSpeed(applicationContext)) {
            try {
                val pm = getSystemService(android.os.PowerManager::class.java)
                if (!pm.isInteractive) return
                val text = "↓ " + Traffic.rate(Traffic.downBps) + "   ↑ " + Traffic.rate(Traffic.upBps)
                getSystemService(android.app.NotificationManager::class.java)
                    .notify(Notifs.ID, Notifs.vpn(this, detail = text))
            } catch (ignored: Exception) {
            }
        }
    }

    private fun pingTick() {
        if (VpnState.state != ConnState.CONNECTED) return
        try {
            if (!getSystemService(android.os.PowerManager::class.java).isInteractive) return
        } catch (ignored: Exception) {
        }
        Traffic.measurePing()
    }

    private fun directInternet(): Boolean = try {
        val c = java.net.URL(HEALTH_URL).openConnection(java.net.Proxy.NO_PROXY) as java.net.HttpURLConnection
        c.connectTimeout = 4000
        c.readTimeout = 4000
        try {
            c.responseCode in 200..399
        } finally {
            c.disconnect()
        }
    } catch (e: Exception) {
        false
    }

    /** Auto-failover: select the next best server (same group first, lowest ping). Max 3 per episode. */
    private fun switchToNext(): Boolean {
        if (!Prefs.autoFailover(applicationContext)) return false
        val cur = ConfigStore.selected() ?: return false
        failTried.add(cur.id)
        if (failTried.size > 3) return false
        if (!directInternet()) return false
        val all = ConfigStore.configs.toList().filter { it.id !in failTried && it.ping != -2L }
        val pool = all.filter { it.group == cur.group }.ifEmpty { all }
        val next = pool.minByOrNull { if (it.ping > 0) it.ping else Long.MAX_VALUE / 2 } ?: return false
        ConfigStore.select(next.id)
        AppLog.add("Failover: " + cur.displayName() + " -> " + next.displayName())
        return true
    }

    /** @param strict reconnect after one failed check instead of three. */
    private fun healthCheck(strict: Boolean) {
        if (VpnState.state != ConnState.CONNECTED) return
        val gen = generation
        Usage.sync()
        if (!Prefs.autoReconnect(applicationContext)) return

        val tunnelDead = try {
            !TProxyService.TProxyIsRunning()
        } catch (t: Throwable) {
            false
        }
        var ok = !tunnelDead
        if (ok) {
            val cfg = ConfigStore.selected()
            if (cfg != null) {
                val ms = try {
                    Libv2ray.measureOutboundDelay(XrayConfig.build(cfg, forTest = true), HEALTH_URL)
                } catch (t: Throwable) {
                    -1L
                }
                ok = ms > 0
            }
        }

        // The user may have disconnected (or a reconnect may have started) while we were testing.
        if (gen != generation || VpnState.state != ConnState.CONNECTED) return
        if (ok) {
            failures = 0
            failTried.clear()
            return
        }
        failures++
        AppLog.add("Connection check failed (" + failures + ")")
        if (strict || tunnelDead || failures >= 3) {
            failures = 0
            if (!strict && !tunnelDead) switchToNext()
            VpnState.connecting()
            surfaces()
            val sid = lastStartId
            worker.execute { connect(sid) }
        }
    }

    private fun teardown() {
        generation++
        Usage.sync()
        stopWatchdog()
        if (tunnelRunning) {
            try {
                TProxyService.TProxyStopService()
            } catch (ignored: Throwable) {
            }
            tunnelRunning = false
        }
        try {
            core?.stopLoop()
        } catch (ignored: Throwable) {
        }
        core = null
        try {
            tun?.close()
        } catch (ignored: Exception) {
        }
        tun = null
    }

    override fun onRevoke() {
        worker.execute {
            teardown()
            releaseHold()
            everConnected = false
            VpnState.disconnected()
            surfaces()
            stopForegroundCompat()
            stopSelf(lastStartId)
        }
    }

    override fun onDestroy() {
        // Clean up only if this instance still holds something. After a normal disconnect it holds
        // nothing, and a blind teardown here would kill a connection that was just started again.
        try {
            hold?.close()
        } catch (ignored: Exception) {
        }
        if (tunnelRunning || core != null || tun != null) {
            worker.execute {
                teardown()
                VpnState.disconnected()
                surfaces()
            }
        }
        super.onDestroy()
    }
}
