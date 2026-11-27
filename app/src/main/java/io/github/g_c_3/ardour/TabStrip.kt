package io.github.g_c_3.ardour

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Typeface
import android.text.TextPaint
import android.text.TextUtils
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import kotlin.math.abs
import kotlin.math.hypot

// The open tabs as a strip above the address bar (DECISIONS.md 173, 176), shown by the Settings switch "Top bar tab switch".
// Tabs are trapezium shaped with rounded top corners over a grey separator line. Sliding sideways moves the strip, a tap opens
// a tab, a swipe down on a tab opens it, a swipe up closes it, and a long press then a drag moves it to where it is let go.
// 3 seconds after the last touch the strip glides back to the open tab.
class TabStrip(
    context: Context,
    private val onPick: (Int) -> Unit,
    private val onClose: (Int) -> Unit,
    private val onMove: (Int, Int) -> Unit,
) : HorizontalScrollView(context) {
    private val row = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.BOTTOM
        // The tabs overhang the row's padding by their overlap: clipped there, the outer slanted sides lost their outline.
        clipToPadding = false
        clipChildren = false
    }
    private val chips = LinkedHashMap<Int, TabChip>()
    private val dying = HashSet<TabChip>()
    private var activeKey = Int.MIN_VALUE
    private val glideBack = Runnable { centreActive() }
    private val rule = Paint()
    private val slop = ViewConfiguration.get(context).scaledTouchSlop
    private val overlap = Ui.dp(context, 5)

    private enum class Mode { NONE, SCROLL, VERTICAL, DRAG }

    private var mode = Mode.NONE
    private var target: TabChip? = null
    private var downX = 0f
    private var downY = 0f
    private var downScroll = 0
    private var lastX = 0f
    private var acted = false
    private var dragTo = 0
    private val startDrag = Runnable { beginDrag() }
    private val edgeScroll = object : Runnable {
        override fun run() {
            if (mode != Mode.DRAG) return
            val at = IntArray(2)
            getLocationOnScreen(at)
            val zone = Ui.dp(context, 36)
            val step = Ui.dp(context, 9)
            val v = when {
                lastX < at[0] + zone -> -step
                lastX > at[0] + width - zone -> step
                else -> 0
            }
            if (v != 0) {
                scrollBy(v, 0)
                applyDrag()
            }
            postOnAnimation(this)
        }
    }

    init {
        isHorizontalScrollBarEnabled = false
        overScrollMode = OVER_SCROLL_NEVER
        isHorizontalFadingEdgeEnabled = true
        setFadingEdgeLength(Ui.dp(context, 14))
        addView(row, ViewGroup.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT))
        row.setPadding(Ui.dp(context, 8), 0, Ui.dp(context, 8), 0)
    }

    // The tab width follows the Settings size and the screen's shape; the open tab is brought into view after it changes.
    fun applySize() {
        val w = TabSizes.width(context)
        for (c in live()) {
            val params = c.layoutParams as LinearLayout.LayoutParams
            if (params.width != w) {
                params.width = w
                c.layoutParams = params
            }
        }
        removeCallbacks(glideBack)
        post { centreActive() }
    }

    private fun lp(): LinearLayout.LayoutParams =
        LinearLayout.LayoutParams(TabSizes.width(context), ViewGroup.LayoutParams.MATCH_PARENT).apply { setMargins(-overlap, 0, -overlap, 0) }

    // Live tabs in order (those not shrinking away).
    private fun live(): List<TabChip> = (0 until row.childCount).map { row.getChildAt(it) as TabChip }.filter { it !in dying }

    // The tabs and the open one. Tabs that did not change are left alone; a tab that is new grows in, one that is gone
    // shrinks away; the strip moves to the open tab when that changed.
    fun set(items: List<TabSwitcher.Item>, activeIndex: Int) {
        val first = chips.isEmpty() && row.childCount == 0
        val keys = items.map { it.key }.toSet()
        for ((k, c) in chips.toList()) if (k !in keys) {
            chips.remove(k)
            shrinkAway(c)
        }
        var pos = 0
        items.forEachIndexed { i, item ->
            val fresh = chips[item.key] == null
            val chip = chips[item.key] ?: TabChip(context).also { chips[item.key] = it }
            chip.bind(item, i == activeIndex)
            chip.setOnClickListener { onPick(live().indexOf(chip)) }
            while (pos < row.childCount && row.getChildAt(pos) in dying) pos++
            if (row.getChildAt(pos) !== chip) {
                if (chip.parent === row) row.removeView(chip)
                row.addView(chip, pos, lp())
            }
            if (fresh && !first) {
                chip.alpha = 0f
                chip.scaleX = 0.8f
                chip.animate().alpha(1f).scaleX(1f).setDuration(180).start()
            }
            pos++
        }
        val newKey = items.getOrNull(activeIndex)?.key ?: Int.MIN_VALUE
        val changed = newKey != activeKey || first
        activeKey = newKey
        if (changed) {
            removeCallbacks(glideBack)
            post { centreActive() }
            if (dying.isNotEmpty()) postDelayed({ centreActive() }, 220)
        }
    }

    // A tab that is gone: it lifts and fades, and its place closes up, in about 150 ms. Several can go at once.
    private fun shrinkAway(c: TabChip) {
        dying.add(c)
        c.isClickable = false
        c.animate().cancel()
        c.animate().alpha(0f).translationY(minOf(c.translationY, 0f) - Ui.dp(context, 8)).setDuration(130).start()
        val w = c.width
        ValueAnimator.ofFloat(1f, 0f).apply {
            duration = 160
            addUpdateListener {
                val f = it.animatedValue as Float
                val params = c.layoutParams as LinearLayout.LayoutParams
                params.width = (w * f).toInt()
                params.leftMargin = (-overlap * f).toInt()
                params.rightMargin = (-overlap * f).toInt()
                c.layoutParams = params
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    row.removeView(c)
                    dying.remove(c)
                }
            })
            start()
        }
    }

    private fun centreActive() {
        val chip = chips[activeKey] ?: return
        val to = (chip.left + chip.width / 2 - width / 2).coerceIn(0, maxOf(row.width - width, 0))
        smoothScrollTo(to, 0)
    }

    private fun chipAt(x: Float): TabChip? =
        live().filter { abs((it.left + it.width / 2f) - x) <= it.width / 2f }.minByOrNull { abs((it.left + it.width / 2f) - x) }

    override fun dispatchDraw(canvas: Canvas) {
        // The separator line under the tabs; the open tab covers it.
        rule.color = Ui.LINE
        val h = Ui.dp(context, 1.5f)
        canvas.drawRect(scrollX.toFloat(), height - h, (scrollX + width).toFloat(), height.toFloat(), rule)
        super.dispatchDraw(canvas)
    }

    override fun dispatchTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                removeCallbacks(glideBack)
                mode = Mode.NONE
                acted = false
                downX = e.rawX
                downY = e.rawY
                lastX = e.rawX
                downScroll = scrollX
                target = chipAt(e.x + scrollX)
                if (target != null) postDelayed(startDrag, ViewConfiguration.getLongPressTimeout().toLong())
            }
            MotionEvent.ACTION_MOVE -> {
                lastX = e.rawX
                val dx = e.rawX - downX
                val dy = e.rawY - downY
                when (mode) {
                    Mode.NONE -> {
                        if (abs(dx) > slop && abs(dx) >= abs(dy)) {
                            mode = Mode.SCROLL
                            removeCallbacks(startDrag)
                        } else if (abs(dy) > slop && abs(dy) > abs(dx) * 1.2f && target != null) {
                            removeCallbacks(startDrag)
                            takeOver(e)
                            mode = Mode.VERTICAL
                        } else if (abs(dx) > slop * 2 || abs(dy) > slop * 2) {
                            removeCallbacks(startDrag)
                        }
                    }
                    Mode.VERTICAL -> {
                        followVertical(dy)
                        return true
                    }
                    Mode.DRAG -> {
                        applyDrag()
                        return true
                    }
                    Mode.SCROLL -> {}
                }
                if (mode == Mode.VERTICAL) return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                removeCallbacks(startDrag)
                postDelayed(glideBack, 3000)
                val was = mode
                mode = Mode.NONE
                if (was == Mode.VERTICAL) {
                    if (!acted) target?.let { springBack(it) }
                    return true
                }
                if (was == Mode.DRAG) {
                    endDrag()
                    return true
                }
            }
        }
        return super.dispatchTouchEvent(e)
    }

    // The strip and the tab under the finger stop their own touch handling; this class takes the rest of the gesture.
    private fun takeOver(e: MotionEvent) {
        val cancel = MotionEvent.obtain(e)
        cancel.action = MotionEvent.ACTION_CANCEL
        super.dispatchTouchEvent(cancel)
        cancel.recycle()
        parent?.requestDisallowInterceptTouchEvent(true)
    }

    private fun springBack(c: TabChip) {
        c.animate().translationY(0f).setDuration(320).setInterpolator(SpringInterpolator()).start()
    }

    // Up closes the tab, down opens it; the tab follows the finger a little meanwhile.
    private fun followVertical(dy: Float) {
        val c = target ?: return
        if (acted) return
        c.translationY = dy.coerceIn(-c.height * 1.1f, Ui.dp(context, 14).toFloat())
        if (dy <= -Ui.dp(context, 30)) {
            acted = true
            performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
            onClose(live().indexOf(c))
            if (c !in dying) springBack(c)
        } else if (dy >= Ui.dp(context, 22)) {
            acted = true
            performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
            onPick(live().indexOf(c))
            springBack(c)
        }
    }

    private fun beginDrag() {
        val c = target ?: return
        if (mode != Mode.NONE || c in dying) return
        mode = Mode.DRAG
        val ev = MotionEvent.obtain(0L, 0L, MotionEvent.ACTION_MOVE, downX, downY, 0)
        takeOver(ev)
        ev.recycle()
        performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
        c.animate().cancel()
        c.animate().scaleX(1.05f).setDuration(120).start()
        c.translationZ = Ui.dp(context, 8).toFloat()
        dragTo = live().indexOf(c)
        postOnAnimation(edgeScroll)
    }

    // The lifted tab follows the finger; the tabs it passes make room for it.
    private fun applyDrag() {
        val c = target ?: return
        c.translationX = (lastX - downX) + (scrollX - downScroll)
        val list = live()
        val from = list.indexOf(c)
        val centre = c.left + c.width / 2f + c.translationX
        val to = list.filter { it !== c }.count { it.left + it.width / 2f < centre }
        dragTo = to
        val slot = (c.width - 2 * overlap).toFloat()
        list.forEachIndexed { k, other ->
            if (other === c) return@forEachIndexed
            val want = when {
                to > from && k in (from + 1)..to -> -slot
                to < from && k in to until from -> slot
                else -> 0f
            }
            if (other.shift != want) {
                other.shift = want
                other.animate().translationX(want).setDuration(140).start()
            }
        }
    }

    private fun endDrag() {
        removeCallbacks(edgeScroll)
        val c = target ?: return
        val list = live()
        val from = list.indexOf(c)
        val to = dragTo
        val visual = c.left + c.translationX
        for (o in list) {
            o.animate().cancel()
            o.translationX = 0f
            o.shift = 0f
        }
        c.animate().cancel()
        c.animate().scaleX(1f).scaleY(1f).setDuration(160).start()
        c.translationZ = if (c.open) Ui.dp(context, 1).toFloat() else 0f
        if (to == from) {
            c.translationX = visual - c.left
            c.animate().translationX(0f).setDuration(260).setInterpolator(SpringInterpolator()).start()
            return
        }
        // The tab settles from where it was let go into its new place, once the strip has laid it out there.
        row.viewTreeObserver.addOnPreDrawListener(object : ViewTreeObserver.OnPreDrawListener {
            override fun onPreDraw(): Boolean {
                row.viewTreeObserver.removeOnPreDrawListener(this)
                c.translationX = visual - c.left
                c.animate().translationX(0f).setDuration(280).setInterpolator(SpringInterpolator()).start()
                return true
            }
        })
        c.translationX = 0f
        onMove(from, to)
    }

    // A rotation or a new width: the open tab is brought into view again (DECISIONS.md 180).
    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w != oldw) applySize()
    }

    override fun onDetachedFromWindow() {
        removeCallbacks(glideBack)
        removeCallbacks(startDrag)
        removeCallbacks(edgeScroll)
        super.onDetachedFromWindow()
    }
}

// One tab of the strip: a trapezium with rounded top corners, the title, the shield mark for a Shield tab; the open tab has
// an ember outline along its sides and top, and sits over the others and over the separator line.
@SuppressLint("ViewConstructor")
private class TabChip(context: Context) : View(context) {
    private var item: TabSwitcher.Item? = null
    var open = false
        private set
    var shift = 0f
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val glyph = Paint(Paint.ANTI_ALIAS_FLAG)
    private val shape = Path()
    private val shieldPath = Path()
    private val text = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        textSize = Ui.dp(context, 12).toFloat()
    }

    init {
        isClickable = true
        Ui.springPress(this)
    }

    fun bind(tab: TabSwitcher.Item, isOpen: Boolean) {
        item = tab
        open = isOpen
        translationZ = if (isOpen) Ui.dp(context, 1).toFloat() else 0f
        contentDescription = (if (isOpen) "Open tab: " else "Tab: ") + tab.title + if (tab.shield) ", Shield" else ""
        invalidate()
    }

    // Left side up to the top, rounded corner, across, rounded corner, down the right side. The bottom is left open: filled, the
    // shape closes itself; stroked, only the sides and the top get a line.
    private fun buildShape(w: Float, h: Float, inset: Float) {
        val slant = Ui.dp(context, 9).toFloat()
        val r = Ui.dp(context, 8).toFloat()
        val len = hypot(slant, h)
        val ux = slant / len
        val uy = h / len
        val left = inset
        val right = w - inset
        val top = inset
        val tlx = left + slant
        val trx = right - slant
        shape.reset()
        shape.moveTo(left, h)
        shape.lineTo(tlx - ux * r, top + uy * r)
        shape.quadTo(tlx, top, tlx + r, top)
        shape.lineTo(trx - r, top)
        shape.quadTo(trx, top, trx + ux * r, top + uy * r)
        shape.lineTo(right, h)
    }

    override fun onDraw(canvas: Canvas) {
        val tab = item ?: return
        val d = Ui.dp(context, 1).toFloat()
        line.strokeWidth = if (open) d * 1.5f else d
        buildShape(width.toFloat(), height.toFloat(), line.strokeWidth / 2f)
        fill.color = if (open) Ui.CARD else Ui.cardAlpha(0xB0)
        canvas.drawPath(shape, fill)
        line.color = if (open) Ui.EMBER else Ui.LINE
        canvas.drawPath(shape, line)
        // The title is centred; one too long for the tab starts at the left and ends in dots.
        val shieldW = if (tab.shield) d * 14 else 0f
        val room = width - d * 34 - shieldW
        text.textSize = Ui.dp(context, 12) * (height / Ui.dp(context, 32).toFloat()).coerceIn(0.85f, 1.4f)
        text.color = if (open) Ui.INK else Ui.MUTED
        val whole = text.measureText(tab.title)
        val label = if (whole <= room) tab.title else TextUtils.ellipsize(tab.title, text, room, TextUtils.TruncateAt.END).toString()
        var x = if (whole <= room) (width - shieldW - text.measureText(label)) / 2f else d * 17
        if (tab.shield) {
            drawShieldGlyph(canvas, shieldPath, glyph, x + d * 4, height / 2f, d * 4.5f, Ui.EMBER, null)
            x += shieldW
        }
        val base = height / 2f - (text.ascent() + text.descent()) / 2f
        canvas.drawText(label, x, base, text)
    }
}

// The sizes of the tabs of the strip (DECISIONS.md 181): width and height each XS, S, M or L; S is the default. A landscape
// screen has wider tabs.
object TabSizes {
    private val widths = intArrayOf(96, 124, 152, 180)
    private val heights = intArrayOf(26, 32, 38, 44)
    val labels = listOf("XS", "S", "M", "L")

    fun width(context: Context): Int {
        val base = widths[Prefs.getInt(context, Prefs.TAB_WIDTH, 1).coerceIn(0, 3)]
        val landscape = context.resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
        return Ui.dp(context, if (landscape) (base * 1.25f).toInt() else base)
    }

    fun height(context: Context): Int = Ui.dp(context, heights[Prefs.getInt(context, Prefs.TAB_HEIGHT, 1).coerceIn(0, 3)])
}
