package io.github.g_c_3.ardour

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.view.View

// The small "i" beside a Settings option (DECISIONS.md 140, 141): a light grey ring with a dot and a stem, drawn by
// Ardour. The view is a slightly larger touch area around the 16 dp ring.
class InfoButton(context: Context) : View(context) {
    private val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = Ui.dp(context, 1).toFloat()
        color = 0xFFD2CFC9.toInt()
    }
    private val tint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFF6F5F2.toInt() }
    private val mark = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF8E8C87.toInt() }
    private val stem = RectF()

    init {
        isClickable = true
        isFocusable = true
        Ui.springPress(this)
    }

    override fun onDraw(canvas: Canvas) {
        val cx = width / 2f
        val cy = height / 2f
        val r = Ui.dp(context, 8).toFloat()
        canvas.drawCircle(cx, cy, r, tint)
        canvas.drawCircle(cx, cy, r - ring.strokeWidth / 2f, ring)
        canvas.drawCircle(cx, cy - r * 0.38f, r * 0.14f, mark)
        val half = r * 0.12f
        stem.set(cx - half, cy - r * 0.16f, cx + half, cy + r * 0.46f)
        canvas.drawRoundRect(stem, half, half, mark)
    }
}
