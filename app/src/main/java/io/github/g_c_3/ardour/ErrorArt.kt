package io.github.g_c_3.ardour

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.view.View
import android.view.animation.LinearInterpolator
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

// The picture of Ardour's own "page not available" screen (DECISIONS.md 187): an ember glow behind a ring, a symbol for what went
// wrong, and three small lights that circle the ring while the screen is shown. Drawn by Ardour; one animator, only while visible.
class ErrorArt(context: Context) : View(context) {
    var kind: PageError = PageError.OTHER
        set(value) {
            field = value
            invalidate()
        }
    private var turn = 0f
    private var animator: ValueAnimator? = null
    private val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val glow = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val path = Path()
    private val box = RectF()
    private var glowShader: RadialGradient? = null

    private fun d(n: Int) = Ui.dp(context, n).toFloat()

    override fun onMeasure(widthSpec: Int, heightSpec: Int) {
        setMeasuredDimension(Ui.dp(context, 150), Ui.dp(context, 150))
    }

    override fun onVisibilityChanged(changedView: View, visibility: Int) {
        super.onVisibilityChanged(changedView, visibility)
        if (visibility == VISIBLE && isAttachedToWindow) start() else stop()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (visibility == VISIBLE) start()
    }

    override fun onDetachedFromWindow() {
        stop()
        super.onDetachedFromWindow()
    }

    // The glow's gradient is made once here (centred at the origin) and scaled to breathe.
    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        glowShader = RadialGradient(0f, 0f, d(74), intArrayOf(0x55E8451D, 0x00E8451D), null, Shader.TileMode.CLAMP)
    }

    private fun start() {
        if (animator != null || !ValueAnimator.areAnimatorsEnabled()) return
        animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 6000
            repeatCount = ValueAnimator.INFINITE
            interpolator = LinearInterpolator()
            addUpdateListener {
                turn = it.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    private fun stop() {
        animator?.cancel()
        animator = null
    }

    override fun onDraw(canvas: Canvas) {
        val cx = width / 2f
        val cy = height / 2f
        val r = d(52)
        // Glow, breathing slowly.
        val breathe = 0.85f + 0.15f * sin(turn * 2f * PI.toFloat() * 2f)
        glow.shader = glowShader
        canvas.save()
        canvas.translate(cx, cy)
        canvas.scale(breathe, breathe)
        canvas.drawCircle(0f, 0f, d(74), glow)
        canvas.restore()
        // Ring and its three lights.
        ring.strokeWidth = d(3)
        ring.color = Ui.EMBER
        fill.color = Ui.CARD
        canvas.drawCircle(cx, cy, r, fill)
        canvas.drawCircle(cx, cy, r, ring)
        for (i in 0 until 3) {
            val a = (turn + i / 3f) * 2f * PI.toFloat()
            fill.color = if (i == 0) Ui.EMBER else (0xAAF2791A).toInt()
            canvas.drawCircle(cx + cos(a) * (r + d(11)), cy + sin(a) * (r + d(11)), d(3) * (if (i == 0) 1.2f else 0.8f), fill)
        }
        stroke.strokeWidth = d(4)
        stroke.color = Ui.INK
        when (kind) {
            PageError.OFFLINE -> wifi(canvas, cx, cy)
            PageError.NOT_FOUND -> magnifier(canvas, cx, cy)
            PageError.UNREACHABLE -> plug(canvas, cx, cy)
            PageError.SECURITY, PageError.NOT_SECURE -> shield(canvas, cx, cy)
            PageError.OTHER -> page(canvas, cx, cy)
        }
    }

    // Three arcs and a dot, cut by a slash.
    private fun wifi(canvas: Canvas, cx: Float, cy: Float) {
        val base = cy + d(16)
        for (i in 1..3) {
            val rr = d(11) * i
            box.set(cx - rr, base - rr, cx + rr, base + rr)
            canvas.drawArc(box, 225f, 90f, false, stroke)
        }
        fill.color = Ui.INK
        canvas.drawCircle(cx, base, d(3), fill)
        stroke.color = Ui.EMBER
        canvas.drawLine(cx - d(24), cy - d(22), cx + d(24), cy + d(26), stroke)
    }

    // A lens with a question mark.
    private fun magnifier(canvas: Canvas, cx: Float, cy: Float) {
        canvas.drawCircle(cx - d(6), cy - d(6), d(20), stroke)
        canvas.drawLine(cx + d(9), cy + d(9), cx + d(26), cy + d(26), stroke)
        fill.color = Ui.EMBER
        fill.textSize = d(24)
        fill.textAlign = Paint.Align.CENTER
        fill.typeface = android.graphics.Typeface.create("sans-serif", android.graphics.Typeface.BOLD)
        canvas.drawText("?", cx - d(6), cy + d(2), fill)
    }

    // Two halves of a plug that do not meet, with a small spark between them.
    private fun plug(canvas: Canvas, cx: Float, cy: Float) {
        val gap = d(7)
        // Left half: a block and two prongs.
        box.set(cx - d(30), cy - d(10), cx - gap, cy + d(10))
        canvas.drawRoundRect(box, d(4), d(4), stroke)
        canvas.drawLine(cx - gap, cy - d(5), cx - gap + d(8), cy - d(5), stroke)
        canvas.drawLine(cx - gap, cy + d(5), cx - gap + d(8), cy + d(5), stroke)
        // Right half: a block and a cable.
        box.set(cx + gap + d(8), cy - d(10), cx + d(30), cy + d(10))
        canvas.drawRoundRect(box, d(4), d(4), stroke)
        canvas.drawLine(cx + d(30), cy, cx + d(38), cy, stroke)
        stroke.color = Ui.EMBER
        path.reset()
        path.moveTo(cx - d(1), cy - d(14))
        path.lineTo(cx + d(2), cy - d(5))
        path.lineTo(cx - d(2), cy - d(1))
        path.lineTo(cx + d(2), cy + d(8))
        canvas.drawPath(path, stroke)
    }

    private fun shield(canvas: Canvas, cx: Float, cy: Float) {
        drawShieldGlyph(canvas, path, fill, cx, cy, d(26), Ui.EMBER, null)
        fill.color = 0xFFFFFFFF.toInt()
        fill.textSize = d(26)
        fill.textAlign = Paint.Align.CENTER
        fill.typeface = android.graphics.Typeface.create("sans-serif", android.graphics.Typeface.BOLD)
        canvas.drawText("!", cx, cy + d(9), fill)
    }

    // A page with a folded corner and a crack through it.
    private fun page(canvas: Canvas, cx: Float, cy: Float) {
        path.reset()
        path.moveTo(cx - d(18), cy - d(26))
        path.lineTo(cx + d(8), cy - d(26))
        path.lineTo(cx + d(20), cy - d(14))
        path.lineTo(cx + d(20), cy + d(26))
        path.lineTo(cx - d(18), cy + d(26))
        path.close()
        canvas.drawPath(path, stroke)
        canvas.drawLine(cx + d(8), cy - d(26), cx + d(8), cy - d(14), stroke)
        canvas.drawLine(cx + d(8), cy - d(14), cx + d(20), cy - d(14), stroke)
        stroke.color = Ui.EMBER
        path.reset()
        path.moveTo(cx - d(18), cy + d(2))
        path.lineTo(cx - d(6), cy - d(4))
        path.lineTo(cx, cy + d(6))
        path.lineTo(cx + d(10), cy)
        path.lineTo(cx + d(20), cy + d(8))
        canvas.drawPath(path, stroke)
    }
}
