package io.github.g_c_3.ardour

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.drawable.GradientDrawable
import android.text.InputType
import android.text.TextUtils
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.LinearLayout
import kotlin.math.cos
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

    init {
        isClickable = true
        isFocusable = true
        Ui.springPress(this)
    }

    override fun setEnabled(enabled: Boolean) {
        super.setEnabled(enabled)
        alpha = if (enabled) 1f else 0.3f
    }

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
class AddressField(context: Context, private val onSubmit: (String) -> Unit, private val onFocus: (Boolean) -> Unit) :
    LinearLayout(context) {
    val input = EditText(context)
    private var url: String? = null
    private val rim = GradientDrawable().apply {
        setColor(Ui.CARD)
        cornerRadius = Ui.dp(context, 26).toFloat()
        setStroke(Ui.dp(context, 1), Ui.LINE)
    }

    init {
        gravity = Gravity.CENTER_VERTICAL
        background = rim
        clipChildren = false
        setGlow(false)
        input.apply {
            background = null
            hint = "Search or enter address"
            setHintTextColor(0xFF9A9CA6.toInt())
            setTextColor(Ui.INK)
            textSize = 15.5f
            isSingleLine = true
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
            imeOptions = EditorInfo.IME_ACTION_GO or EditorInfo.IME_FLAG_NO_EXTRACT_UI
            highlightColor = 0x40E8451D
            setPadding(Ui.dp(context, 16), 0, Ui.dp(context, 16), 0)
            ellipsize = TextUtils.TruncateAt.END
            setOnEditorActionListener { _, _, _ ->
                onSubmit(text.toString())
                true
            }
            setOnFocusChangeListener { _, focused ->
                rim.setStroke(Ui.dp(context, if (focused) 2 else 1), if (focused) Ui.EMBER else Ui.LINE)
                setGlow(focused)
                if (focused) {
                    setText(url ?: "")
                    placeCursor()
                } else {
                    setText(url?.let { UrlInput.display(it) } ?: "")
                }
                onFocus(focused)
            }
        }
        addView(input, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        isClickable = true
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

    // Slight ember glow around the pill; a little stronger while editing.
    private fun setGlow(focused: Boolean) {
        elevation = Ui.dp(context, if (focused) 7 else 4).toFloat()
        outlineSpotShadowColor = ((if (focused) 0x99 else 0x59) shl 24) or (Ui.EMBER and 0x00FFFFFF)
        outlineAmbientShadowColor = 0x22000000
    }

    // Cursor after the site name, set when the editor opens; no touch of the text follows it.
    private fun placeCursor() {
        val at = { input.setSelection(UrlInput.nameEnd(input.text.toString()).coerceIn(0, input.text.length)) }
        at()
        input.post { at() }
    }

    fun showUrl(value: String) {
        url = value
        if (!input.hasFocus()) input.setText(UrlInput.display(value))
    }
}
