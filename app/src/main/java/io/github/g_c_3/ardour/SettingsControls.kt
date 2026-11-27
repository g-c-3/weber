package io.github.g_c_3.ardour

import android.annotation.SuppressLint
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.View
import kotlin.math.abs
import kotlin.math.roundToInt

// Controls of the Tabs settings (DECISIONS.md 181), drawn by Ardour.

// A slider with four stops (XS, S, M, L): a track that fills with ember up to the thumb, which follows the finger and settles
// on the nearest stop with a small overshoot. `onChange` runs with the stop's index when the finger lifts on a new stop.
@SuppressLint("ViewConstructor")
class StepSlider(context: Context, private val labels: List<String>, start: Int, private val onChange: (Int) -> Unit) : View(context) {
    private var index = start.coerceIn(0, labels.size - 1)
    private var pos = index.toFloat()
    private var settle: ValueAnimator? = null
    private val track = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeCap = Paint.Cap.ROUND }
    private val dot = Paint(Paint.ANTI_ALIAS_FLAG)
    private val glow = Paint(Paint.ANTI_ALIAS_FLAG)
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        textSize = Ui.dp(context, 12).toFloat()
    }

    private fun d(n: Int) = Ui.dp(context, n).toFloat()

    private fun x(i: Float): Float {
        val left = d(22)
        return left + (width - 2 * left) * (i / (labels.size - 1))
    }

    override fun onMeasure(widthSpec: Int, heightSpec: Int) {
        setMeasuredDimension(MeasureSpec.getSize(widthSpec), Ui.dp(context, 58))
    }

    override fun onDraw(canvas: Canvas) {
        val y = d(20)
        track.strokeWidth = d(4)
        track.color = Ui.LINE
        canvas.drawLine(x(0f), y, x((labels.size - 1).toFloat()), y, track)
        track.color = Ui.EMBER
        canvas.drawLine(x(0f), y, x(pos), y, track)
        for (i in labels.indices) {
            dot.color = if (i <= pos + 0.01f) Ui.EMBER else Ui.LINE
            canvas.drawCircle(x(i.toFloat()), y, d(5), dot)
            val near = (1f - abs(pos - i)).coerceIn(0f, 1f)
            text.color = if (near > 0.5f) Ui.EMBER else Ui.MUTED
            canvas.drawText(labels[i], x(i.toFloat()), y + d(28), text)
        }
        glow.color = (0x40 shl 24) or (Ui.EMBER and 0x00FFFFFF)
        canvas.drawCircle(x(pos), y, d(15), glow)
        dot.color = Ui.EMBER
        canvas.drawCircle(x(pos), y, d(10), dot)
        dot.color = 0xFFFFFFFF.toInt()
        canvas.drawCircle(x(pos), y, d(4), dot)
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                parent?.requestDisallowInterceptTouchEvent(true)
                settle?.cancel()
                follow(e.x)
            }
            MotionEvent.ACTION_MOVE -> follow(e.x)
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                val target = pos.roundToInt().coerceIn(0, labels.size - 1)
                settle = ValueAnimator.ofFloat(pos, target.toFloat()).apply {
                    duration = 300
                    interpolator = SpringInterpolator()
                    addUpdateListener {
                        pos = it.animatedValue as Float
                        invalidate()
                    }
                    start()
                }
                if (target != index) {
                    index = target
                    onChange(target)
                }
            }
        }
        return true
    }

    private fun follow(touchX: Float) {
        val left = d(22)
        val value = ((touchX - left) / (width - 2 * left) * (labels.size - 1)).coerceIn(0f, (labels.size - 1).toFloat())
        val before = pos.roundToInt()
        pos = value
        if (pos.roundToInt() != before) performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
        invalidate()
    }
}

// A number wheel: scrolled up and down by hand, the number in the middle is the value. It flings and settles on a whole
// number; `onChange` runs with the value once it has settled on a new one.
@SuppressLint("ViewConstructor")
class NumberWheel(context: Context, private val min: Int, private val max: Int, start: Int, private val onChange: (Int) -> Unit) : View(context) {
    private var value = start.coerceIn(min, max)
    private var pos = value.toFloat()
    private var settle: ValueAnimator? = null
    private var lastY = 0f
    private var velocity: VelocityTracker? = null
    private val band = Paint(Paint.ANTI_ALIAS_FLAG)
    private val digits = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
    }
    private val box = RectF()

    private fun d(n: Int) = Ui.dp(context, n).toFloat()

    private val rowHeight get() = d(30)

    override fun onMeasure(widthSpec: Int, heightSpec: Int) {
        setMeasuredDimension(Ui.dp(context, 76), Ui.dp(context, 100))
    }

    override fun onDraw(canvas: Canvas) {
        val cy = height / 2f
        band.color = Ui.cardAlpha(0xFF)
        band.style = Paint.Style.FILL
        box.set(d(4), cy - rowHeight / 2f, width - d(4), cy + rowHeight / 2f)
        canvas.drawRoundRect(box, d(14), d(14), band)
        band.style = Paint.Style.STROKE
        band.strokeWidth = d(2)
        band.color = Ui.EMBER
        canvas.drawRoundRect(box, d(14), d(14), band)
        val first = (pos - 2.5f).toInt().coerceAtLeast(min)
        val last = (pos + 2.5f).toInt().coerceAtMost(max)
        for (n in first..last) {
            val off = n - pos
            val near = (1f - abs(off) / 2.2f).coerceIn(0f, 1f)
            digits.textSize = d(14) + d(9) * (1f - minOf(abs(off), 1f))
            digits.color = if (abs(off) < 0.5f) Ui.EMBER else Ui.INK
            digits.alpha = (255 * near).toInt()
            canvas.drawText(n.toString(), width / 2f, cy + off * rowHeight - (digits.ascent() + digits.descent()) / 2f, digits)
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                parent?.requestDisallowInterceptTouchEvent(true)
                settle?.cancel()
                lastY = e.y
                velocity = VelocityTracker.obtain().also { it.addMovement(e) }
            }
            MotionEvent.ACTION_MOVE -> {
                velocity?.addMovement(e)
                val before = pos.roundToInt()
                pos = (pos - (e.y - lastY) / rowHeight).coerceIn(min.toFloat(), max.toFloat())
                lastY = e.y
                if (pos.roundToInt() != before) performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                invalidate()
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                velocity?.addMovement(e)
                velocity?.computeCurrentVelocity(1000)
                val v = velocity?.yVelocity ?: 0f
                velocity?.recycle()
                velocity = null
                // A fling carries the wheel on a little: about a fifth of a second of its speed.
                val target = (pos - v * 0.2f / rowHeight).roundToInt().coerceIn(min, max)
                settle = ValueAnimator.ofFloat(pos, target.toFloat()).apply {
                    duration = 320
                    interpolator = android.view.animation.DecelerateInterpolator()
                    addUpdateListener {
                        val before = pos.roundToInt()
                        pos = it.animatedValue as Float
                        if (pos.roundToInt() != before) performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                        invalidate()
                    }
                    start()
                }
                if (target != value) {
                    value = target
                    onChange(target)
                }
            }
        }
        return true
    }
}
