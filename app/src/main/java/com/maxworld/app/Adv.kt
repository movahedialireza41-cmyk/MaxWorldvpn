package com.maxworld.app

import android.content.Context
import android.content.SharedPreferences
import java.io.File

/**
 * Routing and privacy settings. Plain fields (read by the Xray config builder and the VPN
 * service); the Routing screen keeps its own UI state and calls [save] after each change.
 */
object Adv {
    private var prefs: SharedPreferences? = null
    var assetsDir: File? = null
        private set

    // split tunneling: 0 = off, 1 = only the chosen apps use the VPN, 2 = chosen apps bypass it
    var splitMode = 0
    var splitApps: Set<String> = emptySet()

    // routing: 0 = everything through the proxy, 1 = Iran (sites and IPs) goes direct
    var routePreset = 0
    var blockAds = false
    var customRules = ""

    // dns
    var dnsPreset = 0 // 0 cloudflare, 1 google, 2 quad9, 3 adguard, 4 custom
    var dnsDoh = false
    var dnsCustom = ""

    // connection tweaks
    var mux = false
    var muxConc = 8
    var fragment = false
    var fragLength = "100-200"
    var fragInterval = "10-20"
    var fingerprint = "chrome"
    var ipv6 = false

    private class Preset(val ips: List<String>, val doh: String)

    private val presets = listOf(
        Preset(listOf("1.1.1.1", "1.0.0.1"), "https+local://cloudflare-dns.com/dns-query"),
        Preset(listOf("8.8.8.8", "8.8.4.4"), "https+local://dns.google/dns-query"),
        Preset(listOf("9.9.9.9", "149.112.112.112"), "https+local://dns.quad9.net/dns-query"),
        Preset(listOf("94.140.14.14", "94.140.15.15"), "https+local://dns.adguard-dns.com/dns-query")
    )

    private val ipRegex = Regex("^\\d{1,3}(\\.\\d{1,3}){3}$")

    fun init(ctx: Context) {
        if (prefs != null) return
        val app = ctx.applicationContext
        assetsDir = File(app.filesDir, "assets").apply { mkdirs() }
        val p = app.getSharedPreferences("adv", Context.MODE_PRIVATE)
        prefs = p
        splitMode = p.getInt("splitMode", 0)
        splitApps = p.getStringSet("splitApps", emptySet())?.toSet() ?: emptySet()
        routePreset = p.getInt("routePreset", 0)
        blockAds = p.getBoolean("blockAds", false)
        customRules = p.getString("customRules", "") ?: ""
        dnsPreset = p.getInt("dnsPreset", 0)
        dnsDoh = p.getBoolean("dnsDoh", false)
        dnsCustom = p.getString("dnsCustom", "") ?: ""
        mux = p.getBoolean("mux", false)
        muxConc = p.getInt("muxConc", 8)
        fragment = p.getBoolean("fragment", false)
        fragLength = p.getString("fragLength", "100-200") ?: "100-200"
        fragInterval = p.getString("fragInterval", "10-20") ?: "10-20"
        fingerprint = p.getString("fingerprint", "chrome") ?: "chrome"
        ipv6 = p.getBoolean("ipv6", false)
    }

    fun save() {
        prefs?.edit()
            ?.putInt("splitMode", splitMode)
            ?.putStringSet("splitApps", splitApps)
            ?.putInt("routePreset", routePreset)
            ?.putBoolean("blockAds", blockAds)
            ?.putString("customRules", customRules)
            ?.putInt("dnsPreset", dnsPreset)
            ?.putBoolean("dnsDoh", dnsDoh)
            ?.putString("dnsCustom", dnsCustom)
            ?.putBoolean("mux", mux)
            ?.putInt("muxConc", muxConc)
            ?.putBoolean("fragment", fragment)
            ?.putString("fragLength", fragLength)
            ?.putString("fragInterval", fragInterval)
            ?.putString("fingerprint", fingerprint)
            ?.putBoolean("ipv6", ipv6)
            ?.apply()
    }

    private fun customTokens(): List<String> =
        dnsCustom.split(Regex("[,\\s]+")).map { it.trim() }.filter { it.isNotEmpty() }

    /** Plain IPs handed to Android as the VPN's DNS servers. */
    fun dnsIps(): List<String> {
        if (dnsPreset in presets.indices) return presets[dnsPreset].ips
        val ips = customTokens().filter { ipRegex.matches(it) }
        return ips.ifEmpty { presets[0].ips }
    }

    /** Servers for Xray's own resolver (it looks up server and routing domains). */
    fun xrayDnsServers(): List<String> {
        val list = ArrayList<String>()
        if (dnsDoh) {
            if (dnsPreset in presets.indices) {
                list.add(presets[dnsPreset].doh)
            } else {
                customTokens().filter { it.startsWith("http") }.forEach {
                    list.add(if (it.startsWith("https://")) "https+local://" + it.removePrefix("https://") else it)
                }
            }
        }
        if (list.isEmpty()) list.addAll(dnsIps())
        list.add("localhost")
        return list
    }
}

/** The geoip.dat / geosite.dat files that "Iran direct" and "block ads" need. */
object Geo {
    private val names = listOf("geoip.dat", "geosite.dat")

    private fun base() = "chocolate4u/Iran-v2ray-rules@release"

    fun ready(): Boolean {
        val d = Adv.assetsDir ?: return false
        return names.all { File(d, it).let { f -> f.exists() && f.length() > 100_000 } }
    }

    /** Copies the geo files shipped inside the APK (added at build time) so no download is needed. */
    fun installBundled(ctx: android.content.Context) {
        val d = Adv.assetsDir ?: return
        if (ready()) return
        for (name in names) {
            val tmp = File(d, "$name.tmp")
            try {
                ctx.assets.open("geo/$name").use { input -> tmp.outputStream().use { input.copyTo(it) } }
                val dest = File(d, name)
                dest.delete()
                if (!tmp.renameTo(dest)) return
            } catch (ignored: Exception) {
                tmp.delete()
                return
            }
        }
    }

    /** Refreshes the files in the background when they are over a month old (tries at most once a day). */
    fun autoUpdate(ctx: android.content.Context) {
        val d = Adv.assetsDir ?: return
        if (!ready()) return
        val now = System.currentTimeMillis()
        val age = now - File(d, "geoip.dat").lastModified()
        if (age < 30L * 24 * 3600 * 1000) return
        if (now - Prefs.geoTried(ctx) < 24L * 3600 * 1000) return
        Prefs.setGeoTried(ctx, now)
        download()
    }

    fun sizeMb(): Double {
        val d = Adv.assetsDir ?: return 0.0
        return names.sumOf { File(d, it).takeIf { f -> f.exists() }?.length() ?: 0L } / 1048576.0
    }

    fun delete() {
        val d = Adv.assetsDir ?: return
        names.forEach { File(d, it).delete() }
    }

    /**
     * Downloads both files. When the VPN is connected the download goes through its local
     * HTTP proxy, because the app itself is excluded from the tunnel.
     * @return true when both files were saved.
     */
    fun download(): Boolean {
        val d = Adv.assetsDir ?: return false
        for (name in names) {
            val urls = listOf(
                "https://cdn.jsdelivr.net/gh/${base()}/$name",
                "https://raw.githubusercontent.com/Chocolate4U/Iran-v2ray-rules/release/$name"
            )
            var saved = false
            for (u in urls) {
                if (fetch(u, File(d, "$name.tmp"))) {
                    val tmp = File(d, "$name.tmp")
                    val dest = File(d, name)
                    dest.delete()
                    saved = tmp.renameTo(dest)
                    if (saved) break
                }
            }
            if (!saved) return false
        }
        return true
    }

    private fun fetch(url: String, out: File): Boolean {
        return try {
            val proxy = if (VpnState.state == ConnState.CONNECTED) {
                java.net.Proxy(java.net.Proxy.Type.HTTP, java.net.InetSocketAddress("127.0.0.1", XrayConfig.HTTP_PORT))
            } else {
                java.net.Proxy.NO_PROXY
            }
            val conn = java.net.URL(url).openConnection(proxy) as java.net.HttpURLConnection
            conn.connectTimeout = 15000
            conn.readTimeout = 30000
            conn.useCaches = false
            conn.instanceFollowRedirects = true
            try {
                if (conn.responseCode != 200) return false
                conn.inputStream.use { input -> out.outputStream().use { input.copyTo(it) } }
                out.length() > 100_000
            } finally {
                conn.disconnect()
            }
        } catch (e: Exception) {
            out.delete()
            false
        }
    }
}
