package io.github.g_c_3.ardour

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.view.animation.LinearInterpolator

// Page loading line (DECISIONS.md 67): 2 dp, a faint track, an ember-to-amber fill that glides to each reported
// value, a soft sheen travelling along the fill, and a small glow at the leading edge that fades as the load
// completes. Finishing fills to the end and fades out. Until the engine passes 20% (its value while it waits for the
// server) a light sweeps the track, so the wait does not look like a stall (DECISIONS.md 91).
class PageProgress(context: Context) : View(context) {
    private val barHeight = Ui.dp(context, 2).toFloat()
    private val glowRadius = Ui.dp(context, 7).toFloat()
    private val sheenWidth = Ui.dp(context, 72).toFloat()
    private val track = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Ui.LINE; alpha = 110 }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val sheen = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        shader = LinearGradient(
            0f, 0f, sheenWidth, 0f,
            intArrayOf(0x00FFFFFF, 0x80FFFFFF.toInt(), 0x00FFFFFF), null, Shader.TileMode.CLAMP,
        )
    }
    private val glow = Paint(Paint.ANTI_ALIAS_FLAG)
    private val glowShader = RadialGradient(
        0f, 0f, glowRadius, intArrayOf(0x70F2791A, 0x00F2791A), null, Shader.TileMode.CLAMP,
    )
    private val shaderMatrix = Matrix()
    private val rect = RectF()
    private val clip = Path()

    private var shown = 0f
    private var indeterminate = false
    private var phase = 0f
    private var glide: ValueAnimator? = null
    private var loop: ValueAnimator? = null

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        fill.shader = LinearGradient(0f, 0f, w.toFloat(), 0f, Ui.EMBER, Ui.AMBER, Shader.TileMode.CLAMP)
    }

    // A load begins: visible at once, sweeping until the engine reports real progress.
    fun start() {
        finishing = false
        animate().cancel()
        glide?.cancel()
        alpha = 1f
        visibility = VISIBLE
        shown = 0.04f
        indeterminate = true
        startLoop()
        invalidate()
    }

    // The engine's reported progress, 0 to 1. The fill glides to it instead of jumping.
    // Ignored while finishing: a late report would cancel the fill that starts the fade, and the line would stay.
    fun update(fraction: Float) {
        if (visibility != VISIBLE || finishing) return
        if (indeterminate && fraction <= 0.2f) return
        indeterminate = false
        val target = fraction.coerceIn(0.06f, 1f)
        if (target <= shown) return
        glideTo(target, (160 + 520 * (target - shown)).toLong().coerceIn(160L, 640L), DecelerateInterpolator(1.6f)) {}
    }

    // The load ended: fill to the end, then fade out. onGone runs once the line is completely gone (at once when it was
    // not showing); a new start() before that also runs it, so the caller checks that it is still current.
    private var finishing = false

    fun finish(onGone: (() -> Unit)? = null) {
        if (visibility != VISIBLE) {
            onGone?.invoke()
            return
        }
        if (finishing) return
        finishing = true
        indeterminate = false
        glideTo(1f, 240L, AccelerateDecelerateInterpolator()) {
            animate().alpha(0f).setStartDelay(80).setDuration(340).withEndAction {
                finishing = false
                visibility = INVISIBLE
                alpha = 1f
                shown = 0f
                stopLoop()
                onGone?.invoke()
            }.start()
        }
    }

    private fun glideTo(target: Float, durationMs: Long, ease: android.view.animation.Interpolator, onEnd: () -> Unit) {
        glide?.cancel()
        if (!ValueAnimator.areAnimatorsEnabled()) {
            shown = target
            invalidate()
            onEnd()
            return
        }
        glide = ValueAnimator.ofFloat(shown, target).apply {
            duration = durationMs
            interpolator = ease
            addUpdateListener {
                shown = it.animatedValue as Float
                invalidate()
            }
            addListener(object : android.animation.AnimatorListenerAdapter() {
                private var cancelled = false
                override fun onAnimationCancel(animation: android.animation.Animator) {
                    cancelled = true
                }

                override fun onAnimationEnd(animation: android.animation.Animator) {
                    if (!cancelled) onEnd()
                }
            })
            start()
        }
    }

    private fun startLoop() {
        if (loop != null) return
        if (!ValueAnimator.areAnimatorsEnabled()) {
            phase = 0.5f
            return
        }
        loop = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 1500
            repeatCount = ValueAnimator.INFINITE
            interpolator = LinearInterpolator()
            addUpdateListener {
                phase = it.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    private fun stopLoop() {
        loop?.cancel()
        loop = null
    }

    override fun onDetachedFromWindow() {
        glide?.cancel()
        stopLoop()
        super.onDetachedFromWindow()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (visibility == VISIBLE) startLoop()
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val cy = height / 2f
        val top = cy - barHeight / 2f
        val bottom = cy + barHeight / 2f
        val r = barHeight / 2f
        rect.set(0f, top, w, bottom)
        canvas.drawRoundRect(rect, r, r, track)
        if (indeterminate) {
            val seg = w * 0.3f
            val x = -seg + (w + seg) * phase
            clip.reset()
            clip.addRoundRect(rect, r, r, Path.Direction.CW)
            canvas.save()
            canvas.clipPath(clip)
            rect.set(x, top, x + seg, bottom)
            canvas.drawRoundRect(rect, r, r, fill)
            canvas.restore()
            return
        }
        val end = w * shown
        if (end <= 0f) return
        rect.set(0f, top, end, bottom)
        canvas.drawRoundRect(rect, r, r, fill)
        // Sheen: a soft highlight that travels along the filled part.
        clip.reset()
        clip.addRoundRect(rect, r, r, Path.Direction.CW)
        canvas.save()
        canvas.clipPath(clip)
        shaderMatrix.setTranslate(-sheenWidth + (end + sheenWidth) * phase, 0f)
        sheen.shader.setLocalMatrix(shaderMatrix)
        canvas.drawRect(0f, top, end, bottom, sheen)
        canvas.restore()
        // Leading-edge glow, fading out as the load nears its end.
        shaderMatrix.setTranslate(end, cy)
        glowShader.setLocalMatrix(shaderMatrix)
        glow.shader = glowShader
        glow.alpha = (255 * (1f - shown * 0.8f)).toInt().coerceIn(0, 255)
        canvas.drawCircle(end, cy, glowRadius, glow)
    }
}
