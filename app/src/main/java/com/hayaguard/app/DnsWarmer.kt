package com.hayaguard.app

import android.util.Log
import java.net.InetAddress
import java.util.concurrent.Executors

object DnsWarmer {

    private const val TAG = "DnsWarmer"
    private val executor = Executors.newSingleThreadExecutor { r ->
        Thread(r, "DnsWarmer").apply {
            priority = Thread.MIN_PRIORITY
            isDaemon = true
        }
    }

    private val domains = arrayOf(
        "m.facebook.com",
        "www.facebook.com",
        "static.xx.fbcdn.net",
        "scontent.fbcdn.net",
        "video.xx.fbcdn.net",
        "external.xx.fbcdn.net",
        "z-m-scontent.fbcdn.net",
        "edge-chat.facebook.com"
    )

    @Volatile
    private var warmed = false

    fun warmUp() {
        if (warmed) return
        warmed = true

        executor.execute {
            for (domain in domains) {
                try {
                    val addresses = InetAddress.getAllByName(domain)
                    Log.d(TAG, "Resolved $domain -> ${addresses.size} addresses")
                } catch (e: Exception) {
                    Log.d(TAG, "Failed to resolve $domain")
                }
            }
            Log.d(TAG, "DNS warming complete")
        }
    }

    fun shutdown() {
        executor.shutdownNow()
    }
}
