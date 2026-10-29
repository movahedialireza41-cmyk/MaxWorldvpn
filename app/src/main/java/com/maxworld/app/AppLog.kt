package com.maxworld.app

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Short activity log kept in memory only (nothing is written to storage). No addresses or keys are logged. */
object AppLog {
    private const val MAX = 300
    private val buffer = ArrayList<String>()
    private val fmt = SimpleDateFormat("HH:mm:ss", Locale.US)

    /** Bumps on every change so the UI re-reads the lines. */
    var version by mutableIntStateOf(0)
        private set

    fun add(msg: String) {
        synchronized(buffer) {
            buffer.add(fmt.format(Date()) + "  " + msg)
            while (buffer.size > MAX) buffer.removeAt(0)
        }
        version++
    }

    fun lines(): List<String> = synchronized(buffer) { buffer.toList() }

    fun clear() {
        synchronized(buffer) { buffer.clear() }
        version++
    }
}
