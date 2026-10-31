package com.maxworld.app

import org.json.JSONArray
import org.json.JSONObject

/** Builds the Xray JSON configuration for one [VpnConfig]. */
object XrayConfig {

    /** Local SOCKS port that the tunnel (hev-socks5-tunnel) forwards all traffic to. */
    const val SOCKS_PORT = 10808

    /** Local HTTP proxy (same traffic as the SOCKS one) for apps and for downloads while connected. */
    const val HTTP_PORT = 10809

    private val PRIVATE_RANGES = listOf(
        "10.0.0.0/8", "172.16.0.0/12", "192.168.0.0/16", "127.0.0.0/8",
        "169.254.0.0/16", "100.64.0.0/10", "fc00::/7", "fe80::/10"
    )

    /**
     * @param forTest true builds a config without inbounds, used only to measure delay
     * so it can run while the VPN is up without clashing on the local port.
     */
    fun build(c: VpnConfig, forTest: Boolean = false, httpPort: Int = 0): String {
        if (c.rawJson.isNotEmpty()) return buildRaw(c, forTest, httpPort)
        val tunnelMode = !forTest && httpPort == 0
        val inbounds = inboundList(forTest, httpPort)

        val outbounds = JSONArray()
        var usesFragment = false

        fun tlsLike(m: VpnConfig) = m.security == "tls" || m.security == "reality"

        when (c.protocol) {
            "chain" -> {
                val hops = members(c).filter { it.protocol != "wireguard" && it.protocol != "hysteria2" }
                if (hops.isEmpty()) throw IllegalStateException("chain has no servers")
                val built = ArrayList<JSONObject>()
                for ((i, m) in hops.withIndex()) {
                    val tag = if (i == hops.lastIndex) "proxy" else "hop$i"
                    val ob = proxyOutbound(m, tag)
                    if (i > 0) setDialer(ob, "hop${i - 1}")
                    else if (Adv.fragment && tlsLike(m)) {
                        setDialer(ob, "fragment")
                        usesFragment = true
                    }
                    built.add(ob)
                }
                // Xray sends traffic to the FIRST outbound by default: that must be the last hop
                // ("proxy"), which reaches the earlier hops through its dialer chain.
                outbounds.put(built.last())
                built.dropLast(1).forEach { outbounds.put(it) }
            }
            "balance" -> {
                val list = members(c)
                if (list.isEmpty()) throw IllegalStateException("group has no servers")
                for ((i, m) in list.withIndex()) {
                    val ob = proxyOutbound(m, "lb$i")
                    if (Adv.fragment && tlsLike(m)) {
                        setDialer(ob, "fragment")
                        usesFragment = true
                    }
                    outbounds.put(ob)
                }
            }
            else -> {
                val ob = proxyOutbound(c, "proxy")
                if (Adv.fragment && tlsLike(c)) {
                    setDialer(ob, "fragment")
                    usesFragment = true
                }
                outbounds.put(ob)
            }
        }
        outbounds.put(obj("tag" to "direct", "protocol" to "freedom", "settings" to JSONObject()))
        outbounds.put(obj("tag" to "block", "protocol" to "blackhole", "settings" to JSONObject()))
        if (usesFragment) {
            outbounds.put(
                obj(
                    "tag" to "fragment",
                    "protocol" to "freedom",
                    "settings" to obj(
                        "fragment" to obj(
                            "packets" to "tlshello",
                            "length" to Adv.fragLength,
                            "interval" to Adv.fragInterval
                        )
                    ),
                    "streamSettings" to obj("sockopt" to obj("tcpNoDelay" to true))
                )
            )
        }

        val isBalance = c.protocol == "balance"
        val rules = JSONArray()
        rules.put(
            obj("type" to "field", "ip" to JSONArray(PRIVATE_RANGES), "outboundTag" to "direct")
        )
        var geoRouting = false
        if (tunnelMode) {
            customRules(isBalance).forEach { rules.put(it) }
            Shield.rules(isBalance).forEach { rules.put(it) }
            if (Geo.ready()) {
                if (Adv.blockAds) {
                    rules.put(
                        obj("type" to "field", "domain" to arr("geosite:category-ads-all"), "outboundTag" to "block")
                    )
                }
                if (Adv.routePreset == 1) {
                    geoRouting = true
                    rules.put(
                        obj(
                            "type" to "field",
                            "domain" to arr("geosite:category-ir", "regexp:.*\\.ir$"),
                            "outboundTag" to "direct"
                        )
                    )
                    rules.put(obj("type" to "field", "ip" to arr("geoip:ir"), "outboundTag" to "direct"))
                }
            }
        }

        val routing = obj(
            "domainStrategy" to (if (geoRouting) "IPIfNonMatch" else "AsIs"),
            "rules" to rules
        )
        val root = obj(
            "log" to obj("loglevel" to "warning"),
            "inbounds" to inbounds,
            "outbounds" to outbounds,
            "dns" to obj("servers" to JSONArray(Adv.xrayDnsServers())),
            "routing" to routing
        )

        if (isBalance) {
            val strategy = c.method.ifEmpty { "random" }
            routing.put(
                "balancers",
                arr(obj("tag" to "balancer", "selector" to arr("lb"), "strategy" to obj("type" to strategy)))
            )
            rules.put(obj("type" to "field", "network" to "tcp,udp", "balancerTag" to "balancer"))
            if (strategy == "leastPing") {
                root.put(
                    "observatory",
                    obj(
                        "subjectSelector" to arr("lb"),
                        "probeUrl" to "https://www.gstatic.com/generate_204",
                        "probeInterval" to "60s"
                    )
                )
            }
        }
        return root.toString()
    }

    /** Servers of a chain/balance group, in order, skipping deleted ones and nested groups. */
    private fun members(c: VpnConfig): List<VpnConfig> =
        c.members.split(',').map { it.trim() }.filter { it.isNotEmpty() }
            .mapNotNull { id -> ConfigStore.configs.firstOrNull { it.id == id } }
            .filter { it.rawJson.isEmpty() && !it.isGroup() }

    private fun setDialer(ob: JSONObject, tag: String) {
        val ss = ob.optJSONObject("streamSettings") ?: JSONObject()
        val sock = ss.optJSONObject("sockopt") ?: JSONObject()
        sock.put("dialerProxy", tag)
        ss.put("sockopt", sock)
        ob.put("streamSettings", ss)
    }

    /**
     * User rules, one per line: `direct:domain:example.com`, `block:geosite:category-ads-all`,
     * `proxy:geoip:us`, `direct:192.168.1.0/24`. Lines starting with # are ignored.
     */
    private fun customRules(balance: Boolean): List<JSONObject> {
        val out = ArrayList<JSONObject>()
        val ipLike = Regex("^[0-9a-fA-F:.]+(/\\d+)?$")
        for (raw in Adv.customRules.lines()) {
            val line = raw.trim()
            if (line.isEmpty() || line.startsWith("#")) continue
            val action = line.substringBefore(':').lowercase()
            val value = line.substringAfter(':', "").trim()
            if (value.isEmpty() || action !in listOf("direct", "proxy", "block")) continue
            val rule = obj("type" to "field")
            val items = JSONArray(value.split(',').map { it.trim() }.filter { it.isNotEmpty() })
            val isIp = value.startsWith("geoip:") || ipLike.matches(value.substringBefore(',').trim())
            rule.put(if (isIp) "ip" else "domain", items)
            if (action == "proxy" && balance) rule.put("balancerTag", "balancer")
            else rule.put("outboundTag", if (action == "proxy") "proxy" else action)
            out.add(rule)
        }
        return out
    }

    /**
     * @param httpPort when > 0 the list holds one local HTTP proxy on that port instead of the
     * tunnel's SOCKS inbound. The speed test uses it so it can run beside a live VPN.
     */
    private fun inboundList(forTest: Boolean, httpPort: Int = 0): JSONArray {
        val inbounds = JSONArray()
        if (httpPort > 0) {
            inbounds.put(
                obj(
                    "tag" to "speed-http",
                    "listen" to "127.0.0.1",
                    "port" to httpPort,
                    "protocol" to "http",
                    "settings" to JSONObject()
                )
            )
        } else if (!forTest) {
            inbounds.put(
                obj(
                    "tag" to "socks",
                    "listen" to (if (Adv.shareLan) "0.0.0.0" else "127.0.0.1"),
                    "port" to SOCKS_PORT,
                    "protocol" to "socks",
                    "settings" to obj("auth" to "noauth", "udp" to true, "userLevel" to 8),
                    "sniffing" to obj("enabled" to true, "destOverride" to arr("http", "tls", "quic"))
                )
            )
            inbounds.put(
                obj(
                    "tag" to "http",
                    "listen" to (if (Adv.shareLan) "0.0.0.0" else "127.0.0.1"),
                    "port" to HTTP_PORT,
                    "protocol" to "http",
                    "settings" to JSONObject(),
                    "sniffing" to obj("enabled" to true, "destOverride" to arr("http", "tls"))
                )
            )
        }
        return inbounds
    }

    /** A full user-supplied Xray config: keep everything but replace the inbounds with ours. */
    private fun buildRaw(c: VpnConfig, forTest: Boolean, httpPort: Int = 0): String {
        val root = JSONObject(c.rawJson)
        if (root.optJSONArray("outbounds") == null) throw IllegalArgumentException("config has no outbounds")
        root.put("inbounds", inboundList(forTest, httpPort))
        if (!root.has("log")) root.put("log", obj("loglevel" to "warning"))
        return root.toString()
    }

    // ------------------------------------------------------------------- outbound

    /** WireGuard runs inside Xray itself, so every other feature (routing, tests, kill switch) just works. */
    private fun wireguardOutbound(c: VpnConfig, tag: String): JSONObject {
        val host = if (c.address.contains(':') && !c.address.startsWith("[")) "[${c.address}]" else c.address
        val peer = JSONObject()
            .put("publicKey", c.publicKey)
            .put("endpoint", "$host:${c.port}")
            .put("keepAlive", 25)
            .put("allowedIPs", JSONArray().put("0.0.0.0/0").put("::/0"))
        if (c.wgPsk.isNotBlank()) peer.put("preSharedKey", c.wgPsk)
        val addresses = JSONArray()
        c.wgAddress.split(',', ' ').map { it.trim() }.filter { it.isNotEmpty() }.forEach { addresses.put(it) }
        val st = JSONObject()
            .put("secretKey", c.userId)
            .put("address", addresses)
            .put("peers", JSONArray().put(peer))
            .put("mtu", if (c.wgMtu in 576..1500) c.wgMtu else 1280)
        val rv = c.wgReserved.split(',').mapNotNull { it.trim().toIntOrNull() }
        if (rv.size == 3) st.put("reserved", JSONArray(rv))
        return JSONObject().put("tag", tag).put("protocol", "wireguard").put("settings", st)
    }

    /** Hysteria2 runs inside Xray too: a "hysteria" outbound on the QUIC-based hysteria transport. */
    private fun hysteriaOutbound(c: VpnConfig, tag: String): JSONObject {
        val tls = JSONObject()
            .put("serverName", c.sni.ifEmpty { c.address })
            .put("alpn", JSONArray().put(c.alpn.ifEmpty { "h3" }))
        if (c.allowInsecure) tls.put("allowInsecure", true)
        val ss = JSONObject()
            .put("network", "hysteria")
            .put("security", "tls")
            .put("tlsSettings", tls)
            .put(
                "hysteriaSettings",
                JSONObject().put("version", 2).put("auth", c.userId).put("udpIdleTimeout", 60)
            )
        if (c.hyObfsPass.isNotBlank()) {
            ss.put(
                "finalmask",
                JSONObject().put(
                    "udp",
                    JSONArray().put(
                        JSONObject().put("type", "salamander").put("settings", JSONObject().put("password", c.hyObfsPass))
                    )
                )
            )
        }
        return JSONObject()
            .put("tag", tag)
            .put("protocol", "hysteria")
            .put("settings", JSONObject().put("version", 2).put("address", c.address).put("port", c.port))
            .put("streamSettings", ss)
    }

    private fun proxyOutbound(c: VpnConfig, tag: String): JSONObject {
        if (c.protocol == "wireguard") return wireguardOutbound(c, tag)
        if (c.protocol == "hysteria2") return hysteriaOutbound(c, tag)
        val settings = when (c.protocol) {
            "vless" -> obj(
                "vnext" to arr(
                    obj(
                        "address" to c.address,
                        "port" to c.port,
                        "users" to arr(
                            obj(
                                "id" to c.userId,
                                "encryption" to c.encryption.ifEmpty { "none" },
                                "flow" to c.flow,
                                "level" to 0
                            )
                        )
                    )
                )
            )
            "vmess" -> obj(
                "vnext" to arr(
                    obj(
                        "address" to c.address,
                        "port" to c.port,
                        "users" to arr(
                            obj(
                                "id" to c.userId,
                                "alterId" to c.alterId,
                                "security" to c.method.ifEmpty { "auto" },
                                "level" to 0
                            )
                        )
                    )
                )
            )
            "trojan" -> obj(
                "servers" to arr(
                    obj("address" to c.address, "port" to c.port, "password" to c.userId, "level" to 0)
                )
            )
            "shadowsocks" -> obj(
                "servers" to arr(
                    obj(
                        "address" to c.address,
                        "port" to c.port,
                        "method" to c.method,
                        "password" to c.userId,
                        "level" to 0
                    )
                )
            )
            else -> throw IllegalArgumentException("Unsupported protocol: ${c.protocol}")
        }

        return obj(
            "tag" to tag,
            "protocol" to c.protocol,
            "settings" to settings,
            "streamSettings" to stream(c),
            "mux" to muxFor(c)
        )
    }

    // ------------------------------------------------------------ stream settings

    private fun stream(c: VpnConfig): JSONObject {
        val s = JSONObject()
        val net = c.network.ifEmpty { "tcp" }
        s.put("network", net)

        when (net) {
            "tcp" -> {
                if (c.headerType == "http") {
                    s.put(
                        "tcpSettings",
                        obj(
                            "header" to obj(
                                "type" to "http",
                                "request" to obj(
                                    "version" to "1.1",
                                    "method" to "GET",
                                    "path" to csv(c.path.ifEmpty { "/" }),
                                    "headers" to obj(
                                        "Host" to csv(c.host),
                                        "User-Agent" to arr("Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36"),
                                        "Accept-Encoding" to arr("gzip, deflate"),
                                        "Connection" to arr("keep-alive"),
                                        "Pragma" to "no-cache"
                                    )
                                )
                            )
                        )
                    )
                }
            }
            "kcp" -> {
                val kcp = obj("header" to obj("type" to c.headerType.ifEmpty { "none" }))
                if (c.path.isNotEmpty()) kcp.put("seed", c.path)
                s.put("kcpSettings", kcp)
            }
            "ws" -> {
                val ws = obj("path" to c.path.ifEmpty { "/" })
                if (c.host.isNotEmpty()) ws.put("headers", obj("Host" to c.host))
                s.put("wsSettings", ws)
            }
            "h2" -> {
                s.put("httpSettings", obj("host" to csv(c.host), "path" to c.path.ifEmpty { "/" }))
            }
            "grpc" -> {
                val grpc = obj("serviceName" to c.serviceName, "multiMode" to (c.grpcMode == "multi"))
                if (c.authority.isNotEmpty()) grpc.put("authority", c.authority)
                s.put("grpcSettings", grpc)
            }
            "httpupgrade" -> {
                s.put("httpupgradeSettings", obj("path" to c.path.ifEmpty { "/" }, "host" to c.host))
            }
            "xhttp" -> {
                s.put(
                    "xhttpSettings",
                    obj(
                        "path" to c.path.ifEmpty { "/" },
                        "host" to c.host,
                        "mode" to c.xhttpMode.ifEmpty { "auto" }
                    )
                )
            }
            else -> {
                // Unknown transport: let the core report a clear error.
            }
        }

        val serverName = c.sni.ifEmpty { c.host.substringBefore(',').ifEmpty { c.address } }
        when (c.security) {
            "tls" -> {
                s.put("security", "tls")
                val tls = obj(
                    "serverName" to serverName,
                    "fingerprint" to c.fingerprint.ifEmpty { Adv.fingerprint }
                )
                if (c.allowInsecure) tls.put("allowInsecure", true)
                if (c.alpn.isNotEmpty()) tls.put("alpn", csv(c.alpn))
                s.put("tlsSettings", tls)
            }
            "reality" -> {
                s.put("security", "reality")
                s.put(
                    "realitySettings",
                    obj(
                        "serverName" to c.sni.ifEmpty { c.address },
                        "fingerprint" to c.fingerprint.ifEmpty { Adv.fingerprint },
                        "publicKey" to c.publicKey,
                        "shortId" to c.shortId,
                        "spiderX" to c.spiderX
                    )
                )
            }
            else -> s.put("security", "none")
        }
        return s
    }

    // -------------------------------------------------------------------- helpers

    /** Mux saves handshakes, but does not work with XTLS flows or with most non-TCP transports. */
    private fun muxFor(c: VpnConfig): JSONObject {
        val ok = Adv.mux && c.flow.isEmpty() && c.protocol != "shadowsocks" &&
            c.network in listOf("tcp", "ws", "httpupgrade")
        return if (ok) obj("enabled" to true, "concurrency" to Adv.muxConc) else obj("enabled" to false)
    }

    private fun obj(vararg pairs: Pair<String, Any?>): JSONObject {
        val o = JSONObject()
        for ((k, v) in pairs) o.put(k, v)
        return o
    }

    private fun arr(vararg items: Any): JSONArray {
        val a = JSONArray()
        for (i in items) a.put(i)
        return a
    }

    /** "a, b,c" -> ["a","b","c"], dropping empty parts. */
    private fun csv(text: String): JSONArray =
        JSONArray(text.split(',').map { it.trim() }.filter { it.isNotEmpty() })
}
