package io.github.g_c_3.ardour

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.View
import android.view.animation.LinearInterpolator
import android.widget.LinearLayout
import android.widget.TextView
import kotlin.math.PI
import kotlin.math.cos

// Hand-drawn line icons for the address actions.
class EditIcon(context: Context, kind: EditIcon.Kind, tint: Int) : View(context) {
    enum class Kind { COPY, PASTE, CLEAR, GO, CHECK }

    var kind: Kind = kind
        set(value) {
            field = value
            invalidate()
        }
    var tint: Int = tint
        set(value) {
            field = value
            invalidate()
        }

    // Go only: while on, the arrow is fully amber; its glow and brightness pulse and an arrow-shaped echo grows out
    // from it and fades (DECISIONS.md 71, 102). Off: a dim static arrow.
    var flow: Boolean = false
        set(value) {
            field = value
            updateFlow()
        }
    private var phase = 0f
    private var flowAnim: ValueAnimator? = null
    private var shownNow = false
    private val flowLine = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    private val u = Ui.dp(context, 1).toFloat() * Ui.geoScale(context)
    private val k = 1.6f // the Go arrow is drawn larger than the other icons
    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = u * 1.6f
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val solid = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val cover = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = 0xFFFFFFFF.toInt()
    }
    private val rect = RectF()
    private val path = Path()

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        flowLine.shader = LinearGradient(w / 2f - 9f * u * k, 0f, w / 2f + 8f * u * k, 0f, Ui.EMBER, Ui.AMBER, Shader.TileMode.CLAMP)
    }

    override fun onVisibilityAggregated(isVisible: Boolean) {
        super.onVisibilityAggregated(isVisible)
        shownNow = isVisible
        updateFlow()
    }

    override fun onDetachedFromWindow() {
        flowAnim?.cancel()
        flowAnim = null
        super.onDetachedFromWindow()
    }

    private fun updateFlow() {
        val run = flow && shownNow && kind == Kind.GO
        if (run && ValueAnimator.areAnimatorsEnabled()) {
            if (flowAnim == null) {
                flowAnim = ValueAnimator.ofFloat(0f, 1f).apply {
                    duration = 2400
                    repeatCount = ValueAnimator.INFINITE
                    interpolator = LinearInterpolator()
                    addUpdateListener {
                        phase = it.animatedValue as Float
                        invalidate()
                    }
                    start()
                }
            }
        } else {
            flowAnim?.cancel()
            flowAnim = null
            phase = 0f
            invalidate()
        }
    }

    // The arrow: full ember-to-amber, with a soft glow and a slight change in brightness that breathe in and out.
    private fun drawFlowArrow(canvas: Canvas, cx: Float, cy: Float) {
        val x0 = cx - 9f * u * k
        val x1 = cx + 7f * u * k
        val head = 4.6f * u * k
        val s = 0.5f + 0.5f * cos(2f * PI.toFloat() * phase)
        if (flowAnim != null) {
            // Echo: the arrow itself grows outward from its centre and fades out completely, once per cycle.
            val e = 1f - (1f - phase) * (1f - phase)
            val f = 1f + 1.4f * e
            val a = (1f - phase) * (1f - phase)
            path.reset()
            path.moveTo(x1 - head, cy - head)
            path.lineTo(x1, cy)
            path.lineTo(x1 - head, cy + head)
            canvas.save()
            canvas.scale(f, f, cx, cy)
            line.color = Ui.AMBER
            line.alpha = (150 * a).toInt()
            line.strokeWidth = u * 2.2f / f
            canvas.drawLine(x0, cy, x1, cy, line)
            canvas.drawPath(path, line)
            canvas.restore()
        }
        path.reset()
        path.moveTo(x1 - head, cy - head)
        path.lineTo(x1, cy)
        path.lineTo(x1 - head, cy + head)
        line.color = Ui.AMBER
        for ((width, base, swing) in listOf(Triple(7.6f, 10f, 22f), Triple(5.4f, 16f, 30f))) {
            line.strokeWidth = u * width
            line.alpha = (base + swing * s).toInt()
            canvas.drawLine(x0, cy, x1, cy, line)
            canvas.drawPath(path, line)
        }
        flowLine.strokeWidth = u * 3.2f
        flowLine.alpha = (205 + 50 * s).toInt()
        canvas.drawLine(x0, cy, x1, cy, flowLine)
        canvas.drawPath(path, flowLine)
        line.strokeWidth = u * 1.6f
    }

    // Draws this action's icon in its tint.
    override fun onDraw(canvas: Canvas) {
        val cx = width / 2f
        val cy = height / 2f
        if (kind == Kind.GO && flow) {
            drawFlowArrow(canvas, cx, cy)
            return
        }
        line.alpha = 255
        line.color = tint
        solid.color = tint
        when (kind) {
            Kind.COPY -> {
                rect.set(cx - 3f * u, cy - 6.5f * u, cx + 6.5f * u, cy + 3f * u)
                canvas.drawRoundRect(rect, 2f * u, 2f * u, line)
                rect.set(cx - 6.5f * u, cy - 3f * u, cx + 3f * u, cy + 6.5f * u)
                canvas.drawRoundRect(rect, 2f * u, 2f * u, cover)
                canvas.drawRoundRect(rect, 2f * u, 2f * u, line)
            }
            Kind.PASTE -> {
                rect.set(cx - 5.5f * u, cy - 5f * u, cx + 5.5f * u, cy + 7f * u)
                canvas.drawRoundRect(rect, 2.2f * u, 2.2f * u, line)
                rect.set(cx - 2.8f * u, cy - 7f * u, cx + 2.8f * u, cy - 3.4f * u)
                canvas.drawRoundRect(rect, 1.4f * u, 1.4f * u, solid)
                canvas.drawLine(cx - 2.6f * u, cy + 1f * u, cx + 2.6f * u, cy + 1f * u, line)
                canvas.drawLine(cx - 2.6f * u, cy + 3.8f * u, cx + 1f * u, cy + 3.8f * u, line)
            }
            Kind.CLEAR -> {
                canvas.drawCircle(cx, cy, 6.5f * u, line)
                canvas.drawLine(cx - 2.5f * u, cy - 2.5f * u, cx + 2.5f * u, cy + 2.5f * u, line)
                canvas.drawLine(cx + 2.5f * u, cy - 2.5f * u, cx - 2.5f * u, cy + 2.5f * u, line)
            }
            Kind.GO -> {
                line.color = tint
                line.alpha = 90
                line.strokeWidth = u * 3.2f
                canvas.drawLine(cx - 9f * u * k, cy, cx + 7f * u * k, cy, line)
                path.reset()
                path.moveTo(cx + 7f * u * k - 4.6f * u * k, cy - 4.6f * u * k)
                path.lineTo(cx + 7f * u * k, cy)
                path.lineTo(cx + 7f * u * k - 4.6f * u * k, cy + 4.6f * u * k)
                canvas.drawPath(path, line)
                line.strokeWidth = u * 1.6f
            }
            Kind.CHECK -> {
                path.reset()
                path.moveTo(cx - 5f * u, cy + 0.5f * u)
                path.lineTo(cx - 1.5f * u, cy + 4f * u)
                path.lineTo(cx + 5.5f * u, cy - 4f * u)
                canvas.drawPath(path, line)
            }
        }
    }
}

private class Chip(context: Context, val icon: EditIcon, label: String, private val primary: Boolean) : LinearLayout(context) {
    val text = TextView(context)
    init {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER
        clipChildren = false
        clipToPadding = false
        val pad = Ui.dp(context, if (primary) 8 else 9)
        setPadding(pad, 0, pad, 0)
        text.apply {
            this.text = label
            setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, Ui.textPx(context, 13f))
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            setTextColor(Ui.INK)
            includeFontPadding = false
            isSingleLine = true
        }
        addView(icon, LayoutParams(Ui.sdp(context, if (primary) 48 else 15), Ui.sdp(context, if (primary) 30 else 15)))
        addView(text, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply { marginStart = Ui.dp(context, 4) })
        if (primary) text.visibility = GONE
        val radius = Ui.sdp(context, 20).toFloat()
        if (primary) {
            contentDescription = label
        } else {
            background = StateListDrawable().apply {
                addState(
                    intArrayOf(android.R.attr.state_pressed),
                    GradientDrawable().apply {
                        cornerRadius = radius
                        setColor(0x1FE8451D)
                    },
                )
            }
        }
        isClickable = true
        isFocusable = false
        Ui.springPress(this)
    }

    fun setActive(on: Boolean) {
        isEnabled = on
        animate().alpha(if (on) 1f else 0.4f).setDuration(160).start()
        if (primary) icon.flow = on
    }

    // Puts the chip at its final alpha at once: cancelling the rise-in also freezes a running fade at its start value.
    fun settleAlpha() {
        alpha = if (isEnabled) 1f else 0.4f
    }
}

// The floating actions over the page while the address is edited: Copy, Paste, Clear and Go. They never take focus,
// so the keyboard and the cursor stay in the address field. Copy, Clear and Go dim when the field is empty, Paste dims
// when the clipboard holds no text, and Go lights up and pops as soon as there is something to open (DECISIONS.md 68).
class EditBar(
    context: Context,
    onCopy: () -> Unit,
    onPaste: () -> Unit,
    onClear: () -> Unit,
    onGo: () -> Unit,
) : LinearLayout(context) {
    private val copy = Chip(context, EditIcon(context, EditIcon.Kind.COPY, Ui.INK), "Copy", false)
    private val paste = Chip(context, EditIcon(context, EditIcon.Kind.PASTE, Ui.INK), "Paste", false)
    private val clear = Chip(context, EditIcon(context, EditIcon.Kind.CLEAR, Ui.INK), "Clear", false)
    private val go = Chip(context, EditIcon(context, EditIcon.Kind.GO, Ui.INK), "Go", true)
    private var hadText = false
    private val restoreCopy = Runnable {
        copy.icon.kind = EditIcon.Kind.COPY
        copy.icon.tint = Ui.INK
        copy.text.text = "Copy"
    }

    init {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        val pad = Ui.dp(context, 5)
        setPadding(pad, pad, pad, pad)
        clipChildren = false
        clipToPadding = false
        background = GradientDrawable().apply {
            setColor(0xF2FFFFFF.toInt())
            cornerRadius = Ui.sdp(context, 30).toFloat()
            setStroke(Ui.dp(context, 1), 0x40E8451D)
        }
        elevation = Ui.dp(context, 8).toFloat()
        outlineSpotShadowColor = 0x70E8451D
        outlineAmbientShadowColor = 0x22000000
        visibility = GONE
        alpha = 0f
        copy.minimumWidth = (copy.paddingLeft * 2 + Ui.sdp(context, 15) + Ui.dp(context, 4) + copy.text.paint.measureText("Copied")).toInt()
        val h = Ui.sdp(context, 36)
        val gap = Ui.dp(context, 2)
        for ((i, chip) in listOf(copy, paste, clear, go).withIndex()) {
            addView(chip, LayoutParams(LayoutParams.WRAP_CONTENT, h).apply { if (i > 0) marginStart = gap })
        }
        copy.setOnClickListener {
            if (!it.isEnabled) return@setOnClickListener
            it.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
            onCopy()
            copied()
        }
        paste.setOnClickListener {
            if (!it.isEnabled) return@setOnClickListener
            it.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
            onPaste()
        }
        clear.setOnClickListener {
            if (!it.isEnabled) return@setOnClickListener
            it.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
            onClear()
        }
        go.setOnClickListener {
            if (!it.isEnabled) return@setOnClickListener
            it.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
            onGo()
        }
        refresh(false, true)
    }

    // hasText: the address field holds text. clipboardText: the clipboard holds text.
    fun refresh(hasText: Boolean, clipboardText: Boolean) {
        copy.setActive(hasText)
        clear.setActive(hasText)
        go.setActive(hasText)
        paste.setActive(clipboardText)
        if (hasText && !hadText && visibility == VISIBLE) {
            go.scaleX = 0.84f
            go.scaleY = 0.84f
            go.animate().scaleX(1f).scaleY(1f).setDuration(440).setInterpolator(SpringInterpolator()).start()
        }
        hadText = hasText
    }

    // The four actions rise into place one after another.
    fun playIn() {
        val chips = listOf(copy, paste, clear, go)
        for ((i, chip) in chips.withIndex()) {
            chip.animate().cancel()
            chip.settleAlpha()
            chip.translationY = Ui.dp(context, 8).toFloat()
            chip.scaleX = 0.9f
            chip.scaleY = 0.9f
            chip.animate().translationY(0f).scaleX(1f).scaleY(1f)
                .setStartDelay(110L + i * 45L).setDuration(420).setInterpolator(SpringInterpolator()).start()
        }
    }

    private fun copied() {
        copy.icon.kind = EditIcon.Kind.CHECK
        copy.icon.tint = 0xFF1E9E5A.toInt()
        copy.text.text = "Copied"
        copy.icon.animate().cancel()
        copy.icon.scaleX = 0.4f
        copy.icon.scaleY = 0.4f
        copy.icon.animate().scaleX(1f).scaleY(1f).setDuration(380).setInterpolator(SpringInterpolator()).start()
        removeCallbacks(restoreCopy)
        postDelayed(restoreCopy, 1300)
    }
}
