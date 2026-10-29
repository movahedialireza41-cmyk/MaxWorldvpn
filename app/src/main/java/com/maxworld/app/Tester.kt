package com.maxworld.app

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import libv2ray.Libv2ray

/** Measures the real delay of a server by loading a small page through it. */
object Tester {
    private const val TEST_URL = "https://www.gstatic.com/generate_204"
    private val gate = Semaphore(4)

    /** @return delay in ms, or -2 if the server did not answer. */
    suspend fun delay(c: VpnConfig): Long = gate.withPermit {
        withContext(Dispatchers.IO) {
            try {
                Libv2ray.measureOutboundDelay(XrayConfig.build(c, forTest = true), TEST_URL)
            } catch (t: Throwable) {
                -2L
            }
        }
    }
}
