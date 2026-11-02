package xyz.anands.launcher

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.os.Build

/**
 * Redraws any app icon in the Anands scheme: dark rounded tile, orange glyph.
 *
 * 1. Icon ships a one-colour layer (Android 13+ themed icon)  -> that layer in orange.
 * 2. Adaptive icon whose foreground is a shape on transparency -> foreground as an orange silhouette.
 * 3. Anything else (full-bleed or old-style icon)             -> whole icon mapped dark-to-orange.
 */
object IconTheme {
    private val ORANGE = Color.parseColor("#F68B1F")
    private val TILE = Color.parseColor("#1A1A1C")
    private val DEEP = Color.parseColor("#101012")
    private val LINE = Color.parseColor("#2A2A2D")
    private const val SIZE = 128

    fun restyle(context: Context, src: Drawable): Drawable =
        try {
            BitmapDrawable(context.resources, build(src))
        } catch (e: Exception) {
            src
        }

    private fun build(src: Drawable): Bitmap {
        val rect = RectF(0f, 0f, SIZE.toFloat(), SIZE.toFloat())
        val radius = SIZE * 0.30f

        // Glyph layer, cut to a rounded square with smooth edges.
        val glyph = glyphFor(src)
        val mask = Paint(Paint.ANTI_ALIAS_FLAG).apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN) }
        Canvas(glyph).drawRoundRect(rect, radius, radius, mask)

        val out = Bitmap.createBitmap(SIZE, SIZE, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.color = TILE
        canvas.drawRoundRect(rect, radius, radius, paint)
        canvas.drawBitmap(glyph, 0f, 0f, null)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2f
        paint.color = LINE
        canvas.drawRoundRect(RectF(1f, 1f, SIZE - 1f, SIZE - 1f), radius, radius, paint)
        return out
    }

    private fun glyphFor(src: Drawable): Bitmap {
        if (src is AdaptiveIconDrawable) {
            val mono: Drawable? = if (Build.VERSION.SDK_INT >= 33) src.monochrome else null
            if (mono != null) return silhouette(render(mono, adaptive = true))

            val fg = src.foreground
            if (fg != null) {
                val bmp = render(fg, adaptive = true)
                if (opaqueFraction(bmp) < 0.8f) return silhouette(bmp)
            }
            return duotone(render(src, adaptive = true))
        }
        val bmp = render(src, adaptive = false)
        return if (opaqueFraction(bmp) < 0.6f) silhouette(bmp) else duotone(bmp)
    }

    /** Draws [d] into a SIZE x SIZE bitmap. Adaptive layers are 108dp with a 72dp visible window, so scale 1.5x. */
    private fun render(d: Drawable, adaptive: Boolean): Bitmap {
        val bmp = Bitmap.createBitmap(SIZE, SIZE, Bitmap.Config.ARGB_8888)
        val pad = if (adaptive) -SIZE / 4 else SIZE / 20
        d.setBounds(pad, pad, SIZE - pad, SIZE - pad)
        d.draw(Canvas(bmp))
        return bmp
    }

    private fun opaqueFraction(bmp: Bitmap): Float {
        val px = IntArray(SIZE * SIZE)
        bmp.getPixels(px, 0, SIZE, 0, 0, SIZE, SIZE)
        var n = 0
        for (p in px) if ((p ushr 24) > 200) n++
        return n.toFloat() / px.size
    }

    /** Every visible pixel becomes orange, keeping its transparency. */
    private fun silhouette(bmp: Bitmap): Bitmap {
        val px = IntArray(SIZE * SIZE)
        bmp.getPixels(px, 0, SIZE, 0, 0, SIZE, SIZE)
        val rgb = ORANGE and 0x00FFFFFF
        for (i in px.indices) px[i] = (px[i] and -0x1000000) or rgb
        bmp.setPixels(px, 0, SIZE, 0, 0, SIZE, SIZE)
        return bmp
    }

    /** Brightness decides the colour: black becomes near-black, white becomes orange. */
    private fun duotone(bmp: Bitmap): Bitmap {
        val lo = floatArrayOf(Color.red(DEEP) / 255f, Color.green(DEEP) / 255f, Color.blue(DEEP) / 255f)
        val hi = floatArrayOf(Color.red(ORANGE) / 255f, Color.green(ORANGE) / 255f, Color.blue(ORANGE) / 255f)
        fun row(c: Int) = floatArrayOf(
            (hi[c] - lo[c]) * 0.213f, (hi[c] - lo[c]) * 0.715f, (hi[c] - lo[c]) * 0.072f, 0f, lo[c] * 255f,
        )
        val m = row(0) + row(1) + row(2) + floatArrayOf(0f, 0f, 0f, 1f, 0f)
        val out = Bitmap.createBitmap(SIZE, SIZE, Bitmap.Config.ARGB_8888)
        val paint = Paint().apply { colorFilter = ColorMatrixColorFilter(ColorMatrix(m)) }
        Canvas(out).drawBitmap(bmp, 0f, 0f, paint)
        return out
    }
}
