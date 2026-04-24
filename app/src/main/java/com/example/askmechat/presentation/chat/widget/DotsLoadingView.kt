package com.example.askmechat.presentation.chat.widget

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import androidx.core.content.ContextCompat
import com.example.askmechat.R

/**
 * Animated "dots bouncing wave" loading indicator.
 * Used inside AI bubbles while the first stream chunk is awaited.
 */
class DotsLoadingView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0
) : View(context, attrs, defStyle) {

    companion object {
        private const val DEFAULT_DOT_COUNT = 6
        private const val DEFAULT_DOT_SIZE = 11f
        private const val DEFAULT_SPACING = 19f
        private const val DEFAULT_WAVE_HEIGHT = 11f
        private const val DEFAULT_DURATION = 600L
    }

    var dotCount = DEFAULT_DOT_COUNT
        set(value) { field = value; invalidate() }

    var dotSize = DEFAULT_DOT_SIZE
        set(value) { field = value; invalidate() }

    var spacing = DEFAULT_SPACING
        set(value) { field = value; invalidate() }

    var waveHeight = DEFAULT_WAVE_HEIGHT
        set(value) { field = value; invalidate() }

    var duration = DEFAULT_DURATION
        set(value) { field = value; invalidate() }

    var dotColor = ContextCompat.getColor(context, R.color.color_dot)
        set(value) { field = value; paint.color = value; invalidate() }

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = dotColor
        style = Paint.Style.FILL
    }

    private val dotsY = MutableList(dotCount) { 0f }
    private val animators = mutableListOf<ValueAnimator>()
    private var isAnimating = false
    private var isVisibleDots = false

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val totalWidth = dotCount * dotSize + (dotCount - 1) * spacing
        val desiredWidth = totalWidth.toInt() + paddingStart + paddingEnd
        val desiredHeight = (dotSize * 3).toInt() + paddingTop + paddingBottom
        setMeasuredDimension(desiredWidth, desiredHeight)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (!isVisibleDots) return

        val startX = (width - totalWidth()) / 2f
        val centerY = height / 2f

        for (i in 0 until dotCount) {
            val x = startX + i * (dotSize + spacing)
            val y = centerY + dotsY[i]
            canvas.drawCircle(x, y, dotSize / 2f, paint)
        }
    }

    private fun totalWidth(): Float =
        dotCount * dotSize + (dotCount - 1) * spacing

    fun startAnimating() {
        if (isAnimating) return
        isAnimating = true
        isVisibleDots = true
        invalidate()

        animators.clear()
        for (i in 0 until dotCount) {
            // ValueAnimator (not ObjectAnimator) — the dots array is
            // updated directly in addUpdateListener, so there's nothing
            // for reflection to target. Using ObjectAnimator with a
            // property name triggered a noisy
            // "Method setDotYn() with type float not found" log.
            val animator = ValueAnimator.ofFloat(0f, -waveHeight, 0f).apply {
                duration = this@DotsLoadingView.duration
                startDelay = (i * (duration / dotCount))
                repeatCount = ValueAnimator.INFINITE
                repeatMode = ValueAnimator.RESTART
                interpolator = AccelerateDecelerateInterpolator()
                addUpdateListener {
                    dotsY[i] = it.animatedValue as Float
                    invalidate()
                }
            }
            animators.add(animator)
        }
        animators.forEach { it.start() }
    }

    fun stopAnimating() {
        if (!isAnimating) return
        isAnimating = false
        animators.forEach { it.cancel() }
        animators.clear()
        for (i in 0 until dotCount) dotsY[i] = 0f
        isVisibleDots = false
        invalidate()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        stopAnimating()
    }
}
