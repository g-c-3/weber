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
import kotlin.math.sin
import kotlin.random.Random

// Quick clean animation (DECISIONS.md 157): coloured paper strips come out of a glowing slot at the top of the page and
// fall, sway and fade, as paper does through a shredder. One animator, about 1.3 s, drawn over the page; touches pass
// through to what is below. It removes itself when done.
class ShredView(context: Context, private val slotY: Int, private val onDone: (View) -> Unit) : View(context) {
    private class Strip(val x: Float, val w: Float, val len: Float, val color: Int, val delay: Float, val phase: Float, val spin: Float)

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
        val count = 26
        val unit = width.toFloat() / count
        val gap = Ui.dp(context, 2).toFloat()
        for (i in 0 until count) {
            strips.add(
                Strip(
                    x = i * unit + gap / 2f,
                    w = unit - gap,
                    len = Ui.dp(context, 90).toFloat() + random.nextFloat() * Ui.dp(context, 120),
                    color = PALETTE[random.nextInt(PALETTE.size)],
                    // Strips leave the slot in a loose sweep from one side with some jitter.
                    delay = (i.toFloat() / count) * 0.28f + random.nextFloat() * 0.1f,
                    phase = random.nextFloat() * 6.28f,
                    spin = (random.nextFloat() - 0.5f) * 16f,
                ),
            )
        }
        animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 1350L
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
        val fall = height - slotTop
        canvas.save()
        canvas.clipRect(0f, slotTop, width.toFloat(), height.toFloat())
        for (s in strips) {
            val t = ((progress - s.delay) / 0.62f).coerceIn(0f, 1f)
            if (t <= 0f || t >= 1f) continue
            // Accelerating fall from the slot, a small sway and tilt, a quick fade in and a longer fade out.
            val y = -s.len + (fall + s.len) * (t * t * (0.55f + 0.45f * t))
            val sway = sin(t * 7f + s.phase) * Ui.dp(context, 5)
            val alpha = minOf(t / 0.08f, 1f) * minOf((1f - t) / 0.25f, 1f)
            canvas.save()
            canvas.translate(s.x + s.w / 2f + sway, slotTop + y + s.len / 2f)
            canvas.rotate(s.spin * sin(t * PI.toFloat()))
            rect.set(-s.w / 2f, -s.len / 2f, s.w / 2f, s.len / 2f)
            fill.color = (s.color and 0x00FFFFFF) or ((255 * alpha).toInt() shl 24)
            canvas.drawRoundRect(rect, 2f * resources.displayMetrics.density, 2f * resources.displayMetrics.density, fill)
            // A lighter edge on one side gives the paper some thickness.
            edge.color = 0x00FFFFFF or ((110 * alpha).toInt() shl 24)
            rect.set(-s.w / 2f, -s.len / 2f, -s.w / 2f + Ui.dp(context, 1), s.len / 2f)
            canvas.drawRect(rect, edge)
            canvas.restore()
        }
        canvas.restore()
        // The slot: a dark bar with an ember glow, visible while strips come out.
        val slotAlpha = minOf(progress / 0.06f, 1f) * minOf((1f - progress) / 0.2f, 1f)
        slot.color = (0x00242630) or ((230 * slotAlpha).toInt() shl 24)
        rect.set(Ui.dp(context, 10).toFloat(), slotTop - Ui.dp(context, 3), width - Ui.dp(context, 10).toFloat(), slotTop + Ui.dp(context, 3))
        canvas.drawRoundRect(rect, Ui.dp(context, 3).toFloat(), Ui.dp(context, 3).toFloat(), slot)
        slot.color = (Ui.EMBER and 0x00FFFFFF) or ((200 * slotAlpha).toInt() shl 24)
        rect.set(Ui.dp(context, 14).toFloat(), slotTop - 1f, width - Ui.dp(context, 14).toFloat(), slotTop + 1f)
        canvas.drawRoundRect(rect, 1f, 1f, slot)
    }

    private companion object {
        val PALETTE = intArrayOf(
            0xFFE8451D.toInt(), 0xFFF2791A.toInt(), 0xFFFFC247.toInt(), 0xFFFF6B6B.toInt(),
            0xFF2EC4B6.toInt(), 0xFF8E7DFF.toInt(), 0xFF4CC9F0.toInt(), 0xFFF4F1EA.toInt(),
        )
    }
}
