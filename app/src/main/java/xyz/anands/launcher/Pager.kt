package xyz.anands.launcher

import android.content.Context
import android.view.MotionEvent
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.view.GestureDetector
import kotlin.math.floor
import kotlin.math.roundToInt

/** Horizontal pager that always settles exactly on a page. No androidx needed. */
class SnapPager(context: Context) : HorizontalScrollView(context) {
    var onPage: ((Int) -> Unit)? = null
    private var page = 0
    private var flung = false

    init {
        isHorizontalScrollBarEnabled = false
        overScrollMode = OVER_SCROLL_NEVER
        isFillViewport = true
    }

    private fun count() = (getChildAt(0) as? ViewGroup)?.childCount ?: 1

    override fun onScrollChanged(l: Int, t: Int, oldl: Int, oldt: Int) {
        super.onScrollChanged(l, t, oldl, oldt)
        if (width > 0) {
            val p = ((l + width / 2) / width).coerceIn(0, count() - 1)
            if (p != page) {
                page = p
                onPage?.invoke(p)
            }
        }
    }

    override fun fling(velocityX: Int) {
        flung = true
        if (width == 0) return
        val cur = scrollX.toFloat() / width
        val base = floor(cur).toInt()
        val target = when {
            velocityX > 400 -> base + 1
            velocityX < -400 -> base
            else -> cur.roundToInt()
        }
        goTo(target)
    }

    override fun onTouchEvent(ev: MotionEvent): Boolean {
        if (ev.actionMasked == MotionEvent.ACTION_DOWN) flung = false
        val handled = super.onTouchEvent(ev)
        if ((ev.actionMasked == MotionEvent.ACTION_UP || ev.actionMasked == MotionEvent.ACTION_CANCEL) && !flung && width > 0) {
            goTo((scrollX.toFloat() / width).roundToInt())
        }
        return handled
    }

    fun goTo(target: Int, animate: Boolean = true) {
        val t = target.coerceIn(0, count() - 1)
        if (animate) smoothScrollTo(t * width, 0) else scrollTo(t * width, 0)
    }

    fun currentPage() = page
}

/** Wraps a widget so a long press can be detected without stealing touches from the widget itself. */
class LongPressFrame(context: Context, private val onLong: () -> Unit) : FrameLayout(context) {
    private val detector = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
        override fun onLongPress(e: MotionEvent) = onLong()
    })

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        detector.onTouchEvent(ev)
        return false
    }
}
