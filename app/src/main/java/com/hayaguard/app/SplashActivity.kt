package com.hayaguard.app

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.AlphaAnimation
import android.view.animation.AnimationSet
import android.view.animation.ScaleAnimation
import androidx.appcompat.app.AppCompatActivity
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen

@SuppressLint("CustomSplashScreen")
class SplashActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        
        setContentView(R.layout.activity_splash)
        
        val logo = findViewById<View>(R.id.splashLogo)
        val text = findViewById<View>(R.id.craftedByText)
        
        val scaleAnim = ScaleAnimation(
            0.5f, 1f, 0.5f, 1f,
            ScaleAnimation.RELATIVE_TO_SELF, 0.5f,
            ScaleAnimation.RELATIVE_TO_SELF, 0.5f
        )
        scaleAnim.duration = 800
        scaleAnim.interpolator = AccelerateDecelerateInterpolator()
        
        val fadeAnim = AlphaAnimation(0f, 1f)
        fadeAnim.duration = 800
        
        val animSet = AnimationSet(true)
        animSet.addAnimation(scaleAnim)
        animSet.addAnimation(fadeAnim)
        
        logo.startAnimation(animSet)
        
        val textFade = AlphaAnimation(0f, 1f)
        textFade.duration = 600
        textFade.startOffset = 400
        text.startAnimation(textFade)
        
        Handler(Looper.getMainLooper()).postDelayed({
            startActivity(Intent(this, MainActivity::class.java))
            finish()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                overrideActivityTransition(OVERRIDE_TRANSITION_CLOSE, android.R.anim.fade_in, android.R.anim.fade_out)
            } else {
                @Suppress("DEPRECATION")
                overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
            }
        }, 2000)
    }
}
