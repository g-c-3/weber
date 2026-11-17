package io.github.g_c_3.ardour

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView

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

    private val u = Ui.dp(context, 1).toFloat()
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

    override fun onDraw(canvas: Canvas) {
        val cx = width / 2f
        val cy = height / 2f
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
                canvas.drawLine(cx - 6f * u, cy, cx + 6f * u, cy, line)
                path.reset()
                path.moveTo(cx + 1.5f * u, cy - 4.5f * u)
                path.lineTo(cx + 6f * u, cy)
                path.lineTo(cx + 1.5f * u, cy + 4.5f * u)
                canvas.drawPath(path, line)
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
    private val activeBg: GradientDrawable
    private val idleBg: GradientDrawable

    init {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER
        val pad = Ui.dp(context, if (primary) 14 else 9)
        setPadding(pad, 0, pad, 0)
        text.apply {
            this.text = label
            textSize = 13f
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            setTextColor(Ui.INK)
            includeFontPadding = false
            isSingleLine = true
        }
        addView(icon, LayoutParams(Ui.dp(context, 15), Ui.dp(context, 15)))
        addView(text, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply { marginStart = Ui.dp(context, 4) })
        val radius = Ui.dp(context, 20).toFloat()
        activeBg = GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, intArrayOf(Ui.EMBER, Ui.AMBER)).apply { cornerRadius = radius }
        idleBg = GradientDrawable().apply {
            cornerRadius = radius
            setColor(0x14242630)
        }
        if (primary) {
            background = idleBg
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
        if (primary) {
            background = if (on) activeBg else idleBg
            val c = if (on) 0xFFFFFFFF.toInt() else Ui.INK
            text.setTextColor(c)
            icon.tint = c
            elevation = if (on) Ui.dp(context, 4).toFloat() else 0f
            outlineSpotShadowColor = 0xB0E8451D.toInt()
        }
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
            cornerRadius = Ui.dp(context, 28).toFloat()
            setStroke(Ui.dp(context, 1), 0x40E8451D)
        }
        elevation = Ui.dp(context, 8).toFloat()
        outlineSpotShadowColor = 0x70E8451D
        outlineAmbientShadowColor = 0x22000000
        visibility = GONE
        alpha = 0f
        copy.minimumWidth = (copy.paddingLeft * 2 + Ui.dp(context, 15) + Ui.dp(context, 4) + copy.text.paint.measureText("Copied")).toInt()
        val h = Ui.dp(context, 36)
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
