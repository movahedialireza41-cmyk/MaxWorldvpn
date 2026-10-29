package com.maxworld.app

import org.json.JSONObject
import java.util.UUID

/** One proxy server. Field names follow the common share-link vocabulary. */
data class VpnConfig(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "",
    val protocol: String = "vless", // vless, vmess, trojan, shadowsocks, wireguard, hysteria2
    val address: String = "",
    val port: Int = 443,
    val userId: String = "", // uuid for vless/vmess, password for trojan/shadowsocks
    val method: String = "", // shadowsocks cipher, or vmess security
    val flow: String = "",
    val encryption: String = "none",
    val alterId: Int = 0,
    val network: String = "tcp", // tcp, ws, grpc, h2, httpupgrade, xhttp, kcp
    val headerType: String = "",
    val host: String = "",
    val path: String = "",
    val serviceName: String = "",
    val grpcMode: String = "",
    val authority: String = "",
    val xhttpMode: String = "",
    val security: String = "none", // none, tls, reality
    val sni: String = "",
    val fingerprint: String = "",
    val alpn: String = "",
    val allowInsecure: Boolean = false,
    val publicKey: String = "",
    val shortId: String = "",
    val spiderX: String = "",
    val ping: Long = -1L, // last measured delay in ms, -1 = not tested, -2 = failed
    val group: String = "", // id of the subscription it came from, "" = added by hand
    val favorite: Boolean = false,
    val rawJson: String = "", // full Xray config for protocol "custom"
    val speed: Double = -1.0, // last download speed in Mbps, -1 = not tested, -2 = failed
    val members: String = "", // comma separated server ids for protocol "chain" / "balance"
    // WireGuard: userId = own private key, publicKey = the server's public key
    val wgAddress: String = "", // own tunnel address(es), e.g. 10.0.0.2/32
    val wgPsk: String = "",
    val wgReserved: String = "", // "0,0,0" (used by some services such as WARP)
    val wgMtu: Int = 0,
    // Hysteria2: userId = auth password, sni/allowInsecure as usual
    val hyObfsPass: String = "" // salamander obfuscation password, empty = off
) {
    fun isGroup(): Boolean = protocol == "chain" || protocol == "balance"

    fun displayName(): String = name.ifBlank { "$address:$port" }

    /** Used to skip duplicates when importing. */
    fun dedupKey(): String =
        if (isGroup()) "group|$protocol|$members|$method" else if (rawJson.isNotEmpty()) "custom|${rawJson.hashCode()}" else listOf(protocol, address, port, userId, network, path, host, serviceName, security, sni).joinToString("|")

    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("name", name)
        put("protocol", protocol)
        put("address", address)
        put("port", port)
        put("userId", userId)
        put("method", method)
        put("flow", flow)
        put("encryption", encryption)
        put("alterId", alterId)
        put("network", network)
        put("headerType", headerType)
        put("host", host)
        put("path", path)
        put("serviceName", serviceName)
        put("grpcMode", grpcMode)
        put("authority", authority)
        put("xhttpMode", xhttpMode)
        put("security", security)
        put("sni", sni)
        put("fingerprint", fingerprint)
        put("alpn", alpn)
        put("allowInsecure", allowInsecure)
        put("publicKey", publicKey)
        put("shortId", shortId)
        put("spiderX", spiderX)
        put("ping", ping)
        put("group", group)
        put("favorite", favorite)
        put("rawJson", rawJson)
        put("speed", speed)
        put("members", members)
        put("wgAddress", wgAddress)
        put("wgPsk", wgPsk)
        put("wgReserved", wgReserved)
        put("wgMtu", wgMtu)
        put("hyObfsPass", hyObfsPass)
    }

    companion object {
        fun fromJson(o: JSONObject): VpnConfig = VpnConfig(
            id = o.optString("id").ifEmpty { UUID.randomUUID().toString() },
            name = o.optString("name"),
            protocol = o.optString("protocol", "vless"),
            address = o.optString("address"),
            port = o.optInt("port", 443),
            userId = o.optString("userId"),
            method = o.optString("method"),
            flow = o.optString("flow"),
            encryption = o.optString("encryption", "none"),
            alterId = o.optInt("alterId", 0),
            network = o.optString("network", "tcp"),
            headerType = o.optString("headerType"),
            host = o.optString("host"),
            path = o.optString("path"),
            serviceName = o.optString("serviceName"),
            grpcMode = o.optString("grpcMode"),
            authority = o.optString("authority"),
            xhttpMode = o.optString("xhttpMode"),
            security = o.optString("security", "none"),
            sni = o.optString("sni"),
            fingerprint = o.optString("fingerprint"),
            alpn = o.optString("alpn"),
            allowInsecure = o.optBoolean("allowInsecure", false),
            publicKey = o.optString("publicKey"),
            shortId = o.optString("shortId"),
            spiderX = o.optString("spiderX"),
            ping = o.optLong("ping", -1L),
            group = o.optString("group"),
            favorite = o.optBoolean("favorite", false),
            rawJson = o.optString("rawJson"),
            speed = o.optDouble("speed", -1.0),
            members = o.optString("members"),
            wgAddress = o.optString("wgAddress"),
            wgPsk = o.optString("wgPsk"),
            wgReserved = o.optString("wgReserved"),
            wgMtu = o.optInt("wgMtu", 0),
            hyObfsPass = o.optString("hyObfsPass")
        )
    }
}
