package io.github.g_c_3.ardour

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout

// A Settings card that opens another page: title and note on the left, a drawn chevron on the right.
object NavCard {
    fun build(ctx: Context, label: String, note: String, onOpen: () -> Unit): View {
        val row = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(Ui.row(ctx, label, note), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            addView(ChevronView(ctx), LinearLayout.LayoutParams(Ui.dp(ctx, 24), Ui.dp(ctx, 24)).apply {
                marginStart = Ui.dp(ctx, 12)
            })
        }
        return Ui.card(ctx).apply {
            addView(row)
            isClickable = true
            isFocusable = true
            contentDescription = "$label. $note"
            setOnClickListener { onOpen() }
            Ui.springPress(this)
        }
    }
}

// Right-pointing chevron in the muted text colour.
class ChevronView(context: Context) : View(context) {
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Ui.MUTED
        style = Paint.Style.STROKE
        strokeWidth = Ui.dp(context, 2).toFloat()
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val path = Path()

    override fun onDraw(canvas: Canvas) {
        val cx = width / 2f
        val cy = height / 2f
        val s = minOf(width, height) * 0.3f
        path.reset()
        path.moveTo(cx - s * 0.5f, cy - s)
        path.lineTo(cx + s * 0.5f, cy)
        path.lineTo(cx - s * 0.5f, cy + s)
        canvas.drawPath(path, stroke)
    }
}
