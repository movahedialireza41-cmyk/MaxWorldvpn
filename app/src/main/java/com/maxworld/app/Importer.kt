package com.maxworld.app

import org.json.JSONObject

/** What the app found inside a piece of text (clipboard, QR code, subscription body). */
sealed class Imported {
    data class Servers(val count: Int) : Imported()
    data class SubUrl(val url: String) : Imported()
    object None : Imported()
}

object Importer {

    /** Adds whatever it can recognise to the store and says what it was. */
    fun fromText(raw: String): Imported {
        val text = raw.trim()
        if (text.isEmpty()) return Imported.None

        // WireGuard .conf text (also what a WireGuard QR code contains).
        if (text.contains("[Interface]", ignoreCase = true) && text.contains("[Peer]", ignoreCase = true)) {
            val cfg = LinkParser.parseWireguardConf(text) ?: return Imported.None
            return Imported.Servers(ConfigStore.add(listOf(cfg)))
        }

        // Full Xray JSON config.
        if (text.startsWith("{")) {
            val cfg = jsonConfig(text) ?: return Imported.None
            return Imported.Servers(ConfigStore.add(listOf(cfg)))
        }

        // Subscription address.
        if ((text.startsWith("http://") || text.startsWith("https://")) && !text.any { it.isWhitespace() }) {
            return Imported.SubUrl(text)
        }

        var found = LinkParser.parseAll(text)
        if (found.isEmpty()) {
            val decoded = LinkParser.decodeBlob(text)
            if (decoded != null) found = LinkParser.parseAll(decoded)
        }
        if (found.isEmpty()) return Imported.None
        return Imported.Servers(ConfigStore.add(found))
    }

    /** Parses a subscription body into servers without touching the store. */
    fun parseBody(body: String): List<VpnConfig> {
        var found = LinkParser.parseAll(body)
        if (found.isEmpty()) {
            val decoded = LinkParser.decodeBlob(body)
            if (decoded != null) found = LinkParser.parseAll(decoded)
        }
        return found
    }

    fun jsonConfig(text: String): VpnConfig? {
        return try {
        val root = JSONObject(text)
        val outbounds = root.optJSONArray("outbounds") ?: return null
        if (outbounds.length() == 0) return null

        var address = ""
        var port = 0
        val first = outbounds.optJSONObject(0)
        val settings = first?.optJSONObject("settings")
        val node = settings?.optJSONArray("vnext")?.optJSONObject(0)
            ?: settings?.optJSONArray("servers")?.optJSONObject(0)
        if (node != null) {
            address = node.optString("address")
            port = node.optInt("port", 0)
        }
        VpnConfig(
            name = root.optString("remarks").ifBlank { first?.optString("tag").orEmpty().ifBlank { "Custom JSON" } },
            protocol = "custom",
            address = address,
            port = port,
            network = "json",
            security = "custom",
            rawJson = text
        )
        } catch (e: Exception) {
            null
        }
    }
}
