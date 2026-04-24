package com.example.askmechat.presentation.chat.widget

import android.graphics.Canvas
import android.graphics.Paint
import android.text.style.ReplacementSpan

/**
 * Breathing-dot typing cursor.
 *
 * Draws a small circle at the cursor position whose radius smoothly
 * oscillates between [MIN_SCALE] and [MAX_SCALE] of [baseDotRadiusDp].
 * The [scale] property is driven externally by a ValueAnimator.
 */
class TypingCursorSpan(
    private val dotColor: Int,
    private val baseDotRadiusDp: Float = 3.5f
) : ReplacementSpan() {

    var scale: Float = 1f

    override fun getSize(
        paint: Paint,
        text: CharSequence?,
        start: Int,
        end: Int,
        fm: Paint.FontMetricsInt?
    ): Int {
        val density = paint.density
        fm?.let {
            val originalFm = paint.fontMetricsInt
            it.top = originalFm.top
            it.ascent = originalFm.ascent
            it.descent = originalFm.descent
            it.bottom = originalFm.bottom
        }
        return ((baseDotRadiusDp * MAX_SCALE * 2f + HORIZONTAL_PADDING_DP) * density).toInt()
    }

    override fun draw(
        canvas: Canvas,
        text: CharSequence?,
        start: Int,
        end: Int,
        x: Float,
        top: Int,
        y: Int,
        bottom: Int,
        paint: Paint
    ) {
        val density = paint.density
        val radius = baseDotRadiusDp * density * scale
        val padding = HORIZONTAL_PADDING_DP * density

        val savedColor = paint.color
        val savedStyle = paint.style
        val savedAntiAlias = paint.isAntiAlias

        paint.color = dotColor
        paint.style = Paint.Style.FILL
        paint.isAntiAlias = true

        val fm = paint.fontMetricsInt
        val centerY = y.toFloat() + (fm.descent + fm.ascent) / 2f
        val centerX = x + padding + baseDotRadiusDp * MAX_SCALE * density

        canvas.drawCircle(centerX, centerY, radius, paint)

        paint.color = savedColor
        paint.style = savedStyle
        paint.isAntiAlias = savedAntiAlias
    }

    companion object {
        const val MIN_SCALE = 0.5f
        const val MAX_SCALE = 1.2f
        private const val HORIZONTAL_PADDING_DP = 4f
    }
}

/** Approximate display density from Paint.textSize */
private val Paint.density: Float
    get() = (textSize / 16f).coerceAtLeast(1f)
