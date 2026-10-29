package com.maxworld.app

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import libv2ray.CoreCallbackHandler
import libv2ray.Libv2ray
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.ServerSocket
import java.net.Socket
import java.net.URL

/**
 * Real download-speed test: starts a temporary Xray instance for ONE server on a free local
 * port, downloads a test file through it and measures the throughput. It works while the VPN is
 * connected because the app itself is excluded from the tunnel.
 */
object SpeedTester {
    private val oneAtATime = Semaphore(1)

    private val callback = object : CoreCallbackHandler {
        override fun startup(): Long = 0
        override fun shutdown(): Long = 0
        override fun onEmitStatus(code: Long, msg: String?): Long = 0
    }

    private const val MAX_SECONDS = 12.0

    /** @return speed in Mbps, or -2.0 if the server could not deliver data. */
    suspend fun run(c: VpnConfig, megabytes: Int): Double = oneAtATime.withPermit {
        withContext(Dispatchers.IO) {
            val bytes = megabytes.coerceIn(1, 100) * 1_000_000L
            val port = try {
                ServerSocket(0).use { it.localPort }
            } catch (e: Exception) {
                return@withContext -2.0
            }

            var controller: libv2ray.CoreController? = null
            try {
                controller = Libv2ray.newCoreController(callback)
                controller.startLoop(XrayConfig.build(c, false, port), 0)

                if (!waitForPort(port)) return@withContext -2.0

                val urls = listOf(
                    "https://speed.cloudflare.com/__down?bytes=$bytes",
                    "https://cachefly.cachefly.net/10mb.test"
                )
                for (u in urls) {
                    ensureActive()
                    val mbps = download(u, port, bytes)
                    if (mbps != null) return@withContext mbps
                }
                -2.0
            } catch (e: CancellationException) {
                throw e
            } catch (t: Throwable) {
                -2.0
            } finally {
                try {
                    controller?.stopLoop()
                } catch (t: Throwable) {
                }
            }
        }
    }

    private fun waitForPort(port: Int): Boolean {
        repeat(40) {
            try {
                Socket().use { s ->
                    s.connect(InetSocketAddress("127.0.0.1", port), 200)
                    return true
                }
            } catch (e: Exception) {
                Thread.sleep(100)
            }
        }
        return false
    }

    private fun kotlinx.coroutines.CoroutineScope.download(url: String, port: Int, wanted: Long): Double? {
        val proxy = Proxy(Proxy.Type.HTTP, InetSocketAddress("127.0.0.1", port))
        val conn = try {
            (URL(url).openConnection(proxy) as HttpURLConnection).apply {
                useCaches = false // nothing is ever cached or saved to disk
                connectTimeout = 8000
                readTimeout = 8000
                setRequestProperty("User-Agent", "MaxWorld")
                setRequestProperty("Cache-Control", "no-cache")
            }
        } catch (e: Exception) {
            return null
        }
        return try {
            if (conn.responseCode != 200) return null
            val buf = ByteArray(32 * 1024)
            var total = 0L
            var t0 = 0L
            conn.inputStream.use { input ->
                while (true) {
                    ensureActive()
                    val n = input.read(buf)
                    if (n < 0) break
                    if (t0 == 0L) t0 = System.nanoTime() // start the clock at the first byte
                    total += n
                    val sec = (System.nanoTime() - t0) / 1e9
                    if (total >= wanted || sec >= MAX_SECONDS) break
                }
            }
            val seconds = if (t0 == 0L) 0.0 else (System.nanoTime() - t0) / 1e9
            if (total < 50_000 || seconds < 0.15) null else total * 8.0 / seconds / 1e6
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        } finally {
            conn.disconnect()
        }
    }
}
