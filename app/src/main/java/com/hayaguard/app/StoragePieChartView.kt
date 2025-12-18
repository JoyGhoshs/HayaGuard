package com.hayaguard.app

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View

class StoragePieChartView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var cachePercentage = 0f
    private var essentialPercentage = 0f

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rectF = RectF()
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = Color.WHITE
        strokeWidth = 3f
    }

    private val cacheColor = 0xFFFF9500.toInt()
    private val essentialColor = 0xFF34C759.toInt()
    private val emptyColor = 0xFFE5E5EA.toInt()

    private var centerHoleRadius = 0.55f

    fun setData(cachePercent: Float, essentialPercent: Float) {
        this.cachePercentage = cachePercent.coerceIn(0f, 100f)
        this.essentialPercentage = essentialPercent.coerceIn(0f, 100f)
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val centerX = width / 2f
        val centerY = height / 2f
        val radius = minOf(centerX, centerY) - 8f

        rectF.set(
            centerX - radius,
            centerY - radius,
            centerX + radius,
            centerY + radius
        )

        val total = cachePercentage + essentialPercentage
        if (total <= 0) {
            paint.style = Paint.Style.FILL
            paint.color = emptyColor
            canvas.drawCircle(centerX, centerY, radius, paint)
        } else {
            var startAngle = -90f

            if (cachePercentage > 0) {
                val sweepAngle = (cachePercentage / 100f) * 360f
                paint.style = Paint.Style.FILL
                paint.color = cacheColor
                canvas.drawArc(rectF, startAngle, sweepAngle, true, paint)
                canvas.drawArc(rectF, startAngle, sweepAngle, true, strokePaint)
                startAngle += sweepAngle
            }

            if (essentialPercentage > 0) {
                val sweepAngle = (essentialPercentage / 100f) * 360f
                paint.style = Paint.Style.FILL
                paint.color = essentialColor
                canvas.drawArc(rectF, startAngle, sweepAngle, true, paint)
                canvas.drawArc(rectF, startAngle, sweepAngle, true, strokePaint)
                startAngle += sweepAngle
            }

            val remaining = 100f - total
            if (remaining > 0) {
                val sweepAngle = (remaining / 100f) * 360f
                paint.style = Paint.Style.FILL
                paint.color = emptyColor
                canvas.drawArc(rectF, startAngle, sweepAngle, true, paint)
            }
        }

        val holeRadius = radius * centerHoleRadius
        paint.color = Color.WHITE
        canvas.drawCircle(centerX, centerY, holeRadius, paint)

        drawCenterText(canvas, centerX, centerY)
    }

    private fun drawCenterText(canvas: Canvas, centerX: Float, centerY: Float) {
        val total = cachePercentage + essentialPercentage
        val text = String.format("%.0f%%", total)
        
        paint.color = 0xFF1C1C1E.toInt()
        paint.textSize = minOf(width, height) / 5f
        paint.textAlign = Paint.Align.CENTER
        paint.style = Paint.Style.FILL

        val textY = centerY - (paint.descent() + paint.ascent()) / 2
        canvas.drawText(text, centerX, textY, paint)
    }
}
