package com.maxworld.app

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * "Shield": ad / tracker blocking that does not depend on the geo files.
 * Block lists (hosts or Adblock format) are downloaded, reduced to plain domains and turned into
 * Xray routing rules. Lives beside the older "Block ads" switch in Routing and does not change it.
 */
object Shield {
    class Source(val id: String, val name: String, val url: String, var enabled: Boolean, var count: Int)

    private const val MAX_DOMAINS = 120_000
    private val PRESETS = listOf(
        Triple("stevenblack", "StevenBlack Unified", "https://raw.githubusercontent.com/StevenBlack/hosts/master/hosts"),
        Triple("adguard", "AdGuard DNS filter", "https://adguardteam.github.io/AdGuardSDNSFilter/Filters/filter.txt"),
        Triple("hagezilight", "HaGeZi Light", "https://raw.githubusercontent.com/hagezi/dns-blocklists/main/hosts/light.txt"),
        Triple("hagezipro", "HaGeZi Pro", "https://raw.githubusercontent.com/hagezi/dns-blocklists/main/hosts/pro.txt"),
        Triple("peterlowe", "Peter Lowe ad servers", "https://pgl.yoyo.org/adservers/serverlist.php?hostformat=hosts&showintro=0&mimetype=plaintext")
    )
    private val domainRegex = Regex("^[a-z0-9]([a-z0-9-]*[a-z0-9])?(\\.[a-z0-9]([a-z0-9-]*[a-z0-9])?)+$")
    private val ipRegex = Regex("^[0-9.]+$")

    private var prefs: SharedPreferences? = null
    private var dir: File? = null

    var enabled = false
    var builtin = true // also use the geosite ad list when the geo files are present
    var googleAds = true // block Google / YouTube ad servers
    var blockQuic = true // block QUIC (UDP 443) so apps fall back to TCP, where the site name can be seen

    /** Ad servers of Google and YouTube; they are mostly already in the lists, this makes sure of them. */
    private val GOOGLE_ADS = listOf(
        "doubleclick.net", "googlesyndication.com", "googleadservices.com", "2mdn.net",
        "adservice.google.com", "ads.youtube.com", "googleads.g.doubleclick.net", "pagead2.googlesyndication.com",
        "admob.com", "adsense.google.com", "ad.doubleclick.net", "pubads.g.doubleclick.net"
    )
    var allow = ""
    val sources = ArrayList<Source>()

    fun init(ctx: Context) {
        if (prefs != null) return
        val app = ctx.applicationContext
        dir = File(app.filesDir, "shield").apply { mkdirs() }
        val p = app.getSharedPreferences("shield", Context.MODE_PRIVATE)
        prefs = p
        enabled = p.getBoolean("enabled", false)
        builtin = p.getBoolean("builtin", true)
        googleAds = p.getBoolean("googleAds", true)
        blockQuic = p.getBoolean("blockQuic", true)
        allow = p.getString("allow", "") ?: ""
        sources.clear()
        try {
            val arr = JSONArray(p.getString("sources", "[]"))
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                sources.add(Source(o.getString("id"), o.getString("name"), o.getString("url"), o.optBoolean("enabled", true), o.optInt("count", 0)))
            }
        } catch (ignored: Exception) {
        }
    }

    fun save() {
        val arr = JSONArray()
        sources.forEach {
            arr.put(JSONObject().put("id", it.id).put("name", it.name).put("url", it.url).put("enabled", it.enabled).put("count", it.count))
        }
        prefs?.edit()
            ?.putBoolean("enabled", enabled)
            ?.putBoolean("builtin", builtin)
            ?.putBoolean("googleAds", googleAds)
            ?.putBoolean("blockQuic", blockQuic)
            ?.putString("allow", allow)
            ?.putString("sources", arr.toString())
            ?.apply()
    }

    fun presets(): List<Triple<String, String, String>> = PRESETS.filter { p -> sources.none { it.id == p.first } }

    fun addPreset(id: String) {
        val p = PRESETS.firstOrNull { it.first == id } ?: return
        if (sources.any { it.id == id }) return
        sources.add(Source(id, p.second, p.third, true, 0))
        save()
    }

    /** Adds a list by address. Returns false when the address is not an http(s) link. */
    fun addCustom(url: String): Boolean {
        val u = url.trim()
        if (!Regex("^https?://\\S+$", RegexOption.IGNORE_CASE).matches(u)) return false
        if (sources.any { it.url == u }) return true
        val host = try { URL(u).host } catch (e: Exception) { "list" }
        sources.add(Source("c" + Integer.toHexString(u.hashCode()), host, u, true, 0))
        save()
        return true
    }

    fun remove(id: String) {
        sources.removeAll { it.id == id }
        File(dir, "$id.txt").delete()
        save()
    }

    /** Downloads one list and keeps its domains. Returns the number of domains, or -1 on failure. */
    fun refresh(s: Source): Int {
        val body = fetch(s.url) ?: return -1
        val domains = parse(body)
        if (domains.isEmpty()) return -1
        try {
            File(dir, "${s.id}.txt").writeText(domains.joinToString("\n"))
        } catch (e: Exception) {
            return -1
        }
        s.count = domains.size
        save()
        return domains.size
    }

    fun totalDomains(): Int = loadDomains().size

    /** Reduces hosts-file / Adblock / plain-domain text to a list of domains. */
    fun parse(text: String): List<String> {
        val out = LinkedHashSet<String>()
        for (raw in text.lineSequence()) {
            var line = raw.trim()
            if (line.isEmpty() || line[0] == '#' || line[0] == '!' || line[0] == '[') continue
            if (line.startsWith("@@") || line.contains('$') || line.contains('/') && !line.startsWith("||")) continue
            var d: String
            if (line.startsWith("||")) {
                line = line.removePrefix("||")
                d = line.takeWhile { it != '^' && it != '/' && it != ':' && it != '*' }
                if (line.length > d.length && line[d.length] == '*') continue
            } else {
                val tokens = line.substringBefore('#').trim().split(Regex("\\s+"))
                d = if (tokens.size >= 2 && (tokens[0] == "0.0.0.0" || tokens[0] == "127.0.0.1" || tokens[0] == "::1" || tokens[0] == "::")) tokens[1]
                else if (tokens.size == 1) tokens[0] else continue
            }
            d = d.lowercase().removePrefix("*.").trim('.')
            if (d == "localhost" || ipRegex.matches(d) || !domainRegex.matches(d) || d.length > 120) continue
            out.add(d)
            if (out.size >= MAX_DOMAINS) break
        }
        return out.toList()
    }

    private fun loadDomains(): List<String> {
        val all = LinkedHashSet<String>()
        for (s in sources.filter { it.enabled }) {
            try {
                File(dir, "${s.id}.txt").takeIf { it.exists() }?.forEachLine { if (it.isNotEmpty()) all.add(it) }
            } catch (ignored: Exception) {
            }
            if (all.size >= MAX_DOMAINS) break
        }
        return all.take(MAX_DOMAINS)
    }

    private fun allowDomains(): List<String> =
        allow.split(Regex("[\\s,]+")).map { it.trim().lowercase().trim('.') }.filter { domainRegex.matches(it) }

    /** Routing rules for the Xray config, or nothing when Shield is off. */
    fun rules(isBalance: Boolean): List<JSONObject> {
        if (!enabled) return emptyList()
        val out = ArrayList<JSONObject>()
        if (blockQuic) {
            out.add(JSONObject().put("type", "field").put("network", "udp").put("port", "443").put("outboundTag", "block"))
        }
        val allowed = allowDomains()
        if (allowed.isNotEmpty()) {
            val r = JSONObject().put("type", "field").put("domain", JSONArray(allowed.map { "domain:$it" }))
            if (isBalance) r.put("balancerTag", "balancer") else r.put("outboundTag", "proxy")
            out.add(r)
        }
        if (googleAds) {
            out.add(JSONObject().put("type", "field").put("domain", JSONArray(GOOGLE_ADS.map { "domain:$it" })).put("outboundTag", "block"))
        }
        val blocked = loadDomains()
        if (blocked.isNotEmpty()) {
            out.add(JSONObject().put("type", "field").put("domain", JSONArray(blocked.map { "domain:$it" })).put("outboundTag", "block"))
        }
        if (builtin && Geo.ready()) {
            out.add(JSONObject().put("type", "field").put("domain", JSONArray(listOf("geosite:category-ads-all"))).put("outboundTag", "block"))
        }
        return out
    }

    private fun fetch(url: String): String? = try {
        val proxy = if (VpnState.state == ConnState.CONNECTED) {
            java.net.Proxy(java.net.Proxy.Type.HTTP, java.net.InetSocketAddress("127.0.0.1", XrayConfig.HTTP_PORT))
        } else {
            java.net.Proxy.NO_PROXY
        }
        val conn = URL(url).openConnection(proxy) as HttpURLConnection
        conn.connectTimeout = 15000
        conn.readTimeout = 30000
        conn.setRequestProperty("User-Agent", "MaxWorld")
        conn.instanceFollowRedirects = true
        try {
            if (conn.responseCode !in 200..299) null
            else conn.inputStream.use { input ->
                val out = java.io.ByteArrayOutputStream()
                val buf = ByteArray(16384)
                var total = 0
                while (total < 12_000_000) {
                    val n = input.read(buf)
                    if (n < 0) break
                    out.write(buf, 0, n)
                    total += n
                }
                out.toString("UTF-8")
            }
        } finally {
            conn.disconnect()
        }
    } catch (e: Exception) {
        null
    }
}
