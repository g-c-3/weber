package io.github.g_c_3.ardour

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.text.TextPaint
import android.text.TextUtils
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.HorizontalScrollView
import android.widget.LinearLayout

// The open tabs as a strip above the address bar (DECISIONS.md 173), shown by the Settings switch "Top bar tab switch". It
// swipes sideways and a tap on a tab selects it; 3 seconds after the last touch it glides back to the open tab.
class TabStrip(context: Context, private val onPick: (Int) -> Unit) : HorizontalScrollView(context) {
    private val row = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
    }
    private var shown: List<String> = emptyList()
    private var active = -1
    private val glideBack = Runnable { centreActive() }

    init {
        isHorizontalScrollBarEnabled = false
        overScrollMode = OVER_SCROLL_NEVER
        isHorizontalFadingEdgeEnabled = true
        setFadingEdgeLength(Ui.dp(context, 16))
        addView(row, ViewGroup.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT))
    }

    // The tabs and the open one. Tabs that did not change are left alone; the strip moves to the open tab when it changed.
    fun set(items: List<TabSwitcher.Item>, activeIndex: Int) {
        val key = items.map { "${it.title}|${it.shield}|${it.start}" }
        val changed = activeIndex != active || key.size != shown.size
        val rebuild = key != shown || activeIndex != active
        shown = key
        active = activeIndex
        if (rebuild) {
            row.removeAllViews()
            items.forEachIndexed { i, item ->
                row.addView(
                    TabChip(context, item, i == activeIndex) { onPick(i) },
                    LinearLayout.LayoutParams(Ui.dp(context, 124), ViewGroup.LayoutParams.MATCH_PARENT)
                        .apply { setMargins(Ui.dp(context, 3), Ui.dp(context, 1), Ui.dp(context, 3), Ui.dp(context, 1)) },
                )
            }
        }
        if (changed) {
            removeCallbacks(glideBack)
            post { centreActive() }
        }
    }

    private fun centreActive() {
        val chip = row.getChildAt(active) ?: return
        val to = (chip.left + chip.width / 2 - width / 2).coerceIn(0, maxOf(row.width - width, 0))
        smoothScrollTo(to, 0)
    }

    override fun dispatchTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> removeCallbacks(glideBack)
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> postDelayed(glideBack, 3000)
        }
        return super.dispatchTouchEvent(e)
    }

    override fun onDetachedFromWindow() {
        removeCallbacks(glideBack)
        super.onDetachedFromWindow()
    }
}

// One tab of the strip: a small rounded card with the title; the shield mark for a Shield tab; an ember outline for the open tab.
@SuppressLint("ViewConstructor")
private class TabChip(context: Context, private val item: TabSwitcher.Item, private val open: Boolean, tap: () -> Unit) : View(context) {
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val glyph = Paint(Paint.ANTI_ALIAS_FLAG)
    private val path = Path()
    private val box = RectF()
    private val text = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        textSize = Ui.dp(context, 12).toFloat()
    }

    init {
        isClickable = true
        contentDescription = (if (open) "Open tab: " else "Tab: ") + item.title + if (item.shield) ", Shield" else ""
        setOnClickListener { tap() }
        Ui.springPress(this)
    }

    override fun onDraw(canvas: Canvas) {
        val d = Ui.dp(context, 1).toFloat()
        val inset = line.strokeWidth.coerceAtLeast(d * 1.5f) / 2f
        box.set(inset, inset, width - inset, height - inset)
        val radius = box.height() / 2f
        fill.color = if (open) Ui.CARD else Ui.cardAlpha(0xB0)
        canvas.drawRoundRect(box, radius, radius, fill)
        line.strokeWidth = if (open) d * 1.5f else d
        line.color = if (open) Ui.EMBER else Ui.LINE
        canvas.drawRoundRect(box, radius, radius, line)
        var left = d * 12
        if (item.shield) {
            drawShieldGlyph(canvas, path, glyph, left + d * 4, height / 2f, d * 4.5f, Ui.EMBER, null)
            left += d * 14
        }
        text.color = if (open) Ui.INK else Ui.MUTED
        val room = width - left - d * 12
        val label = TextUtils.ellipsize(item.title, text, room, TextUtils.TruncateAt.END).toString()
        val base = height / 2f - (text.ascent() + text.descent()) / 2f
        canvas.drawText(label, left, base, text)
    }
}
