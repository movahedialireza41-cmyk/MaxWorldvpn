package com.maxworld.app

import android.content.Context
import androidx.compose.runtime.mutableStateListOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID

data class Sub(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val url: String,
    val updatedAt: Long = 0L,
    /** Bytes used / total and expiry (ms), from the provider's subscription-userinfo header. 0 = unknown. */
    val used: Long = 0L,
    val total: Long = 0L,
    val expire: Long = 0L
)

/** Subscription links: a URL that returns a list of servers, refreshed on demand and once a day. */
object Subs {
    val list = mutableStateListOf<Sub>()
    private var file: File? = null
    private const val DAY = 24L * 60 * 60 * 1000

    fun init(ctx: Context) {
        if (file != null) return
        file = File(ctx.applicationContext.filesDir, "subs.json")
        load()
    }

    fun add(name: String, url: String): Sub {
        val s = Sub(name = name.ifBlank { hostOf(url) }, url = url.trim())
        list.add(s)
        save()
        return s
    }

    fun remove(id: String) {
        list.removeAll { it.id == id }
        ConfigStore.removeGroup(id)
        save()
    }

    fun nameOf(id: String): String = list.firstOrNull { it.id == id }?.name.orEmpty()

    fun count(id: String): Int = ConfigStore.configs.count { it.group == id }

    /** Downloads one subscription and swaps in its servers. Returns the number of servers, or -1 on failure. */
    suspend fun refresh(sub: Sub): Int {
        val dl = withContext(Dispatchers.IO) { download(sub.url) } ?: return -1
        val body = dl.first
        val servers = Importer.parseBody(body)
        if (servers.isEmpty()) return -1
        ConfigStore.replaceGroup(sub.id, servers)
        val i = list.indexOfFirst { it.id == sub.id }
        if (i >= 0) {
            var next = list[i].copy(updatedAt = System.currentTimeMillis())
            val info = parseUserInfo(dl.second)
            if (info != null) next = next.copy(used = info[0], total = info[1], expire = info[2])
            list[i] = next
            save()
        }
        return servers.size
    }

    /** Refreshes subscriptions that have not been updated for a day. */
    suspend fun autoUpdate() {
        val now = System.currentTimeMillis()
        for (s in list.toList()) {
            if (now - s.updatedAt > DAY) refresh(s)
        }
    }

    /** "upload=1; download=2; total=3; expire=1700000000" -> [used, total, expireMs], or null. */
    private fun parseUserInfo(h: String?): LongArray? {
        if (h.isNullOrBlank()) return null
        var up = 0L
        var down = 0L
        var total = 0L
        var exp = 0L
        var any = false
        for (part in h.split(';')) {
            val kv = part.trim().split('=')
            if (kv.size != 2) continue
            val v = kv[1].trim().toDoubleOrNull()?.toLong() ?: continue
            when (kv[0].trim().lowercase()) {
                "upload" -> { up = v; any = true }
                "download" -> { down = v; any = true }
                "total" -> { total = v; any = true }
                "expire" -> { exp = v * 1000; any = true }
            }
        }
        return if (any) longArrayOf(up + down, total, exp) else null
    }

    /** Returns the body and the subscription-userinfo header. */
    private fun download(url: String): Pair<String, String?>? = try {
        // The app itself is outside the tunnel; while connected, go through the local proxy
        // so blocked subscription links can still be refreshed.
        val proxy = if (VpnState.state == ConnState.CONNECTED) {
            java.net.Proxy(java.net.Proxy.Type.HTTP, java.net.InetSocketAddress("127.0.0.1", XrayConfig.HTTP_PORT))
        } else {
            java.net.Proxy.NO_PROXY
        }
        val conn = URL(url).openConnection(proxy) as HttpURLConnection
        conn.connectTimeout = 15000
        conn.readTimeout = 20000
        conn.setRequestProperty("User-Agent", "MaxWorld/0.2 v2rayNG")
        conn.instanceFollowRedirects = true
        try {
            if (conn.responseCode in 200..299) {
                val info = conn.getHeaderField("subscription-userinfo")
                val text = conn.inputStream.use { input ->
                    val out = java.io.ByteArrayOutputStream()
                    val buf = ByteArray(8192)
                    var total = 0
                    while (total < 5_000_000) {
                        val n = input.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n)
                        total += n
                    }
                    out.toString("UTF-8")
                }
                Pair(text, info)
            } else null
        } finally {
            conn.disconnect()
        }
    } catch (e: Exception) {
        null
    }

    private fun hostOf(url: String): String = try {
        URL(url.trim()).host.ifBlank { "Subscription" }
    } catch (e: Exception) {
        "Subscription"
    }

    private fun load() {
        val f = file ?: return
        if (!f.exists()) return
        try {
            val arr = JSONArray(f.readText())
            val items = ArrayList<Sub>()
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                items.add(Sub(o.optString("id"), o.optString("name"), o.optString("url"), o.optLong("updatedAt", 0L),
                    o.optLong("used", 0L), o.optLong("total", 0L), o.optLong("expire", 0L)))
            }
            list.clear()
            list.addAll(items)
        } catch (e: Exception) {
        }
    }

    private fun save() {
        val f = file ?: return
        try {
            val arr = JSONArray()
            list.forEach {
                arr.put(
                    JSONObject().put("id", it.id).put("name", it.name).put("url", it.url).put("updatedAt", it.updatedAt)
                        .put("used", it.used).put("total", it.total).put("expire", it.expire)
                )
            }
            f.writeText(arr.toString())
        } catch (e: Exception) {
        }
    }
}
