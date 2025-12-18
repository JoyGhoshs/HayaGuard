package com.hayaguard.app

import android.app.Application
import android.os.Process
import android.util.Log

class HayaGuardApplication : Application() {

    companion object {
        private const val TAG = "HayaGuardApp"
        
        @Volatile
        private var instance: HayaGuardApplication? = null
        
        fun getInstance(): HayaGuardApplication? = instance
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        
        Thread.currentThread().priority = Thread.MAX_PRIORITY
        
        warmupCronet()
        warmupDns()
        
        Log.d(TAG, "Application initialized with warm network stack")
    }

    private fun warmupCronet() {
        Thread {
            Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND)
            try {
                CronetHelper.initialize(this@HayaGuardApplication)
                Log.d(TAG, "Cronet warmed up in background")
            } catch (e: Exception) {
                Log.e(TAG, "Cronet warmup failed: ${e.message}")
            }
        }.start()
    }

    private fun warmupDns() {
        Thread {
            Process.setThreadPriority(Process.THREAD_PRIORITY_LOWEST)
            try {
                DnsWarmer.warmUp()
                Log.d(TAG, "DNS warmed up in background")
            } catch (e: Exception) {
                Log.e(TAG, "DNS warmup failed: ${e.message}")
            }
        }.start()
    }
}
