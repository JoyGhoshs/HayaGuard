package com.hayaguard.app

import android.content.Context
import android.content.SharedPreferences
import java.util.Calendar
import java.util.concurrent.atomic.AtomicLong

object StatsTracker {

    private const val PREFS_NAME = "hayaguard_stats"
    private const val KEY_NSFW_BLOCKED = "nsfw_blocked_count"
    private const val KEY_TIME_SPENT_MS = "time_spent_ms"
    private const val KEY_SESSION_START = "session_start"
    private const val KEY_SPONSORED_REMOVED = "sponsored_removed_count"
    private const val KEY_LAST_RESET_DATE = "last_reset_date"

    private lateinit var prefs: SharedPreferences
    private val sessionNsfwBlocked = AtomicLong(0)
    private val sessionTimeMs = AtomicLong(0)
    private val sessionSponsoredRemoved = AtomicLong(0)
    private var sessionStartTime: Long = 0
    private var isSessionActive = false

    fun initialize(context: Context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        checkAndResetDaily()
    }

    private fun checkAndResetDaily() {
        val today = getTodayDateString()
        val lastResetDate = prefs.getString(KEY_LAST_RESET_DATE, "") ?: ""
        if (lastResetDate != today) {
            prefs.edit()
                .putLong(KEY_TIME_SPENT_MS, 0)
                .putString(KEY_LAST_RESET_DATE, today)
                .apply()
            sessionTimeMs.set(0)
        }
    }

    private fun getTodayDateString(): String {
        val cal = Calendar.getInstance()
        return "${cal.get(Calendar.YEAR)}-${cal.get(Calendar.MONTH)}-${cal.get(Calendar.DAY_OF_MONTH)}"
    }

    fun startSession() {
        checkAndResetDaily()
        if (!isSessionActive) {
            sessionStartTime = System.currentTimeMillis()
            isSessionActive = true
        }
    }

    fun pauseSession() {
        if (isSessionActive && sessionStartTime > 0) {
            val elapsed = System.currentTimeMillis() - sessionStartTime
            sessionTimeMs.addAndGet(elapsed)
            isSessionActive = false
        }
    }

    fun resumeSession() {
        if (!isSessionActive) {
            sessionStartTime = System.currentTimeMillis()
            isSessionActive = true
        }
    }

    fun endSession() {
        pauseSession()
        saveStats()
        sessionNsfwBlocked.set(0)
        sessionTimeMs.set(0)
        sessionSponsoredRemoved.set(0)
    }

    fun incrementNsfwBlocked() {
        sessionNsfwBlocked.incrementAndGet()
    }

    fun incrementSponsoredRemoved() {
        sessionSponsoredRemoved.incrementAndGet()
    }

    fun getSponsoredRemovedCount(): Long {
        return prefs.getLong(KEY_SPONSORED_REMOVED, 0) + sessionSponsoredRemoved.get()
    }

    fun getFormattedSponsoredRemoved(): String {
        val count = getSponsoredRemovedCount()
        return when {
            count >= 1000000 -> String.format("%.1fM", count / 1000000.0)
            count >= 1000 -> String.format("%.1fK", count / 1000.0)
            else -> count.toString()
        }
    }

    fun getNsfwBlockedCount(): Long {
        return prefs.getLong(KEY_NSFW_BLOCKED, 0) + sessionNsfwBlocked.get()
    }

    fun getTimeSpentMs(): Long {
        var total = prefs.getLong(KEY_TIME_SPENT_MS, 0) + sessionTimeMs.get()
        if (isSessionActive && sessionStartTime > 0) {
            total += System.currentTimeMillis() - sessionStartTime
        }
        return total
    }

    fun getFormattedTimeSpent(): String {
        val totalMs = getTimeSpentMs()
        val totalSeconds = totalMs / 1000
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60

        return when {
            hours > 0 -> String.format("%dh %dm %ds", hours, minutes, seconds)
            minutes > 0 -> String.format("%dm %ds", minutes, seconds)
            else -> String.format("%ds", seconds)
        }
    }

    fun getFormattedNsfwBlocked(): String {
        val count = getNsfwBlockedCount()
        return when {
            count >= 1000000 -> String.format("%.1fM", count / 1000000.0)
            count >= 1000 -> String.format("%.1fK", count / 1000.0)
            else -> count.toString()
        }
    }

    private fun saveStats() {
        prefs.edit()
            .putLong(KEY_NSFW_BLOCKED, prefs.getLong(KEY_NSFW_BLOCKED, 0) + sessionNsfwBlocked.get())
            .putLong(KEY_TIME_SPENT_MS, prefs.getLong(KEY_TIME_SPENT_MS, 0) + sessionTimeMs.get())
            .putLong(KEY_SPONSORED_REMOVED, prefs.getLong(KEY_SPONSORED_REMOVED, 0) + sessionSponsoredRemoved.get())
            .apply()
    }

    fun resetAllStats() {
        prefs.edit().clear().apply()
        sessionNsfwBlocked.set(0)
        sessionTimeMs.set(0)
        sessionSponsoredRemoved.set(0)
    }

    fun resetDailyTime() {
        prefs.edit().putLong(KEY_TIME_SPENT_MS, 0).apply()
        sessionTimeMs.set(0)
        if (isSessionActive) {
            sessionStartTime = System.currentTimeMillis()
        }
    }
}
