package com.example.askmechat.presentation.splash

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.view.animation.LinearInterpolator
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.askmechat.MainActivity
import com.example.askmechat.databinding.ActivitySplashBinding
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Launch screen shown on cold start.
 *
 * Responsibilities (presentation-only):
 *   • Render a branded hero: sparkle → "AskMe AI" gradient title →
 *     tagline → wave-dots loader.
 *   • Render the Cloud SAGAR / GIT PROJECTS watermark at the bottom.
 *   • Play staggered entrance animations + an idle sparkle
 *     rotation/breathing loop.
 *   • After [SPLASH_DURATION_MS], fade-transition into [MainActivity].
 *
 * The window background is the chat root gradient (via
 * `Theme.AskMeChat.Splash`) so there's no white flash between app launch
 * and the first composed frame.
 */
class SplashActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySplashBinding
    private val runningAnimators = mutableListOf<Any>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySplashBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.dotsLoading.startAnimating()
        playEntranceAnimations()
        startSparkleIdleAnimation()
        scheduleNavigation()
    }

    // ── Entrance ────────────────────────────────────────────────────

    private fun playEntranceAnimations() {
        val targets = listOf(
            Triple(binding.ivSparkle, 80L, 40f),
            Triple(binding.tvTitle, 260L, 32f),
            Triple(binding.tvTagline, 460L, 24f),
            Triple(binding.dotsLoading, 640L, 20f),
            Triple(binding.dividerBrand, 820L, 16f),
            Triple(binding.llBrandLogo, 900L, 16f)
        )
        targets.forEach { (view, delay, translate) ->
            view.alpha = 0f
            view.translationY = translate
            view.animate()
                .alpha(1f)
                .translationY(0f)
                .setStartDelay(delay)
                .setDuration(560L)
                .setInterpolator(DecelerateInterpolator(2f))
                .start()
        }
    }

    // ── Sparkle idle ────────────────────────────────────────────────

    private fun startSparkleIdleAnimation() {
        val sparkle = binding.ivSparkle

        val rotator = ObjectAnimator.ofFloat(sparkle, View.ROTATION, 0f, 360f).apply {
            duration = 9000L
            repeatCount = ValueAnimator.INFINITE
            interpolator = LinearInterpolator()
            start()
        }
        runningAnimators.add(rotator)

        val breath = ValueAnimator.ofFloat(0.92f, 1.1f).apply {
            duration = 1600L
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.REVERSE
            interpolator = AccelerateDecelerateInterpolator()
            addUpdateListener {
                val s = it.animatedValue as Float
                sparkle.scaleX = s
                sparkle.scaleY = s
            }
            start()
        }
        runningAnimators.add(breath)
    }

    // ── Navigation ──────────────────────────────────────────────────

    private fun scheduleNavigation() {
        lifecycleScope.launch {
            delay(SPLASH_DURATION_MS)
            if (!isFinishing && !isDestroyed) goToMain()
        }
    }

    private fun goToMain() {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        startActivity(intent)
        // Smooth fade between splash and chat — avoids the default
        // slide-in which feels jarring for a launch screen.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            overrideActivityTransition(
                OVERRIDE_TRANSITION_OPEN,
                android.R.anim.fade_in,
                android.R.anim.fade_out
            )
        } else {
            @Suppress("DEPRECATION")
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
        }
        finish()
    }

    override fun onDestroy() {
        super.onDestroy()
        runningAnimators.forEach {
            when (it) {
                is ObjectAnimator -> it.cancel()
                is ValueAnimator -> it.cancel()
            }
        }
        runningAnimators.clear()
        binding.dotsLoading.stopAnimating()
    }

    companion object {
        /** Long enough for the sparkle to breathe once + gradient to sweep ~3/4. */
        private const val SPLASH_DURATION_MS: Long = 2200L
    }
}
