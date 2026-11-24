package io.github.g_c_3.ardour

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.drawable.GradientDrawable
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.TextView

// Search field at the top of Settings (DECISIONS.md 147): pill with a drawn magnifier, ember rim while focused, drawn clear
// button once there is text. The text never leaves the field except to filter the rows.
class SearchField(context: Context, private val onQuery: (String) -> Unit) : FrameLayout(context) {
    private val input = EditText(context)
    private val clear = CrossView(context)
    private val rim = GradientDrawable().apply {
        setColor(Ui.CARD)
        cornerRadius = Ui.dp(context, 24).toFloat()
        setStroke(Ui.dp(context, 1), Ui.LINE)
    }
    // Second outline faded in over the grey one, so the rim does not step in whole pixels.
    private val emberRim = GradientDrawable().apply {
        cornerRadius = Ui.dp(context, 24).toFloat()
        setStroke(Ui.dp(context, 2), Ui.EMBER)
        alpha = 0
    }

    init {
        background = rim
        foreground = emberRim
        addView(MagnifierView(context), FrameLayout.LayoutParams(Ui.dp(context, 20), Ui.dp(context, 20), Gravity.START or Gravity.CENTER_VERTICAL).apply {
            marginStart = Ui.dp(context, 16)
        })
        input.apply {
            background = null
            hint = "Search settings"
            setHintTextColor(0xFF9A9CA6.toInt())
            setTextColor(Ui.INK)
            setTextSize(TypedValue.COMPLEX_UNIT_PX, Ui.textPx(context, 15.5f))
            isSingleLine = true
            gravity = Gravity.CENTER_VERTICAL
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            imeOptions = EditorInfo.IME_ACTION_SEARCH or EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING
            setPadding(Ui.dp(context, 46), 0, Ui.dp(context, 44), 0)
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                override fun afterTextChanged(s: Editable?) {
                    clear.visibility = if (s.isNullOrEmpty()) View.GONE else View.VISIBLE
                    onQuery(s?.toString().orEmpty())
                }
            })
            setOnFocusChangeListener { _, focused -> fadeRim(focused) }
            setOnEditorActionListener { v, _, _ ->
                context.getSystemService(InputMethodManager::class.java)?.hideSoftInputFromWindow(v.windowToken, 0)
                v.clearFocus()
                true
            }
        }
        addView(input, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        clear.visibility = View.GONE
        clear.contentDescription = "Clear search"
        clear.setOnClickListener { input.setText("") }
        addView(clear, FrameLayout.LayoutParams(Ui.dp(context, 44), Ui.dp(context, 44), Gravity.END or Gravity.CENTER_VERTICAL))
    }

    private fun fadeRim(focused: Boolean) {
        ValueAnimator.ofInt(emberRim.alpha, if (focused) 255 else 0).apply {
            duration = 180
            addUpdateListener {
                emberRim.alpha = it.animatedValue as Int
                invalidate()
            }
            start()
        }
    }
}

private class MagnifierView(context: Context) : View(context) {
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Ui.MUTED
        style = Paint.Style.STROKE
        strokeWidth = Ui.dp(context, 2).toFloat()
        strokeCap = Paint.Cap.ROUND
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val r = w * 0.32f
        val cx = w * 0.42f
        val cy = height * 0.42f
        canvas.drawCircle(cx, cy, r, stroke)
        val d = r * 0.72f
        canvas.drawLine(cx + d, cy + d, w * 0.88f, height * 0.88f, stroke)
    }
}

private class CrossView(context: Context) : View(context) {
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Ui.MUTED
        style = Paint.Style.STROKE
        strokeWidth = Ui.dp(context, 2).toFloat()
        strokeCap = Paint.Cap.ROUND
    }

    override fun onDraw(canvas: Canvas) {
        val cx = width / 2f
        val cy = height / 2f
        val s = Ui.dp(context, 5).toFloat()
        canvas.drawLine(cx - s, cy - s, cx + s, cy + s, stroke)
        canvas.drawLine(cx - s, cy + s, cx + s, cy - s, stroke)
    }
}

// Filters the Settings cards (DECISIONS.md 147): a row stays when every word typed is in its title, note or buttons; a
// section label that matches keeps its whole card. Cards and labels with no row left are hidden, and a divider is shown
// only between two visible rows. Only views this class hid are shown again, so nothing else's visibility is overridden.
class SettingsFilter(column: ViewGroup, start: Int, end: Int, private val empty: View) {
    private class Group(val label: TextView?, val card: ViewGroup)

    private val groups = ArrayList<Group>()
    private val hidden = HashSet<View>()

    init {
        var i = start
        while (i < end) {
            val v = column.getChildAt(i)
            if (v is TextView && i + 1 < end) {
                (column.getChildAt(i + 1) as? ViewGroup)?.let { groups.add(Group(v, it)) }
                i += 2
            } else {
                if (v is ViewGroup) groups.add(Group(null, v))
                i += 1
            }
        }
    }

    fun update(raw: String) {
        val words = raw.trim().lowercase().split(Regex("\\s+")).filter { it.isNotEmpty() }
        var any = false
        for (g in groups) {
            if (words.isEmpty()) {
                show(g.card)
                g.label?.let { show(it) }
                for (i in 0 until g.card.childCount) show(g.card.getChildAt(i))
                continue
            }
            val labelText = g.label?.text?.toString()?.lowercase().orEmpty()
            val labelMatch = words.all { it in labelText }
            var divider: View? = null
            var seen = false
            var previous = false
            for (i in 0 until g.card.childCount) {
                val c = g.card.getChildAt(i)
                if (c.javaClass == View::class.java) {
                    hide(c)
                    divider = c
                    continue
                }
                val text = StringBuilder().also { gather(c, it) }.toString().lowercase()
                // A row with no text (the update progress line) follows the row above it.
                val visible = if (text.isBlank()) previous else labelMatch || words.all { it in text }
                if (visible) show(c) else hide(c)
                previous = visible
                if (visible) {
                    if (seen) divider?.let { show(it) }
                    seen = true
                }
            }
            if (seen) {
                show(g.card)
                g.label?.let { show(it) }
                any = true
            } else {
                hide(g.card)
                g.label?.let { hide(it) }
            }
        }
        empty.visibility = if (words.isNotEmpty() && !any) View.VISIBLE else View.GONE
    }

    private fun gather(v: View, out: StringBuilder) {
        if (v is TextView) out.append(v.text).append(' ')
        if (v is ViewGroup) for (i in 0 until v.childCount) gather(v.getChildAt(i), out)
    }

    private fun hide(v: View) {
        if (v.visibility == View.VISIBLE) {
            v.visibility = View.GONE
            hidden.add(v)
        }
    }

    private fun show(v: View) {
        if (hidden.remove(v)) v.visibility = View.VISIBLE
    }
}
