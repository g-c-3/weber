package io.github.g_c_3.ardour

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView

// The open tabs as a grid of cards over the page area (DECISIONS.md 161). Hand drawn like the rest of the top row; the
// active tab's card has an ember outline. Tapping a card opens the tab, the cross closes it, the pill adds a tab.
class TabSwitcher(
    context: Context,
    private val onPick: (Int) -> Unit,
    private val onClose: (Int) -> Unit,
    private val onNew: () -> Unit,
) : FrameLayout(context) {
    class Item(val title: String, val host: String, val blank: Boolean)

    var showing = false
        private set

    private val subtitle = Ui.body(context, "")
    private val grid = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
    private val newButton = Ui.pillButton(context, "New tab").apply {
        contentDescription = "New tab"
        setOnClickListener { onNew() }
    }

    init {
        isClickable = true
        visibility = GONE
        val column = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(Ui.dp(context, 14), Ui.dp(context, 14), Ui.dp(context, 14), Ui.dp(context, 96))
            addView(
                Ui.title(context, "Tabs").apply {
                    textSize = 28f
                    setPadding(Ui.dp(context, 6), 0, 0, 0)
                },
            )
            addView(subtitle.apply { setPadding(Ui.dp(context, 6), Ui.dp(context, 2), 0, Ui.dp(context, 12)) })
            addView(grid)
        }
        val scroll = ScrollView(context).apply {
            isVerticalScrollBarEnabled = false
            overScrollMode = OVER_SCROLL_NEVER
            addView(column)
        }
        addView(scroll, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        addView(
            newButton,
            LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT, Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL).apply {
                bottomMargin = Ui.dp(context, 22)
            },
        )
    }

    fun show(items: List<Item>, active: Int) {
        setBackgroundColor(Ui.PAPER)
        render(items, active)
        showing = true
        animate().cancel()
        visibility = VISIBLE
        alpha = 0f
        animate().alpha(1f).setDuration(200).start()
        Ui.enter((0 until minOf(grid.childCount, 5)).map { grid.getChildAt(it) })
        newButton.alpha = 0f
        newButton.animate().alpha(1f).setStartDelay(120).setDuration(260).start()
    }

    // The list changed while it is open (a tab closed): drawn again in place.
    fun update(items: List<Item>, active: Int) {
        if (showing) render(items, active)
    }

    fun hide() {
        if (!showing) return
        showing = false
        animate().cancel()
        animate().alpha(0f).setDuration(160).withEndAction { if (!showing) visibility = GONE }.start()
    }

    private fun render(items: List<Item>, active: Int) {
        subtitle.text = if (items.size == 1) "1 open" else "${items.size} open"
        grid.removeAllViews()
        val gap = Ui.dp(context, 4)
        val height = Ui.sdp(context, 132)
        var i = 0
        while (i < items.size) {
            val row = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
            for (j in 0..1) {
                val index = i + j
                val params = LinearLayout.LayoutParams(0, height, 1f).apply { setMargins(gap, gap, gap, gap) }
                if (index < items.size) {
                    row.addView(TabCard(context, items[index], index == active, { onPick(index) }, { onClose(index) }), params)
                } else {
                    row.addView(View(context), params)
                }
            }
            grid.addView(row)
            i += 2
        }
    }
}

// One card. Colours are read from Ui at every draw, never kept.
@SuppressLint("ViewConstructor")
private class TabCard(
    context: Context,
    private val item: TabSwitcher.Item,
    private val active: Boolean,
    private val onOpen: () -> Unit,
    private val onShut: () -> Unit,
) : View(context) {
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val badge = Paint(Paint.ANTI_ALIAS_FLAG)
    private val cross = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val initial = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        textAlign = Paint.Align.CENTER
    }
    private val titlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
    }
    private val hostPaint = TextPaint(Paint.ANTI_ALIAS_FLAG)
    private val box = RectF()
    private var titleLayout: StaticLayout? = null
    private var layoutWidth = -1

    init {
        isClickable = true
        contentDescription = (if (active) "Open tab: " else "Tab: ") + item.title
        setOnClickListener { onOpen() }
        Ui.springPress(this)
    }

    private fun d(n: Int) = Ui.dp(context, n).toFloat()

    override fun onDraw(canvas: Canvas) {
        val inset = d(6)
        box.set(inset, inset, width - inset, height - inset)
        val radius = d(20)
        fill.color = Ui.CARD
        if (active) fill.setShadowLayer(d(7), 0f, d(1), 0x66E8451D) else fill.clearShadowLayer()
        canvas.drawRoundRect(box, radius, radius, fill)
        line.strokeWidth = if (active) d(2) else d(1)
        line.color = if (active) Ui.EMBER else Ui.LINE
        canvas.drawRoundRect(box, radius, radius, line)

        val pad = d(14)
        val r = d(14)
        val cx = box.left + pad + r
        val cy = box.top + pad + r
        badge.color = if (item.blank) Ui.LINE else (0x26 shl 24) or (Ui.EMBER and 0x00FFFFFF)
        canvas.drawCircle(cx, cy, r, badge)
        initial.color = Ui.EMBER
        initial.textSize = Ui.textPx(context, 15f)
        val letter = if (item.blank) "+" else (item.host.firstOrNull { it.isLetterOrDigit() }?.uppercaseChar()?.toString() ?: "•")
        canvas.drawText(letter, cx, cy - (initial.ascent() + initial.descent()) / 2f, initial)

        // Close cross, top right.
        cross.color = Ui.MUTED
        cross.strokeWidth = d(2)
        val kx = box.right - pad - d(8)
        val ky = box.top + pad + r
        val k = d(5)
        canvas.drawLine(kx - k, ky - k, kx + k, ky + k, cross)
        canvas.drawLine(kx - k, ky + k, kx + k, ky - k, cross)

        // Title, up to two lines, then the host line at the bottom.
        val textWidth = (box.width() - 2 * pad).toInt().coerceAtLeast(1)
        titlePaint.color = Ui.INK
        titlePaint.textSize = Ui.textPx(context, 15f)
        if (titleLayout == null || layoutWidth != textWidth) {
            layoutWidth = textWidth
            titleLayout = StaticLayout.Builder.obtain(item.title, 0, item.title.length, titlePaint, textWidth)
                .setMaxLines(2)
                .setEllipsize(TextUtils.TruncateAt.END)
                .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                .build()
        }
        canvas.save()
        canvas.translate(box.left + pad, cy + r + d(10))
        titleLayout?.draw(canvas)
        canvas.restore()

        hostPaint.color = Ui.MUTED
        hostPaint.textSize = Ui.textPx(context, 12f)
        val host = TextUtils.ellipsize(item.host, hostPaint, textWidth.toFloat(), TextUtils.TruncateAt.END).toString()
        canvas.drawText(host, box.left + pad, box.bottom - pad - hostPaint.descent(), hostPaint)
    }

    // The cross has its own touch zone, a little larger than it is drawn; the rest of the card opens the tab.
    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        val zone = d(48)
        val inCross = event.x > width - zone && event.y < zone
        if (event.actionMasked == MotionEvent.ACTION_UP && inCross && event.x in 0f..width.toFloat() && event.y in 0f..height.toFloat()) {
            animate().alpha(0f).scaleX(0.9f).scaleY(0.9f).setDuration(150).withEndAction { onShut() }.start()
            return true
        }
        if (inCross) return true
        return super.onTouchEvent(event)
    }
}
