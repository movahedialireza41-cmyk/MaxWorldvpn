package com.maxworld.app

import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Connection history: when, which server, how long and how much. Only these totals are kept (last 100). */
object History {
    class Entry(val start: Long, val end: Long, val server: String, val down: Long, val up: Long)

    private var ctx: Context? = null
    private var startAt = 0L
    private var server = ""
    private var down = 0L
    private var up = 0L

    fun init(c: Context) {
        ctx = c.applicationContext
    }

    @Synchronized
    fun begin() {
        startAt = System.currentTimeMillis()
        server = ConfigStore.selected()?.displayName().orEmpty()
        down = 0L
        up = 0L
    }

    @Synchronized
    fun add(d: Long, u: Long) {
        if (startAt != 0L) {
            down += d
            up += u
        }
    }

    @Synchronized
    fun end() {
        if (startAt == 0L) return
        val e = Entry(startAt, System.currentTimeMillis(), server, down, up)
        startAt = 0L
        if (e.end - e.start < 3000) return
        val p = ctx?.getSharedPreferences("history", Context.MODE_PRIVATE) ?: return
        try {
            val arr = JSONArray(p.getString("list", "[]"))
            val out = JSONArray()
            out.put(JSONObject().put("s", e.start).put("e", e.end).put("n", e.server).put("d", e.down).put("u", e.up))
            for (i in 0 until minOf(arr.length(), 99)) out.put(arr.get(i))
            p.edit().putString("list", out.toString()).apply()
        } catch (ignored: Exception) {
        }
    }

    fun list(): List<Entry> {
        val p = ctx?.getSharedPreferences("history", Context.MODE_PRIVATE) ?: return emptyList()
        return try {
            val arr = JSONArray(p.getString("list", "[]"))
            (0 until arr.length()).map {
                val o = arr.getJSONObject(it)
                Entry(o.optLong("s"), o.optLong("e"), o.optString("n"), o.optLong("d"), o.optLong("u"))
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun clear() {
        ctx?.getSharedPreferences("history", Context.MODE_PRIVATE)?.edit()?.remove("list")?.apply()
    }
}

@Composable
fun HistoryDialog(onDismiss: () -> Unit) {
    val p = LocalPalette.current
    var tick by remember { mutableIntStateOf(0) }
    val rows = remember(tick) { History.list() }
    val fmt = remember { SimpleDateFormat("MM/dd  HH:mm", Locale.getDefault()) }
    FullScreenDialog(onDismiss) {
        Text(
            stringResource(R.string.hist_title), color = p.text, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold,
            modifier = Modifier.padding(top = 24.dp, bottom = 4.dp)
        )
        Text(
            stringResource(R.string.hist_total_fmt, rows.size, Traffic.bytes(rows.sumOf { it.down + it.up })),
            color = p.accent, fontSize = 13.sp, modifier = Modifier.padding(bottom = 12.dp)
        )
        if (rows.isEmpty()) {
            Text(stringResource(R.string.hist_empty), color = p.muted, fontSize = 14.sp, modifier = Modifier.weight(1f))
        } else {
            LazyColumn(Modifier.weight(1f)) {
                items(rows) { e ->
                    GlassCard(Modifier.fillMaxWidth().padding(bottom = 10.dp)) {
                        Row(Modifier.fillMaxWidth()) {
                            Text(e.server.ifEmpty { "-" }, color = p.text, fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 1, modifier = Modifier.weight(1f))
                            Text(Traffic.bytes(e.down + e.up), color = p.accent, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        }
                        val mins = ((e.end - e.start) / 60000).toInt()
                        Text(
                            fmt.format(Date(e.start)) + "  ·  " + stringResource(R.string.hist_dur_fmt, mins / 60, mins % 60) +
                                "  ·  ↓ " + Traffic.bytes(e.down) + "  ↑ " + Traffic.bytes(e.up),
                            color = p.muted, fontSize = 12.sp
                        )
                    }
                }
            }
        }
        Row(Modifier.padding(vertical = 14.dp)) {
            Chip(stringResource(R.string.usage_clear), false) { History.clear(); tick++ }
            Spacer(Modifier.padding(horizontal = 4.dp))
            Chip(stringResource(R.string.close), false) { onDismiss() }
        }
    }
}
