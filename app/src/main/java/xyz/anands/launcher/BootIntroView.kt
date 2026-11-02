package xyz.anands.launcher

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.view.View
import android.view.animation.LinearInterpolator

/**
 * The Anands boot animation, drawn live: the logo fades in on black and two orange rings pulse outward.
 * Same timeline as the 36-frame, 12 fps sequence (3 seconds). Tap to skip.
 */
class BootIntroView(
    context: Context,
    private val logo: Bitmap,
    private val onDone: () -> Unit,
) : View(context) {

    private val orange = Color.parseColor("#F68B1F")
    private val ink = Color.parseColor("#0B0B0C")
    private val logoPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = orange
    }

    private var frame = 0f
    private var finished = false

    private val animator = ValueAnimator.ofFloat(0f, 35f).apply {
        duration = 3000
        interpolator = LinearInterpolator()
        addUpdateListener {
            frame = it.animatedValue as Float
            invalidate()
        }
        addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator) {
                finish()
            }
        })
    }

    init {
        isClickable = true
        setOnClickListener { finish() }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        animator.start()
    }

    override fun onDetachedFromWindow() {
        animator.cancel()
        super.onDetachedFromWindow()
    }

    private fun finish() {
        if (finished) return
        finished = true
        animator.cancel()
        animate().alpha(0f).setDuration(300).withEndAction { onDone() }.start()
    }

    private fun ease(t: Float): Float {
        val u = 1f - t.coerceIn(0f, 1f)
        return 1f - u * u * u
    }

    override fun onDraw(canvas: Canvas) {
        canvas.drawColor(ink)
        val k = width / 1080f
        val cx = width / 2f
        val cy = height / 2f - 40f * k

        val fade = ease(frame / 10f)
        // The icon artwork has about 18% empty margin, so size it up to match the 380px logo in the frames.
        val size = (380f / 0.82f) * k * (0.82f + 0.18f * fade)
        logoPaint.alpha = (255 * fade).toInt()
        canvas.drawBitmap(logo, null, RectF(cx - size / 2, cy - size / 2, cx + size / 2, cy + size / 2), logoPaint)

        ringPaint.strokeWidth = 5f * k
        for (start in intArrayOf(6, 24)) {
            val p = (frame - start) / 18f
            if (p in 0f..1f) {
                ringPaint.alpha = ((1f - p) * 0.9f * 255).toInt()
                canvas.drawCircle(cx, cy, (230f + 330f * ease(p)) * k, ringPaint)
            }
        }
    }
}
