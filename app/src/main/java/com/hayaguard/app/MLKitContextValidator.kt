package com.hayaguard.app

import android.graphics.Bitmap
import android.util.Log
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.label.ImageLabeler
import com.google.mlkit.vision.label.ImageLabeling
import com.google.mlkit.vision.label.defaults.ImageLabelerOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

class MLKitContextValidator {

    private val labeler: ImageLabeler

    private val safetyWhitelist = setOf(
        "poster", "banner", "billboard", "flyer", "newspaper", "magazine",
        "screenshot", "television", "monitor", "display device", "computer",
        "car", "vehicle", "automobile", "truck", "bus", "motorcycle",
        "road", "asphalt", "highway", "street", "traffic",
        "crash", "accident", "wreck", "collision",
        "hospital", "ambulance", "emergency vehicle", "fire truck", "police car",
        "crowd", "protest", "demonstration", "rally", "flag",
        "sports equipment", "ball", "stadium", "arena", "court",
        "painting", "sculpture", "statue", "museum", "gallery",
        "diagram", "chart", "graph", "infographic",
        "food", "meal", "dish", "cuisine", "plate", "bowl",
        "building", "architecture", "skyscraper", "tower", "bridge",
        "landscape", "mountain", "forest", "ocean", "lake", "river",
        "animal", "dog", "cat", "bird", "wildlife", "pet"
    )

    private val confidenceThreshold = 0.75f
    private val minMatchCount = 2

    companion object {
        private const val TAG = "MLKitContextValidator"
    }

    init {
        val options = ImageLabelerOptions.Builder()
            .setConfidenceThreshold(0.6f)
            .build()
        labeler = ImageLabeling.getClient(options)
    }

    suspend fun isSafeContext(bitmap: Bitmap): Boolean {
        return suspendCancellableCoroutine { continuation ->
            val inputImage = InputImage.fromBitmap(bitmap, 0)
            
            labeler.process(inputImage)
                .addOnSuccessListener { labels ->
                    var matchCount = 0
                    val matchedLabels = mutableListOf<String>()
                    
                    for (label in labels) {
                        val labelText = label.text.lowercase()
                        if (label.confidence >= confidenceThreshold) {
                            for (safeWord in safetyWhitelist) {
                                if (labelText == safeWord || labelText.contains(safeWord)) {
                                    matchCount++
                                    matchedLabels.add("${label.text}(${label.confidence})")
                                    break
                                }
                            }
                        }
                    }
                    
                    val isSafe = matchCount >= minMatchCount
                    if (isSafe) {
                        Log.d(TAG, "Safe context detected: $matchedLabels")
                    }
                    
                    if (continuation.isActive) {
                        continuation.resume(isSafe)
                    }
                }
                .addOnFailureListener { e ->
                    Log.e(TAG, "ML Kit failed: ${e.message}")
                    if (continuation.isActive) {
                        continuation.resume(false)
                    }
                }
        }
    }

    fun close() {
        labeler.close()
    }
}
