package com.hayaguard.app

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.widget.ImageView
import android.widget.Toast
import com.google.mlkit.vision.text.Text

class OcrOverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var textBlocks: List<Text.TextBlock> = emptyList()
    private var bitmapWidth: Int = 0
    private var bitmapHeight: Int = 0
    private var imageView: ImageView? = null

    private val fillPaint = Paint().apply {
        color = Color.argb(80, 255, 255, 255)
        style = Paint.Style.FILL
    }

    private val strokePaint = Paint().apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 3f
        isAntiAlias = true
    }

    private val scaledRects = mutableListOf<Pair<RectF, String>>()

    fun setImageView(iv: ImageView) {
        imageView = iv
    }

    fun setTextBlocks(blocks: List<Text.TextBlock>, bmpWidth: Int, bmpHeight: Int) {
        textBlocks = blocks
        bitmapWidth = bmpWidth
        bitmapHeight = bmpHeight
        post { 
            calculateScaling()
            invalidate()
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (textBlocks.isNotEmpty()) {
            calculateScaling()
        }
    }

    private fun calculateScaling() {
        if (bitmapWidth == 0 || bitmapHeight == 0 || width == 0 || height == 0) return

        val iv = imageView
        val scaleX: Float
        val scaleY: Float
        val offsetX: Float
        val offsetY: Float

        if (iv != null && iv.drawable != null) {
            val imageMatrix = iv.imageMatrix
            val values = FloatArray(9)
            imageMatrix.getValues(values)
            
            scaleX = values[Matrix.MSCALE_X]
            scaleY = values[Matrix.MSCALE_Y]
            offsetX = values[Matrix.MTRANS_X]
            offsetY = values[Matrix.MTRANS_Y]
        } else {
            val viewAspect = width.toFloat() / height.toFloat()
            val bitmapAspect = bitmapWidth.toFloat() / bitmapHeight.toFloat()

            if (bitmapAspect > viewAspect) {
                scaleX = width.toFloat() / bitmapWidth.toFloat()
                scaleY = scaleX
                offsetX = 0f
                offsetY = (height - bitmapHeight * scaleY) / 2f
            } else {
                scaleY = height.toFloat() / bitmapHeight.toFloat()
                scaleX = scaleY
                offsetX = (width - bitmapWidth * scaleX) / 2f
                offsetY = 0f
            }
        }

        scaledRects.clear()
        for (block in textBlocks) {
            val box = block.boundingBox ?: continue
            val scaledRect = RectF(
                box.left * scaleX + offsetX,
                box.top * scaleY + offsetY,
                box.right * scaleX + offsetX,
                box.bottom * scaleY + offsetY
            )
            scaledRects.add(Pair(scaledRect, block.text))
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        for ((rect, _) in scaledRects) {
            canvas.drawRect(rect, fillPaint)
            canvas.drawRect(rect, strokePaint)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_UP) {
            val touchX = event.x
            val touchY = event.y
            for ((rect, text) in scaledRects) {
                if (rect.contains(touchX, touchY)) {
                    triggerHapticFeedback()
                    copyToClipboard(text)
                    Toast.makeText(context, "Text Copied", Toast.LENGTH_SHORT).show()
                    return true
                }
            }
        }
        return true
    }

    private fun triggerHapticFeedback() {
        try {
            performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)
        } catch (e: Exception) {
        }
    }

    private fun copyToClipboard(text: String) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("OCR Text", text)
        clipboard.setPrimaryClip(clip)
    }
}
