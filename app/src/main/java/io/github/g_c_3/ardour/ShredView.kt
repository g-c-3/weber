package io.github.g_c_3.ardour

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.view.View
import android.view.animation.LinearInterpolator
import kotlin.math.PI
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

// Quick clean animation (DECISIONS.md 157, 171): coloured paper strips come out of a glowing slot at the top of the page and
// fall, sway and tilt, as paper does through a shredder. At the bottom of the screen each strip crumbles into a puff of
// minute coloured scraps that rise, drift and fade like dust. One animator, about 1.9 s, drawn over the page; touches pass
// through to what is below. It removes itself when done.
class ShredView(context: Context, private val slotY: Int, private val onDone: (View) -> Unit) : View(context) {
    // One scrap of dust: its offset across the strip, size, height of its hop, sideways drift and colour.
    private class Dust(val dx: Float, val size: Float, val rise: Float, val drift: Float, val color: Int)
    private class Strip(
        val x: Float, val w: Float, val len: Float, val color: Int, val delay: Float, val phase: Float, val spin: Float,
        val dust: List<Dust>,
    )

    private val strips = ArrayList<Strip>()
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val edge = Paint(Paint.ANTI_ALIAS_FLAG)
    private val slot = Paint(Paint.ANTI_ALIAS_FLAG)
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
                    size = Ui.dp(context, 1) * (0.8f + random.nextFloat() * 1.4f),
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
                ),
            )
        }
        animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 1900L
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
                drawStrip(canvas, s, cx, slotTop + y + s.len / 2f, s.len, s.spin * sin(t * PI.toFloat()), min(t / 0.08f, 1f))
            } else {
                val u = progress - land
                val cx = s.x + s.w / 2f + sin(7f + s.phase) * sway
                // The strip crumbles where it landed, and its scraps hop up, drift and settle as dust.
                if (u < CRUMBLE) {
                    val k = 1f - u / CRUMBLE
                    drawStrip(canvas, s, cx, bottom - s.len * k / 2f, s.len * k, 0f, k)
                }
                drawDust(canvas, s, cx, bottom, u / DUST)
            }
        }
        canvas.restore()
        // The slot: a dark bar with an ember glow, visible while strips come out.
        val slotAlpha = minOf(progress / 0.05f, 1f) * ((0.5f - progress) / 0.15f).coerceIn(0f, 1f)
        if (slotAlpha <= 0f) return
        slot.color = (0x00242630) or ((230 * slotAlpha).toInt() shl 24)
        rect.set(Ui.dp(context, 10).toFloat(), slotTop - Ui.dp(context, 3), width - Ui.dp(context, 10).toFloat(), slotTop + Ui.dp(context, 3))
        canvas.drawRoundRect(rect, Ui.dp(context, 3).toFloat(), Ui.dp(context, 3).toFloat(), slot)
        slot.color = (Ui.EMBER and 0x00FFFFFF) or ((200 * slotAlpha).toInt() shl 24)
        rect.set(Ui.dp(context, 14).toFloat(), slotTop - 1f, width - Ui.dp(context, 14).toFloat(), slotTop + 1f)
        canvas.drawRoundRect(rect, 1f, 1f, slot)
    }

    private fun drawStrip(canvas: Canvas, s: Strip, cx: Float, cy: Float, len: Float, tilt: Float, alpha: Float) {
        canvas.save()
        canvas.translate(cx, cy)
        canvas.rotate(tilt)
        val radius = minOf(2f * resources.displayMetrics.density, s.w * 0.4f)
        rect.set(-s.w / 2f, -len / 2f, s.w / 2f, len / 2f)
        fill.color = (s.color and 0x00FFFFFF) or ((255 * alpha).toInt() shl 24)
        canvas.drawRoundRect(rect, radius, radius, fill)
        // A lighter edge on one side gives the paper some thickness.
        edge.color = 0x00FFFFFF or ((110 * alpha).toInt() shl 24)
        rect.set(-s.w / 2f, -len / 2f, -s.w / 2f + minOf(Ui.dp(context, 1).toFloat(), s.w * 0.3f), len / 2f)
        canvas.drawRect(rect, edge)
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
        // Shares of the animation: a strip falls for FALL, then crumbles over CRUMBLE while its dust lives for DUST.
        const val FALL = 0.46f
        const val DUST = 0.22f
        const val CRUMBLE = 0.07f
        const val DUST_PER_STRIP = 6
        val PALETTE = intArrayOf(
            0xFFE8451D.toInt(), 0xFFF2791A.toInt(), 0xFFFFC247.toInt(), 0xFFFF6B6B.toInt(),
            0xFF2EC4B6.toInt(), 0xFF8E7DFF.toInt(), 0xFF4CC9F0.toInt(), 0xFFF4F1EA.toInt(),
        )
    }
}
