package io.github.g_c_3.ardour

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.view.View

// The small "i" beside a Settings option (DECISIONS.md 140): a ring with a dot and a stem, drawn by Ardour. The view
// is a larger touch area around the 22 dp ring.
class InfoButton(context: Context) : View(context) {
    private val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = Ui.dp(context, 1).toFloat() * 1.5f
        color = Ui.EMBER
    }
    private val tint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = (0x1F shl 24) or (Ui.EMBER and 0x00FFFFFF) }
    private val mark = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Ui.EMBER }
    private val stem = RectF()

    init {
        isClickable = true
        isFocusable = true
        Ui.springPress(this)
    }

    override fun onDraw(canvas: Canvas) {
        val cx = width / 2f
        val cy = height / 2f
        val r = Ui.dp(context, 11).toFloat()
        canvas.drawCircle(cx, cy, r, tint)
        canvas.drawCircle(cx, cy, r - ring.strokeWidth / 2f, ring)
        canvas.drawCircle(cx, cy - Ui.dp(context, 4).toFloat() - 0.5f, Ui.dp(context, 1).toFloat() * 1.4f, mark)
        val half = Ui.dp(context, 1).toFloat() * 1.2f
        stem.set(cx - half, cy - Ui.dp(context, 1).toFloat() * 1.5f, cx + half, cy + Ui.dp(context, 5).toFloat())
        canvas.drawRoundRect(stem, half, half, mark)
    }
}
