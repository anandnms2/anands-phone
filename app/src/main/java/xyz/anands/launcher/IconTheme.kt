package xyz.anands.launcher

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.os.Build

/**
 * Anands icon style: orange tile, white glyph.
 *
 * Only apps that ship a real one-colour (monochrome) layer get restyled, because that layer
 * is a clean glyph. Every other app keeps its own icon untouched, so nothing ends up as a
 * broken blob.
 */
object IconTheme {
    val TILE: Int = Color.parseColor("#E8710A")
    private const val SIZE = 128

    fun restyle(context: Context, src: Drawable): Drawable {
        if (Build.VERSION.SDK_INT < 33 || src !is AdaptiveIconDrawable) return src
        val mono = src.monochrome ?: return src
        return try {
            val out = Bitmap.createBitmap(SIZE, SIZE, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(out)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = TILE }
            val radius = SIZE * 0.30f
            canvas.drawRoundRect(RectF(0f, 0f, SIZE.toFloat(), SIZE.toFloat()), radius, radius, paint)

            // Glyph layer, forced white, kept inside the tile with some air around it.
            val glyph = Bitmap.createBitmap(SIZE, SIZE, Bitmap.Config.ARGB_8888)
            val pad = -SIZE / 40
            mono.setBounds(pad, pad, SIZE - pad, SIZE - pad)
            mono.draw(Canvas(glyph))
            val px = IntArray(SIZE * SIZE)
            glyph.getPixels(px, 0, SIZE, 0, 0, SIZE, SIZE)
            for (i in px.indices) px[i] = (px[i] and -0x1000000) or 0x00FFFFFF
            glyph.setPixels(px, 0, SIZE, 0, 0, SIZE, SIZE)
            canvas.drawBitmap(glyph, 0f, 0f, null)
            BitmapDrawable(context.resources, out)
        } catch (e: Exception) {
            src
        }
    }
}
