package io.github.g_c_3.ardour

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.drawable.GradientDrawable
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.LinearLayout
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

// Toolbar icon drawn by hand; dimmed when disabled.
class IconButton(context: Context, kind: Kind) : View(context) {
    enum class Kind { BACK, FORWARD, RELOAD, STOP, MENU }

    var kind: Kind = kind
        set(v) {
            field = v
            invalidate()
        }

    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = Ui.dp(context, 2).toFloat()
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
        val r = Ui.dp(context, 9).toFloat()
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
                canvas.drawArc(arc, 40f, 280f, false, stroke)
                val end = Math.toRadians(-40.0)
                val ex = cx + r * cos(end).toFloat()
                val ey = cy + r * sin(end).toFloat()
                val back = atan2(cos(end), -sin(end)) + Math.PI
                val len = r * 0.8f
                path.reset()
                path.moveTo(ex + len * cos(back + 0.6).toFloat(), ey + len * sin(back + 0.6).toFloat())
                path.lineTo(ex, ey)
                path.lineTo(ex + len * cos(back - 0.6).toFloat(), ey + len * sin(back - 0.6).toFloat())
                canvas.drawPath(path, stroke)
            }
            Kind.STOP -> {
                val h = r * 0.8f
                canvas.drawLine(cx - h, cy - h, cx + h, cy + h, stroke)
                canvas.drawLine(cx - h, cy + h, cx + h, cy - h, stroke)
            }
            Kind.MENU -> {
                val gap = r * 0.95f
                for (i in -1..1) canvas.drawCircle(cx + i * gap, cy, Ui.dp(context, 2).toFloat() * 1.05f, dot)
            }
        }
    }
}

// Pill-shaped address field: host while idle, full address while editing, ember rim when focused.
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
        elevation = Ui.dp(context, 2).toFloat()
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
            setPadding(Ui.dp(context, 20), 0, Ui.dp(context, 20), 0)
            setOnEditorActionListener { _, _, _ ->
                onSubmit(text.toString())
                true
            }
            setOnFocusChangeListener { _, focused ->
                rim.setStroke(Ui.dp(context, if (focused) 2 else 1), if (focused) Ui.EMBER else Ui.LINE)
                if (focused) {
                    setText(url ?: "")
                    selectAll()
                } else {
                    setText(url?.let { UrlInput.display(it) } ?: "")
                }
                onFocus(focused)
            }
        }
        addView(input, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
    }

    fun showUrl(value: String) {
        url = value
        if (!input.hasFocus()) input.setText(UrlInput.display(value))
    }
}
