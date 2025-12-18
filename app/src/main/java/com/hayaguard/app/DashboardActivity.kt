package com.hayaguard.app

import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.ImageButton
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat

class DashboardActivity : AppCompatActivity() {

    private lateinit var btnHome: ImageButton
    private lateinit var btnRefresh: ImageButton
    private lateinit var tvNsfwBlockedCount: TextView
    private lateinit var tvTimeSpent: TextView
    private lateinit var tvTrackersBlocked: TextView
    private lateinit var tvDeviceTier: TextView
    private lateinit var tvAiResolution: TextView
    private lateinit var tvGpuStatus: TextView
    private lateinit var btnResetStats: Button
    private lateinit var pieChart: PieChartView
    private lateinit var tvFamilyPercent: TextView
    private lateinit var tvPoliticalPercent: TextView
    private lateinit var tvViralPercent: TextView
    private lateinit var tvNewsPercent: TextView
    private lateinit var tvSponsoredPercent: TextView
    private lateinit var tvSponsoredRemoved: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_dashboard)

        WindowCompat.setDecorFitsSystemWindows(window, true)
        WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightStatusBars = true

        initViews()
        setupClickListeners()
        setupBackPressHandler()
        loadStats()
    }

    private fun initViews() {
        btnHome = findViewById(R.id.btnHome)
        btnRefresh = findViewById(R.id.btnRefresh)
        tvNsfwBlockedCount = findViewById(R.id.tvNsfwBlockedCount)
        tvTimeSpent = findViewById(R.id.tvTimeSpent)
        tvTrackersBlocked = findViewById(R.id.tvTrackersBlocked)
        tvDeviceTier = findViewById(R.id.tvDeviceTier)
        tvAiResolution = findViewById(R.id.tvAiResolution)
        tvGpuStatus = findViewById(R.id.tvGpuStatus)
        btnResetStats = findViewById(R.id.btnResetStats)
        pieChart = findViewById(R.id.pieChart)
        tvFamilyPercent = findViewById(R.id.tvFamilyPercent)
        tvPoliticalPercent = findViewById(R.id.tvPoliticalPercent)
        tvViralPercent = findViewById(R.id.tvViralPercent)
        tvNewsPercent = findViewById(R.id.tvNewsPercent)
        tvSponsoredPercent = findViewById(R.id.tvSponsoredPercent)
        tvSponsoredRemoved = findViewById(R.id.tvSponsoredRemoved)
    }

    private fun setupClickListeners() {
        btnHome.setOnClickListener {
            finish()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                overrideActivityTransition(OVERRIDE_TRANSITION_CLOSE, R.anim.slide_in_left, R.anim.slide_out_right)
            } else {
                @Suppress("DEPRECATION")
                overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right)
            }
        }

        btnRefresh.setOnClickListener {
            loadStats()
        }

        btnResetStats.setOnClickListener {
            showResetConfirmationDialog()
        }
    }

    private fun loadStats() {
        tvNsfwBlockedCount.text = StatsTracker.getFormattedNsfwBlocked()
        tvTimeSpent.text = StatsTracker.getFormattedTimeSpent()
        tvTrackersBlocked.text = formatTrackerCount(TrackerBlocker.getBlockedCount())
        tvSponsoredRemoved.text = StatsTracker.getFormattedSponsoredRemoved()

        loadDeviceInfo()
        loadFeedDna()
    }

    private fun loadFeedDna() {
        val familyPercent = FeedAnalyzer.getFamilyFriendsPercentage()
        val politicalPercent = FeedAnalyzer.getPoliticalToxicPercentage()
        val viralPercent = FeedAnalyzer.getViralJunkPercentage()
        val newsPercent = FeedAnalyzer.getNewsInfoPercentage()
        val sponsoredPercent = FeedAnalyzer.getSponsoredPercentage()

        tvFamilyPercent.text = String.format("%.0f%%", familyPercent)
        tvPoliticalPercent.text = String.format("%.0f%%", politicalPercent)
        tvViralPercent.text = String.format("%.0f%%", viralPercent)
        tvNewsPercent.text = String.format("%.0f%%", newsPercent)
        tvSponsoredPercent.text = String.format("%.0f%%", sponsoredPercent)

        val slices = listOf(
            PieChartView.Slice(FeedAnalyzer.getFamilyFriendsCount().toFloat(), 0xFF34C759.toInt(), "Family/Friends"),
            PieChartView.Slice(FeedAnalyzer.getPoliticalToxicCount().toFloat(), 0xFFFF3B30.toInt(), "Political/Toxic"),
            PieChartView.Slice(FeedAnalyzer.getViralJunkCount().toFloat(), 0xFFFF9500.toInt(), "Viral/Junk"),
            PieChartView.Slice(FeedAnalyzer.getNewsInfoCount().toFloat(), 0xFF1877F2.toInt(), "News/Info"),
            PieChartView.Slice(FeedAnalyzer.getSponsoredCount().toFloat(), 0xFF8E8E93.toInt(), "Sponsored")
        )
        pieChart.setData(slices)
    }

    private fun loadDeviceInfo() {
        val tier = DeviceCapabilityProfiler.getTier()
        tvDeviceTier.text = tier.name.replace("_", " ")
        tvDeviceTier.setTextColor(getTierColor(tier))

        val resolution = AdaptivePerformanceEngine.getNsfwjsInputSize()
        tvAiResolution.text = "${resolution}x${resolution}"

        val gpuEnabled = DeviceCapabilityProfiler.isGpuAvailable()
        tvGpuStatus.text = if (gpuEnabled) "Enabled" else "Disabled"
        tvGpuStatus.setTextColor(if (gpuEnabled) 0xFF34C759.toInt() else 0xFF8E8E93.toInt())
    }

    private fun getTierColor(tier: DeviceTier): Int {
        return when (tier) {
            DeviceTier.LOW_END -> 0xFFFF9500.toInt()
            DeviceTier.MID_RANGE -> 0xFF1877F2.toInt()
            DeviceTier.HIGH_END -> 0xFF34C759.toInt()
        }
    }

    private fun formatTrackerCount(count: Long): String {
        return when {
            count >= 1000000 -> String.format("%.1fM", count / 1000000.0)
            count >= 1000 -> String.format("%.1fK", count / 1000.0)
            else -> count.toString()
        }
    }

    private fun showResetConfirmationDialog() {
        AlertDialog.Builder(this)
            .setTitle("Reset Statistics")
            .setMessage("This will reset all tracked statistics. This action cannot be undone.")
            .setPositiveButton("Reset") { _, _ ->
                StatsTracker.resetAllStats()
                TrackerBlocker.resetBlockedCount()
                FeedAnalyzer.resetAll()
                loadStats()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun setupBackPressHandler() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                finish()
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    overrideActivityTransition(OVERRIDE_TRANSITION_CLOSE, R.anim.slide_in_left, R.anim.slide_out_right)
                } else {
                    @Suppress("DEPRECATION")
                    overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right)
                }
            }
        })
    }
}
