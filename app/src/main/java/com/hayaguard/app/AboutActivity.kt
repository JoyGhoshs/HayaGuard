package com.hayaguard.app

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat

class AboutActivity : AppCompatActivity() {

    private lateinit var btnBack: ImageButton
    private lateinit var tvVersion: TextView
    private lateinit var layoutTwitter: LinearLayout
    private lateinit var layoutGithub: LinearLayout
    private lateinit var layoutEmail: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_about)

        WindowCompat.setDecorFitsSystemWindows(window, true)
        WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightStatusBars = true

        initViews()
        setupClickListeners()
        setupBackPressHandler()
        loadVersionInfo()
    }

    private fun initViews() {
        btnBack = findViewById(R.id.btnBack)
        tvVersion = findViewById(R.id.tvVersion)
        layoutTwitter = findViewById(R.id.layoutTwitter)
        layoutGithub = findViewById(R.id.layoutGithub)
        layoutEmail = findViewById(R.id.layoutEmail)
    }

    private fun setupClickListeners() {
        btnBack.setOnClickListener {
            finish()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                overrideActivityTransition(OVERRIDE_TRANSITION_CLOSE, R.anim.slide_in_left, R.anim.slide_out_right)
            } else {
                @Suppress("DEPRECATION")
                overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right)
            }
        }

        layoutTwitter.setOnClickListener {
            openUrl("https://x.com/0xrahmanmaheer")
        }

        layoutGithub.setOnClickListener {
            openUrl("https://github.com/system00-security")
        }

        layoutEmail.setOnClickListener {
            val intent = Intent(Intent.ACTION_SENDTO).apply {
                data = Uri.parse("mailto:arrahman.maheer@gmail.com")
                putExtra(Intent.EXTRA_SUBJECT, "HayaGuard Feedback")
            }
            startActivity(Intent.createChooser(intent, "Send Email"))
        }
    }

    private fun openUrl(url: String) {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
        startActivity(intent)
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

    private fun loadVersionInfo() {
        try {
            val packageInfo = packageManager.getPackageInfo(packageName, 0)
            val versionName = packageInfo.versionName
            val versionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                packageInfo.longVersionCode
            } else {
                @Suppress("DEPRECATION")
                packageInfo.versionCode.toLong()
            }
            tvVersion.text = "Version $versionName ($versionCode)"
        } catch (e: Exception) {
            tvVersion.text = "Version 1.0.0"
        }
    }
}
