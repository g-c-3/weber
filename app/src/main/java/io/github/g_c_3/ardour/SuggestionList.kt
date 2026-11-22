package io.github.g_c_3.ardour

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView

// Small magnifier drawn by hand, in front of each suggestion.
private class SuggestIcon(context: Context) : View(context) {
    private val u = Ui.dp(context, 1).toFloat() * Ui.geoScale(context)
    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = u * 1.6f
        strokeCap = Paint.Cap.ROUND
        color = 0xFF9A9CA6.toInt()
    }

    override fun onDraw(canvas: Canvas) {
        val cx = width / 2f
        val cy = height / 2f
        canvas.drawCircle(cx - 1.5f * u, cy - 1.5f * u, 4.6f * u, line)
        canvas.drawLine(cx + 1.8f * u, cy + 1.8f * u, cx + 6f * u, cy + 6f * u, line)
    }
}

// The suggestions under a search field or the address bar (DECISIONS.md 119, 125). Rows never take focus, so the
// keyboard and the cursor stay in the field; a tap on a row searches it.
class SuggestionList(context: Context, private val onPick: (String) -> Unit) : LinearLayout(context) {
    private var listed = false
    private val rowHeight = Ui.sdp(context, 44)
    private val inset = Ui.dp(context, 4)

    init {
        orientation = VERTICAL
        visibility = GONE
        alpha = 0f
        setPadding(0, inset, 0, inset)
        background = GradientDrawable().apply {
            setColor(0xF7FFFFFF.toInt())
            cornerRadius = Ui.sdp(context, 22).toFloat()
            setStroke(Ui.dp(context, 1), 0x40E8451D)
        }
        elevation = Ui.dp(context, 8).toFloat()
        outlineSpotShadowColor = 0x70E8451D
        outlineAmbientShadowColor = 0x22000000
    }

    fun showItems(items: List<String>) {
        if (items.isEmpty()) {
            hideList()
            return
        }
        removeAllViews()
        for (item in items) {
            addView(row(item), LayoutParams(LayoutParams.MATCH_PARENT, rowHeight).apply {
                marginStart = inset
                marginEnd = inset
            })
        }
        if (!listed) {
            listed = true
            animate().cancel()
            visibility = VISIBLE
            alpha = 0f
            translationY = -Ui.dp(context, 6).toFloat()
            animate().alpha(1f).translationY(0f).setDuration(260).setInterpolator(SpringInterpolator()).start()
        }
    }

    fun hideList() {
        if (!listed) return
        listed = false
        animate().cancel()
        animate().alpha(0f).setDuration(120).withEndAction {
            if (!listed) {
                visibility = GONE
                removeAllViews()
            }
        }.start()
    }

    private fun row(item: String): LinearLayout = LinearLayout(context).apply {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(Ui.dp(context, 10), 0, Ui.dp(context, 10), 0)
        contentDescription = item
        background = StateListDrawable().apply {
            addState(
                intArrayOf(android.R.attr.state_pressed),
                GradientDrawable().apply {
                    cornerRadius = Ui.sdp(context, 16).toFloat()
                    setColor(0x1FE8451D)
                },
            )
        }
        addView(SuggestIcon(context), LayoutParams(Ui.sdp(context, 18), Ui.sdp(context, 18)))
        addView(
            TextView(context).apply {
                text = item
                setTextSize(TypedValue.COMPLEX_UNIT_PX, Ui.textPx(context, 15.5f))
                setTextColor(Ui.INK)
                includeFontPadding = false
                isSingleLine = true
                ellipsize = TextUtils.TruncateAt.END
            },
            LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f).apply { marginStart = Ui.dp(context, 10) },
        )
        isClickable = true
        isFocusable = false
        Ui.springPress(this)
        setOnClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
            onPick(item)
        }
    }
}
