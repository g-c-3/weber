package io.github.g_c_3.ardour

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.view.View

// The small "i" beside a Settings option (DECISIONS.md 140, 141, 143): only the letter, a grey dot over a rounded stem,
// drawn by Ardour. The view is a larger touch area around the 13 dp glyph.
class InfoButton(context: Context) : View(context) {
    private val mark = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF9A9892.toInt() }
    private val stem = RectF()

    init {
        isClickable = true
        isFocusable = true
        Ui.springPress(this)
    }

    override fun onDraw(canvas: Canvas) {
        val cx = width / 2f
        val cy = height / 2f
        val unit = Ui.dp(context, 1).toFloat()
        val half = unit * 1.1f
        canvas.drawCircle(cx, cy - unit * 5f, unit * 1.35f, mark)
        stem.set(cx - half, cy - unit * 2.4f, cx + half, cy + unit * 5.6f)
        canvas.drawRoundRect(stem, half, half, mark)
    }
}
