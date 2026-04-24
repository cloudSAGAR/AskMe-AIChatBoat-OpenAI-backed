package com.example.askmechat.presentation.chat.widget

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.util.AttributeSet
import android.view.View
import android.view.animation.LinearInterpolator

/**
 * Four-second shimmer overlay used on suggestion chips.
 */
class SuggestionChipShimmerView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val baseDark = 0xFF2C3740.toInt()
    private val shimmerLight = 0xFF3A4650.toInt()
    private val shimmerHighlight = 0xFF4A5660.toInt()

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var shimmerTranslation = 0f
    private var shimmerAnimator: ValueAnimator? = null
    private val clipPath = Path()
    private val rectF = RectF()
    private var cornerRadius = 0f

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w > 0) {
            cornerRadius = 20f * resources.displayMetrics.density
            rectF.set(0f, 0f, w.toFloat(), h.toFloat())
            clipPath.reset()
            clipPath.addRoundRect(rectF, cornerRadius, cornerRadius, Path.Direction.CW)
            startShimmer(w.toFloat())
        }
    }

    private fun startShimmer(width: Float) {
        shimmerAnimator?.cancel()
        // Continuous loop — each sweep takes ~1.2s, restarts immediately.
        // The adapter decides when to stop it (input focus → setShimmerActive(false)).
        shimmerAnimator = ValueAnimator.ofFloat(-width, width * 2).apply {
            duration = 4500L
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.RESTART
            interpolator = LinearInterpolator()
            addUpdateListener {
                shimmerTranslation = it.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (width == 0 || height == 0) return

        val shimmerWidth = width * 0.6f

        paint.shader = LinearGradient(
            shimmerTranslation, 0f,
            shimmerTranslation + shimmerWidth, 0f,
            intArrayOf(baseDark, shimmerLight, shimmerHighlight, shimmerLight, baseDark),
            floatArrayOf(0f, 0.3f, 0.5f, 0.7f, 1f),
            Shader.TileMode.CLAMP
        )

        canvas.save()
        canvas.clipPath(clipPath)
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
        canvas.restore()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        shimmerAnimator?.cancel()
        shimmerAnimator = null
    }
}
