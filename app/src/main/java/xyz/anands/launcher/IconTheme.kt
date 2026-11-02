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
 * Strict Anands icons: EVERY app sits on an orange rounded tile.
 *
 * - App ships a one-colour (monochrome) layer -> that layer, forced white.
 * - Any other app -> its own full icon, inset on the tile. Never silhouetted, so it can't break.
 */
object IconTheme {
    val TILE: Int = Color.parseColor("#E8710A")
    private const val SIZE = 128

    fun restyle(context: Context, src: Drawable): Drawable =
        try {
            BitmapDrawable(context.resources, build(src))
        } catch (e: Exception) {
            src
        }

    private fun build(src: Drawable): Bitmap {
        val out = Bitmap.createBitmap(SIZE, SIZE, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = TILE }
        val radius = SIZE * 0.30f
        canvas.drawRoundRect(RectF(0f, 0f, SIZE.toFloat(), SIZE.toFloat()), radius, radius, paint)

        val mono: Drawable? =
            if (Build.VERSION.SDK_INT >= 33 && src is AdaptiveIconDrawable) src.monochrome else null

        if (mono != null) {
            val glyph = Bitmap.createBitmap(SIZE, SIZE, Bitmap.Config.ARGB_8888)
            val pad = -SIZE / 40
            mono.setBounds(pad, pad, SIZE - pad, SIZE - pad)
            mono.draw(Canvas(glyph))
            val px = IntArray(SIZE * SIZE)
            glyph.getPixels(px, 0, SIZE, 0, 0, SIZE, SIZE)
            for (i in px.indices) px[i] = (px[i] and -0x1000000) or 0x00FFFFFF
            glyph.setPixels(px, 0, SIZE, 0, 0, SIZE, SIZE)
            canvas.drawBitmap(glyph, 0f, 0f, null)
        } else {
            val inset = (SIZE * 0.17f).toInt()
            src.setBounds(inset, inset, SIZE - inset, SIZE - inset)
            src.draw(canvas)
        }
        return out
    }
}
