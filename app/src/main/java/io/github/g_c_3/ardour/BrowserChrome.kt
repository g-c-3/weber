package io.github.g_c_3.ardour

import android.animation.ArgbEvaluator
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.drawable.GradientDrawable
import android.text.InputType
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.animation.PathInterpolator
import android.view.inputmethod.InputMethodManager
import android.widget.FrameLayout
import android.widget.EditText
import android.widget.TextView
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

// Toolbar icon drawn by hand; dimmed when disabled.
class IconButton(context: Context, kind: Kind) : View(context) {
    enum class Kind { BACK, FORWARD, RELOAD, STOP, MENU, UP, DOWN }

    var kind: Kind = kind
        set(v) {
            field = v
            invalidate()
        }

    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = Ui.dp(context, 2) * 0.9f
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        color = Ui.INK
    }
    private val dot = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Ui.INK }
    private val path = Path()
    private val arc = RectF()

    // Touch response: the icon turns ember at once and eases back to ink a moment after the finger lifts.
    private var warmth = 0f
    private var warmAnim: ValueAnimator? = null
    private val blend = ArgbEvaluator()

    private fun warm(on: Boolean) {
        warmAnim?.cancel()
        if (!ValueAnimator.areAnimatorsEnabled()) {
            applyWarmth(if (on) 1f else 0f)
            return
        }
        warmAnim = ValueAnimator.ofFloat(warmth, if (on) 1f else 0f).apply {
            duration = if (on) 90L else 460L
            startDelay = if (on) 0L else 130L
            addUpdateListener { applyWarmth(it.animatedValue as Float) }
            start()
        }
    }

    private fun applyWarmth(w: Float) {
        warmth = w
        val c = blend.evaluate(w, Ui.INK, Ui.EMBER) as Int
        stroke.color = c
        dot.color = c
        invalidate()
    }

    init {
        isClickable = true
        isFocusable = true
        Ui.springPress(this) { warm(it) }
    }

    override fun setEnabled(enabled: Boolean) {
        super.setEnabled(enabled)
        alpha = if (enabled) 1f else 0.3f
        if (!enabled && translationY != 0f) settle()
    }

    // Back to rest after a swipe, also when the swipe itself disabled the button (no more history).
    private fun settle() {
        animate().translationY(0f).setDuration(420).setInterpolator(SpringInterpolator()).start()
    }

    // A swipe down that starts on the button runs the same action as a tap (DECISIONS.md 104). The icon follows the
    // finger a little, a tick confirms the swipe, and the icon springs back on release. A swipe never also taps.
    var onSwipeDown: (() -> Unit)? = null
    private var downX = 0f
    private var downY = 0f
    private var swiped = false
    private val swipeDistance = Ui.dp(context, 26).toFloat()
    private val followLimit = Ui.dp(context, 9).toFloat()

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val action = onSwipeDown
        if (action == null) return super.onTouchEvent(event)
        if (!isEnabled) {
            if (event.actionMasked == MotionEvent.ACTION_UP || event.actionMasked == MotionEvent.ACTION_CANCEL) settle()
            return super.onTouchEvent(event)
        }
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.rawX
                downY = event.rawY
                swiped = false
                parent?.requestDisallowInterceptTouchEvent(true)
            }
            MotionEvent.ACTION_MOVE -> {
                val dy = event.rawY - downY
                val dx = event.rawX - downX
                if (!swiped) {
                    if (dy > 0f && dy > abs(dx)) translationY = min(dy * 0.35f, followLimit)
                    if (dy >= swipeDistance && dy > abs(dx) * 1.2f) {
                        swiped = true
                        val cancel = MotionEvent.obtain(event)
                        cancel.action = MotionEvent.ACTION_CANCEL
                        super.onTouchEvent(cancel)
                        cancel.recycle()
                        performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
                        action()
                    }
                }
                if (swiped) return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                val was = swiped
                swiped = false
                settle()
                if (was) return true
            }
        }
        return super.onTouchEvent(event)
    }

    // Draws the icon of the current kind: arrows, stop cross, reload arc, menu dots, up and down chevrons.
    override fun onDraw(canvas: Canvas) {
        val cx = width / 2f
        val cy = height / 2f
        val r = Ui.dp(context, 8).toFloat()
        when (kind) {
            Kind.BACK, Kind.FORWARD -> {
                val s = if (kind == Kind.BACK) 1f else -1f
                path.reset()
                path.moveTo(cx + s * r, cy)
                path.lineTo(cx - s * r, cy)
                path.moveTo(cx - s * r * 0.2f, cy - r * 0.75f)
                path.lineTo(cx - s * r, cy)
                path.lineTo(cx - s * r * 0.2f, cy + r * 0.75f)
                canvas.drawPath(path, stroke)
            }
            Kind.RELOAD -> {
                arc.set(cx - r, cy - r, cx + r, cy + r)
                canvas.drawArc(arc, 60f, 250f, false, stroke)
                val end = Math.toRadians(-50.0)
                val ex = cx + r * cos(end).toFloat()
                val ey = cy + r * sin(end).toFloat()
                // Radial and tangent (clockwise) unit vectors at the arc end; the head points along the tangent.
                val ux = cos(end).toFloat()
                val uy = sin(end).toFloat()
                val tx = -uy
                val ty = ux
                val h = r * 0.75f
                path.reset()
                path.moveTo(ex + ux * h, ey + uy * h)
                path.lineTo(ex - ux * h, ey - uy * h)
                path.lineTo(ex + tx * r * 1.15f, ey + ty * r * 1.15f)
                path.close()
                canvas.drawPath(path, dot)
            }
            Kind.STOP -> {
                val h = r * 0.8f
                canvas.drawLine(cx - h, cy - h, cx + h, cy + h, stroke)
                canvas.drawLine(cx - h, cy + h, cx + h, cy - h, stroke)
            }
            Kind.UP, Kind.DOWN -> {
                val s = if (kind == Kind.UP) 1f else -1f
                path.reset()
                path.moveTo(cx - r * 0.75f, cy + s * r * 0.35f)
                path.lineTo(cx, cy - s * r * 0.35f)
                path.lineTo(cx + r * 0.75f, cy + s * r * 0.35f)
                canvas.drawPath(path, stroke)
            }
            Kind.MENU -> {
                val gap = r * 0.95f
                for (i in -1..1) canvas.drawCircle(cx + i * gap, cy, Ui.dp(context, 2).toFloat() * 1.05f, dot)
            }
        }
    }
}

// Pill-shaped address field: host while idle, full address while editing (cursor after the site name), ember rim when focused.
// Idle: a short host is centred; a long one starts at the left and runs to the pill's curve, cut mid-letter.
// Editing: the text starts at the left (DECISIONS.md 111, 112, 114).
class AddressField(context: Context, private val onSubmit: (String) -> Unit, private val onFocus: (Boolean) -> Unit) :
    FrameLayout(context) {
    val input = EditText(context)
    private val label = HostLabel(context)
    private val sidePad = Ui.dp(context, 12)

    // Page loading line, inside the pill along its bottom straight edge; it widens with the pill (DECISIONS.md 70).
    val progress = PageProgress(context)
    private var url: String? = null
    private val rim = GradientDrawable().apply {
        setColor(Ui.CARD)
        cornerRadius = Ui.dp(context, 26).toFloat()
        setStroke(Ui.dp(context, 1), Ui.LINE)
    }
    // The ember rim is a second outline faded in over the grey one: a stroke width in whole pixels would step at the end.
    private val emberRim = GradientDrawable().apply {
        cornerRadius = Ui.dp(context, 26).toFloat()
        setStroke(Ui.dp(context, 2), Ui.EMBER)
    }
    private var focusedNow = false
    private var loadingNow = false
    private var rimAmount = 0f
    private var rimAnim: ValueAnimator? = null

    init {
        background = rim
        foreground = emberRim
        clipChildren = false
        applyRim(0f)
        label.apply {
            setTextSize(TypedValue.COMPLEX_UNIT_PX, Ui.textPx(context, 15.5f))
            setPadding(sidePad, 0, sidePad, 0)
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }
        addView(label, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        refreshLabel()
        input.apply {
            alpha = 0f
            gravity = Gravity.CENTER_VERTICAL or Gravity.START
            background = null
            hint = "Search or enter address"
            setHintTextColor(0xFF9A9CA6.toInt())
            setTextColor(Ui.INK)
            setTextSize(TypedValue.COMPLEX_UNIT_PX, Ui.textPx(context, 15.5f))
            isSingleLine = true
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
            imeOptions = EditorInfo.IME_ACTION_GO or EditorInfo.IME_FLAG_NO_EXTRACT_UI
            highlightColor = 0x40E8451D
            setPadding(sidePad, 0, sidePad, 0)
            ellipsize = TextUtils.TruncateAt.END
            setOnEditorActionListener { _, _, _ ->
                onSubmit(text.toString())
                true
            }
            setOnFocusChangeListener { _, focused ->
                focusedNow = focused
                updateRim()
                if (focused) {
                    setText(url ?: "")
                    placeCursor()
                } else {
                    setText(url?.let { UrlInput.display(it) } ?: "")
                    refreshLabel()
                }
                glide(focused)
                onFocus(focused)
            }
        }
        addView(input, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        progress.visibility = View.INVISIBLE
        addView(
            progress,
            LayoutParams(LayoutParams.MATCH_PARENT, Ui.dp(context, 6), Gravity.BOTTOM).apply {
                marginStart = Ui.dp(context, 16)
                marginEnd = Ui.dp(context, 16)
                bottomMargin = Ui.dp(context, 3)
            },
        )
        isClickable = true
    }

    // Host (or the hint) shown while idle.
    private fun refreshLabel() {
        val shown = url?.let { UrlInput.display(it) }
        if (shown.isNullOrEmpty()) {
            label.text = "Search or enter address"
            label.setTextColor(0xFF9A9CA6.toInt())
        } else {
            label.text = shown
            label.setTextColor(Ui.INK)
        }
    }

    // Distance from the left start of the text to its centred place.
    private fun centreShift(): Float {
        val room = (width - 2 * sidePad).coerceAtLeast(0)
        val text = label.paint.measureText(label.text.toString()).coerceAtMost(room.toFloat())
        return (room - text) / 2f
    }

    // Editing starts: the centred host glides left and gives way to the editor. Editing ends: the host glides back to
    // the centre.
    private fun glide(editing: Boolean) {
        label.animate().cancel()
        input.animate().cancel()
        if (!ValueAnimator.areAnimatorsEnabled()) {
            label.alpha = if (editing) 0f else 1f
            label.translationX = 0f
            input.alpha = if (editing) 1f else 0f
            return
        }
        if (editing) {
            label.animate().translationX(-centreShift()).alpha(0f).setDuration(220).setInterpolator(PathInterpolator(0.2f, 0f, 0f, 1f)).start()
            input.animate().alpha(1f).setDuration(180).start()
        } else {
            label.translationX = -centreShift()
            label.alpha = 0f
            label.animate().translationX(0f).alpha(1f).setDuration(380).setInterpolator(SpringInterpolator()).start()
            input.animate().alpha(0f).setDuration(140).start()
        }
    }

    // The system font size changed: text sizes follow in place (the row height is set by the activity).
    fun applyScale() {
        val size = Ui.textPx(context, 15.5f)
        input.setTextSize(TypedValue.COMPLEX_UNIT_PX, size)
        label.setTextSize(TypedValue.COMPLEX_UNIT_PX, size)
    }

    // The ember rim and the loading line never show together: while a page loads, the editor has the plain rim and
    // the line; when the load ends the ember rim fades in over the plain one (DECISIONS.md 70).
    // Order: a load starts, the rim leaves and the line appears; the load ends, the line fills and fades away
    // completely, and only then does the ember rim fade in, slowly.
    private var loadGen = 0

    fun setLoading(on: Boolean) {
        if (on) {
            loadGen++
            loadingNow = true
            progress.start()
            updateRim(0L, 240L)
        } else {
            val gen = loadGen
            progress.finish {
                if (gen == loadGen) {
                    loadingNow = false
                    updateRim(140L, 760L)
                }
            }
        }
    }

    private fun updateRim(delayMs: Long = 0L, fadeInMs: Long = 520L) {
        val target = if (focusedNow && !loadingNow) 1f else 0f
        rimAnim?.cancel()
        rimAnim = null
        if (target == rimAmount) return
        if (!ValueAnimator.areAnimatorsEnabled()) {
            applyRim(target)
            return
        }
        rimAnim = ValueAnimator.ofFloat(rimAmount, target).apply {
            duration = if (target > rimAmount) fadeInMs else 240L
            startDelay = if (target > rimAmount) delayMs else 0L
            interpolator = PathInterpolator(0.4f, 0f, 0.2f, 1f)
            addUpdateListener { applyRim(it.animatedValue as Float) }
            start()
        }
    }

    // amount 0: plain rim, quiet glow. 1: ember rim, 2 dp, stronger glow.
    private fun applyRim(amount: Float) {
        rimAmount = amount
        emberRim.alpha = (255 * amount).toInt()
        elevation = Ui.dp(context, 4) + Ui.dp(context, 3) * amount
        val alpha = (0x59 + (0x99 - 0x59) * amount).toInt()
        outlineSpotShadowColor = (alpha shl 24) or (Ui.EMBER and 0x00FFFFFF)
        outlineAmbientShadowColor = 0x22000000
    }

    // While idle the whole pill is one button (DECISIONS.md 69): the field takes no touch of its own, so a tap never
    // drops the cursor under the finger. A tap opens the editor with the cursor already after the site name. Once the
    // editor is open, touches reach the text as usual.
    override fun onInterceptTouchEvent(e: MotionEvent): Boolean = !input.hasFocus()

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(e: MotionEvent): Boolean {
        if (input.hasFocus()) return super.onTouchEvent(e)
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> animate().scaleX(0.985f).scaleY(0.985f).setDuration(90).start()
            MotionEvent.ACTION_UP -> {
                release()
                if (e.x >= 0f && e.x <= width && e.y >= 0f && e.y <= height) performClick()
            }
            MotionEvent.ACTION_CANCEL -> release()
        }
        return true
    }

    private fun release() {
        animate().scaleX(1f).scaleY(1f).setDuration(380).setInterpolator(SpringInterpolator()).start()
    }

    override fun performClick(): Boolean {
        if (!input.hasFocus()) {
            input.requestFocus()
            (context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).showSoftInput(input, 0)
        }
        return super.performClick()
    }

    // Cursor after the site name, set when the editor opens; no touch of the text follows it.
    private fun placeCursor() {
        val at = { input.setSelection(UrlInput.nameEnd(input.text.toString()).coerceIn(0, input.text.length)) }
        at()
        input.post { at() }
    }

    fun showUrl(value: String) {
        url = value
        if (!input.hasFocus()) {
            input.setText(UrlInput.display(value))
            refreshLabel()
        }
    }
}

// Idle host label: centred when it fits; when longer it starts at the left and runs to the curve of the pill's end,
// cut mid-letter, with no fade and no ellipsis (DECISIONS.md 114).
private class HostLabel(context: Context) : TextView(context) {
    private val inset = Ui.dp(context, 3).toFloat()
    private val clip = Path()

    init {
        isSingleLine = true
        gravity = Gravity.START or Gravity.CENTER_VERTICAL
    }

    override fun onDraw(canvas: Canvas) {
        val room = (width - paddingLeft - paddingRight).toFloat()
        val text = paint.measureText(this.text.toString())
        if (text <= room) {
            canvas.save()
            canvas.translate((room - text) / 2f, 0f)
            super.onDraw(canvas)
            canvas.restore()
            return
        }
        val radius = height / 2f - inset
        clip.rewind()
        clip.addRoundRect(inset, inset, width - inset, height - inset, radius, radius, Path.Direction.CW)
        canvas.save()
        canvas.clipPath(clip)
        paint.color = currentTextColor
        canvas.drawText(this.text.toString(), paddingLeft.toFloat(), baseline.toFloat(), paint)
        canvas.restore()
    }
}
