package io.github.g_c_3.ardour

import android.animation.ArgbEvaluator
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.TextView

// One row of the browser menu. `toggle` null means a plain action; otherwise it is the switch's current state.
class MenuItem(
    val label: String,
    val enabled: Boolean = true,
    val toggle: Boolean? = null,
    val onTap: () -> Unit,
)

// Card that opens from the menu button, drawn by Ardour (DECISIONS.md 38).
object MenuPopup {
    fun show(anchor: View, items: List<MenuItem>) {
        val context = anchor.context
        val card = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            minimumWidth = Ui.dp(context, 232)
            background = GradientDrawable().apply {
                setColor(Ui.CARD)
                cornerRadius = Ui.dp(context, 20).toFloat()
                setStroke(Ui.dp(context, 1), Ui.LINE)
            }
            elevation = Ui.dp(context, 10).toFloat()
            outlineSpotShadowColor = 0x66000000
            setPadding(0, Ui.dp(context, 6), 0, Ui.dp(context, 6))
        }
        val margin = Ui.dp(context, 14)
        val holder = FrameLayout(context).apply {
            setPadding(margin, margin, margin, margin)
            clipToPadding = false
            addView(card, FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }
        val popup = PopupWindow(holder, ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, true).apply {
            setBackgroundDrawable(ColorDrawable(0))
            isOutsideTouchable = true
        }
        for (item in items) card.addView(row(context, item, popup))

        holder.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED)
        val loc = IntArray(2)
        anchor.getLocationOnScreen(loc)
        val x = context.resources.displayMetrics.widthPixels - holder.measuredWidth
        // Above the button when it sits low on the screen, below it when it sits at the top.
        val below = loc[1] < context.resources.displayMetrics.heightPixels / 2
        val y = if (below) loc[1] + anchor.height - margin else loc[1] - holder.measuredHeight + margin
        popup.showAtLocation(anchor.rootView, Gravity.NO_GRAVITY, x, y)

        card.alpha = 0f
        card.scaleX = 0.86f
        card.scaleY = 0.86f
        card.post {
            card.pivotX = card.width.toFloat()
            card.pivotY = if (below) 0f else card.height.toFloat()
            card.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(380).setInterpolator(SpringInterpolator()).start()
        }
    }

    private fun row(context: Context, item: MenuItem, popup: PopupWindow): View {
        val pressed = ColorDrawable(Ui.PAPER)
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(Ui.dp(context, 20), Ui.dp(context, 14), Ui.dp(context, 20), Ui.dp(context, 14))
            background = StateListDrawable().apply {
                addState(intArrayOf(android.R.attr.state_pressed), pressed)
                addState(intArrayOf(), ColorDrawable(0))
            }
            alpha = if (item.enabled) 1f else 0.35f
            isEnabled = item.enabled
            isClickable = true
            isFocusable = true
        }
        row.addView(TextView(context).apply {
            text = item.label
            textSize = 16f
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            setTextColor(Ui.INK)
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        val switch = item.toggle?.let { ToggleSwitch(context, it) }
        if (switch != null) {
            row.addView(switch, LinearLayout.LayoutParams(Ui.dp(context, 44), Ui.dp(context, 26)).apply {
                marginStart = Ui.dp(context, 16)
            })
        }
        row.setOnClickListener {
            if (switch != null) {
                switch.on = !switch.on
                item.onTap()
                row.postDelayed({ popup.dismiss() }, 240)
            } else {
                popup.dismiss()
                item.onTap()
            }
        }
        return row
    }
}

// Pill switch: ember when on; the knob settles with the shared spring.
class ToggleSwitch(context: Context, initial: Boolean) : View(context) {
    private var pos = if (initial) 1f else 0f
    private var anim: ValueAnimator? = null
    private val track = Paint(Paint.ANTI_ALIAS_FLAG)
    private val knob = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Ui.CARD }
    private val rect = RectF()
    private val offColor = 0xFFD9D7D2.toInt()

    var on: Boolean = initial
        set(v) {
            field = v
            val from = pos
            anim?.cancel()
            anim = ValueAnimator.ofFloat(from, if (v) 1f else 0f).apply {
                duration = 360
                interpolator = SpringInterpolator()
                addUpdateListener {
                    pos = it.animatedValue as Float
                    invalidate()
                }
                start()
            }
        }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        track.color = ArgbEvaluator().evaluate(pos.coerceIn(0f, 1f), offColor, Ui.EMBER) as Int
        rect.set(0f, 0f, w, h)
        canvas.drawRoundRect(rect, h / 2f, h / 2f, track)
        val inset = Ui.dp(context, 3).toFloat()
        val r = h / 2f - inset
        val cx = inset + r + (w - 2f * (inset + r)) * pos
        canvas.drawCircle(cx, h / 2f, r, knob)
    }

    override fun onDetachedFromWindow() {
        anim?.cancel()
        super.onDetachedFromWindow()
    }
}
