package com.maxworld.app

import android.content.Context
import android.content.SharedPreferences

object Prefs {
    private fun p(c: Context): SharedPreferences =
        c.applicationContext.getSharedPreferences("maxworld", Context.MODE_PRIVATE)

    fun theme(c: Context): String = p(c).getString("theme", "neon") ?: "neon"
    fun setTheme(c: Context, id: String) = p(c).edit().putString("theme", id).apply()

    fun selectedId(c: Context): String = p(c).getString("selected", "") ?: ""
    fun setSelectedId(c: Context, id: String) = p(c).edit().putString("selected", id).apply()

    fun connectOnBoot(c: Context): Boolean = p(c).getBoolean("boot", false)
    fun setConnectOnBoot(c: Context, v: Boolean) = p(c).edit().putBoolean("boot", v).apply()

    // MIUI permission screens can't be inspected, so we remember that the user opened them.
    fun miuiDone(c: Context): Boolean = p(c).getBoolean("miui_done", false)
    fun setMiuiDone(c: Context, v: Boolean) = p(c).edit().putBoolean("miui_done", v).apply()

    /** Size of the file used by the speed test, in megabytes. */
    fun speedMb(c: Context): Int = p(c).getInt("speed_mb", 10)
    fun setSpeedMb(c: Context, v: Int) = p(c).edit().putInt("speed_mb", v).apply()

    /** Before connecting, ping the servers and switch to the fastest. */
    fun autoBest(c: Context): Boolean = p(c).getBoolean("auto_best", false)
    fun setAutoBest(c: Context, v: Boolean) = p(c).edit().putBoolean("auto_best", v).apply()

    /** Restart the connection when the tunnel stops working. */
    fun autoReconnect(c: Context): Boolean = p(c).getBoolean("auto_reconnect", true)
    fun setAutoReconnect(c: Context, v: Boolean) = p(c).edit().putBoolean("auto_reconnect", v).apply()

    /** When a server stops working, switch to the next best one by itself. */
    fun autoFailover(c: Context): Boolean = p(c).getBoolean("auto_failover", false)
    fun setAutoFailover(c: Context, v: Boolean) = p(c).edit().putBoolean("auto_failover", v).apply()

    /** Show the live speed in the notification. */
    fun notifSpeed(c: Context): Boolean = p(c).getBoolean("notif_speed", true)
    fun setNotifSpeed(c: Context, v: Boolean) = p(c).edit().putBoolean("notif_speed", v).apply()

    /** Notify when a subscription is nearly out of data or about to expire. */
    fun subAlerts(c: Context): Boolean = p(c).getBoolean("sub_alerts", true)
    fun setSubAlerts(c: Context, v: Boolean) = p(c).edit().putBoolean("sub_alerts", v).apply()

    /** Look for a new version every time the app starts. */
    fun updateCheck(c: Context): Boolean = p(c).getBoolean("update_check", true)
    fun setUpdateCheck(c: Context, v: Boolean) = p(c).edit().putBoolean("update_check", v).apply()

    /** Hours between automatic subscription refreshes; 0 = off. */
    fun subHours(c: Context): Int = p(c).getInt("sub_hours", 24)
    fun setSubHours(c: Context, v: Int) = p(c).edit().putInt("sub_hours", v).apply()

    /** Version whose "what's new" page the user has already seen. */
    fun lastSeenVersion(c: Context): String = p(c).getString("seen_version", "") ?: ""
    fun setLastSeenVersion(c: Context, v: String) = p(c).edit().putString("seen_version", v).apply()

    /** The short welcome tour is shown once. */
    fun onboardDone(c: Context): Boolean = p(c).getBoolean("onboard_done", false)
    fun setOnboardDone(c: Context, v: Boolean) = p(c).edit().putBoolean("onboard_done", v).apply()

    /** Auto-connect: 0 off, 1 on mobile data, 2 on any network. */
    fun autoMode(c: Context): Int = p(c).getInt("auto_mode", 0)
    fun setAutoMode(c: Context, v: Int) = p(c).edit().putInt("auto_mode", v).apply()

    /** When the user last disconnected by hand; auto-connect stays quiet for a while after. */
    fun manualStopAt(c: Context): Long = p(c).getLong("manual_stop", 0L)
    fun setManualStopAt(c: Context, v: Long) = p(c).edit().putLong("manual_stop", v).apply()

    /** Kill switch: when the VPN drops by itself, block the internet instead of leaking. */
    fun killSwitch(c: Context): Boolean = p(c).getBoolean("kill", false)
    fun setKillSwitch(c: Context, v: Boolean) = p(c).edit().putBoolean("kill", v).apply()

    fun geoTried(c: Context): Long = p(c).getLong("geo_tried", 0L)
    fun setGeoTried(c: Context, v: Long) = p(c).edit().putLong("geo_tried", v).apply()

    /** The user's own theme: main color (ARGB) and light/dark. */
    fun customAccent(c: Context): Int = p(c).getInt("custom_accent", 0xFF7C4DFF.toInt())
    fun customLight(c: Context): Boolean = p(c).getBoolean("custom_light", false)
    fun setCustom(c: Context, accent: Int, light: Boolean) =
        p(c).edit().putInt("custom_accent", accent).putBoolean("custom_light", light).apply()
}
