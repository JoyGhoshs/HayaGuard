package com.hayaguard.app

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build

object NetworkOptimizer {

    private const val DPR_PATTERN = """dpr=[0-9]+(\.[0-9]+)?"""
    private const val RESOLUTION_PATTERN = """/p[0-9]+x[0-9]+/"""
    private const val SIZE_PATTERN = """/s[0-9]+x[0-9]+/"""
    private const val WIDTH_PATTERN = """_n\.jpg\?.*?w=[0-9]+"""

    private val dprRegex = Regex(DPR_PATTERN)
    private val resolutionRegex = Regex(RESOLUTION_PATTERN)
    private val sizeRegex = Regex(SIZE_PATTERN)

    @Volatile
    private var cachedIsMetered: Boolean? = null
    private var lastCheckTime = 0L
    private const val CACHE_DURATION_MS = 30000L

    fun isMeteredConnection(context: Context): Boolean {
        val now = System.currentTimeMillis()
        if (cachedIsMetered != null && now - lastCheckTime < CACHE_DURATION_MS) {
            return cachedIsMetered!!
        }

        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivityManager.activeNetwork
        val capabilities = connectivityManager.getNetworkCapabilities(network)

        val isMetered = when {
            capabilities == null -> true
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED) -> false
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> false
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> true
            else -> true
        }

        cachedIsMetered = isMetered
        lastCheckTime = now
        return isMetered
    }

    fun optimizeImageUrl(url: String, isMetered: Boolean): String {
        if (!isMetered) return url
        if (!isImageUrl(url)) return url

        var optimized = url

        optimized = dprRegex.replace(optimized, "dpr=1")

        optimized = resolutionRegex.replace(optimized) { match ->
            val original = match.value
            val dimensions = original.removePrefix("/p").removeSuffix("/")
            val parts = dimensions.split("x")
            if (parts.size == 2) {
                try {
                    val width = parts[0].toInt()
                    val height = parts[1].toInt()
                    val newWidth = (width * 0.6).toInt().coerceAtLeast(100)
                    val newHeight = (height * 0.6).toInt().coerceAtLeast(100)
                    "/p${newWidth}x${newHeight}/"
                } catch (e: Exception) {
                    original
                }
            } else {
                original
            }
        }

        optimized = sizeRegex.replace(optimized) { match ->
            val original = match.value
            val dimensions = original.removePrefix("/s").removeSuffix("/")
            val parts = dimensions.split("x")
            if (parts.size == 2) {
                try {
                    val width = parts[0].toInt()
                    val height = parts[1].toInt()
                    val newWidth = (width * 0.6).toInt().coerceAtLeast(100)
                    val newHeight = (height * 0.6).toInt().coerceAtLeast(100)
                    "/s${newWidth}x${newHeight}/"
                } catch (e: Exception) {
                    original
                }
            } else {
                original
            }
        }

        return optimized
    }

    private fun isImageUrl(url: String): Boolean {
        val lower = url.lowercase()
        return lower.contains("fbcdn") && 
               (lower.contains(".jpg") || 
                lower.contains(".jpeg") || 
                lower.contains(".png") || 
                lower.contains(".webp"))
    }

    fun getOptimalAcceptEncoding(): String {
        return "br, gzip, deflate"
    }

    fun invalidateCache() {
        cachedIsMetered = null
        lastCheckTime = 0L
    }
}
