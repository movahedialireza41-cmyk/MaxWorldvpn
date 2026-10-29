package com.maxworld.app

import org.json.JSONArray
import org.json.JSONObject

/** Saves servers and subscriptions to one text file and loads them back. */
object Backup {

    fun export(): String {
        val servers = JSONArray()
        ConfigStore.configs.filter { it.group.isEmpty() }.forEach {
            val link = LinkBuilder.build(it)
            if (link.isNotBlank()) servers.put(link)
        }
        val subs = JSONArray()
        Subs.list.forEach { subs.put(JSONObject().put("name", it.name).put("url", it.url)) }
        return JSONObject()
            .put("app", "MaxWorld")
            .put("version", 1)
            .put("servers", servers)
            .put("subs", subs)
            .toString(2)
    }

    /** @return servers added plus subscriptions added, or -1 if the text is not a backup. */
    fun import(text: String): Int {
        val o = try {
            JSONObject(text)
        } catch (e: Exception) {
            return -1
        }
        if (o.optString("app") != "MaxWorld") return -1
        var added = 0
        val servers = o.optJSONArray("servers")
        if (servers != null) {
            for (i in 0 until servers.length()) {
                val r = Importer.fromText(servers.optString(i))
                if (r is Imported.Servers) added += r.count
            }
        }
        val subs = o.optJSONArray("subs")
        if (subs != null) {
            for (i in 0 until subs.length()) {
                val s = subs.optJSONObject(i) ?: continue
                val url = s.optString("url")
                if (url.isNotBlank() && Subs.list.none { it.url == url }) {
                    Subs.add(s.optString("name"), url)
                    added++
                }
            }
        }
        return added
    }
}
