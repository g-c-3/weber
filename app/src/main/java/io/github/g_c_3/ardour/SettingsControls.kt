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
class StepSlider(
    context: Context,
    private val labels: List<String>,
    start: Int,
    private val compact: Boolean = false,
    private val onMove: ((Int) -> Unit)? = null,
    private val onChange: (Int) -> Unit,
) : View(context) {
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

    // Compact: one line high, no labels (the value is shown by the row, at the end of the slider).
    private val pad get() = if (compact) d(16) else d(22)

    private fun x(i: Float): Float {
        val left = pad
        return left + (width - 2 * left) * (i / (labels.size - 1))
    }

    override fun onMeasure(widthSpec: Int, heightSpec: Int) {
        setMeasuredDimension(MeasureSpec.getSize(widthSpec), Ui.dp(context, if (compact) 36 else 58))
    }

    override fun onDraw(canvas: Canvas) {
        val y = if (compact) height / 2f else d(20)
        // The compact slider is thin and small: a fine line and a small thumb.
        val t = if (compact) 0.6f else 1f
        track.strokeWidth = d(4) * t
        track.color = Ui.LINE
        canvas.drawLine(x(0f), y, x((labels.size - 1).toFloat()), y, track)
        track.color = Ui.EMBER
        canvas.drawLine(x(0f), y, x(pos), y, track)
        for (i in labels.indices) {
            dot.color = if (i <= pos + 0.01f) Ui.EMBER else Ui.LINE
            canvas.drawCircle(x(i.toFloat()), y, d(5) * t, dot)
            val near = (1f - abs(pos - i)).coerceIn(0f, 1f)
            text.color = if (near > 0.5f) Ui.EMBER else Ui.MUTED
            if (!compact) canvas.drawText(labels[i], x(i.toFloat()), y + d(28), text)
        }
        glow.color = (0x40 shl 24) or (Ui.EMBER and 0x00FFFFFF)
        canvas.drawCircle(x(pos), y, d(15) * t, glow)
        dot.color = Ui.EMBER
        canvas.drawCircle(x(pos), y, d(10) * t, dot)
        dot.color = 0xFFFFFFFF.toInt()
        canvas.drawCircle(x(pos), y, d(4) * t, dot)
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(e: MotionEvent): Boolean {
        if (!isEnabled) return false
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
        val left = pad
        val value = ((touchX - left) / (width - 2 * left) * (labels.size - 1)).coerceIn(0f, (labels.size - 1).toFloat())
        val before = pos.roundToInt()
        pos = value
        if (pos.roundToInt() != before) {
            performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
            onMove?.invoke(pos.roundToInt().coerceIn(0, labels.size - 1))
        }
        invalidate()
    }
}

// A number wheel on one line: scrolled sideways by hand, the number in the middle box is the value, in the size of a row title.
// It flings and settles on a whole number; `onChange` runs with the value once it has settled on a new one.
@SuppressLint("ViewConstructor")
class NumberWheel(context: Context, private val min: Int, private val max: Int, start: Int, private val onChange: (Int) -> Unit) : View(context) {
    private var value = start.coerceIn(min, max)
    private var pos = value.toFloat()
    private var settle: ValueAnimator? = null
    private var lastX = 0f
    private var velocity: VelocityTracker? = null
    private val band = Paint(Paint.ANTI_ALIAS_FLAG)
    private val digits = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
    }
    private val box = RectF()

    private fun d(n: Int) = Ui.dp(context, n).toFloat()

    private val cell get() = d(44)

    override fun onMeasure(widthSpec: Int, heightSpec: Int) {
        setMeasuredDimension(Ui.dp(context, 150), Ui.dp(context, 38))
    }

    override fun onDraw(canvas: Canvas) {
        val cx = width / 2f
        val cy = height / 2f
        band.style = Paint.Style.FILL
        band.color = Ui.cardAlpha(0xFF)
        box.set(cx - cell / 2f, d(3), cx + cell / 2f, height - d(3))
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
            // The chosen number has the size of the row title (16 sp); the others are smaller and fade towards the ends.
            digits.textSize = Ui.textPx(context, 13f + 3f * (1f - minOf(abs(off), 1f)))
            digits.color = if (abs(off) < 0.5f) Ui.EMBER else Ui.INK
            digits.alpha = (255 * near).toInt()
            canvas.drawText(n.toString(), cx + off * cell, cy - (digits.ascent() + digits.descent()) / 2f, digits)
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                parent?.requestDisallowInterceptTouchEvent(true)
                settle?.cancel()
                lastX = e.x
                velocity = VelocityTracker.obtain().also { it.addMovement(e) }
            }
            MotionEvent.ACTION_MOVE -> {
                velocity?.addMovement(e)
                val before = pos.roundToInt()
                pos = (pos - (e.x - lastX) / cell).coerceIn(min.toFloat(), max.toFloat())
                lastX = e.x
                if (pos.roundToInt() != before) performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                invalidate()
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                velocity?.addMovement(e)
                velocity?.computeCurrentVelocity(1000)
                val v = velocity?.xVelocity ?: 0f
                velocity?.recycle()
                velocity = null
                // A fling carries the wheel on a little: about a fifth of a second of its speed.
                val target = (pos - v * 0.2f / cell).roundToInt().coerceIn(min, max)
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
