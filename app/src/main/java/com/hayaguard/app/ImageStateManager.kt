package com.hayaguard.app

import java.util.concurrent.ConcurrentHashMap

enum class ImageState {
    PROCESSING,
    DONE,
    FAILED
}

object ImageStateManager {
    private val states = ConcurrentHashMap<String, ImageState>()
    private val results = ConcurrentHashMap<String, CachedResult>()
    private val originals = ConcurrentHashMap<String, CachedResult>()
    private val unblurred = ConcurrentHashMap.newKeySet<String>()
    private val lowConfidence = ConcurrentHashMap.newKeySet<String>()
    private val retryCount = ConcurrentHashMap<String, Int>()
    private const val MAX_RETRIES = 3

    fun normalizeUrl(url: String): String {
        return url.replace(Regex("[&?]_processed=\\d+"), "")
            .replace(Regex("[&?]_unblur=\\d+"), "")
            .replace(Regex("[&?]_t=\\d+"), "")
            .replace(Regex("[&?]_reload=\\d+"), "")
    }

    fun getState(url: String): ImageState? {
        return states[url]
    }

    fun startProcessing(url: String): Boolean {
        return states.putIfAbsent(url, ImageState.PROCESSING) == null
    }

    fun markDone(url: String, result: CachedResult) {
        results[url] = result
        states[url] = ImageState.DONE
    }

    fun markFailed(url: String) {
        states[url] = ImageState.FAILED
    }

    fun forceComplete(url: String) {
        states.remove(url)
    }

    fun getResultOrOriginal(url: String): CachedResult? {
        return results[url] ?: originals[url]
    }

    fun getOriginal(url: String): CachedResult? {
        return originals[url]
    }

    fun storeOriginal(url: String, original: CachedResult) {
        originals[url] = original
    }

    fun markUnblurred(url: String) {
        unblurred.add(url)
    }

    fun isUnblurred(url: String): Boolean {
        return unblurred.contains(url)
    }

    fun markLowConfidence(url: String) {
        lowConfidence.add(url)
    }

    fun isLowConfidence(url: String): Boolean {
        return lowConfidence.contains(url)
    }

    fun getLowConfidenceUrls(): Set<String> {
        return lowConfidence.toSet()
    }

    fun canRetry(url: String): Boolean {
        return (retryCount[url] ?: 0) < MAX_RETRIES
    }

    fun incrementRetry(url: String) {
        retryCount[url] = (retryCount[url] ?: 0) + 1
    }

    fun resetRetry(url: String) {
        retryCount.remove(url)
    }

    fun clear() {
        states.clear()
        results.clear()
        originals.clear()
        unblurred.clear()
        lowConfidence.clear()
        retryCount.clear()
    }
}
