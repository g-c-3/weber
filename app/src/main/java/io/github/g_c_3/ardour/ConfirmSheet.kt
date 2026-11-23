package io.github.g_c_3.ardour

import android.animation.ValueAnimator
import android.app.Activity
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView

// One tickable choice on a confirmation card: a title, one line under it and its starting state.
class Choice(val label: String, val detail: String, val checked: Boolean)

// A confirmation card over the page, drawn by Ardour (DECISIONS.md 38): a dimmed backdrop, a card that rises in with a
// spring, optional tickable choices, a quiet Cancel (own label and action on request) and a glowing confirm button. A
// tap outside the card, or Back, cancels without running the quiet button's action. With choices, the confirm button dims while none is ticked and hands back the ticked states in order.
class ConfirmSheet(private val activity: Activity, private val onChange: () -> Unit) {
    private var backdrop: FrameLayout? = null
    private var card: LinearLayout? = null

    val showing: Boolean get() = backdrop != null

    // Builds the card and rises it in. Cancel, a tap outside and Back dismiss it; confirm hands back the ticked states.
    fun show(
        title: String,
        body: String,
        confirm: String,
        choices: List<Choice> = emptyList(),
        cancelLabel: String = "Cancel",
        onCancelTap: (() -> Unit)? = null,
        dangerConfirm: Boolean = false,
        infoOnly: Boolean = false,
        onConfirm: (List<Boolean>) -> Unit,
    ) {
        if (backdrop != null) return
        val dim = FrameLayout(activity).apply {
            background = ColorDrawable(0x66101018)
            isClickable = true
            alpha = 0f
            setOnClickListener { dismiss() }
        }
        val box = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                setColor(Ui.CARD)
                cornerRadius = Ui.dp(activity, 26).toFloat()
                setStroke(Ui.dp(activity, 1), 0x40E8451D)
            }
            elevation = Ui.dp(activity, 14).toFloat()
            outlineSpotShadowColor = 0x70E8451D
            setPadding(Ui.dp(activity, 24), Ui.dp(activity, 24), Ui.dp(activity, 24), Ui.dp(activity, 16))
            isClickable = true
        }
        box.addView(Ui.title(activity, title).apply { textSize = 20f })
        box.addView(
            Ui.body(activity, body).apply {
                textSize = 15f
                setPadding(0, Ui.dp(activity, 10), 0, Ui.dp(activity, if (choices.isEmpty()) 22 else 12))
            },
        )
        val states = choices.map { it.checked }.toMutableList()
        var confirmButton: TextView? = null
        val refreshConfirm = { confirmButton?.let { Ui.setBusy(it, choices.isNotEmpty() && states.none { on -> on }) } }
        choices.forEachIndexed { i, choice ->
            box.addView(
                ChoiceRow(activity, choice) { on ->
                    states[i] = on
                    refreshConfirm()
                },
            )
        }
        if (choices.isNotEmpty()) box.addView(View(activity), LinearLayout.LayoutParams(1, Ui.dp(activity, 10)))
        val row = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.END or Gravity.CENTER_VERTICAL
        }
        val quietCancel = TextView(activity).apply {
            text = cancelLabel
            textSize = 16f
            typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL)
            setTextColor(Ui.MUTED)
            gravity = Gravity.CENTER
            setPadding(Ui.dp(activity, 20), Ui.dp(activity, 15), Ui.dp(activity, 20), Ui.dp(activity, 15))
            isClickable = true
            setOnClickListener {
                dismiss()
                onCancelTap?.invoke()
            }
        }
        Ui.springPress(quietCancel)
        // A destructive confirm sits faded in neon red and lights up when pressed; the other button becomes the glowing one.
        val cancel = if (dangerConfirm) {
            Ui.pillButton(activity, cancelLabel).apply {
                setOnClickListener {
                    dismiss()
                    onCancelTap?.invoke()
                }
            }
        } else {
            quietCancel
        }
        val go = if (dangerConfirm) {
            dangerButton(confirm) { onConfirm(states.toList()) }
        } else {
            Ui.pillButton(activity, confirm).apply {
                setOnClickListener {
                    dismiss()
                    onConfirm(states.toList())
                }
            }
        }
        confirmButton = go
        refreshConfirm()
        val wrap = ViewGroup.LayoutParams.WRAP_CONTENT
        val first = if (dangerConfirm) go else cancel
        val second = if (dangerConfirm) cancel else go
        if (infoOnly) {
            row.addView(go, LinearLayout.LayoutParams(wrap, wrap))
        } else {
            row.addView(first, LinearLayout.LayoutParams(wrap, wrap))
            row.addView(second, LinearLayout.LayoutParams(wrap, wrap).apply { if (dangerConfirm) marginStart = Ui.dp(activity, 12) })
        }
        box.addView(row, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        val margin = Ui.dp(activity, 16)
        dim.addView(
            box,
            FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM).apply {
                setMargins(margin, margin, margin, Ui.dp(activity, 28))
            },
        )
        activity.addContentView(dim, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        backdrop = dim
        card = box
        box.translationY = Ui.dp(activity, 48).toFloat()
        dim.animate().alpha(1f).setDuration(220).start()
        box.animate().translationY(0f).setDuration(460).setInterpolator(SpringInterpolator()).start()
        onChange()
    }

    // An explanation card with one glowing button (the Settings info buttons, DECISIONS.md 140).
    fun showInfo(title: String, body: String) = show(title, body, "Got it", infoOnly = true) { }

    // Neon red, faded at rest; a press or tap lights it fully, and the action runs a moment later.
    private fun dangerButton(label: String, action: () -> Unit): TextView {
        val neon = 0xFFFF2D55.toInt()
        val view = TextView(activity).apply {
            text = label
            textSize = 16f
            typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL)
            gravity = Gravity.CENTER
            setPadding(Ui.dp(activity, 22), Ui.dp(activity, 15), Ui.dp(activity, 22), Ui.dp(activity, 15))
            isClickable = true
            isFocusable = true
        }
        var fired = false
        fun paint(hot: Boolean) {
            view.setTextColor(if (hot) 0xFFFFFFFF.toInt() else 0x99FF2D55.toInt())
            view.background = GradientDrawable().apply {
                cornerRadius = Ui.dp(activity, 28).toFloat()
                if (hot) setColor(neon) else {
                    setColor(0x14FF2D55)
                    setStroke(Ui.dp(activity, 1), 0x59FF2D55)
                }
            }
            view.elevation = if (hot) Ui.dp(activity, 8).toFloat() else 0f
            view.outlineSpotShadowColor = neon
        }
        paint(false)
        Ui.springPress(view) { down -> if (down) paint(true) else if (!fired) paint(false) }
        view.setOnClickListener {
            if (fired) return@setOnClickListener
            fired = true
            paint(true)
            view.postDelayed({
                dismiss()
                action()
            }, 240)
        }
        return view
    }

    fun dismiss() {
        val dim = backdrop ?: return
        val box = card
        backdrop = null
        card = null
        box?.animate()?.translationY(Ui.dp(activity, 48).toFloat())?.setDuration(220)?.start()
        dim.animate().alpha(0f).setDuration(220).withEndAction {
            (dim.parent as? ViewGroup)?.removeView(dim)
        }.start()
        onChange()
    }
}

// A tickable row: a hand-drawn box that fills ember with a check, then the label and one line of detail.
private class ChoiceRow(context: Context, choice: Choice, private val onToggle: (Boolean) -> Unit) : LinearLayout(context) {
    private val box = CheckBox(context, choice.checked)
    private var on = choice.checked

    init {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(0, Ui.dp(context, 9), 0, Ui.dp(context, 9))
        isClickable = true
        isFocusable = true
        val side = Ui.dp(context, 24)
        addView(box, LayoutParams(side, side).apply { rightMargin = Ui.dp(context, 14) })
        val texts = LinearLayout(context).apply { orientation = VERTICAL }
        texts.addView(TextView(context).apply {
            text = choice.label
            textSize = 16f
            typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL)
            setTextColor(Ui.INK)
        })
        texts.addView(TextView(context).apply {
            text = choice.detail
            textSize = 13f
            setTextColor(Ui.MUTED)
            setLineSpacing(0f, 1.2f)
            setPadding(0, Ui.dp(context, 2), 0, 0)
        })
        addView(texts, LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        Ui.springPress(this)
        setOnClickListener {
            on = !on
            box.set(on)
            onToggle(on)
        }
    }
}

private class CheckBox(context: Context, start: Boolean) : View(context) {
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Ui.EMBER }
    private val edge = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFC9C6C0.toInt()
        style = Paint.Style.STROKE
        strokeWidth = Ui.dp(context, 2).toFloat()
    }
    private val tick = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFFFFFFF.toInt()
        style = Paint.Style.STROKE
        strokeWidth = Ui.dp(context, 2.4f)
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val rect = RectF()
    private val path = Path()
    private var progress = if (start) 1f else 0f
    private var animator: ValueAnimator? = null

    fun set(on: Boolean) {
        animator?.cancel()
        animator = ValueAnimator.ofFloat(progress, if (on) 1f else 0f).apply {
            duration = 200
            interpolator = DecelerateInterpolator()
            addUpdateListener {
                progress = it.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    override fun onDetachedFromWindow() {
        animator?.cancel()
        super.onDetachedFromWindow()
    }

    override fun onDraw(canvas: Canvas) {
        val inset = edge.strokeWidth / 2f
        rect.set(inset, inset, width - inset, height - inset)
        val r = Ui.dp(context, 7).toFloat()
        edge.alpha = (255 * (1f - progress)).toInt()
        canvas.drawRoundRect(rect, r, r, edge)
        fill.alpha = (255 * progress).toInt()
        canvas.drawRoundRect(rect, r, r, fill)
        if (progress > 0f) {
            val w = width.toFloat()
            val h = height.toFloat()
            path.reset()
            path.moveTo(w * 0.27f, h * 0.52f)
            path.lineTo(w * 0.44f, h * 0.68f)
            path.lineTo(w * 0.74f, h * 0.34f)
            tick.alpha = (255 * progress).toInt()
            canvas.save()
            canvas.scale(0.6f + 0.4f * progress, 0.6f + 0.4f * progress, w / 2f, h / 2f)
            canvas.drawPath(path, tick)
            canvas.restore()
        }
    }
}
