package com.hayaguard.app

import android.content.Context
import java.io.File

object CacheManager {

    private val PROTECTED_FILES = setOf(
        "Cookies",
        "Cookies-journal",
        "Cookies-wal",
        "Cookies-shm",
        "Web Data",
        "Web Data-journal",
        "Login Data",
        "Login Data-journal"
    )

    private val PROTECTED_DIRS = setOf(
        "Local Storage",
        "Session Storage",
        "IndexedDB",
        "databases",
        "Service Worker"
    )

    private val CLEARABLE_CACHE_DIRS = setOf(
        "Cache",
        "Code Cache",
        "GPUCache",
        "GrShaderCache",
        "ShaderCache",
        "blob_storage",
        "VideoDecodeStats"
    )

    data class CacheInfo(
        val totalBytes: Long,
        val cacheBytes: Long,
        val essentialBytes: Long
    )

    fun getCacheInfo(context: Context): CacheInfo {
        val appCacheSize = getDirSize(context.cacheDir) + getDirSize(context.codeCacheDir)
        val webViewDir = File(context.dataDir, "app_webview")
        
        var clearableSize = appCacheSize
        var essentialSize = 0L

        if (webViewDir.exists()) {
            webViewDir.listFiles()?.forEach { file ->
                if (file.isDirectory) {
                    if (file.name == "Default") {
                        val (clearable, essential) = calculateDefaultDirSizes(file)
                        clearableSize += clearable
                        essentialSize += essential
                    } else if (isClearableDir(file.name)) {
                        clearableSize += getDirSize(file)
                    } else if (isProtectedDir(file.name)) {
                        essentialSize += getDirSize(file)
                    }
                } else {
                    if (isProtectedFile(file.name)) {
                        essentialSize += file.length()
                    } else {
                        clearableSize += file.length()
                    }
                }
            }
        }

        return CacheInfo(
            totalBytes = clearableSize + essentialSize,
            cacheBytes = clearableSize,
            essentialBytes = essentialSize
        )
    }

    private fun calculateDefaultDirSizes(defaultDir: File): Pair<Long, Long> {
        var clearable = 0L
        var essential = 0L

        defaultDir.listFiles()?.forEach { file ->
            if (file.isDirectory) {
                if (isClearableDir(file.name)) {
                    clearable += getDirSize(file)
                } else if (isProtectedDir(file.name)) {
                    essential += getDirSize(file)
                } else {
                    essential += getDirSize(file)
                }
            } else {
                if (isProtectedFile(file.name)) {
                    essential += file.length()
                } else {
                    clearable += file.length()
                }
            }
        }

        return Pair(clearable, essential)
    }

    fun getCachePercentage(context: Context): Float {
        val info = getCacheInfo(context)
        if (info.totalBytes == 0L) return 0f
        return (info.cacheBytes.toFloat() / info.totalBytes.toFloat()) * 100f
    }

    fun getEssentialPercentage(context: Context): Float {
        val info = getCacheInfo(context)
        if (info.totalBytes == 0L) return 0f
        return (info.essentialBytes.toFloat() / info.totalBytes.toFloat()) * 100f
    }

    fun clearCache(context: Context): Long {
        var clearedBytes = 0L
        
        context.cacheDir?.listFiles()?.forEach { file ->
            if (file.isDirectory) {
                clearedBytes += getDirSize(file)
                file.deleteRecursively()
            } else {
                clearedBytes += file.length()
                file.delete()
            }
        }

        context.codeCacheDir?.listFiles()?.forEach { file ->
            if (file.isDirectory) {
                clearedBytes += getDirSize(file)
                file.deleteRecursively()
            } else {
                clearedBytes += file.length()
                file.delete()
            }
        }

        clearedBytes += clearWebViewCacheSafely(context)

        return clearedBytes
    }

    private fun clearWebViewCacheSafely(context: Context): Long {
        var cleared = 0L
        val webViewDir = File(context.dataDir, "app_webview")

        if (!webViewDir.exists()) return 0L

        webViewDir.listFiles()?.forEach { file ->
            if (file.isDirectory) {
                if (file.name == "Default") {
                    cleared += clearDefaultDirSafely(file)
                } else if (isClearableDir(file.name)) {
                    cleared += getDirSize(file)
                    file.deleteRecursively()
                }
            } else {
                if (!isProtectedFile(file.name)) {
                    cleared += file.length()
                    file.delete()
                }
            }
        }

        return cleared
    }

    private fun clearDefaultDirSafely(defaultDir: File): Long {
        var cleared = 0L

        defaultDir.listFiles()?.forEach { file ->
            if (file.isDirectory) {
                if (isClearableDir(file.name)) {
                    cleared += getDirSize(file)
                    file.deleteRecursively()
                }
            } else {
                if (!isProtectedFile(file.name)) {
                    cleared += file.length()
                    file.delete()
                }
            }
        }

        return cleared
    }

    private fun isProtectedFile(name: String): Boolean {
        return PROTECTED_FILES.any { name.equals(it, ignoreCase = true) || name.startsWith(it, ignoreCase = true) }
    }

    private fun isProtectedDir(name: String): Boolean {
        return PROTECTED_DIRS.any { name.equals(it, ignoreCase = true) }
    }

    private fun isClearableDir(name: String): Boolean {
        return CLEARABLE_CACHE_DIRS.any { name.equals(it, ignoreCase = true) }
    }

    private fun getDirSize(dir: File?): Long {
        if (dir == null || !dir.exists()) return 0L
        var size = 0L
        dir.listFiles()?.forEach { file ->
            size += if (file.isDirectory) {
                getDirSize(file)
            } else {
                file.length()
            }
        }
        return size
    }

    fun formatBytes(bytes: Long): String {
        return when {
            bytes >= 1073741824 -> String.format("%.1f GB", bytes / 1073741824.0)
            bytes >= 1048576 -> String.format("%.1f MB", bytes / 1048576.0)
            bytes >= 1024 -> String.format("%.1f KB", bytes / 1024.0)
            else -> "$bytes B"
        }
    }
}
