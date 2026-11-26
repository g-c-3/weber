package io.github.g_c_3.ardour

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.text.TextPaint
import android.text.TextUtils
import android.view.Gravity
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.View
import android.view.ViewGroup
import android.view.ViewConfiguration
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

// The open tabs over the whole browser screen (DECISIONS.md 162, 163): a translucent sheet that fades in from the bottom.
// At the top two buttons, the tab square with the number of tabs (all) and the ember shield with the number of Shield
// tabs, filter the list; a + at the right adds a tab, a long press on it a Shield tab. Cards show a picture of the page.
// Tap opens a tab, a swipe to either side closes it, a pull down on empty space or Back closes the sheet. The number of
// columns follows the width: two on a phone held upright, more on a wide screen.
class TabSwitcher(
    context: Context,
    private val onPick: (Int) -> Unit,
    private val onClose: (Int) -> Unit,
    private val onNew: (Boolean) -> Unit,
    private val onHidden: () -> Unit,
) : FrameLayout(context) {
    class Item(
        val title: String,
        val host: String,
        val start: Boolean,
        val shield: Boolean,
        val preview: Bitmap?,
        val key: Int = 0,
    )

    var showing = false
        private set

    private var items: List<Item> = emptyList()
    private var active = 0
    private var shieldOnly = false

    // The card of the open tab, and whether the next drawing scrolls to it: on opening the sheet and after a rotation, so the open
    // tab is in view however many tabs there are (DECISIONS.md 177). It works on rows, so it holds for more rows in landscape.
    private var activeCard: View? = null
    private var centreNext = false

    private val grid = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER_VERTICAL
    }
    private val allButton = CountButton(context, false) { setFilter(false) }
    private val shieldButton = CountButton(context, true) { setFilter(true) }
    private val plus = PlusDisc(context, { onNew(false) }, { onNew(true) })
    private var listMode = Prefs.get(context, Prefs.TAB_LIST)
    private val viewToggle = ViewToggle(context) { setListMode(!listMode) }
    private val scroll = ScrollView(context)
    private var pullY = 0f
    private var pullX = 0f
    private var pulling = false
    private var pullEmpty = true
    private var pulled = false

    init {
        isClickable = true
        visibility = GONE
        val head = FrameLayout(context)
        val filters = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(allButton, LinearLayout.LayoutParams(Ui.dp(context, 66), Ui.dp(context, 48)))
            addView(shieldButton, LinearLayout.LayoutParams(Ui.dp(context, 66), Ui.dp(context, 48)).apply { marginStart = Ui.dp(context, 8) })
        }
        head.addView(filters, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT, Gravity.CENTER))
        head.addView(plus, LayoutParams(Ui.dp(context, 32), Ui.dp(context, 32), Gravity.END or Gravity.CENTER_VERTICAL))
        head.addView(
            viewToggle,
            LayoutParams(Ui.dp(context, 32), Ui.dp(context, 32), Gravity.END or Gravity.CENTER_VERTICAL).apply { marginEnd = Ui.dp(context, 46) },
        )
        val column = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(Ui.dp(context, 14), 0, Ui.dp(context, 14), Ui.dp(context, 60))
            addView(head, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, Ui.dp(context, 54)))
            // With few tabs the cards sit in the middle of the sheet and move up as tabs are added (DECISIONS.md 167).
            addView(grid, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f).apply {
                topMargin = Ui.dp(context, 4)
            })
        }
        scroll.apply {
            isVerticalScrollBarEnabled = false
            overScrollMode = OVER_SCROLL_NEVER
            isFillViewport = true
            addView(column)
        }
        addView(scroll, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        // The sheet covers the system bars too; its content keeps clear of them.
        setOnApplyWindowInsetsListener { v, insets ->
            if (android.os.Build.VERSION.SDK_INT >= 30) {
                val bars = insets.getInsets(android.view.WindowInsets.Type.systemBars())
                v.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            } else {
                v.setPadding(insets.systemWindowInsetLeft, insets.systemWindowInsetTop, insets.systemWindowInsetRight, insets.systemWindowInsetBottom)
            }
            if (showing) queueRender()
            insets
        }
    }

    private fun setListMode(on: Boolean) {
        if (listMode == on) return
        listMode = on
        Prefs.set(context, Prefs.TAB_LIST, on)
        render()
    }

    private fun setFilter(shield: Boolean) {
        if (shieldOnly == shield) return
        shieldOnly = shield
        render()
    }

    fun show(list: List<Item>, activeIndex: Int) {
        items = list
        active = activeIndex
        shieldOnly = false
        // Light mode lets more of the browser through than dark mode, but is clearly grey (not dark) and much less see-through.
        setBackgroundColor(if (Ui.dark) ((0xEB shl 24) or (Ui.PAPER and 0x00FFFFFF)) else 0xF0C4C5CA.toInt())
        centreNext = true
        render()
        scroll.scrollTo(0, 0)
        showing = true
        animate().cancel()
        visibility = VISIBLE
        alpha = 0f
        translationY = Ui.dp(context, 56).toFloat()
        animate().alpha(1f).translationY(0f).setDuration(340).setInterpolator(SpringInterpolator()).start()
        // A sheet that has never been laid out has no width yet: draw the cards again once it has one.
        if (width == 0) post { if (showing) render() }
    }

    // The list changed while it is open (a tab closed or opened): drawn again in place.
    fun update(list: List<Item>, activeIndex: Int) {
        items = list
        active = activeIndex
        if (showing) render()
    }

    fun hide() {
        if (!showing) return
        showing = false
        onHidden()
        animate().cancel()
        animate().alpha(0f).translationY(Ui.dp(context, 40).toFloat()).setDuration(220)
            .withEndAction { if (!showing) visibility = GONE }.start()
    }

    // The + shakes when the limit of tabs is reached.
    fun limitNote() = plus.shake()

    // The cards are drawn again after the layout pass, never inside it: views added during a layout pass are not measured and\n    // stay empty (a rotation left the sheet without cards, DECISIONS.md 170).
    private var renderQueued = false

    private fun queueRender() {
        if (renderQueued) return
        renderQueued = true
        centreNext = true
        post {
            renderQueued = false
            if (showing) render()
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (showing && (w != oldw || h != oldh)) queueRender()
    }

    // A pull down closes the sheet once the list is at its top: from empty space with a short pull, from a card with a longer
    // one, so a card is not closed by accident. A pull that starts on a button does nothing. When the list is scrolled, the
    // pull counts from the moment it reaches the top.
    override fun dispatchTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                pullX = e.rawX
                pulled = false
                pulling = !onButton(e.rawX, e.rawY)
                pullEmpty = !onCard(e.rawX, e.rawY)
                pullY = if (scroll.scrollY == 0) e.rawY else -1f
            }
            MotionEvent.ACTION_MOVE -> if (pulling && !pulled) {
                if (scroll.scrollY == 0) {
                    if (pullY < 0f) pullY = e.rawY
                } else {
                    pullY = -1f
                }
                val dy = if (pullY < 0f) 0f else e.rawY - pullY
                val need = Ui.dp(context, if (pullEmpty) 64 else 150)
                if (dy > need && dy > abs(e.rawX - pullX) * 1.5f) {
                    pulled = true
                    val cancel = MotionEvent.obtain(e)
                    cancel.action = MotionEvent.ACTION_CANCEL
                    super.dispatchTouchEvent(cancel)
                    cancel.recycle()
                    hide()
                }
            }
        }
        if (pulled) {
            if (e.actionMasked == MotionEvent.ACTION_UP || e.actionMasked == MotionEvent.ACTION_CANCEL) pulled = false
            return true
        }
        return super.dispatchTouchEvent(e)
    }

    private fun inside(v: View, x: Float, y: Float): Boolean {
        val at = IntArray(2)
        v.getLocationOnScreen(at)
        return x >= at[0] && x <= at[0] + v.width && y >= at[1] && y <= at[1] + v.height
    }

    private fun onButton(x: Float, y: Float) = inside(allButton, x, y) || inside(shieldButton, x, y) || inside(plus, x, y) || inside(viewToggle, x, y)

    private fun onCard(x: Float, y: Float): Boolean {
        for (r in 0 until grid.childCount) {
            val child = grid.getChildAt(r)
            if (child is TabCard) {
                if (inside(child, x, y)) return true
                continue
            }
            val row = child as? ViewGroup ?: continue
            for (c in 0 until row.childCount) if (row.getChildAt(c) is TabCard && inside(row.getChildAt(c), x, y)) return true
        }
        return false
    }

    // The open tab's card is brought to the middle of the sheet once the cards are laid out (as far as the list allows).
    private fun finishRender() {
        if (!centreNext || width == 0) return
        centreNext = false
        val card = activeCard ?: return
        scroll.viewTreeObserver.addOnPreDrawListener(object : android.view.ViewTreeObserver.OnPreDrawListener {
            override fun onPreDraw(): Boolean {
                scroll.viewTreeObserver.removeOnPreDrawListener(this)
                if (card.height == 0 || scroll.height == 0 || card.parent == null) return true
                var y = 0
                var v: View = card
                while (v !== scroll && v.parent is View) {
                    y += v.top
                    v = v.parent as View
                }
                val content = scroll.getChildAt(0)?.height ?: 0
                scroll.scrollTo(0, (y + card.height / 2 - scroll.height / 2).coerceIn(0, maxOf(content - scroll.height, 0)))
                return true
            }
        })
    }

    private fun render() {
        activeCard = null
        allButton.set(items.size, !shieldOnly)
        shieldButton.set(items.count { it.shield }, shieldOnly)
        grid.removeAllViews()
        val shown = items.withIndex().filter { !shieldOnly || it.value.shield }
        if (shown.isEmpty()) {
            grid.addView(
                Ui.body(context, "No Shield tabs open").apply {
                    gravity = Gravity.CENTER
                    setPadding(0, Ui.dp(context, 60), 0, 0)
                },
                LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT),
            )
            return
        }
        val gap = Ui.dp(context, 4)
        if (listMode) {
            // One row to a tab: a small picture, the title and the host.
            for (entry in shown) {
                val index = entry.index
                val card = TabCard(context, entry.value, index == active, true, { onPick(index) }, { onClose(index) })
                if (index == active) activeCard = card
                grid.addView(card, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, Ui.dp(context, 72)).apply { setMargins(gap, gap, gap, gap) })
            }
            grid.requestLayout()
            finishRender()
            return
        }
        val inner = (width - paddingLeft - paddingRight - Ui.dp(context, 28)).coerceAtLeast(Ui.dp(context, 200))
        // Columns follow the width: about 170 dp per card, two at the least, five at the most.
        val columns = (inner / Ui.dp(context, 170)).coerceIn(2, 5)
        val cardWidth = inner / columns - 2 * gap
        // A quarter shorter than the first version (DECISIONS.md 164); on a short screen no taller than most of it.
        val height = min((cardWidth * 1.2f).toInt(), ((this.height - paddingTop - paddingBottom) * 0.6f).toInt())
            .coerceAtLeast(Ui.dp(context, 128))
        var i = 0
        while (i < shown.size) {
            val inRow = min(columns, shown.size - i)
            val row = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
            // A short row (the last one) keeps the card size and sits in the middle; full rows are unchanged (DECISIONS.md 170).
            val short = inRow < columns
            if (short) row.gravity = Gravity.CENTER_HORIZONTAL
            for (j in 0 until inRow) {
                val params = LinearLayout.LayoutParams(if (short) cardWidth else 0, height, if (short) 0f else 1f)
                    .apply { setMargins(gap, gap, gap, gap) }
                val entry = shown[i + j]
                val index = entry.index
                val card = TabCard(context, entry.value, index == active, false, { onPick(index) }, { onClose(index) })
                if (index == active) activeCard = card
                row.addView(card, params)
            }
            grid.addView(row)
            i += columns
        }
        grid.requestLayout()
        finishRender()
    }
}

// The shield of the Shield button (DECISIONS.md 129) as one drawing for the tab list: body in `body`, keyhole in `hole`.
fun drawShieldGlyph(canvas: Canvas, path: Path, paint: Paint, cx: Float, cy: Float, r: Float, body: Int, hole: Int?) {
    path.reset()
    path.moveTo(cx, cy - r * 1.08f)
    path.lineTo(cx + r * 0.92f, cy - r * 0.72f)
    path.lineTo(cx + r * 0.92f, cy + r * 0.08f)
    path.quadTo(cx + r * 0.92f, cy + r * 0.78f, cx, cy + r * 1.1f)
    path.quadTo(cx - r * 0.92f, cy + r * 0.78f, cx - r * 0.92f, cy + r * 0.08f)
    path.lineTo(cx - r * 0.92f, cy - r * 0.72f)
    path.close()
    paint.style = Paint.Style.FILL
    paint.color = body
    canvas.drawPath(path, paint)
    if (hole == null) return
    paint.color = hole
    canvas.drawCircle(cx, cy - r * 0.22f, r * 0.27f, paint)
    path.reset()
    path.moveTo(cx - r * 0.12f, cy - r * 0.05f)
    path.lineTo(cx + r * 0.12f, cy - r * 0.05f)
    path.lineTo(cx + r * 0.22f, cy + r * 0.55f)
    path.lineTo(cx - r * 0.22f, cy + r * 0.55f)
    path.close()
    canvas.drawPath(path, paint)
}

// A filter button with a number inside: the tab square (all tabs) or the ember shield (Shield tabs). The number is centred
// for one digit and for two. The chosen filter has an ember outline.
@SuppressLint("ViewConstructor")
private class CountButton(context: Context, private val shield: Boolean, private val tap: () -> Unit) : View(context) {
    private var count = 0
    private var chosen = false
    private val back = Paint(Paint.ANTI_ALIAS_FLAG)
    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val glyph = Paint(Paint.ANTI_ALIAS_FLAG)
    private val path = Path()
    private val box = RectF()
    private val digits = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        textAlign = Paint.Align.CENTER
    }

    init {
        isClickable = true
        contentDescription = if (shield) "Shield tabs" else "All tabs"
        setOnClickListener { tap() }
        Ui.springPress(this)
    }

    fun set(number: Int, selected: Boolean) {
        count = number
        chosen = selected
        invalidate()
    }

    private fun d(n: Int) = Ui.dp(context, n).toFloat()

    override fun onDraw(canvas: Canvas) {
        box.set(d(2), d(2), width - d(2), height - d(2))
        back.color = Ui.CARD
        canvas.drawRoundRect(box, d(16), d(16), back)
        line.strokeWidth = if (chosen) d(2) else d(1)
        line.color = if (chosen) Ui.EMBER else Ui.LINE
        canvas.drawRoundRect(box, d(16), d(16), line)
        val cx = width / 2f
        val cy = height / 2f
        // Drawn for a 42 dp tall button and scaled with its height.
        val k = height / d(42)
        val text = if (count > 99) "99" else count.toString()
        digits.textSize = Ui.textPx(context, (if (count > 9) 11f else 14f) * k)
        val baseline = cy - (digits.ascent() + digits.descent()) / 2f
        if (shield) {
            drawShieldGlyph(canvas, path, glyph, cx, cy, d(14) * k, Ui.EMBER, null)
            digits.color = 0xFFFFFFFF.toInt()
            canvas.drawText(text, cx, baseline - d(1), digits)
        } else {
            line.strokeWidth = d(2)
            line.color = Ui.INK
            val half = d(12) * k
            canvas.drawRoundRect(cx - half, cy - half, cx + half, cy + half, d(6) * k, d(6) * k, line)
            digits.color = Ui.INK
            canvas.drawText(text, cx, baseline, digits)
        }
    }
}

// The view switch left of the +: three lines (switch to the list) or four squares (back to the grid), no circle.
@SuppressLint("ViewConstructor")
private class ViewToggle(context: Context, private val tap: () -> Unit) : View(context) {
    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val box = RectF()

    init {
        isClickable = true
        contentDescription = "Switch between grid and list"
        setOnClickListener {
            tap()
            invalidate()
        }
        Ui.springPress(this)
    }

    override fun onDraw(canvas: Canvas) {
        val cx = width / 2f
        val cy = height / 2f
        val k = min(width, height) * 0.34f
        line.color = Ui.INK
        line.strokeWidth = Ui.dp(context, 2.5f).toFloat()
        if (Prefs.get(context, Prefs.TAB_LIST)) {
            val g = k * 0.18f
            for (i in 0..1) for (j in 0..1) {
                val l = cx - k + i * (k + g)
                val t = cy - k + j * (k + g)
                box.set(l, t, l + k - g, t + k - g)
                canvas.drawRoundRect(box, Ui.dp(context, 2).toFloat(), Ui.dp(context, 2).toFloat(), line)
            }
        } else {
            for (i in -1..1) canvas.drawLine(cx - k, cy + i * k * 0.8f, cx + k, cy + i * k * 0.8f, line)
        }
    }
}

// The + at the top right: a tap adds a tab, a long press a Shield tab.
@SuppressLint("ViewConstructor")
private class PlusDisc(context: Context, private val tap: () -> Unit, private val hold: () -> Unit) : View(context) {
    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }

    init {
        isClickable = true
        contentDescription = "New tab. Long press for a new Shield tab"
        setOnClickListener { tap() }
        setOnLongClickListener {
            performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)
            hold()
            true
        }
        Ui.springPress(this)
    }

    fun shake() {
        animate().cancel()
        animate().translationX(-Ui.dp(context, 6).toFloat()).setDuration(50).withEndAction {
            animate().translationX(Ui.dp(context, 6).toFloat()).setDuration(80).withEndAction {
                animate().translationX(0f).setDuration(80).start()
            }.start()
        }.start()
    }

    override fun onDraw(canvas: Canvas) {
        val cx = width / 2f
        val cy = height / 2f
        line.color = Ui.INK
        line.strokeWidth = Ui.dp(context, 2.5f).toFloat()
        val k = min(width, height) * 0.34f
        canvas.drawLine(cx - k, cy, cx + k, cy, line)
        canvas.drawLine(cx, cy - k, cx, cy + k, line)
    }
}

// One card: the page picture over a band with the title and host. A shielded tab has a small ember shield at the top
// right; a normal tab has nothing there. Colours are read from Ui at every draw, never kept.
@SuppressLint("ViewConstructor")
private class TabCard(
    context: Context,
    private val item: TabSwitcher.Item,
    private val active: Boolean,
    private val row: Boolean,
    private val onOpen: () -> Unit,
    private val onShut: () -> Unit,
) : View(context) {
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val picture = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val letter = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        textAlign = Paint.Align.CENTER
    }
    private val titlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
    }
    private val hostPaint = TextPaint(Paint.ANTI_ALIAS_FLAG)
    private val box = RectF()
    private val clip = Path()
    private val srcRect = Rect()
    private val dstRect = RectF()
    private val pill = RectF()
    private val shieldPath = Path()
    private val slop = ViewConfiguration.get(context).scaledTouchSlop
    private var downX = 0f
    private var downY = 0f
    private var dragging = false
    private var tracker: VelocityTracker? = null

    init {
        isClickable = true
        contentDescription = (if (active) "Open tab: " else "Tab: ") + item.title + if (item.shield) ", Shield" else ""
        setOnClickListener { onOpen() }
        Ui.springPress(this)
    }

    private fun d(n: Int) = Ui.dp(context, n).toFloat()

    // List mode: a wide row with a small picture, the title and the host, and the shield at the right.
    private fun drawRow(canvas: Canvas) {
        box.set(d(4), d(4), width - d(4), height - d(4))
        val radius = d(16)
        fill.color = Ui.CARD
        if (active) fill.setShadowLayer(d(6), 0f, d(1), 0x66E8451D) else fill.clearShadowLayer()
        canvas.drawRoundRect(box, radius, radius, fill)
        fill.clearShadowLayer()
        val pad = d(8)
        val th = box.height() - 2 * pad
        pill.set(box.left + pad, box.top + pad, box.left + pad + th * 0.8f, box.bottom - pad)
        canvas.save()
        clip.reset()
        clip.addRoundRect(pill, d(10), d(10), Path.Direction.CW)
        canvas.clipPath(clip)
        val bmp = item.preview
        if (bmp != null && !bmp.isRecycled) {
            val scale = pill.width() / bmp.width
            val srcHeight = min(bmp.height.toFloat(), pill.height() / scale)
            srcRect.set(0, 0, bmp.width, srcHeight.toInt().coerceAtLeast(1))
            dstRect.set(pill.left, pill.top, pill.right, pill.top + srcHeight * scale)
            canvas.drawBitmap(bmp, srcRect, dstRect, picture)
        } else {
            fill.color = Ui.PAPER
            canvas.drawRect(pill, fill)
            letter.color = Ui.MUTED
            letter.alpha = 110
            letter.textSize = Ui.textPx(context, 22f)
            canvas.drawText(initialOf(), pill.centerX(), pill.centerY() - (letter.ascent() + letter.descent()) / 2f, letter)
        }
        canvas.restore()
        val left = pill.right + d(12)
        val room = (box.right - pad - (if (item.shield) d(30) else 0f) - left).coerceAtLeast(1f)
        titlePaint.color = Ui.INK
        titlePaint.textSize = Ui.textPx(context, 14f)
        hostPaint.color = Ui.MUTED
        hostPaint.textSize = Ui.textPx(context, 12f)
        canvas.drawText(TextUtils.ellipsize(item.title, titlePaint, room, TextUtils.TruncateAt.END).toString(), left, box.centerY() - d(3), titlePaint)
        canvas.drawText(TextUtils.ellipsize(item.host, hostPaint, room, TextUtils.TruncateAt.END).toString(), left, box.centerY() + d(15), hostPaint)
        line.strokeWidth = if (active) d(2) else d(1)
        line.color = if (active) Ui.EMBER else Ui.LINE
        canvas.drawRoundRect(box, radius, radius, line)
        if (item.shield) drawShield(canvas, box.right - d(24), box.centerY())
    }

    override fun onDraw(canvas: Canvas) {
        if (row) {
            drawRow(canvas)
            return
        }
        val inset = d(6)
        box.set(inset, inset, width - inset, height - inset)
        val radius = d(18)
        fill.color = Ui.CARD
        if (active) fill.setShadowLayer(d(7), 0f, d(1), 0x66E8451D) else fill.clearShadowLayer()
        canvas.drawRoundRect(box, radius, radius, fill)

        val band = d(58)
        val pictureBottom = box.bottom - band
        canvas.save()
        clip.reset()
        clip.addRoundRect(box, radius, radius, Path.Direction.CW)
        canvas.clipPath(clip)
        val bmp = item.preview
        if (bmp != null && !bmp.isRecycled) {
            // Width fits the card, the top of the page shows.
            val scale = box.width() / bmp.width
            val srcHeight = min(bmp.height.toFloat(), (pictureBottom - box.top) / scale)
            srcRect.set(0, 0, bmp.width, srcHeight.toInt().coerceAtLeast(1))
            dstRect.set(box.left, box.top, box.right, box.top + srcHeight * scale)
            canvas.drawBitmap(bmp, srcRect, dstRect, picture)
        } else {
            fill.color = Ui.PAPER
            canvas.drawRect(box.left, box.top, box.right, pictureBottom, fill)
            letter.color = Ui.MUTED
            letter.alpha = 90
            letter.textSize = Ui.textPx(context, 44f)
            val cy = (box.top + pictureBottom) / 2f
            canvas.drawText(initialOf(), box.centerX(), cy - (letter.ascent() + letter.descent()) / 2f, letter)
        }
        fill.clearShadowLayer()
        fill.color = Ui.CARD
        canvas.drawRect(box.left, pictureBottom, box.right, box.bottom, fill)
        canvas.restore()

        // The address pill: ember outline; its text is centred when it fits and starts at the left when it is long.
        pill.set(box.left + d(8), pictureBottom + d(7), box.right - d(8), box.bottom - d(8))
        line.strokeWidth = d(1)
        line.color = Ui.EMBER
        canvas.drawRoundRect(pill, d(13), d(13), line)
        val textWidth = (pill.width() - d(20)).coerceAtLeast(1f)
        titlePaint.color = Ui.INK
        titlePaint.textSize = Ui.textPx(context, 13f)
        hostPaint.color = Ui.MUTED
        hostPaint.textSize = Ui.textPx(context, 11f)
        val title = TextUtils.ellipsize(item.title, titlePaint, textWidth, TextUtils.TruncateAt.END).toString()
        val host = TextUtils.ellipsize(item.host, hostPaint, textWidth, TextUtils.TruncateAt.END).toString()
        val mid = pill.centerY()
        canvas.drawText(title, textStart(pill, titlePaint.measureText(title), textWidth), mid - d(2), titlePaint)
        canvas.drawText(host, textStart(pill, hostPaint.measureText(host), textWidth), mid + d(12), hostPaint)

        // Outline last, over the picture: the open tab is ringed in ember all round.
        line.strokeWidth = if (active) d(2) else d(1)
        line.color = if (active) Ui.EMBER else Ui.LINE
        canvas.drawRoundRect(box, radius, radius, line)

        if (item.shield) drawShield(canvas, box.right - d(22), box.top + d(24))
    }

    // Centred in the pill when the text fits in `room`, from the left edge when it fills it.
    private fun textStart(pill: RectF, measured: Float, room: Float): Float =
        if (measured < room - d(2)) pill.centerX() - measured / 2f else pill.left + d(10)

    private fun initialOf(): String =
        if (item.start) "A" else (item.host.firstOrNull { it.isLetterOrDigit() }?.uppercaseChar()?.toString() ?: "•")

    // The Shield button's shield, small, at the top right: ember with a paper keyhole.
    private fun drawShield(canvas: Canvas, cx: Float, cy: Float) {
        drawShieldGlyph(canvas, shieldPath, fill, cx, cy, d(10), Ui.EMBER, Ui.CARD)
    }

    // Swipe to either side closes the tab; a tap opens it.
    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.rawX
                downY = event.rawY
                dragging = false
                tracker?.recycle()
                tracker = VelocityTracker.obtain().also { it.addMovement(event) }
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                tracker?.addMovement(event)
                val dx = event.rawX - downX
                val dy = event.rawY - downY
                if (!dragging && abs(dx) > slop && abs(dx) > abs(dy)) {
                    dragging = true
                    parent?.requestDisallowInterceptTouchEvent(true)
                }
                if (dragging) {
                    translationX = dx
                    alpha = 1f - min(1f, abs(dx) / width) * 0.85f
                }
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                val dx = event.rawX - downX
                tracker?.addMovement(event)
                tracker?.computeCurrentVelocity(1000)
                val vx = tracker?.xVelocity ?: 0f
                tracker?.recycle()
                tracker = null
                if (dragging) {
                    dragging = false
                    val away = event.actionMasked == MotionEvent.ACTION_UP &&
                        (abs(dx) > width * 0.4f || (abs(vx) > 1500f && vx * dx > 0f))
                    if (away) {
                        val to = (if (dx < 0) -1 else 1) * width * 1.2f
                        animate().translationX(to).alpha(0f).setDuration(190).withEndAction { onShut() }.start()
                    } else {
                        animate().translationX(0f).alpha(1f).setDuration(320).setInterpolator(SpringInterpolator()).start()
                    }
                    return true
                }
                if (event.actionMasked == MotionEvent.ACTION_UP && abs(dx) <= slop && max(abs(event.rawY - downY), 0f) <= slop) performClick()
                return true
            }
        }
        return super.onTouchEvent(event)
    }
}
