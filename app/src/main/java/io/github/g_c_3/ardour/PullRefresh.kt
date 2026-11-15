package io.github.g_c_3.ardour

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.animation.DecelerateInterpolator
import android.view.animation.LinearInterpolator
import android.widget.FrameLayout
import kotlin.math.abs

// Pull a page down from its top to reload it. Off until the user turns it on in Settings (DECISIONS.md 51).
class PullRefreshLayout(
    context: Context,
    private val canPull: () -> Boolean,
    private val onRefresh: () -> Unit,
) : FrameLayout(context) {
    var enabledPull = false
    private val slop = ViewConfiguration.get(context).scaledTouchSlop
    private val trigger = Ui.dp(context, 72).toFloat()
    private val size = Ui.dp(context, 44)
    private val indicator = Indicator(context)
    private var startX = 0f
    private var startY = 0f
    private var pulling = false
    private var refreshing = false
    private var pull = 0f
    private val giveUp = Runnable { finish() }

    fun attachIndicator() {
        addView(indicator, LayoutParams(size, size, Gravity.TOP or Gravity.CENTER_HORIZONTAL))
        hideIndicator(false)
    }

    // Called when the page stops loading.
    fun finish() {
        if (!refreshing) return
        refreshing = false
        removeCallbacks(giveUp)
        indicator.spinning = false
        hideIndicator(true)
    }

    private fun hideIndicator(animated: Boolean) {
        pull = 0f
        indicator.progress = 0f
        if (animated) {
            indicator.animate().translationY(-size.toFloat()).alpha(0f).setDuration(220)
                .setInterpolator(DecelerateInterpolator()).start()
        } else {
            indicator.translationY = -size.toFloat()
            indicator.alpha = 0f
        }
    }

    private fun place() {
        val p = (pull / trigger).coerceIn(0f, 1f)
        indicator.progress = p
        indicator.alpha = p
        indicator.translationY = -size + pull * ((size + Ui.dp(context, 16)).toFloat() / trigger)
    }

    private fun startRefresh() {
        refreshing = true
        indicator.progress = 1f
        indicator.spinning = true
        indicator.animate().translationY(Ui.dp(context, 16).toFloat()).alpha(1f).setDuration(260)
            .setInterpolator(SpringInterpolator()).start()
        onRefresh()
        // Safety: the indicator never stays if no load event arrives.
        postDelayed(giveUp, 10000)
    }

    override fun onInterceptTouchEvent(e: MotionEvent): Boolean {
        if (!enabledPull || refreshing) return false
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                startX = e.x
                startY = e.y
                pulling = false
            }
            MotionEvent.ACTION_MOVE -> if (!pulling && e.pointerCount == 1) {
                val dy = e.y - startY
                if (dy > slop && dy > abs(e.x - startX) * 1.5f && canPull()) {
                    pulling = true
                    startY = e.y
                    pull = 0f
                } else if (dy < 0) {
                    startY = e.y
                }
            }
        }
        return pulling
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        if (!pulling) return false
        when (e.actionMasked) {
            MotionEvent.ACTION_MOVE -> {
                pull = ((e.y - startY) * 0.5f).coerceIn(0f, trigger * 1.5f)
                place()
            }
            MotionEvent.ACTION_UP -> {
                pulling = false
                if (pull >= trigger) startRefresh() else hideIndicator(true)
            }
            MotionEvent.ACTION_CANCEL -> {
                pulling = false
                hideIndicator(true)
            }
        }
        return true
    }
}

// Round chip with an ember arc: fills as the pull grows, spins while the page reloads.
private class Indicator(context: Context) : View(context) {
    var progress = 0f
        set(v) {
            field = v
            invalidate()
        }
    var spinning = false
        set(v) {
            field = v
            if (v) startSpin() else stopSpin()
            invalidate()
        }
    private var angle = -90f
    private var spinner: ValueAnimator? = null
    private val chip = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Ui.CARD
        setShadowLayer(Ui.dp(context, 4).toFloat(), 0f, Ui.dp(context, 1).toFloat(), 0x33000000)
    }
    private val edge = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Ui.LINE
        style = Paint.Style.STROKE
        strokeWidth = Ui.dp(context, 1).toFloat()
    }
    private val arc = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Ui.EMBER
        style = Paint.Style.STROKE
        strokeWidth = Ui.dp(context, 3).toFloat()
        strokeCap = Paint.Cap.ROUND
    }
    private val box = RectF()

    init {
        setLayerType(LAYER_TYPE_SOFTWARE, null)
    }

    private fun startSpin() {
        if (spinner != null || !ValueAnimator.areAnimatorsEnabled()) return
        spinner = ValueAnimator.ofFloat(0f, 360f).apply {
            duration = 900
            repeatCount = ValueAnimator.INFINITE
            interpolator = LinearInterpolator()
            addUpdateListener {
                angle = -90f + it.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    private fun stopSpin() {
        spinner?.cancel()
        spinner = null
        angle = -90f
    }

    override fun onDetachedFromWindow() {
        stopSpin()
        super.onDetachedFromWindow()
    }

    override fun onDraw(canvas: Canvas) {
        val cx = width / 2f
        val cy = height / 2f
        val r = minOf(width, height) / 2f - Ui.dp(context, 5)
        canvas.drawCircle(cx, cy, r, chip)
        canvas.drawCircle(cx, cy, r, edge)
        val ar = r - Ui.dp(context, 9)
        box.set(cx - ar, cy - ar, cx + ar, cy + ar)
        val sweep = if (spinning) 250f else 270f * progress
        canvas.drawArc(box, angle, sweep, false, arc)
    }
}
