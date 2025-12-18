package com.hayaguard.app

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue

object BitmapPool {

    private const val POOL_SIZE = 8

    private val pools = ConcurrentHashMap<Int, ConcurrentLinkedQueue<Bitmap>>()

    private fun getPool(size: Int): ConcurrentLinkedQueue<Bitmap> {
        return pools.getOrPut(size) { ConcurrentLinkedQueue() }
    }

    fun acquire(size: Int): Bitmap {
        return getPool(size).poll() ?: Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    }

    fun acquire224(): Bitmap = acquire(224)

    fun acquire320(): Bitmap = acquire(320)

    fun release(bitmap: Bitmap) {
        if (bitmap.isRecycled) return

        val size = bitmap.width
        if (bitmap.width == bitmap.height) {
            val pool = getPool(size)
            if (pool.size < POOL_SIZE) {
                bitmap.eraseColor(0)
                pool.offer(bitmap)
                return
            }
        }
        bitmap.recycle()
    }

    fun scaleToSize(source: Bitmap, targetSize: Int): Bitmap {
        val target = acquire(targetSize)
        val canvas = Canvas(target)
        val paint = Paint(Paint.FILTER_BITMAP_FLAG)
        val scaleX = targetSize.toFloat() / source.width
        val scaleY = targetSize.toFloat() / source.height
        canvas.scale(scaleX, scaleY)
        canvas.drawBitmap(source, 0f, 0f, paint)
        return target
    }

    fun scaleTo224(source: Bitmap): Bitmap = scaleToSize(source, 224)

    fun scaleTo320(source: Bitmap): Bitmap = scaleToSize(source, 320)

    fun scaleAdaptive(source: Bitmap): Bitmap {
        val size = AdaptivePerformanceEngine.getInputSize()
        return scaleToSize(source, size)
    }

    fun scaleForNsfwjs(source: Bitmap): Bitmap {
        val size = AdaptivePerformanceEngine.getNsfwjsInputSize()
        return scaleToSize(source, size)
    }

    fun scaleForNudeNet(source: Bitmap): Bitmap {
        val size = AdaptivePerformanceEngine.getNudeNetInputSize()
        return scaleToSize(source, size)
    }

    fun clear() {
        pools.values.forEach { pool ->
            pool.forEach { if (!it.isRecycled) it.recycle() }
            pool.clear()
        }
        pools.clear()
    }
}
