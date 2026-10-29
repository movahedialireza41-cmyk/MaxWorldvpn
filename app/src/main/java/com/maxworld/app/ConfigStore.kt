package com.maxworld.app

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONArray
import java.io.File

/** All saved servers plus the selected one. Observable from Compose. */
object ConfigStore {
    val configs = mutableStateListOf<VpnConfig>()
    var selectedId by mutableStateOf("")

    private var appContext: Context? = null
    private var file: File? = null

    fun init(ctx: Context) {
        if (file != null) return
        val app = ctx.applicationContext
        appContext = app
        file = File(app.filesDir, "configs.json")
        selectedId = Prefs.selectedId(app)
        load()
    }

    fun selected(): VpnConfig? = configs.firstOrNull { it.id == selectedId }

    fun select(id: String) {
        selectedId = id
        appContext?.let { Prefs.setSelectedId(it, id) }
    }

    /** Adds new servers, skipping duplicates. Returns how many were really added. */
    fun add(list: List<VpnConfig>, group: String = ""): Int {
        val known = configs.map { it.dedupKey() }.toMutableSet()
        var added = 0
        for (c in list) {
            if (known.add(c.dedupKey())) {
                configs.add(if (group.isEmpty()) c else c.copy(group = group))
                added++
            }
        }
        if (added > 0) {
            if (selected() == null) configs.firstOrNull()?.let { select(it.id) }
            save()
        }
        return added
    }

    fun remove(id: String) {
        configs.removeAll { it.id == id }
        if (selectedId == id) select(configs.firstOrNull()?.id.orEmpty())
        save()
    }

    fun update(c: VpnConfig) {
        val i = configs.indexOfFirst { it.id == c.id }
        if (i >= 0) {
            configs[i] = c
            save()
        }
    }

    fun toggleFavorite(id: String) {
        val i = configs.indexOfFirst { it.id == id }
        if (i >= 0) {
            configs[i] = configs[i].copy(favorite = !configs[i].favorite)
            save()
        }
    }

    fun duplicate(id: String) {
        val i = configs.indexOfFirst { it.id == id }
        if (i >= 0) {
            val src = configs[i]
            configs.add(i + 1, src.copy(id = java.util.UUID.randomUUID().toString(), name = src.displayName() + " (2)", ping = -1L))
            save()
        }
    }

    /** Replaces every server of one subscription with a fresh list, keeping favorites, pings and selection. */
    fun replaceGroup(group: String, fresh: List<VpnConfig>) {
        val old = configs.filter { it.group == group }
        val oldByKey = old.associateBy { it.dedupKey() }
        val selectedKey = selected()?.takeIf { it.group == group }?.dedupKey()

        val seen = HashSet<String>()
        val merged = ArrayList<VpnConfig>()
        for (c in fresh) {
            if (!seen.add(c.dedupKey())) continue
            val prev = oldByKey[c.dedupKey()]
            merged.add(
                if (prev != null) c.copy(id = prev.id, favorite = prev.favorite, ping = prev.ping, group = group)
                else c.copy(group = group)
            )
        }
        configs.removeAll { it.group == group }
        configs.addAll(merged)

        if (selectedKey != null && selected() == null) {
            val again = merged.firstOrNull { it.dedupKey() == selectedKey } ?: configs.firstOrNull()
            select(again?.id.orEmpty())
        } else if (selected() == null) {
            select(configs.firstOrNull()?.id.orEmpty())
        }
        save()
    }

    fun removeGroup(group: String) {
        configs.removeAll { it.group == group }
        if (selected() == null) select(configs.firstOrNull()?.id.orEmpty())
        save()
    }

    fun setPing(id: String, ms: Long) {
        val i = configs.indexOfFirst { it.id == id }
        if (i >= 0) {
            configs[i] = configs[i].copy(ping = ms)
            save()
        }
    }

    fun setSpeed(id: String, mbps: Double) {
        val i = configs.indexOfFirst { it.id == id }
        if (i >= 0) {
            configs[i] = configs[i].copy(speed = mbps)
            save()
        }
    }

    private fun load() {
        val f = file ?: return
        if (!f.exists()) return
        try {
            val arr = JSONArray(f.readText())
            val loaded = ArrayList<VpnConfig>()
            for (i in 0 until arr.length()) loaded.add(VpnConfig.fromJson(arr.getJSONObject(i)))
            configs.clear()
            configs.addAll(loaded)
        } catch (ignored: Exception) {
            // Corrupt file: start empty rather than crash.
        }
    }

    private fun save() {
        val f = file ?: return
        try {
            val arr = JSONArray()
            configs.forEach { arr.put(it.toJson()) }
            f.writeText(arr.toString())
        } catch (ignored: Exception) {
        }
    }
}
