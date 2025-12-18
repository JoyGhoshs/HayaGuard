package com.hayaguard.app

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View

class PieChartView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    data class Slice(
        val value: Float,
        val color: Int,
        val label: String
    )

    private val slices = mutableListOf<Slice>()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rectF = RectF()
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = Color.WHITE
        strokeWidth = 3f
    }

    private var centerHoleRadius = 0.55f

    fun setData(data: List<Slice>) {
        slices.clear()
        slices.addAll(data.filter { it.value > 0 })
        invalidate()
    }

    fun setCenterHoleRadius(radius: Float) {
        centerHoleRadius = radius.coerceIn(0f, 0.9f)
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        if (slices.isEmpty()) {
            drawEmptyState(canvas)
            return
        }

        val total = slices.sumOf { it.value.toDouble() }.toFloat()
        if (total <= 0) {
            drawEmptyState(canvas)
            return
        }

        val centerX = width / 2f
        val centerY = height / 2f
        val radius = minOf(centerX, centerY) - 16f

        rectF.set(
            centerX - radius,
            centerY - radius,
            centerX + radius,
            centerY + radius
        )

        var startAngle = -90f

        for (slice in slices) {
            val sweepAngle = (slice.value / total) * 360f

            paint.style = Paint.Style.FILL
            paint.color = slice.color
            canvas.drawArc(rectF, startAngle, sweepAngle, true, paint)

            canvas.drawArc(rectF, startAngle, sweepAngle, true, strokePaint)

            startAngle += sweepAngle
        }

        val holeRadius = radius * centerHoleRadius
        paint.color = Color.WHITE
        canvas.drawCircle(centerX, centerY, holeRadius, paint)

        drawCenterText(canvas, centerX, centerY, total.toInt())
    }

    private fun drawEmptyState(canvas: Canvas) {
        val centerX = width / 2f
        val centerY = height / 2f
        val radius = minOf(centerX, centerY) - 16f

        paint.style = Paint.Style.FILL
        paint.color = 0xFFE5E5EA.toInt()
        canvas.drawCircle(centerX, centerY, radius, paint)

        val holeRadius = radius * centerHoleRadius
        paint.color = Color.WHITE
        canvas.drawCircle(centerX, centerY, holeRadius, paint)

        paint.color = 0xFF8E8E93.toInt()
        paint.textSize = 14f * resources.displayMetrics.density
        paint.textAlign = Paint.Align.CENTER
        canvas.drawText("No data", centerX, centerY + 5f, paint)
    }

    private fun drawCenterText(canvas: Canvas, centerX: Float, centerY: Float, total: Int) {
        paint.color = 0xFF1C1C1E.toInt()
        paint.textAlign = Paint.Align.CENTER

        paint.textSize = 28f * resources.displayMetrics.density
        paint.isFakeBoldText = true
        canvas.drawText(total.toString(), centerX, centerY + 10f, paint)

        paint.textSize = 11f * resources.displayMetrics.density
        paint.isFakeBoldText = false
        paint.color = 0xFF8E8E93.toInt()
        canvas.drawText("posts", centerX, centerY + 30f, paint)
    }
}
