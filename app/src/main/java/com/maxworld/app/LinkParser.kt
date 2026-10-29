package com.maxworld.app

import android.net.Uri
import android.util.Base64
import org.json.JSONObject

/** Turns vless:// vmess:// trojan:// ss:// share links into [VpnConfig]s. */
object LinkParser {

    private val linkRegex = Regex("(vless|vmess|trojan|ss|wireguard|hysteria2|hy2)://[^\\s\"'<>]+", RegexOption.IGNORE_CASE)

    /** Finds every supported link inside arbitrary text (clipboard, file, subscription body). */
    fun parseAll(text: String): List<VpnConfig> =
        linkRegex.findAll(text).mapNotNull { parse(it.value) }.toList()

    fun parse(raw: String): VpnConfig? = try {
        val link = raw.trim()
        when {
            link.startsWith("vless://", ignoreCase = true) -> parseUri(link, "vless")
            link.startsWith("trojan://", ignoreCase = true) -> parseUri(link, "trojan")
            link.startsWith("vmess://", ignoreCase = true) -> parseVmess(link)
            link.startsWith("ss://", ignoreCase = true) -> parseShadowsocks(link)
            link.startsWith("wireguard://", ignoreCase = true) -> parseWireguardLink(link)
            link.startsWith("hysteria2://", ignoreCase = true) -> parseHysteria2(link, "hysteria2://".length)
            link.startsWith("hy2://", ignoreCase = true) -> parseHysteria2(link, "hy2://".length)
            else -> null
        }
    } catch (e: Exception) {
        null
    }

    // ------------------------------------------------------------------ hysteria2

    private fun parseHysteria2(link: String, prefixLen: Int): VpnConfig? {
        // Normalize so Uri can parse it as a hierarchical address.
        val uri = Uri.parse("hysteria2://" + link.substring(prefixLen))
        val host = uri.host?.trim('[', ']').orEmpty()
        val port = if (uri.port > 0) uri.port else 443
        val auth = Uri.decode(uri.userInfo.orEmpty())
        if (host.isEmpty() || auth.isEmpty()) return null
        fun q(k: String): String = uri.getQueryParameter(k).orEmpty()
        val insecure = q("insecure").let { it == "1" || it.equals("true", true) } ||
            q("allowInsecure").let { it == "1" || it.equals("true", true) }
        return VpnConfig(
            name = uri.fragment.orEmpty().ifBlank { "Hysteria2 $host:$port" },
            protocol = "hysteria2",
            address = host,
            port = port,
            userId = auth,
            sni = q("sni").ifEmpty { q("peer") },
            alpn = q("alpn"),
            allowInsecure = insecure,
            hyObfsPass = if (q("obfs").equals("salamander", true)) q("obfs-password") else "",
            network = "udp",
            security = "none"
        )
    }

    // ------------------------------------------------------------------ wireguard

    private fun parseWireguardLink(link: String): VpnConfig? {
        val uri = Uri.parse(link)
        val host = uri.host?.trim('[', ']').orEmpty()
        val port = uri.port
        val priv = Uri.decode(uri.userInfo.orEmpty())
        fun q(vararg keys: String): String = keys.firstNotNullOfOrNull { k -> uri.getQueryParameter(k)?.takeIf { it.isNotEmpty() } }.orEmpty()
        val pub = q("publickey", "public_key", "peer_public_key", "pbk")
        val addr = q("address", "ip", "localaddress")
        if (host.isEmpty() || port <= 0 || priv.isEmpty() || pub.isEmpty()) return null
        return VpnConfig(
            name = uri.fragment.orEmpty().ifBlank { "WireGuard $host:$port" },
            protocol = "wireguard",
            address = host,
            port = port,
            userId = priv,
            publicKey = pub,
            wgAddress = addr,
            wgPsk = q("presharedkey", "preshared_key", "psk"),
            wgReserved = q("reserved"),
            wgMtu = q("mtu").toIntOrNull() ?: 0,
            network = "udp",
            security = "none"
        )
    }

    /** Reads the text of a standard WireGuard .conf file ([Interface] and [Peer] sections). */
    fun parseWireguardConf(text: String): VpnConfig? {
        var section = ""
        val iface = HashMap<String, String>()
        val peer = HashMap<String, String>()
        var title = ""
        for (raw in text.lines()) {
            val line = raw.trim()
            if (line.isEmpty()) continue
            if (line.startsWith("#") || line.startsWith(";")) {
                if (title.isEmpty()) title = line.trimStart('#', ';').trim()
                continue
            }
            if (line.startsWith("[")) {
                section = line.trim('[', ']').lowercase()
                if (section == "peer" && peer.isNotEmpty()) break // only the first peer
                continue
            }
            val i = line.indexOf('=')
            if (i < 0) continue
            val k = line.substring(0, i).trim().lowercase()
            val v = line.substring(i + 1).trim()
            if (section == "interface") iface[k] = v else if (section == "peer") peer[k] = v
        }
        val priv = iface["privatekey"].orEmpty()
        val pub = peer["publickey"].orEmpty()
        val ep = peer["endpoint"].orEmpty()
        if (priv.isEmpty() || pub.isEmpty() || ep.isEmpty()) return null
        val host = ep.substringBeforeLast(':').trim('[', ']')
        val port = ep.substringAfterLast(':').toIntOrNull() ?: return null
        if (host.isEmpty()) return null
        return VpnConfig(
            name = title.take(40).ifBlank { "WireGuard $host:$port" },
            protocol = "wireguard",
            address = host,
            port = port,
            userId = priv,
            publicKey = pub,
            wgAddress = iface["address"].orEmpty().replace(" ", ""),
            wgPsk = peer["presharedkey"].orEmpty(),
            wgReserved = iface["reserved"].orEmpty().replace(" ", ""),
            wgMtu = iface["mtu"]?.toIntOrNull() ?: 0,
            network = "udp",
            security = "none"
        )
    }

    // ---------------------------------------------------------------- vless / trojan

    private fun parseUri(link: String, protocol: String): VpnConfig? {
        val uri = Uri.parse(link)
        val host = uri.host?.trim('[', ']').orEmpty()
        val port = uri.port
        val user = Uri.decode(uri.userInfo.orEmpty())
        if (host.isEmpty() || port <= 0 || user.isEmpty()) return null

        fun q(key: String): String = uri.getQueryParameter(key).orEmpty()

        val network = normalizeNetwork(q("type").ifEmpty { "tcp" })
        val security = q("security").ifEmpty { if (protocol == "trojan") "tls" else "none" }.lowercase()
        val insecure = q("allowInsecure").let { it == "1" || it.equals("true", true) } ||
            q("insecure").let { it == "1" || it.equals("true", true) }

        return VpnConfig(
            name = uri.fragment.orEmpty().ifBlank { "$protocol $host:$port" },
            protocol = protocol,
            address = host,
            port = port,
            userId = user,
            flow = q("flow"),
            encryption = q("encryption").ifEmpty { "none" },
            network = network,
            headerType = q("headerType"),
            host = q("host"),
            path = q("path").ifEmpty { q("seed") },
            serviceName = q("serviceName"),
            grpcMode = q("mode").takeIf { network == "grpc" }.orEmpty(),
            authority = q("authority"),
            xhttpMode = q("mode").takeIf { network == "xhttp" }.orEmpty(),
            security = security,
            sni = q("sni").ifEmpty { q("peer") },
            fingerprint = q("fp"),
            alpn = q("alpn"),
            allowInsecure = insecure,
            publicKey = q("pbk"),
            shortId = q("sid"),
            spiderX = q("spx")
        )
    }

    // ---------------------------------------------------------------------- vmess

    private fun parseVmess(link: String): VpnConfig? {
        val body = link.substring("vmess://".length).trim()

        // Newer URI style: vmess://uuid@host:port?...
        if (body.contains('@')) {
            val base = parseUri(link, "vmess") ?: return null
            val uri = Uri.parse(link)
            return base.copy(method = uri.getQueryParameter("encryption").orEmpty().ifEmpty { "auto" })
        }

        val json = JSONObject(b64(body.substringBefore('#')))
        val network = normalizeNetwork(json.optString("net", "tcp").ifEmpty { "tcp" })
        val port = json.optString("port").toIntOrNull() ?: return null
        val address = json.optString("add").trim()
        val id = json.optString("id").trim()
        if (address.isEmpty() || id.isEmpty()) return null

        val path = json.optString("path")
        val host = json.optString("host")
        val type = json.optString("type")
        val isGrpc = network == "grpc"

        return VpnConfig(
            name = json.optString("ps").ifBlank { "vmess $address:$port" },
            protocol = "vmess",
            address = address,
            port = port,
            userId = id,
            method = json.optString("scy").ifEmpty { "auto" },
            alterId = json.optString("aid").toIntOrNull() ?: 0,
            network = network,
            headerType = if (isGrpc) "" else type,
            host = if (isGrpc) "" else host,
            path = if (isGrpc) "" else path,
            serviceName = if (isGrpc) path else "",
            grpcMode = if (isGrpc) type else "",
            authority = if (isGrpc) host else "",
            xhttpMode = if (network == "xhttp") type else "",
            security = if (json.optString("tls").equals("tls", true)) "tls" else "none",
            sni = json.optString("sni"),
            fingerprint = json.optString("fp"),
            alpn = json.optString("alpn")
        )
    }

    // ---------------------------------------------------------------- shadowsocks

    private fun parseShadowsocks(link: String): VpnConfig? {
        var body = link.substring("ss://".length).trim()
        var name = ""
        val hash = body.indexOf('#')
        if (hash >= 0) {
            name = Uri.decode(body.substring(hash + 1))
            body = body.substring(0, hash)
        }
        val query = body.indexOf('?')
        if (query >= 0) body = body.substring(0, query) // SIP003 plugins are not supported
        body = body.trimEnd('/')

        val method: String
        val password: String
        val hostPort: String
        if (body.contains('@')) {
            val userInfo = body.substringBeforeLast('@')
            hostPort = body.substringAfterLast('@')
            val decoded = if (userInfo.contains(':')) Uri.decode(userInfo) else b64(userInfo)
            method = decoded.substringBefore(':')
            password = decoded.substringAfter(':')
        } else {
            val decoded = b64(body)
            val at = decoded.lastIndexOf('@')
            if (at < 0) return null
            val userInfo = decoded.substring(0, at)
            hostPort = decoded.substring(at + 1)
            method = userInfo.substringBefore(':')
            password = userInfo.substringAfter(':')
        }

        val colon = hostPort.lastIndexOf(':')
        if (colon < 0) return null
        val host = hostPort.substring(0, colon).trim('[', ']')
        val port = hostPort.substring(colon + 1).trim().toIntOrNull() ?: return null
        if (host.isEmpty() || method.isEmpty() || password.isEmpty()) return null

        return VpnConfig(
            name = name.ifBlank { "ss $host:$port" },
            protocol = "shadowsocks",
            address = host,
            port = port,
            userId = password,
            method = method,
            network = "tcp",
            security = "none"
        )
    }

    // -------------------------------------------------------------------- helpers

    /** Subscription bodies are often one big base64 blob of links. Returns the decoded text or null. */
    fun decodeBlob(text: String): String? = try {
        val t = text.trim().replace("\n", "").replace("\r", "").replace(" ", "")
        if (t.isEmpty()) null else b64(t)
    } catch (e: Exception) {
        null
    }

    private fun normalizeNetwork(raw: String): String = when (raw.lowercase()) {
        "raw", "tcp" -> "tcp"
        "h2", "http" -> "h2"
        "splithttp", "xhttp" -> "xhttp"
        "mkcp", "kcp" -> "kcp"
        else -> raw.lowercase()
    }

    /** Base64 that tolerates URL-safe alphabets and missing padding. */
    private fun b64(input: String): String {
        var t = input.trim().replace('-', '+').replace('_', '/')
        while (t.length % 4 != 0) t += "="
        return String(Base64.decode(t, Base64.DEFAULT), Charsets.UTF_8)
    }
}
