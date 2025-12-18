package com.hayaguard.app

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import java.nio.FloatBuffer

class NudeNetDetector(context: Context) {

    private var ortEnvironment: OrtEnvironment? = null
    private var ortSession: OrtSession? = null
    private var isInitialized = false
    private val inputSize = 320
    private val explicitClasses = setOf(0, 5, 15, 16)
    private val moderateClasses = setOf(6, 7)
    private val explicitThreshold = 0.60f
    private val breastThreshold = 0.75f
    private val ignoreClasses = setOf(8, 9)

    data class DetectionResult(val isNSFW: Boolean, val confidence: Float)

    init {
        try {
            ortEnvironment = OrtEnvironment.getEnvironment()
            val modelBytes = context.assets.open("320n.onnx").readBytes()
            val sessionOptions = OrtSession.SessionOptions()
            ortSession = ortEnvironment?.createSession(modelBytes, sessionOptions)
            isInitialized = true
        } catch (e: Exception) {
            Log.e("NudeNetDetector", "Failed to load: ${e.message}")
            isInitialized = false
        }
    }

    fun detectNSFW(bitmap: Bitmap): DetectionResult {
        if (!isInitialized || ortSession == null) return DetectionResult(false, 0f)

        return try {
            val needsScale = bitmap.width != inputSize || bitmap.height != inputSize
            val scaledBitmap = if (needsScale) {
                BitmapPool.scaleTo320(bitmap)
            } else {
                bitmap
            }
            
            val inputTensor = prepareInput(scaledBitmap)
            val inputName = ortSession!!.inputNames.first()
            val results = ortSession!!.run(mapOf(inputName to inputTensor))
            val outputTensor = results[0] as OnnxTensor
            val shape = outputTensor.info.shape
            val result = processOutputTensor(outputTensor, shape)

            inputTensor.close()
            results.close()
            if (needsScale && scaledBitmap != bitmap) {
                BitmapPool.release(scaledBitmap)
            }

            result
        } catch (e: Exception) {
            DetectionResult(false, 0f)
        }
    }

    private fun prepareInput(bitmap: Bitmap): OnnxTensor {
        val pixels = IntArray(inputSize * inputSize)
        bitmap.getPixels(pixels, 0, inputSize, 0, 0, inputSize, inputSize)
        val floatBuffer = FloatBuffer.allocate(1 * 3 * inputSize * inputSize)

        for (y in 0 until inputSize) {
            for (x in 0 until inputSize) {
                val pixel = pixels[y * inputSize + x]
                val r = ((pixel shr 16) and 0xFF) / 255.0f
                val g = ((pixel shr 8) and 0xFF) / 255.0f
                val b = (pixel and 0xFF) / 255.0f
                floatBuffer.put(0 * inputSize * inputSize + y * inputSize + x, r)
                floatBuffer.put(1 * inputSize * inputSize + y * inputSize + x, g)
                floatBuffer.put(2 * inputSize * inputSize + y * inputSize + x, b)
            }
        }

        return OnnxTensor.createTensor(
            ortEnvironment,
            floatBuffer,
            longArrayOf(1, 3, inputSize.toLong(), inputSize.toLong())
        )
    }

    private fun processOutputTensor(tensor: OnnxTensor, shape: LongArray): DetectionResult {
        val floatBuffer = tensor.floatBuffer

        if (shape.size == 3) {
            val features = shape[1].toInt()
            val boxes = shape[2].toInt()

            if (features == 21 || features == 22) {
                return processYoloTransposed(floatBuffer, features, boxes)
            } else if (boxes == 21 || boxes == 22) {
                return processYoloStandard(floatBuffer, features, boxes)
            }
        }
        return DetectionResult(false, 0f)
    }

    private fun processYoloTransposed(buffer: FloatBuffer, features: Int, boxes: Int): DetectionResult {
        val numClasses = features - 4
        var maxNsfwScore = 0f
        for (b in 0 until boxes) {
            var maxScore = 0f
            var maxClassIdx = -1
            for (c in 0 until numClasses) {
                val idx = (4 + c) * boxes + b
                if (idx < buffer.capacity()) {
                    val score = buffer.get(idx)
                    if (score > maxScore) {
                        maxScore = score
                        maxClassIdx = c
                    }
                }
            }
            if (maxClassIdx in ignoreClasses) continue
            if (maxClassIdx in explicitClasses && maxScore > explicitThreshold && maxScore > maxNsfwScore) {
                maxNsfwScore = maxScore
            } else if (maxClassIdx in moderateClasses && maxScore > breastThreshold && maxScore > maxNsfwScore) {
                maxNsfwScore = maxScore
            }
        }
        return DetectionResult(maxNsfwScore > 0f, maxNsfwScore)
    }

    private fun processYoloStandard(buffer: FloatBuffer, numBoxes: Int, features: Int): DetectionResult {
        val numClasses = features - 4
        var maxNsfwScore = 0f
        for (b in 0 until numBoxes) {
            var maxScore = 0f
            var maxClassIdx = -1
            for (c in 0 until numClasses) {
                val idx = b * features + 4 + c
                if (idx < buffer.capacity()) {
                    val score = buffer.get(idx)
                    if (score > maxScore) {
                        maxScore = score
                        maxClassIdx = c
                    }
                }
            }
            if (maxClassIdx in ignoreClasses) continue
            if (maxClassIdx in explicitClasses && maxScore > explicitThreshold && maxScore > maxNsfwScore) {
                maxNsfwScore = maxScore
            } else if (maxClassIdx in moderateClasses && maxScore > breastThreshold && maxScore > maxNsfwScore) {
                maxNsfwScore = maxScore
            }
        }
        return DetectionResult(maxNsfwScore > 0f, maxNsfwScore)
    }

    fun close() {
        try {
            ortSession?.close()
            ortEnvironment?.close()
        } catch (_: Exception) {}
    }
}
