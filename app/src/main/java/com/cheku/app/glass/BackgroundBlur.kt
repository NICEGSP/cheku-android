package com.cheku.app.glass

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.net.Uri
import kotlin.math.roundToInt

object BackgroundBlur {
    /** 拖动停止多久后才把新半径应用到背景 */
    const val SettleMillis = 120L

    /** 解码上限 */
    const val MaxDecodedWidth = 2048

    /** 盒式模糊半径的经验系数 */
    const val BoxRadiusCalibration = 0.7f

    const val LiveMaxWidth = 384

    /** 按上限解码 */
    fun decodeCapped(context: Context, uri: Uri, maxWidth: Int = MaxDecodedWidth): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        runCatching {
            context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        val opts = BitmapFactory.Options().apply { inSampleSize = sampleSizeFor(bounds.outWidth, maxWidth) }
        return runCatching {
            context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
        }.getOrNull()
    }

    fun sampleSizeFor(sourceWidth: Int, maxWidth: Int = MaxDecodedWidth): Int {
        if (sourceWidth <= 0 || maxWidth <= 0) return 1
        var sample = 1
        while (sourceWidth / sample > maxWidth && sample < 1 shl 10) sample *= 2
        return sample
    }

    class LiveBlurRenderer {
        private var srcW = 0
        private var srcH = 0
        private var smallW = 0
        private var smallH = 0
        private var factor = 1
        private var small: Bitmap? = null
        private var canvas: Canvas? = null
        private var paint: Paint? = null
        private var pixels: IntArray = IntArray(0)
        private var out: Array<Bitmap?> = arrayOfNulls(2)
        private var next = 0
        private val srcRect = Rect()
        private val dstRect = Rect()

        fun render(source: Bitmap, radiusPx: Float, screenWidthPx: Int): Bitmap? {
            if (radiusPx < 1f) return null
            if (source.width <= 0 || source.height <= 0 || screenWidthPx <= 0) return null
            prepare(source)

            val smallBitmap = small ?: return null
            val canvasLocal = canvas ?: return null
            val paintLocal = paint ?: return null

            // 1) 缩放：预分配的 Canvas + 带滤镜的 Paint（替代每次 createScaledBitmap）
            srcRect.set(0, 0, source.width, source.height)
            dstRect.set(0, 0, smallW, smallH)
            canvasLocal.drawBitmap(source, srcRect, dstRect, paintLocal)

            // 2) 小图上做 3 次盒式模糊。半径换算：小图 r 像素显示到屏幕上会放大
            //    (screenWidthPx / smallW) 倍，所以要先把屏幕半径折算到小图尺度。
            smallBitmap.getPixels(pixels, 0, smallW, 0, 0, smallW, smallH)
            val radius = (radiusPx * BoxRadiusCalibration * smallW / screenWidthPx)
                .roundToInt()
                .coerceIn(1, ((minOf(smallW, smallH) - 1) / 2).coerceAtLeast(1))
            blur(pixels, smallW, smallH, radius)

            val target = out[next]
            next = 1 - next
            if (target == null) return null
            target.setPixels(pixels, 0, smallW, 0, 0, smallW, smallH)
            return target
        }

        private fun prepare(source: Bitmap) {
            if (srcW == source.width && srcH == source.height) return
            srcW = source.width
            srcH = source.height
            factor = maxOf(1, ceilDiv(srcW, LiveMaxWidth))
            smallW = maxOf(1, srcW / factor)
            smallH = maxOf(1, srcH / factor)
            factor = maxOf(1, srcW / smallW) // 用整数比，保证半径换算与缩放一致
            val scaled = Bitmap.createBitmap(smallW, smallH, Bitmap.Config.ARGB_8888)
            small = scaled
            canvas = Canvas(scaled)
            paint = Paint().apply {
                isFilterBitmap = true
                isAntiAlias = true
            }
            pixels = IntArray(smallW * smallH)
            out = arrayOf(
                Bitmap.createBitmap(smallW, smallH, Bitmap.Config.ARGB_8888),
                Bitmap.createBitmap(smallW, smallH, Bitmap.Config.ARGB_8888)
            )
            next = 0
        }

        private fun ceilDiv(a: Int, b: Int): Int = (a + b - 1) / b
    }

    /** 3 次盒式模糊 */
    fun blur(argb: IntArray, width: Int, height: Int, radius: Int) {
        if (width <= 0 || height <= 0) return
        if (argb.size < width * height) return
        val r = radius.coerceIn(0, (minOf(width, height) - 1) / 2)
        if (r <= 0) return

        val tmp = IntArray(maxOf(width, height))
        repeat(3) {
            boxBlurHorizontal(argb, tmp, width, height, r)
            boxBlurVertical(argb, tmp, width, height, r)
        }
    }

    private fun boxBlurHorizontal(p: IntArray, tmp: IntArray, w: Int, h: Int, r: Int) {
        val window = 2 * r + 1
        val half = window / 2
        for (y in 0 until h) {
            val row = y * w
            var a = 0
            var red = 0
            var green = 0
            var blue = 0
            for (i in -r..r) {
                val c = p[row + i.coerceIn(0, w - 1)]
                a += c ushr 24 and 0xFF
                red += c ushr 16 and 0xFF
                green += c ushr 8 and 0xFF
                blue += c and 0xFF
            }
            for (x in 0 until w) {
                tmp[x] = (((a + half) / window) shl 24) or (((red + half) / window) shl 16) or
                    (((green + half) / window) shl 8) or ((blue + half) / window)
                val out = p[row + (x - r).coerceIn(0, w - 1)]
                val inNew = p[row + (x + r + 1).coerceIn(0, w - 1)]
                a += (inNew ushr 24 and 0xFF) - (out ushr 24 and 0xFF)
                red += (inNew ushr 16 and 0xFF) - (out ushr 16 and 0xFF)
                green += (inNew ushr 8 and 0xFF) - (out ushr 8 and 0xFF)
                blue += (inNew and 0xFF) - (out and 0xFF)
            }
            System.arraycopy(tmp, 0, p, row, w)
        }
    }

    private fun boxBlurVertical(p: IntArray, tmp: IntArray, w: Int, h: Int, r: Int) {
        val window = 2 * r + 1
        val half = window / 2
        for (x in 0 until w) {
            var a = 0
            var red = 0
            var green = 0
            var blue = 0
            for (i in -r..r) {
                val c = p[i.coerceIn(0, h - 1) * w + x]
                a += c ushr 24 and 0xFF
                red += c ushr 16 and 0xFF
                green += c ushr 8 and 0xFF
                blue += c and 0xFF
            }
            for (y in 0 until h) {
                tmp[y] = (((a + half) / window) shl 24) or (((red + half) / window) shl 16) or
                    (((green + half) / window) shl 8) or ((blue + half) / window)
                val out = p[(y - r).coerceIn(0, h - 1) * w + x]
                val inNew = p[(y + r + 1).coerceIn(0, h - 1) * w + x]
                a += (inNew ushr 24 and 0xFF) - (out ushr 24 and 0xFF)
                red += (inNew ushr 16 and 0xFF) - (out ushr 16 and 0xFF)
                green += (inNew ushr 8 and 0xFF) - (out ushr 8 and 0xFF)
                blue += (inNew and 0xFF) - (out and 0xFF)
            }
            for (y in 0 until h) p[y * w + x] = tmp[y]
        }
    }
}
