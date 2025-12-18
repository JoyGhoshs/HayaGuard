package com.hayaguard.app

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.support.common.ops.NormalizeOp
import org.tensorflow.lite.support.image.ImageProcessor
import org.tensorflow.lite.support.image.TensorImage
import org.tensorflow.lite.support.image.ops.ResizeOp
import java.io.FileInputStream
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel

class GenderClassifier(context: Context) {

    companion object {
        private const val TAG = "GenderClassifier"
        private const val MODEL_FILE = "model_gender_q.tflite"
    }

    private var interpreter: Interpreter? = null
    private val inputImageSize = 128

    private val inputImageProcessor = ImageProcessor.Builder()
        .add(ResizeOp(inputImageSize, inputImageSize, ResizeOp.ResizeMethod.BILINEAR))
        .add(NormalizeOp(0f, 255f))
        .build()

    enum class Gender {
        MALE,
        FEMALE,
        UNKNOWN
    }

    data class GenderResult(
        val gender: Gender,
        val confidence: Float
    )

    init {
        try {
            val model = loadModelFile(context, MODEL_FILE)
            val options = Interpreter.Options().apply {
                setNumThreads(4)
            }
            interpreter = Interpreter(model, options)
            Log.d(TAG, "Gender classifier initialized with $MODEL_FILE")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load gender model: ${e.message}")
        }
    }

    private fun loadModelFile(context: Context, modelPath: String): MappedByteBuffer {
        val assetFileDescriptor = context.assets.openFd(modelPath)
        val fileInputStream = FileInputStream(assetFileDescriptor.fileDescriptor)
        val fileChannel = fileInputStream.channel
        val startOffset = assetFileDescriptor.startOffset
        val declaredLength = assetFileDescriptor.declaredLength
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)
    }

    fun classify(faceBitmap: Bitmap): GenderResult {
        if (interpreter == null) {
            Log.e(TAG, "Interpreter not initialized")
            return GenderResult(Gender.UNKNOWN, 0f)
        }

        return try {
            val tensorInputImage = TensorImage.fromBitmap(faceBitmap)
            val genderOutputArray = Array(1) { FloatArray(2) }
            val processedImageBuffer = inputImageProcessor.process(tensorInputImage).buffer

            interpreter?.run(processedImageBuffer, genderOutputArray)

            val maleProbability = genderOutputArray[0][0]
            val femaleProbability = genderOutputArray[0][1]

            Log.d(TAG, "Gender prediction - Male: $maleProbability, Female: $femaleProbability")

            if (maleProbability > femaleProbability) {
                GenderResult(Gender.MALE, maleProbability)
            } else {
                GenderResult(Gender.FEMALE, femaleProbability)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Classification error: ${e.message}")
            GenderResult(Gender.UNKNOWN, 0f)
        }
    }

    fun close() {
        interpreter?.close()
    }
}
