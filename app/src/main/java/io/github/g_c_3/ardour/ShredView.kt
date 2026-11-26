package io.github.g_c_3.ardour

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.view.View
import android.view.animation.LinearInterpolator
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

// Quick clean animation (DECISIONS.md 157, 171, 172, 174): a thin ember line, like the page loading line, fills from left to
// right across the top of the page; coloured paper strips come out of it as it passes and fall, sway and tilt, as paper does
// through a shredder. Each strip is made of small cells. On the way down it splits, its pieces drift apart, and the cells
// come away one by one, from the tail first, shrinking to minute scraps that trail behind it, flutter and fade out before they
// reach the bottom: the strip is dust by the end. One animator, about 2.4 s, drawn over the page; touches pass through to
// what is below. It removes itself when done.
class ShredView(context: Context, private val slotY: Int, private val onDone: (View) -> Unit) : View(context) {
    // A strip of COLS x ROWS cells. Per cell: when it comes away (theta, in strip time), where it was then in the world
    // (wx, wy), the ease of the strip then, its speed against the strip's (slow), sideways drift, dust size and colour.
    private class Strip(
        val x: Float, val w: Float, val len: Float, val color: Int, val delay: Float, val phase: Float, val spin: Float,
        val theta: FloatArray, val wx: FloatArray, val wy: FloatArray, val ease0: FloatArray,
        val slow: FloatArray, val drift: FloatArray, val size: FloatArray, val tone: IntArray, val jx: FloatArray,
    )

    private val strips = ArrayList<Strip>()
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val edge = Paint(Paint.ANTI_ALIAS_FLAG)
    private val slot = Paint(Paint.ANTI_ALIAS_FLAG)
    private val line = Paint(Paint.ANTI_ALIAS_FLAG)
    private val glow = Paint(Paint.ANTI_ALIAS_FLAG)
    private val glowMatrix = Matrix()
    private val glowShader = RadialGradient(0f, 0f, Ui.dp(context, 7).toFloat(), intArrayOf(0x70F2791A, 0x00F2791A), null, Shader.TileMode.CLAMP)
    private val rect = RectF()
    private var progress = 0f

    // When a strip splits and when its cells come away, in strip time (1 is the bottom). A strip stays whole through the upper
    // part of the screen and is dust before the bottom; on a landscape screen, which is short, only a fraction of that whole
    // stretch is kept (DECISIONS.md 175).
    private var splitAt = 0.66f
    private var splitSpan = 0.2f
    private var thetaMin = 0.78f
    private var thetaSpan = 0.18f
    private var morph = 0.08f
    private var animator: ValueAnimator? = null

    init {
        isClickable = false
        isFocusable = false
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        start()
    }

    override fun onDetachedFromWindow() {
        animator?.cancel()
        super.onDetachedFromWindow()
    }

    private fun ease(t: Float) = t * t * (0.55f + 0.45f * t)

    private fun smooth(x: Float): Float {
        val c = x.coerceIn(0f, 1f)
        return c * c * (3f - 2f * c)
    }

    // Where a cell sits in the strip's own frame when the strip has split by `split` (0 to 1): columns draw apart, rows spread.
    private fun offX(col: Int, cw: Float, jitter: Float, split: Float) =
        (col - 1) * cw + (col - 1) * Ui.dp(context, 1) * 1.4f * split + jitter * Ui.dp(context, 1) * 2.5f * split

    private fun offY(row: Int, ch: Float, split: Float) = (row - (ROWS - 1) / 2f) * ch * (1f + 0.25f * split)

    private fun start() {
        if (width == 0 || height == 0) {
            post { start() }
            return
        }
        val random = Random(System.nanoTime())
        if (width > height) {
            splitAt = 0.32f
            splitSpan = 0.25f
            thetaMin = 0.44f
            thetaSpan = 0.36f
            morph = 0.1f
        }
        val slotTop = slotY.toFloat()
        val fall = height - slotTop
        val sway = Ui.dp(context, 5)
        // Strip width is the same on every screen: 78 across a 360 dp wide portrait screen, more across a wider one (DECISIONS.md 168).
        val unit = Ui.dp(context, 360).toFloat() / 78f
        val count = (width / unit).toInt().coerceAtLeast(1)
        val gap = Ui.dp(context, 1) * 0.6f
        for (i in 0 until count) {
            val w = unit - gap
            val len = Ui.dp(context, 90).toFloat() + random.nextFloat() * Ui.dp(context, 120)
            val x = i * unit + gap / 2f
            val phase = random.nextFloat() * 6.28f
            val spin = (random.nextFloat() - 0.5f) * 16f
            val color = PALETTE[random.nextInt(PALETTE.size)]
            val cw = w / COLS
            val ch = len / ROWS
            val theta = FloatArray(N)
            val wx = FloatArray(N)
            val wy = FloatArray(N)
            val e0 = FloatArray(N)
            val jx = FloatArray(N) { random.nextFloat() * 2f - 1f }
            for (c in 0 until N) {
                val row = c / COLS
                // The tail (top rows) comes away first, the leading end last, with chance in between.
                theta[c] = thetaMin + thetaSpan * (0.6f * random.nextFloat() + 0.4f * row / (ROWS - 1f))
                val split = smooth((theta[c] - splitAt) / splitSpan)
                val ox = offX(c % COLS, cw, jx[c], split)
                val oy = offY(row, ch, split)
                val tilt = (spin * sin(theta[c] * PI.toFloat()) * PI / 180.0).toFloat()
                e0[c] = ease(theta[c])
                wx[c] = x + w / 2f + sin(theta[c] * 7f + phase) * sway + ox * cos(tilt) - oy * sin(tilt)
                wy[c] = slotTop - len + fall * e0[c] + len / 2f + ox * sin(tilt) + oy * cos(tilt)
            }
            strips.add(
                Strip(
                    x = x, w = w, len = len, color = color,
                    // Strips leave the line in a loose sweep from the left with some jitter.
                    delay = (i.toFloat() / count) * 0.24f + random.nextFloat() * 0.08f,
                    phase = phase, spin = spin, theta = theta, wx = wx, wy = wy, ease0 = e0,
                    slow = FloatArray(N) { 0.2f + random.nextFloat() * 0.3f },
                    drift = FloatArray(N) { (random.nextFloat() - 0.5f) * Ui.dp(context, 52) },
                    size = FloatArray(N) { Ui.dp(context, 1) * (0.7f + random.nextFloat() * 1.3f) },
                    tone = IntArray(N) { if (random.nextFloat() < 0.7f) color else PALETTE[random.nextInt(PALETTE.size)] },
                    jx = jx,
                ),
            )
        }
        animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 2400L
            interpolator = LinearInterpolator()
            addUpdateListener {
                progress = it.animatedValue as Float
                invalidate()
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    onDone(this@ShredView)
                }
            })
            start()
        }
    }

    override fun onDraw(canvas: Canvas) {
        if (strips.isEmpty()) return
        val slotTop = slotY.toFloat()
        val bottom = height.toFloat()
        val fall = bottom - slotTop
        val sway = Ui.dp(context, 5).toFloat()
        canvas.save()
        canvas.clipRect(0f, slotTop, width.toFloat(), bottom)
        for (s in strips) {
            if (progress < s.delay) continue
            val t = (progress - s.delay) / FALL
            if (t >= END) continue
            drawStrip(canvas, s, t, slotTop, fall, bottom, sway)
        }
        canvas.restore()
        drawLine(canvas, slotTop)
    }

    private fun drawStrip(canvas: Canvas, s: Strip, t: Float, slotTop: Float, fall: Float, bottom: Float, sway: Float) {
        val cx = s.x + s.w / 2f + sin(t * 7f + s.phase) * sway
        val cy = slotTop - s.len + fall * ease(t) + s.len / 2f
        val tilt = s.spin * sin(min(t, 1f) * PI.toFloat())
        val appear = min(t / 0.08f, 1f)
        fill.color = (s.color and 0x00FFFFFF) or ((255 * appear).toInt() shl 24)
        if (t < splitAt) {
            // Whole: one strip.
            canvas.save()
            canvas.translate(cx, cy)
            canvas.rotate(tilt)
            val radius = minOf(2f * resources.displayMetrics.density, s.w * 0.4f)
            rect.set(-s.w / 2f, -s.len / 2f, s.w / 2f, s.len / 2f)
            canvas.drawRoundRect(rect, radius, radius, fill)
            // A lighter edge on one side gives the paper some thickness.
            edge.color = 0x00FFFFFF or ((110 * appear).toInt() shl 24)
            rect.set(-s.w / 2f, -s.len / 2f, -s.w / 2f + minOf(Ui.dp(context, 1).toFloat(), s.w * 0.3f), s.len / 2f)
            canvas.drawRect(rect, edge)
            canvas.restore()
            return
        }
        val cw = s.w / COLS
        val ch = s.len / ROWS
        val split = smooth((t - splitAt) / splitSpan)
        // Cells still in the strip: drawn in its tilted frame.
        canvas.save()
        canvas.translate(cx, cy)
        canvas.rotate(tilt)
        for (c in 0 until N) {
            if (t >= s.theta[c]) continue
            val x = offX(c % COLS, cw, s.jx[c], split)
            val y = offY(c / COLS, ch, split)
            rect.set(x - cw * (1f - 0.1f * split) / 2f, y - ch * (1f - 0.25f * split) / 2f, x + cw * (1f - 0.1f * split) / 2f, y + ch * (1f - 0.25f * split) / 2f)
            canvas.drawRect(rect, fill)
        }
        canvas.restore()
        // Cells that came away: they shrink to scraps, fall slower than the strip, flutter, and fade before the bottom.
        val e = ease(t)
        val timeFade = ((END - t) / 0.25f).coerceIn(0f, 1f)
        val zone = Ui.dp(context, 36).toFloat()
        for (c in 0 until N) {
            if (t < s.theta[c]) continue
            val u = t - s.theta[c]
            val k = smooth(u / morph)
            val y = s.wy[c] + fall * (e - s.ease0[c]) * s.slow[c]
            val a = ((bottom - y) / zone).coerceIn(0f, 1f) * timeFade
            if (a <= 0f) continue
            val x = s.wx[c] + s.drift[c] * u + sin(u * 9f + s.theta[c] * 40f) * Ui.dp(context, 1) * 1.5f * k
            val w = cw * (1f - 0.1f * split) * (1f - k) + s.size[c] * k
            val h = ch * (1f - 0.25f * split) * (1f - k) + s.size[c] * k
            fill.color = (s.tone[c] and 0x00FFFFFF) or ((255 * a * appear).toInt() shl 24)
            rect.set(x - w / 2f, y - h / 2f, x + w / 2f, y + h / 2f)
            canvas.drawRect(rect, fill)
        }
    }

    // The line the strips come out of: 2 dp, a faint track, an ember-to-amber fill that runs from left to right, a small glow
    // at its head; it fades once the fill is complete.
    private fun drawLine(canvas: Canvas, y: Float) {
        val alpha = min(progress / 0.04f, 1f) * ((LINE + 0.14f - progress) / 0.12f).coerceIn(0f, 1f)
        if (alpha <= 0f) return
        val left = Ui.dp(context, 10).toFloat()
        val right = width - left
        val half = Ui.dp(context, 1).toFloat()
        val head = left + (right - left) * (progress / LINE).coerceIn(0f, 1f)
        if (line.shader == null) line.shader = LinearGradient(left, 0f, right, 0f, Ui.EMBER, Ui.AMBER, Shader.TileMode.CLAMP)
        line.alpha = (255 * alpha).toInt()
        rect.set(left, y - half, head, y + half)
        canvas.drawRoundRect(rect, half, half, line)
        slot.color = (Ui.LINE and 0x00FFFFFF) or ((110 * alpha).toInt() shl 24)
        rect.set(head, y - half, right, y + half)
        canvas.drawRect(rect, slot)
        glowMatrix.setTranslate(head, y)
        glowShader.setLocalMatrix(glowMatrix)
        glow.shader = glowShader
        glow.alpha = (255 * alpha).toInt()
        canvas.drawCircle(head, y, Ui.dp(context, 7).toFloat(), glow)
    }

    private companion object {
        // Strip time t runs 0 to END; a strip would meet the bottom at t = 1, but is dust before it. FALL is the share of
        // the animation one unit of t takes; LINE the share the line takes to fill. 
        const val FALL = 0.5f
        const val END = 1.35f
        const val LINE = 0.3f
        const val COLS = 3
        const val ROWS = 6
        const val N = COLS * ROWS
        val PALETTE = intArrayOf(
            0xFFE8451D.toInt(), 0xFFF2791A.toInt(), 0xFFFFC247.toInt(), 0xFFFF6B6B.toInt(),
            0xFF2EC4B6.toInt(), 0xFF8E7DFF.toInt(), 0xFF4CC9F0.toInt(), 0xFFF4F1EA.toInt(),
        )
    }
}
