package io.github.g_c_3.ardour

import android.animation.ArgbEvaluator
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.util.TypedValue
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.animation.AccelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.TextView
import kotlin.math.max

// One row of the browser menu. `toggle` null means a plain action; otherwise it is the switch's current state.
class MenuItem(
    val label: String,
    val enabled: Boolean = true,
    val toggle: Boolean? = null,
    val dividerBefore: Boolean = false,
    val onTap: () -> Unit,
)

// Card that grows out of the menu button, drawn by Ardour (DECISIONS.md 38, 104): the dots turn, the page dims softly, the
// rows settle in one after another; closing reverses it. Thin lines separate the groups of rows.
object MenuPopup {
    fun show(anchor: View, items: List<MenuItem>) {
        val context = anchor.context
        val card = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                setColor(Ui.CARD)
                cornerRadius = Ui.dp(context, 18).toFloat()
                setStroke(Ui.dp(context, 1), Ui.LINE)
            }
            elevation = Ui.dp(context, 10).toFloat()
            outlineSpotShadowColor = 0x66000000
            setPadding(0, Ui.dp(context, 4), 0, Ui.dp(context, 4))
            isClickable = true
        }
        val margin = Ui.dp(context, 14)
        val holder = FrameLayout(context).apply {
            setPadding(margin, margin, margin, margin)
            clipToPadding = false
            addView(card, FrameLayout.LayoutParams(naturalWidth(context, items), ViewGroup.LayoutParams.WRAP_CONTENT))
        }
        val popup = PopupWindow(holder, ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, true).apply {
            setBackgroundDrawable(ColorDrawable(0))
            isOutsideTouchable = true
        }
        // Closing fades and shrinks the card first, then removes the window. A touch outside it, or on the clear margin
        // around it, closes it the same way.
        var closing = false
        var below = false
        var dimAmount = 0f
        var growFrom = 0f
        // The page behind the card dims with the card's progress (the popup window's own dim).
        fun setDim(amount: Float) {
            dimAmount = amount
            val root = holder.rootView
            val lp = root.layoutParams as? WindowManager.LayoutParams ?: return
            lp.flags = lp.flags or WindowManager.LayoutParams.FLAG_DIM_BEHIND
            lp.dimAmount = amount
            (context.getSystemService(Context.WINDOW_SERVICE) as WindowManager).updateViewLayout(root, lp)
        }
        fun close() {
            if (closing) return
            closing = true
            ValueAnimator.ofFloat(dimAmount, 0f).apply {
                duration = 150
                addUpdateListener { if (popup.isShowing) setDim(it.animatedValue as Float) }
                start()
            }
            card.animate().cancel()
            card.animate().alpha(0f).scaleX(0.94f).scaleY(0.94f).translationY(if (below) -Ui.dp(context, 4).toFloat() else Ui.dp(context, 4).toFloat())
                .setDuration(150).setInterpolator(AccelerateInterpolator()).withEndAction { popup.dismiss() }.start()
        }
        holder.setOnClickListener { close() }
        popup.setTouchInterceptor { _, event ->
            if (event.action == MotionEvent.ACTION_OUTSIDE) {
                close()
                true
            } else {
                false
            }
        }
        for (item in items) {
            if (item.dividerBefore) card.addView(divider(context))
            card.addView(row(context, item, ::close))
        }
        // The menu button turns while the card is open and always turns back, however the card closes.
        anchor.animate().rotation(90f).setDuration(420).setInterpolator(SpringInterpolator()).start()
        popup.setOnDismissListener { anchor.animate().rotation(0f).setDuration(420).setInterpolator(SpringInterpolator()).start() }

        holder.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED)
        val loc = IntArray(2)
        anchor.getLocationOnScreen(loc)
        // The card sits under the Reload and menu buttons: its right edge a little left of the menu button's centre,
        // never closer than 8 dp to the screen's left edge. The card grows out of the menu button's centre.
        val screenWidth = context.resources.displayMetrics.widthPixels
        val anchorCentre = loc[0] + anchor.width / 2
        val cardWidth = holder.measuredWidth - 2 * margin
        val edge = Ui.dp(context, 8)
        val cardLeft = max(edge, minOf(anchorCentre - Ui.dp(context, 36) - cardWidth, screenWidth - cardWidth - edge))
        val x = cardLeft - margin
        growFrom = (anchorCentre - cardLeft).toFloat()
        // Above the button when it sits low on the screen, below it when it sits at the top.
        below = loc[1] < context.resources.displayMetrics.heightPixels / 2
        val y = if (below) loc[1] + anchor.height - margin else loc[1] - holder.measuredHeight + margin
        popup.showAtLocation(anchor.rootView, Gravity.NO_GRAVITY, x, y)

        // Opens out of the button's corner with a spring, then the rows settle in one after another.
        val lift = Ui.dp(context, 10).toFloat()
        val finals = (0 until card.childCount).map { card.getChildAt(it).alpha }
        for (i in 0 until card.childCount) {
            card.getChildAt(i).apply {
                alpha = 0f
                translationY = if (below) -lift else lift
            }
        }
        card.alpha = 0f
        card.scaleX = 0.6f
        card.scaleY = 0.6f
        card.post {
            card.pivotX = growFrom
            card.pivotY = if (below) 0f else card.height.toFloat()
            ObjectAnimator.ofFloat(card, View.ALPHA, 0f, 1f).setDuration(140).apply { interpolator = DecelerateInterpolator() }.start()
            card.animate().scaleX(1f).scaleY(1f).setDuration(440).setInterpolator(SpringInterpolator()).start()
            ValueAnimator.ofFloat(0f, 0.14f).apply {
                duration = 220
                addUpdateListener { if (!closing && popup.isShowing) setDim(it.animatedValue as Float) }
                start()
            }
            for (i in 0 until card.childCount) {
                card.getChildAt(i).animate().alpha(finals[i]).translationY(0f)
                    .setStartDelay(60L + i * 34L).setDuration(380).setInterpolator(SpringInterpolator()).start()
            }
        }
    }

    // The card is exactly as wide as its longest row plus 26 dp each side, measured from the text itself so that nothing
    // else can make it wider (DECISIONS.md 108).
    private fun naturalWidth(context: Context, items: List<MenuItem>): Int {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            textSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 15f, context.resources.displayMetrics)
        }
        var widest = 0f
        for (item in items) {
            var w = paint.measureText(item.label)
            if (item.toggle != null) w += Ui.dp(context, 10) + max(paint.measureText("Off"), paint.measureText("On"))
            widest = max(widest, w)
        }
        return widest.toInt() + Ui.dp(context, 2 * 26 + 4)
    }

    // Thin line between groups of rows.
    private fun divider(context: Context): View = View(context).apply {
        setBackgroundColor(Ui.LINE)
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(context, 1)).apply {
            setMargins(Ui.dp(context, 20), Ui.dp(context, 2), Ui.dp(context, 20), Ui.dp(context, 2))
        }
    }

    // One centred row: a plain action, or a toggle shown as an Off or On word beside its label (DECISIONS.md 106, 107).
    private fun row(context: Context, item: MenuItem, close: () -> Unit): View {
        val pressed = ColorDrawable(Ui.PAPER)
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(Ui.dp(context, 26), Ui.dp(context, 10), Ui.dp(context, 26), Ui.dp(context, 10))
            background = StateListDrawable().apply {
                addState(intArrayOf(android.R.attr.state_pressed), pressed)
                addState(intArrayOf(), ColorDrawable(0))
            }
            alpha = if (item.enabled) 1f else 0.35f
            isEnabled = item.enabled
            isClickable = true
            isFocusable = true
        }
        val label = TextView(context).apply {
            text = item.label
            textSize = 15f
            gravity = Gravity.CENTER
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            setTextColor(Ui.INK)
        }
        // A toggle reads "Label  Off" in amber, centred as one group. Off and On share one slot as wide as the wider word,
        // so the label never moves; a tap swaps the words in place.
        var state = item.toggle
        val off = stateWord(context, "Off")
        val on = stateWord(context, "On")
        fun paint(animate: Boolean) {
            for ((word, lit) in listOf(off to (state == false), on to (state == true))) {
                val alpha = if (lit) 1f else 0f
                val scale = if (lit) 1f else 0.7f
                if (animate) {
                    word.animate().alpha(alpha).scaleX(scale).scaleY(scale).setDuration(340).setInterpolator(SpringInterpolator()).start()
                } else {
                    word.alpha = alpha
                    word.scaleX = scale
                    word.scaleY = scale
                }
            }
        }
        if (state != null) {
            val slot = FrameLayout(context).apply {
                addView(off, FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER))
                addView(on, FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER))
            }
            row.addView(label)
            row.addView(slot, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                marginStart = Ui.dp(context, 10)
            })
            paint(false)
        } else {
            row.addView(label, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }
        row.setOnClickListener {
            if (state != null) {
                state = !(state as Boolean)
                paint(true)
                item.onTap()
                row.postDelayed({ close() }, 320)
            } else {
                close()
                item.onTap()
            }
        }
        return row
    }

    private fun stateWord(context: Context, text: String) = TextView(context).apply {
        this.text = text
        textSize = 15f
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        setTextColor(Ui.AMBER)
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
