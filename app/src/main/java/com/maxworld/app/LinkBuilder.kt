package com.maxworld.app

import android.net.Uri
import android.util.Base64
import org.json.JSONObject

/** Turns a [VpnConfig] back into a share link (the reverse of [LinkParser]). */
object LinkBuilder {

    /** @return the share link, or the raw JSON for custom configs. */
    fun build(c: VpnConfig): String = when {
        c.rawJson.isNotEmpty() -> c.rawJson
        c.isGroup() -> ""
        c.protocol == "vmess" -> vmess(c)
        c.protocol == "shadowsocks" -> shadowsocks(c)
        c.protocol == "wireguard" -> wireguard(c)
        c.protocol == "hysteria2" -> hysteria2(c)
        else -> uriStyle(c)
    }

    /** Custom JSON cannot be put in a QR code or a link. */
    fun isLink(c: VpnConfig): Boolean = c.rawJson.isEmpty() && !c.isGroup()

    private fun hostPart(c: VpnConfig): String =
        if (c.address.contains(':') && !c.address.startsWith("[")) "[${c.address}]" else c.address

    private fun uriStyle(c: VpnConfig): String {
        val q = LinkedHashMap<String, String>()
        if (c.protocol == "vless") q["encryption"] = c.encryption.ifEmpty { "none" }
        q["type"] = c.network
        q["security"] = c.security
        fun put(k: String, v: String) {
            if (v.isNotEmpty()) q[k] = v
        }
        put("flow", c.flow)
        put("sni", c.sni)
        put("fp", c.fingerprint)
        put("alpn", c.alpn)
        put("pbk", c.publicKey)
        put("sid", c.shortId)
        put("spx", c.spiderX)
        put("host", c.host)
        put("path", c.path)
        put("serviceName", c.serviceName)
        put("authority", c.authority)
        put("headerType", c.headerType)
        put("mode", c.grpcMode.ifEmpty { c.xhttpMode })
        if (c.allowInsecure) q["allowInsecure"] = "1"

        val query = q.entries.joinToString("&") { "${it.key}=${Uri.encode(it.value)}" }
        return "${c.protocol}://${Uri.encode(c.userId)}@${hostPart(c)}:${c.port}?$query#${Uri.encode(c.displayName())}"
    }

    private fun hysteria2(c: VpnConfig): String {
        val q = LinkedHashMap<String, String>()
        if (c.sni.isNotEmpty()) q["sni"] = c.sni
        if (c.alpn.isNotEmpty()) q["alpn"] = c.alpn
        if (c.allowInsecure) q["insecure"] = "1"
        if (c.hyObfsPass.isNotEmpty()) {
            q["obfs"] = "salamander"
            q["obfs-password"] = c.hyObfsPass
        }
        val query = if (q.isEmpty()) "" else "?" + q.entries.joinToString("&") { "${it.key}=${Uri.encode(it.value)}" }
        return "hysteria2://${Uri.encode(c.userId)}@${hostPart(c)}:${c.port}$query#${Uri.encode(c.displayName())}"
    }

    private fun wireguard(c: VpnConfig): String {
        val q = LinkedHashMap<String, String>()
        q["publickey"] = c.publicKey
        if (c.wgAddress.isNotEmpty()) q["address"] = c.wgAddress
        if (c.wgPsk.isNotEmpty()) q["presharedkey"] = c.wgPsk
        if (c.wgReserved.isNotEmpty()) q["reserved"] = c.wgReserved
        if (c.wgMtu > 0) q["mtu"] = c.wgMtu.toString()
        val query = q.entries.joinToString("&") { "${it.key}=${Uri.encode(it.value)}" }
        return "wireguard://${Uri.encode(c.userId)}@${hostPart(c)}:${c.port}?$query#${Uri.encode(c.displayName())}"
    }

    private fun vmess(c: VpnConfig): String {
        val grpc = c.network == "grpc"
        val j = JSONObject().apply {
            put("v", "2")
            put("ps", c.displayName())
            put("add", c.address)
            put("port", c.port.toString())
            put("id", c.userId)
            put("aid", c.alterId.toString())
            put("scy", c.method.ifEmpty { "auto" })
            put("net", c.network)
            put("type", if (grpc) c.grpcMode else if (c.network == "xhttp") c.xhttpMode else c.headerType)
            put("host", if (grpc) c.authority else c.host)
            put("path", if (grpc) c.serviceName else c.path)
            put("tls", if (c.security == "tls") "tls" else "")
            put("sni", c.sni)
            put("alpn", c.alpn)
            put("fp", c.fingerprint)
        }
        return "vmess://" + Base64.encodeToString(j.toString().toByteArray(), Base64.NO_WRAP)
    }

    private fun shadowsocks(c: VpnConfig): String {
        val userInfo = Base64.encodeToString(
            "${c.method}:${c.userId}".toByteArray(),
            Base64.NO_WRAP or Base64.NO_PADDING or Base64.URL_SAFE
        )
        return "ss://$userInfo@${hostPart(c)}:${c.port}#${Uri.encode(c.displayName())}"
    }
}
