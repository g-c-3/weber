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
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

// Quick clean animation (DECISIONS.md 157, 171, 172): a thin ember line, like the page loading line, fills from left to right
// across the top of the page; coloured paper strips come out of it as it passes and fall, sway and tilt, as paper does through
// a shredder. On the way down each strip splits in two, then in four, and the pieces are cut across, so it is shredded finer
// and finer. At the bottom of the screen the pieces crumble into minute coloured scraps that rise, drift and fade like dust.
// One animator, about 2.1 s, drawn over the page; touches pass through to what is below. It removes itself when done.
class ShredView(context: Context, private val slotY: Int, private val onDone: (View) -> Unit) : View(context) {
    // One scrap of dust: its offset across the strip, size, height of its hop, sideways drift and colour.
    private class Dust(val dx: Float, val size: Float, val rise: Float, val drift: Float, val color: Int)
    private class Strip(
        val x: Float, val w: Float, val len: Float, val color: Int, val delay: Float, val phase: Float, val spin: Float,
        val dust: List<Dust>, val jitter: FloatArray, val cut: FloatArray, val lag: FloatArray,
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

    private fun start() {
        if (width == 0) {
            post { start() }
            return
        }
        val random = Random(System.nanoTime())
        // Strip width is the same on every screen: 78 across a 360 dp wide portrait screen, more across a wider one (DECISIONS.md 168).
        val unit = Ui.dp(context, 360).toFloat() / 78f
        val count = (width / unit).toInt().coerceAtLeast(1)
        val gap = Ui.dp(context, 1) * 0.6f
        for (i in 0 until count) {
            val w = unit - gap
            val dust = List(DUST_PER_STRIP) {
                Dust(
                    dx = (random.nextFloat() - 0.5f) * w,
                    size = Ui.dp(context, 1) * (0.6f + random.nextFloat() * 1.3f),
                    rise = Ui.dp(context, 14) + random.nextFloat() * Ui.dp(context, 46),
                    drift = (random.nextFloat() - 0.5f) * Ui.dp(context, 48),
                    color = PALETTE[random.nextInt(PALETTE.size)],
                )
            }
            strips.add(
                Strip(
                    x = i * unit + gap / 2f,
                    w = w,
                    len = Ui.dp(context, 90).toFloat() + random.nextFloat() * Ui.dp(context, 120),
                    color = PALETTE[random.nextInt(PALETTE.size)],
                    // Strips leave the slot in a loose sweep from one side with some jitter.
                    delay = (i.toFloat() / count) * 0.24f + random.nextFloat() * 0.08f,
                    phase = random.nextFloat() * 6.28f,
                    spin = (random.nextFloat() - 0.5f) * 16f,
                    dust = dust,
                    // Per piece: sideways scatter and tilt, where it is cut across, and how far the upper part lags.
                    jitter = FloatArray(4) { random.nextFloat() * 2f - 1f },
                    cut = FloatArray(4) { 0.25f + random.nextFloat() * 0.5f },
                    lag = FloatArray(4) { 0.4f + random.nextFloat() * 0.6f },
                ),
            )
        }
        animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 2100L
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
        val sway = Ui.dp(context, 5)
        canvas.save()
        canvas.clipRect(0f, slotTop, width.toFloat(), bottom)
        for (s in strips) {
            val land = s.delay + FALL
            if (progress < s.delay || progress >= land + DUST) continue
            if (progress < land) {
                val t = (progress - s.delay) / FALL
                // Accelerating fall from the slot until the strip's lower end meets the bottom, with a small sway and tilt.
                val y = -s.len + fall * (t * t * (0.55f + 0.45f * t))
                val cx = s.x + s.w / 2f + sin(t * 7f + s.phase) * sway
                drawStrip(
                    canvas, s, cx, slotTop + y + s.len / 2f, s.len, s.spin * sin(t * PI.toFloat()), min(t / 0.08f, 1f),
                    smooth((t - 0.22f) / 0.16f), smooth((t - 0.52f) / 0.16f),
                )
            } else {
                val u = progress - land
                val cx = s.x + s.w / 2f + sin(7f + s.phase) * sway
                // The strip crumbles where it landed, and its scraps hop up, drift and settle as dust.
                if (u < CRUMBLE) {
                    val k = 1f - u / CRUMBLE
                    drawStrip(canvas, s, cx, bottom - s.len * k / 2f, s.len * k, 0f, k, 1f, 1f)
                }
                drawDust(canvas, s, cx, bottom, u / DUST)
            }
        }
        canvas.restore()
        drawLine(canvas, slotTop)
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

    private fun smooth(x: Float): Float {
        val c = x.coerceIn(0f, 1f)
        return c * c * (3f - 2f * c)
    }

    // One strip. split1 and split2 (0 to 1) are how far it has split: first in two, then in four, with each piece cut across
    // and its upper part lagging; the pieces scatter a little and tilt apart.
    private fun drawStrip(canvas: Canvas, s: Strip, cx: Float, cy: Float, len: Float, tilt: Float, alpha: Float, split1: Float, split2: Float) {
        canvas.save()
        canvas.translate(cx, cy)
        fill.color = (s.color and 0x00FFFFFF) or ((255 * alpha).toInt() shl 24)
        if (split1 <= 0f) {
            canvas.rotate(tilt)
            val radius = minOf(2f * resources.displayMetrics.density, s.w * 0.4f)
            rect.set(-s.w / 2f, -len / 2f, s.w / 2f, len / 2f)
            canvas.drawRoundRect(rect, radius, radius, fill)
            // A lighter edge on one side gives the paper some thickness.
            edge.color = 0x00FFFFFF or ((110 * alpha).toInt() shl 24)
            rect.set(-s.w / 2f, -len / 2f, -s.w / 2f + minOf(Ui.dp(context, 1).toFloat(), s.w * 0.3f), len / 2f)
            canvas.drawRect(rect, edge)
            canvas.restore()
            return
        }
        val q = s.w / 4f
        val d1 = Ui.dp(context, 1) * 1.4f
        val d2 = Ui.dp(context, 1) * 0.7f
        val d3 = Ui.dp(context, 1) * 2.5f
        for (c in 0..3) {
            val x = (c - 1.5f) * q + (if (c < 2) -1f else 1f) * split1 * d1 + (if (c % 2 == 0) -1f else 1f) * split2 * d2 + s.jitter[c] * split1 * d3
            canvas.save()
            canvas.rotate(tilt + s.jitter[c] * split1 * 7f)
            if (split2 <= 0f) {
                rect.set(x - q * 0.45f, -len / 2f, x + q * 0.45f, len / 2f)
                canvas.drawRect(rect, fill)
            } else {
                val cutAt = -len / 2f + len * s.cut[c]
                val gap = split2 * s.lag[c] * len * 0.22f
                rect.set(x - q * 0.45f, -len / 2f - gap, x + q * 0.45f, cutAt - gap)
                canvas.drawRect(rect, fill)
                rect.set(x - q * 0.45f, cutAt, x + q * 0.45f, len / 2f)
                canvas.drawRect(rect, fill)
            }
            canvas.restore()
        }
        canvas.restore()
    }

    // t runs 0 to 1: each scrap rises in an arc, drifts sideways and fades as it settles on the bottom edge.
    private fun drawDust(canvas: Canvas, s: Strip, cx: Float, bottom: Float, t: Float) {
        val alpha = min(t / 0.08f, 1f) * (1f - t) * (1f - t * 0.3f)
        if (alpha <= 0f) return
        for (d in s.dust) {
            val x = cx + d.dx + d.drift * t
            val y = bottom - d.size - d.rise * 4f * t * (1f - t)
            fill.color = (d.color and 0x00FFFFFF) or ((255 * alpha).toInt() shl 24)
            rect.set(x, y, x + d.size, y + d.size)
            canvas.drawRect(rect, fill)
        }
    }

    private companion object {
        // Shares of the animation: a strip falls for FALL, then crumbles over CRUMBLE while its dust lives for DUST. The line
        // fills over LINE; its strips leave it with delays that sweep across the same time.
        const val FALL = 0.46f
        const val DUST = 0.22f
        const val CRUMBLE = 0.07f
        const val LINE = 0.3f
        const val DUST_PER_STRIP = 8
        val PALETTE = intArrayOf(
            0xFFE8451D.toInt(), 0xFFF2791A.toInt(), 0xFFFFC247.toInt(), 0xFFFF6B6B.toInt(),
            0xFF2EC4B6.toInt(), 0xFF8E7DFF.toInt(), 0xFF4CC9F0.toInt(), 0xFFF4F1EA.toInt(),
        )
    }
}
