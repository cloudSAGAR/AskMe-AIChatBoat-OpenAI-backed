package com.example.askmechat.presentation.chat.widget

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Shader
import android.util.AttributeSet
import android.view.animation.LinearInterpolator
import androidx.appcompat.widget.AppCompatTextView

/**
 * TextView that paints its glyphs with a moving gradient shader — the
 * signature "AI title" look seen in ChatGPT, Gemini, Pi etc.
 *
 * The gradient slides horizontally at a constant speed while the view is
 * attached; the animator is cancelled in `onDetachedFromWindow` so there
 * are no leaks or background work when the screen is not visible.
 */
class AnimatedGradientTextView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = android.R.attr.textViewStyle
) : AppCompatTextView(context, attrs, defStyleAttr) {

    private val gradientColors = intArrayOf(
        0xFFBB43FF.toInt(),   // violet
        0xFFE5C9FF.toInt(),   // light lavender
        0xFF9D2AED.toInt(),   // purple
        0xFF5A7DFF.toInt(),   // cool blue
        0xFFBB43FF.toInt()    // wrap back to violet
    )

    private var shader: LinearGradient? = null
    private val shaderMatrix = Matrix()
    private var translate = 0f
    private var animator: ValueAnimator? = null

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w <= 0) return
        shader = LinearGradient(
            0f, 0f,
            w.toFloat() * 2f, 0f,
            gradientColors,
            null,
            Shader.TileMode.MIRROR
        )
        paint.shader = shader
        startShift()
    }

    private fun startShift() {
        animator?.cancel()
        val width = width.toFloat()
        if (width <= 0f) return
        animator = ValueAnimator.ofFloat(0f, width * 2f).apply {
            duration = 3500L
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.RESTART
            interpolator = LinearInterpolator()
            addUpdateListener {
                translate = it.animatedValue as Float
                shaderMatrix.reset()
                shaderMatrix.setTranslate(-translate, 0f)
                shader?.setLocalMatrix(shaderMatrix)
                invalidate()
            }
            start()
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        animator?.cancel()
        animator = null
    }
}
