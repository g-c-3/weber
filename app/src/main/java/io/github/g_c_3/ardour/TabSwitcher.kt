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
import android.view.ViewConfiguration
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

// The open tabs over the whole browser screen (DECISIONS.md 162): a translucent sheet that fades in from the bottom, a
// filter (All, Shield), and cards with a picture of the page. Tap opens a tab, a swipe to either side closes it, the cross at
// the top right (or Back) closes the list. The number of columns follows the width: two on a phone held upright, more on a
// wide screen.
class TabSwitcher(
    context: Context,
    private val onPick: (Int) -> Unit,
    private val onClose: (Int) -> Unit,
    private val onNew: () -> Unit,
) : FrameLayout(context) {
    class Item(
        val title: String,
        val host: String,
        val start: Boolean,
        val shield: Boolean,
        val preview: Bitmap?,
    )

    var showing = false
        private set

    private var items: List<Item> = emptyList()
    private var active = 0
    private var shieldOnly = false

    private val subtitle = Ui.body(context, "")
    private val grid = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
    private val allChip = chip("All") { setFilter(false) }
    private val shieldChip = chip("Shield") { setFilter(true) }
    private val newButton = Ui.pillButton(context, "New tab").apply {
        contentDescription = "New tab"
        setOnClickListener { onNew() }
    }
    private val resetPill = Runnable { newButton.text = "New tab" }

    init {
        isClickable = true
        visibility = GONE
        val head = FrameLayout(context)
        val titles = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            addView(Ui.title(context, "Tabs").apply { textSize = 28f })
            addView(subtitle.apply { setPadding(0, Ui.dp(context, 2), 0, 0) })
        }
        head.addView(titles, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT, Gravity.START or Gravity.CENTER_VERTICAL))
        val close = CloseDisc(context).apply {
            contentDescription = "Close tab list"
            setOnClickListener { hide() }
        }
        head.addView(close, LayoutParams(Ui.dp(context, 44), Ui.dp(context, 44), Gravity.END or Gravity.CENTER_VERTICAL))
        val filters = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(allChip, LinearLayout.LayoutParams(Ui.dp(context, 84), Ui.dp(context, 38)))
            addView(shieldChip, LinearLayout.LayoutParams(Ui.dp(context, 96), Ui.dp(context, 38)).apply { marginStart = Ui.dp(context, 8) })
        }
        val column = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(Ui.dp(context, 14), Ui.dp(context, 10), Ui.dp(context, 14), Ui.dp(context, 100))
            addView(head, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, Ui.dp(context, 56)))
            addView(filters, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                topMargin = Ui.dp(context, 10)
                bottomMargin = Ui.dp(context, 8)
            })
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
        // The sheet covers the system bars too; its content keeps clear of them.
        setOnApplyWindowInsetsListener { v, insets ->
            if (android.os.Build.VERSION.SDK_INT >= 30) {
                val bars = insets.getInsets(android.view.WindowInsets.Type.systemBars())
                v.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            } else {
                v.setPadding(insets.systemWindowInsetLeft, insets.systemWindowInsetTop, insets.systemWindowInsetRight, insets.systemWindowInsetBottom)
            }
            insets
        }
    }

    private fun chip(text: String, tap: () -> Unit) = TextView(context).apply {
        this.text = text
        textSize = 14f
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        gravity = Gravity.CENTER
        isClickable = true
        setOnClickListener { tap() }
        Ui.springPress(this)
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
        // Light mode lets more of the browser through than dark mode.
        setBackgroundColor(((if (Ui.dark) 0xEB else 0xB0) shl 24) or (Ui.PAPER and 0x00FFFFFF))
        render()
        showing = true
        animate().cancel()
        visibility = VISIBLE
        alpha = 0f
        translationY = Ui.dp(context, 56).toFloat()
        animate().alpha(1f).translationY(0f).setDuration(340).setInterpolator(SpringInterpolator()).start()
    }

    // The list changed while it is open (a tab closed): drawn again in place.
    fun update(list: List<Item>, activeIndex: Int) {
        items = list
        active = activeIndex
        if (showing) render()
    }

    fun hide() {
        if (!showing) return
        showing = false
        animate().cancel()
        animate().alpha(0f).translationY(Ui.dp(context, 40).toFloat()).setDuration(220)
            .withEndAction { if (!showing) visibility = GONE }.start()
    }

    // The New tab pill says so when the limit is reached.
    fun limitNote(text: String) {
        newButton.text = text
        newButton.removeCallbacks(resetPill)
        newButton.postDelayed(resetPill, 1800)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (showing && w != oldw) render()
    }

    private fun render() {
        val shields = items.count { it.shield }
        subtitle.text = (if (items.size == 1) "1 open" else "${items.size} open") + if (shields > 0) ", $shields in Shield" else ""
        Ui.choiceLook(context, allChip, !shieldOnly)
        Ui.choiceLook(context, shieldChip, shieldOnly)
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
        val inner = (width - paddingLeft - paddingRight - Ui.dp(context, 28)).coerceAtLeast(Ui.dp(context, 200))
        // Columns follow the width: about 170 dp per card, two at the least, five at the most.
        val columns = (inner / Ui.dp(context, 170)).coerceIn(2, 5)
        val cardWidth = inner / columns - 2 * gap
        // Twice as long as wide for a phone held upright; on a short screen no taller than most of it.
        val height = min((cardWidth * 1.6f).toInt(), ((this.height - paddingTop - paddingBottom) * 0.78f).toInt())
            .coerceAtLeast(Ui.dp(context, 170))
        var i = 0
        while (i < shown.size) {
            val row = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
            for (j in 0 until columns) {
                val params = LinearLayout.LayoutParams(0, height, 1f).apply { setMargins(gap, gap, gap, gap) }
                val entry = shown.getOrNull(i + j)
                if (entry != null) {
                    val index = entry.index
                    row.addView(TabCard(context, entry.value, index == active, { onPick(index) }, { onClose(index) }), params)
                } else {
                    row.addView(View(context), params)
                }
            }
            grid.addView(row)
            i += columns
        }
    }
}

// The round cross at the top right of the sheet.
@SuppressLint("ViewConstructor")
private class CloseDisc(context: Context) : View(context) {
    private val disc = Paint(Paint.ANTI_ALIAS_FLAG)
    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }

    init {
        isClickable = true
        Ui.springPress(this)
    }

    override fun onDraw(canvas: Canvas) {
        val cx = width / 2f
        val cy = height / 2f
        val r = min(width, height) / 2f - Ui.dp(context, 3)
        disc.color = Ui.CARD
        disc.setShadowLayer(Ui.dp(context, 4).toFloat(), 0f, Ui.dp(context, 1).toFloat(), 0x33000000)
        canvas.drawCircle(cx, cy, r, disc)
        line.color = Ui.INK
        line.strokeWidth = Ui.dp(context, 2).toFloat()
        val k = r * 0.32f
        canvas.drawLine(cx - k, cy - k, cx + k, cy + k, line)
        canvas.drawLine(cx - k, cy + k, cx + k, cy - k, line)
    }
}

// One card: the page picture over a band with the title and host. A shielded tab has a small ember shield at the top
// right; a normal tab has nothing there. Colours are read from Ui at every draw, never kept.
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

    override fun onDraw(canvas: Canvas) {
        val inset = d(6)
        box.set(inset, inset, width - inset, height - inset)
        val radius = d(18)
        fill.color = Ui.CARD
        if (active) fill.setShadowLayer(d(7), 0f, d(1), 0x66E8451D) else fill.clearShadowLayer()
        canvas.drawRoundRect(box, radius, radius, fill)

        val band = d(54)
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
        // Band with the title.
        fill.clearShadowLayer()
        fill.color = Ui.CARD
        canvas.drawRect(box.left, pictureBottom, box.right, box.bottom, fill)
        line.strokeWidth = d(1)
        line.color = Ui.LINE
        canvas.drawLine(box.left, pictureBottom, box.right, pictureBottom, line)
        canvas.restore()

        val pad = d(10)
        val r = d(12)
        val cx = box.left + pad + r
        val cy = pictureBottom + band / 2f
        fill.color = (0x26 shl 24) or (Ui.EMBER and 0x00FFFFFF)
        canvas.drawCircle(cx, cy, r, fill)
        letter.color = Ui.EMBER
        letter.alpha = 255
        letter.textSize = Ui.textPx(context, 13f)
        canvas.drawText(initialOf(), cx, cy - (letter.ascent() + letter.descent()) / 2f, letter)
        val textLeft = cx + r + d(8)
        val textWidth = (box.right - pad - textLeft).coerceAtLeast(1f)
        titlePaint.color = Ui.INK
        titlePaint.textSize = Ui.textPx(context, 13f)
        hostPaint.color = Ui.MUTED
        hostPaint.textSize = Ui.textPx(context, 11f)
        val title = TextUtils.ellipsize(item.title, titlePaint, textWidth, TextUtils.TruncateAt.END).toString()
        val host = TextUtils.ellipsize(item.host, hostPaint, textWidth, TextUtils.TruncateAt.END).toString()
        canvas.drawText(title, textLeft, cy - d(2), titlePaint)
        canvas.drawText(host, textLeft, cy + d(13), hostPaint)

        // Outline last, over the picture.
        line.strokeWidth = if (active) d(2) else d(1)
        line.color = if (active) Ui.EMBER else Ui.LINE
        canvas.drawRoundRect(box, radius, radius, line)

        if (item.shield) drawShield(canvas, box.right - d(24), box.top + d(24))
    }

    private fun initialOf(): String =
        if (item.start) "A" else (item.host.firstOrNull { it.isLetterOrDigit() }?.uppercaseChar()?.toString() ?: "•")

    // A small ember shield on a round chip.
    private fun drawShield(canvas: Canvas, cx: Float, cy: Float) {
        fill.color = Ui.CARD
        fill.alpha = 0xE6
        canvas.drawCircle(cx, cy, d(14), fill)
        fill.alpha = 255
        val w = d(7)
        val h = d(8)
        shieldPath.reset()
        shieldPath.moveTo(cx, cy - h)
        shieldPath.lineTo(cx + w, cy - h * 0.62f)
        shieldPath.quadTo(cx + w, cy + h * 0.35f, cx, cy + h)
        shieldPath.quadTo(cx - w, cy + h * 0.35f, cx - w, cy - h * 0.62f)
        shieldPath.close()
        fill.color = Ui.EMBER
        canvas.drawPath(shieldPath, fill)
        fill.color = Ui.CARD
        canvas.drawCircle(cx, cy - h * 0.1f, d(1) + 0.8f, fill)
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
