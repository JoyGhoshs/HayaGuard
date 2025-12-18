package com.hayaguard.app

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout

class ContentFilterPipeline(context: Context) {

    private val nsfwjsDetector = NSFWJSDetector(context)
    private val nudeNetDetector = NudeNetDetector(context)
    private val contextValidator = MLKitContextValidator()

    companion object {
        private const val TAG = "ContentFilterPipeline"
        private const val MIN_DIMENSION = 100
        private const val MIN_SKIN_RATIO = 0.15f
    }

    data class FilterResult(
        val isNSFW: Boolean,
        val confidence: Float,
        val category: String,
        val stage: String
    )

    suspend fun processImage(bitmap: Bitmap): FilterResult {
        if (bitmap.width < MIN_DIMENSION || bitmap.height < MIN_DIMENSION) {
            return FilterResult(false, 0f, "", "size_skip")
        }

        val preFilter = ImagePreFilter.shouldScanImage(bitmap)
        if (!preFilter.shouldScan) {
            return FilterResult(false, 0f, "", "prefilter_${preFilter.reason}")
        }

        val timeoutMs = AdaptivePerformanceEngine.getTimeoutMs()
        val fallbackAction = AdaptivePerformanceEngine.getTimeoutFallbackAction()

        return try {
            withTimeout(timeoutMs) {
                performInference(bitmap)
            }
        } catch (e: TimeoutCancellationException) {
            Log.w(TAG, "Inference timeout after ${timeoutMs}ms, applying fallback: $fallbackAction")
            when (fallbackAction) {
                AdaptivePerformanceEngine.TimeoutFallbackAction.BLUR -> FilterResult(true, 0.5f, "timeout", "timeout_blur")
                AdaptivePerformanceEngine.TimeoutFallbackAction.SHOW -> FilterResult(false, 0f, "", "timeout_show")
            }
        }
    }

    private suspend fun performInference(bitmap: Bitmap): FilterResult {
        val scaledNsfwjs = BitmapPool.scaleForNsfwjs(bitmap)
        val scaledNudeNet = BitmapPool.scaleForNudeNet(bitmap)

        try {
            val nsfwjsResult = nsfwjsDetector.detectNSFW(scaledNsfwjs)
            val nudeNetResult = nudeNetDetector.detectNSFW(scaledNudeNet)

            val isNsfwjsFlagged = nsfwjsResult.isNSFW
            val isNudeNetFlagged = nudeNetResult.isNSFW

            if (!isNsfwjsFlagged && !isNudeNetFlagged) {
                return FilterResult(false, 0f, "", "ml_pass")
            }

            val maxConfidence: Float
            val category: String

            if (nudeNetResult.confidence > nsfwjsResult.confidence) {
                maxConfidence = nudeNetResult.confidence
                category = "nudenet"
            } else {
                maxConfidence = nsfwjsResult.confidence
                category = nsfwjsResult.category
            }

            val isSafeContext = contextValidator.isSafeContext(bitmap)

            if (isSafeContext) {
                Log.d(TAG, "Context override: safe context detected")
                return FilterResult(false, maxConfidence, category, "context_override")
            }

            return FilterResult(true, maxConfidence, category, "nsfw_detected")

        } finally {
            BitmapPool.release(scaledNsfwjs)
            BitmapPool.release(scaledNudeNet)
        }
    }

    fun close() {
        nsfwjsDetector.close()
        nudeNetDetector.close()
        contextValidator.close()
        BitmapPool.clear()
    }
}
