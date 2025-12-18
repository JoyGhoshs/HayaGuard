package com.hayaguard.app

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.os.Process
import android.util.Log
import org.tensorflow.lite.gpu.CompatibilityList
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.asCoroutineDispatcher
import java.util.concurrent.Executors
import java.util.concurrent.ThreadFactory
import java.util.concurrent.atomic.AtomicInteger

enum class DeviceTier {
    LOW_END,
    MID_RANGE,
    HIGH_END
}

object DeviceCapabilityProfiler {

    private const val TAG = "DeviceProfiler"
    private var cachedTier: DeviceTier? = null
    private var cachedScore: Int = -1
    private var gpuAvailable: Boolean? = null

    fun profile(context: Context): DeviceTier {
        cachedTier?.let { return it }

        val ramScore = calculateRamScore(context)
        val cpuScore = calculateCpuScore()
        val gpuScore = calculateGpuScore()

        val totalScore = ramScore + cpuScore + gpuScore
        cachedScore = totalScore

        val tier = when {
            totalScore >= 8 -> DeviceTier.HIGH_END
            totalScore >= 5 -> DeviceTier.MID_RANGE
            else -> DeviceTier.LOW_END
        }

        cachedTier = tier
        Log.d(TAG, "Device profiled: tier=$tier, score=$totalScore (RAM=$ramScore, CPU=$cpuScore, GPU=$gpuScore)")
        return tier
    }

    fun getTier(): DeviceTier {
        return cachedTier ?: DeviceTier.MID_RANGE
    }

    fun getPerformanceScore(): Int {
        return if (cachedScore >= 0) cachedScore else 5
    }

    fun isGpuAvailable(): Boolean {
        return gpuAvailable ?: false
    }

    private fun calculateRamScore(context: Context): Int {
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memInfo = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memInfo)
        val totalRamGB = memInfo.totalMem / (1024.0 * 1024.0 * 1024.0)

        return when {
            totalRamGB >= 8.0 -> 4
            totalRamGB >= 6.0 -> 3
            totalRamGB >= 4.0 -> 2
            totalRamGB >= 3.0 -> 1
            else -> 0
        }
    }

    private fun calculateCpuScore(): Int {
        val cores = Runtime.getRuntime().availableProcessors()

        return when {
            cores >= 8 -> 4
            cores >= 6 -> 3
            cores >= 4 -> 2
            else -> 1
        }
    }

    private fun calculateGpuScore(): Int {
        return try {
            val compatList = CompatibilityList()
            val isSupported = compatList.isDelegateSupportedOnThisDevice
            gpuAvailable = isSupported
            if (isSupported) 2 else 0
        } catch (e: Exception) {
            gpuAvailable = false
            0
        }
    }

    fun reset() {
        cachedTier = null
        cachedScore = -1
        gpuAvailable = null
    }
}

object AdaptivePerformanceEngine {

    private const val TAG = "AdaptiveEngine"

    private var processingDispatcher: CoroutineDispatcher? = null
    private var executorService: java.util.concurrent.ExecutorService? = null

    private class LowPriorityThreadFactory(private val namePrefix: String) : ThreadFactory {
        private val threadNumber = AtomicInteger(1)
        
        override fun newThread(r: Runnable): Thread {
            return Thread({
                Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND + Process.THREAD_PRIORITY_MORE_FAVORABLE)
                r.run()
            }, "$namePrefix-${threadNumber.getAndIncrement()}").apply {
                isDaemon = true
                priority = Thread.MIN_PRIORITY
            }
        }
    }

    fun initialize(context: Context) {
        val tier = DeviceCapabilityProfiler.profile(context)
        setupDispatcher(tier)
        Log.d(TAG, "Engine initialized for $tier: inputSize=${getInputSize()}, threads=${getThreadCount()}, timeout=${getTimeoutMs()}ms")
    }

    private fun setupDispatcher(tier: DeviceTier) {
        executorService?.shutdown()

        val threadCount = getThreadCount(tier)
        executorService = Executors.newFixedThreadPool(threadCount, LowPriorityThreadFactory("HayaGuard-AI"))
        processingDispatcher = executorService!!.asCoroutineDispatcher()
    }

    fun getDispatcher(): CoroutineDispatcher {
        return processingDispatcher ?: kotlinx.coroutines.Dispatchers.Default
    }

    fun getInputSize(): Int {
        return getInputSize(DeviceCapabilityProfiler.getTier())
    }

    fun getInputSize(tier: DeviceTier): Int {
        return when (tier) {
            DeviceTier.LOW_END -> 128
            DeviceTier.MID_RANGE -> 224
            DeviceTier.HIGH_END -> 300
        }
    }

    fun getNsfwjsInputSize(): Int {
        return when (DeviceCapabilityProfiler.getTier()) {
            DeviceTier.LOW_END -> 128
            DeviceTier.MID_RANGE -> 224
            DeviceTier.HIGH_END -> 224
        }
    }

    fun getNudeNetInputSize(): Int {
        return when (DeviceCapabilityProfiler.getTier()) {
            DeviceTier.LOW_END -> 160
            DeviceTier.MID_RANGE -> 320
            DeviceTier.HIGH_END -> 320
        }
    }

    fun getThreadCount(): Int {
        return getThreadCount(DeviceCapabilityProfiler.getTier())
    }

    fun getThreadCount(tier: DeviceTier): Int {
        return when (tier) {
            DeviceTier.LOW_END -> 1
            DeviceTier.MID_RANGE -> 2
            DeviceTier.HIGH_END -> 4
        }
    }

    fun getTimeoutMs(): Long {
        return getTimeoutMs(DeviceCapabilityProfiler.getTier())
    }

    fun getTimeoutMs(tier: DeviceTier): Long {
        return when (tier) {
            DeviceTier.LOW_END -> 2000L
            DeviceTier.MID_RANGE -> 1500L
            DeviceTier.HIGH_END -> 1000L
        }
    }

    fun getTimeoutFallbackAction(): TimeoutFallbackAction {
        return when (DeviceCapabilityProfiler.getTier()) {
            DeviceTier.LOW_END -> TimeoutFallbackAction.SHOW
            DeviceTier.MID_RANGE -> TimeoutFallbackAction.BLUR
            DeviceTier.HIGH_END -> TimeoutFallbackAction.BLUR
        }
    }

    fun getCpuThreadsForInterpreter(): Int {
        return when (DeviceCapabilityProfiler.getTier()) {
            DeviceTier.LOW_END -> 2
            DeviceTier.MID_RANGE -> 3
            DeviceTier.HIGH_END -> 4
        }
    }

    fun shouldUseGpu(): Boolean {
        return DeviceCapabilityProfiler.isGpuAvailable()
    }

    fun shutdown() {
        executorService?.shutdown()
        executorService = null
        processingDispatcher = null
    }

    enum class TimeoutFallbackAction {
        BLUR,
        SHOW
    }
}
