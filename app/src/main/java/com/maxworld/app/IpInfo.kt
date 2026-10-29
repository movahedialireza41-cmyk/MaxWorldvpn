package com.maxworld.app

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.URL
import java.util.Locale

/** Where the traffic leaves the internet. Kept in memory only, never written to disk. */
data class IpData(
    val ip: String,
    val countryCode: String,
    val city: String,
    val isp: String,
    val lat: Double?,
    val lon: Double?
) {
    fun countryName(): String =
        if (countryCode.length == 2) Locale("", countryCode).getDisplayCountry(Locale.getDefault()) else ""
}

object IpInfo {
    var info by mutableStateOf<IpData?>(null)
        private set
    var loading by mutableStateOf(false)
        private set
    var failed by mutableStateOf(false)
        private set

    fun clear() {
        info = null
        loading = false
        failed = false
    }

    /** Asks a public "what is my IP" service through the local proxy, so the answer is the VPN's exit. */
    suspend fun fetch() {
        loading = true
        failed = false
        var result: IpData? = null
        var attempt = 0
        while (result == null && attempt < 3 && VpnState.state == ConnState.CONNECTED) {
            if (attempt > 0) delay(1500)
            result = withContext(Dispatchers.IO) { ipwho() ?: ipApi() }
            attempt++
        }
        if (VpnState.state == ConnState.CONNECTED) {
            info = result
            failed = result == null
        }
        loading = false
    }

    private fun get(url: String): String? = try {
        val proxy = Proxy(Proxy.Type.HTTP, InetSocketAddress("127.0.0.1", XrayConfig.HTTP_PORT))
        val conn = URL(url).openConnection(proxy) as HttpURLConnection
        conn.connectTimeout = 8000
        conn.readTimeout = 8000
        conn.useCaches = false
        conn.setRequestProperty("User-Agent", "MaxWorld")
        try {
            if (conn.responseCode == 200) conn.inputStream.bufferedReader().use { it.readText().take(20_000) } else null
        } finally {
            conn.disconnect()
        }
    } catch (e: Exception) {
        null
    }

    private fun ipwho(): IpData? {
        val body = get("https://ipwho.is/?fields=success,ip,country_code,city,latitude,longitude,connection") ?: return null
        return try {
            val j = JSONObject(body)
            if (!j.optBoolean("success", false)) return null
            IpData(
                ip = j.optString("ip"),
                countryCode = j.optString("country_code").uppercase(),
                city = j.optString("city"),
                isp = j.optJSONObject("connection")?.optString("isp").orEmpty(),
                lat = if (j.has("latitude")) j.optDouble("latitude") else null,
                lon = if (j.has("longitude")) j.optDouble("longitude") else null
            ).takeIf { it.ip.isNotEmpty() }
        } catch (e: Exception) {
            null
        }
    }

    private fun ipApi(): IpData? {
        val body = get("https://ipapi.co/json/") ?: return null
        return try {
            val j = JSONObject(body)
            if (j.has("error")) return null
            IpData(
                ip = j.optString("ip"),
                countryCode = j.optString("country_code").uppercase(),
                city = j.optString("city"),
                isp = j.optString("org"),
                lat = if (j.has("latitude")) j.optDouble("latitude") else null,
                lon = if (j.has("longitude")) j.optDouble("longitude") else null
            ).takeIf { it.ip.isNotEmpty() }
        } catch (e: Exception) {
            null
        }
    }
}

/** Turns "DE" into the German flag emoji. Empty when the code is not two letters. */
fun flagOf(code: String): String {
    if (code.length != 2 || !code.all { it in 'A'..'Z' }) return ""
    val base = 0x1F1E6
    return String(Character.toChars(base + (code[0] - 'A'))) + String(Character.toChars(base + (code[1] - 'A')))
}

/** True when the text already starts with a flag (two regional indicator symbols). */
fun hasFlag(name: String): Boolean {
    val cps = name.codePoints().toArray()
    return cps.any { it in 0x1F1E6..0x1F1FF }
}
