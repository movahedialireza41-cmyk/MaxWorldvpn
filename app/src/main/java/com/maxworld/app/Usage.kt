package com.maxworld.app

import android.content.Context
import hev.htproxy.TProxyService
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/** Data used per day (a few numbers per day, last 60 days). Only totals are stored, never what was visited. */
object Usage {
    private const val KEEP_DAYS = 60
    private var ctx: Context? = null
    private var lastDown = 0L
    private var lastUp = 0L
    private var pendingDown = 0L
    private var pendingUp = 0L
    private var lastFlush = 0L
    private val pendingSrv = HashMap<String, LongArray>()
    private val dayFmt = SimpleDateFormat("yyyyMMdd", Locale.US)

    fun init(c: Context) {
        ctx = c.applicationContext
    }

    private fun prefs() = ctx?.getSharedPreferences("usage", Context.MODE_PRIVATE)

    /** A new tunnel starts counting from zero. */
    @Synchronized
    fun resetBase() {
        lastDown = 0L
        lastUp = 0L
    }

    /** Reads the tunnel counters and adds what is new to today. Safe to call from any thread. */
    @Synchronized
    fun sync() {
        val s = try {
            TProxyService.TProxyGetStats()
        } catch (t: Throwable) {
            null
        } ?: return
        if (s.size < 4) return
        val up = s[1]
        val down = s[3]
        val dd = if (down >= lastDown) down - lastDown else down
        val du = if (up >= lastUp) up - lastUp else up
        lastDown = down
        lastUp = up
        pendingDown += dd
        pendingUp += du
        val sid = ConfigStore.selectedId
        if (sid.isNotEmpty() && (dd > 0 || du > 0)) {
            val a = pendingSrv.getOrPut(sid) { LongArray(2) }
            a[0] += dd
            a[1] += du
        }
        val now = System.currentTimeMillis()
        if (now - lastFlush > 60_000 || pendingDown + pendingUp > 5_000_000) flush()
    }

    @Synchronized
    fun flush() {
        val p = prefs() ?: return
        if (pendingDown == 0L && pendingUp == 0L) {
            lastFlush = System.currentTimeMillis()
            return
        }
        try {
            val o = JSONObject(p.getString("days", "{}") ?: "{}")
            val key = dayFmt.format(Date())
            val cur = o.optJSONArray(key)
            val d = (cur?.optLong(0) ?: 0L) + pendingDown
            val u = (cur?.optLong(1) ?: 0L) + pendingUp
            o.put(key, org.json.JSONArray().put(d).put(u))
            // drop old days
            val keys = o.keys().asSequence().toList().sorted()
            if (keys.size > KEEP_DAYS) keys.take(keys.size - KEEP_DAYS).forEach { o.remove(it) }
            val srv = JSONObject(p.getString("servers", "{}") ?: "{}")
            for ((id, a) in pendingSrv) {
                val c = srv.optJSONArray(id)
                srv.put(id, org.json.JSONArray().put((c?.optLong(0) ?: 0L) + a[0]).put((c?.optLong(1) ?: 0L) + a[1]))
            }
            p.edit().putString("days", o.toString()).putString("servers", srv.toString()).apply()
            pendingSrv.clear()
            pendingDown = 0L
            pendingUp = 0L
        } catch (e: Exception) {
        }
        lastFlush = System.currentTimeMillis()
    }

    /** Total bytes (down + up) used through one server since it was first used. */
    @Synchronized
    fun serverTotal(id: String): Long {
        val p = prefs() ?: return 0L
        val pend = pendingSrv[id]?.let { it[0] + it[1] } ?: 0L
        return try {
            val a = JSONObject(p.getString("servers", "{}") ?: "{}").optJSONArray(id)
            (a?.optLong(0) ?: 0L) + (a?.optLong(1) ?: 0L) + pend
        } catch (e: Exception) {
            pend
        }
    }

    /** Forgets the per-server totals (the daily chart is kept). */
    @Synchronized
    fun clearServers() {
        pendingSrv.clear()
        prefs()?.edit()?.remove("servers")?.apply()
    }

    class Day(val label: String, val down: Long, val up: Long)

    /** The last [n] days, oldest first; days without use have zeros. */
    @Synchronized
    fun lastDays(n: Int): List<Day> {
        flush()
        val o = try {
            JSONObject(prefs()?.getString("days", "{}") ?: "{}")
        } catch (e: Exception) {
            JSONObject()
        }
        val cal = Calendar.getInstance()
        cal.add(Calendar.DAY_OF_YEAR, -(n - 1))
        val out = ArrayList<Day>()
        repeat(n) {
            val key = dayFmt.format(cal.time)
            val a = o.optJSONArray(key)
            out.add(Day(cal.get(Calendar.DAY_OF_MONTH).toString(), a?.optLong(0) ?: 0L, a?.optLong(1) ?: 0L))
            cal.add(Calendar.DAY_OF_YEAR, 1)
        }
        return out
    }

    @Synchronized
    fun clear() {
        pendingDown = 0L
        pendingUp = 0L
        pendingSrv.clear()
        prefs()?.edit()?.remove("days")?.remove("servers")?.apply()
    }
}
