package com.hayaguard.app

import android.content.Context
import android.util.Log
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.gpu.CompatibilityList
import org.tensorflow.lite.gpu.GpuDelegate
import java.io.FileInputStream
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel

object TFLiteInterpreterFactory {

    private const val TAG = "TFLiteFactory"

    enum class DelegateType {
        GPU, NNAPI, CPU
    }

    data class InterpreterResult(
        val interpreter: Interpreter,
        val delegateType: DelegateType,
        val gpuDelegate: GpuDelegate?
    )

    @Volatile
    private var cachedResult: InterpreterResult? = null
    private val lock = Any()

    fun createInterpreter(context: Context, modelName: String): InterpreterResult {
        synchronized(lock) {
            cachedResult?.let { return it }

            val modelBuffer = loadModelFile(context, modelName)
            val result = tryCreateWithFallback(modelBuffer)
            cachedResult = result
            Log.d(TAG, "Initialized with ${result.delegateType}")
            return result
        }
    }

    private fun tryCreateWithFallback(modelBuffer: MappedByteBuffer): InterpreterResult {
        val compatList = CompatibilityList()
        
        if (compatList.isDelegateSupportedOnThisDevice) {
            try {
                val gpuDelegate = GpuDelegate(compatList.bestOptionsForThisDevice)
                val options = Interpreter.Options().apply {
                    addDelegate(gpuDelegate)
                }
                val interpreter = Interpreter(modelBuffer, options)
                return InterpreterResult(interpreter, DelegateType.GPU, gpuDelegate)
            } catch (e: Exception) {
                Log.w(TAG, "GPU failed: ${e.message}")
            }
        }

        try {
            val options = Interpreter.Options().apply {
                setUseNNAPI(true)
            }
            val interpreter = Interpreter(modelBuffer, options)
            return InterpreterResult(interpreter, DelegateType.NNAPI, null)
        } catch (e: Exception) {
            Log.w(TAG, "NNAPI failed: ${e.message}")
        }

        val cpuThreads = AdaptivePerformanceEngine.getCpuThreadsForInterpreter()
        val options = Interpreter.Options().apply {
            setNumThreads(cpuThreads)
        }
        val interpreter = Interpreter(modelBuffer, options)
        return InterpreterResult(interpreter, DelegateType.CPU, null)
    }

    private fun loadModelFile(context: Context, modelName: String): MappedByteBuffer {
        val assetFileDescriptor = context.assets.openFd(modelName)
        val fileInputStream = FileInputStream(assetFileDescriptor.fileDescriptor)
        val fileChannel = fileInputStream.channel
        val startOffset = assetFileDescriptor.startOffset
        val declaredLength = assetFileDescriptor.declaredLength
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)
    }

    fun close() {
        synchronized(lock) {
            cachedResult?.let {
                it.gpuDelegate?.close()
                it.interpreter.close()
            }
            cachedResult = null
        }
    }
}
