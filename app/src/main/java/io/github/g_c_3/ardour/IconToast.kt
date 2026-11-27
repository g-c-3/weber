package io.github.g_c_3.ardour

import android.animation.ArgbEvaluator
import android.animation.ValueAnimator
import android.app.Activity
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView

// Notice shown when "Dark app icon" is flipped (DECISIONS.md 159): a small icon tile whose colour changes from the current
// icon's background to the other one's, beside the line that the home-screen icon changes now (DECISIONS.md 183).
// Slides up with a spring, holds, slides away.
object IconToast {
    private const val HOLD_MS = 3800L

    fun show(activity: Activity, toDark: Boolean) {
        val content = activity.findViewById<ViewGroup>(android.R.id.content)
        content.findViewWithTag<View>("icon-toast")?.let { content.removeView(it) }
        val from = activity.getColor(if (toDark) R.color.ic_launcher_light_background else R.color.ic_launcher_background)
        val to = activity.getColor(if (toDark) R.color.ic_launcher_background else R.color.ic_launcher_light_background)
        val tile = TileView(activity, from)
        val label = TextView(activity).apply {
            text = "The icon changes now. Ardour may close for a moment."
            setTextColor(Ui.INK)
            setTextSize(TypedValue.COMPLEX_UNIT_PX, Ui.textPx(activity, 13.5f))
            maxWidth = Ui.dp(activity, 230)
        }
        val pill = LinearLayout(activity).apply {
            tag = "icon-toast"
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(Ui.dp(activity, 12), Ui.dp(activity, 10), Ui.dp(activity, 18), Ui.dp(activity, 10))
            background = GradientDrawable().apply {
                setColor(Ui.CARD)
                cornerRadius = Ui.dp(activity, 28).toFloat()
                setStroke(Ui.dp(activity, 1), Ui.LINE)
            }
            elevation = Ui.dp(activity, 8).toFloat()
            addView(tile, LinearLayout.LayoutParams(Ui.dp(activity, 44), Ui.dp(activity, 44)).apply { marginEnd = Ui.dp(activity, 12) })
            addView(label)
            alpha = 0f
        }
        content.addView(pill, FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL).apply {
            bottomMargin = Ui.dp(activity, 72)
        })
        if (!ValueAnimator.areAnimatorsEnabled()) {
            tile.color = to
            pill.alpha = 1f
            pill.postDelayed({ content.removeView(pill) }, HOLD_MS)
            return
        }
        pill.translationY = Ui.dp(activity, 80).toFloat()
        pill.animate().alpha(1f).translationY(0f).setDuration(460).setInterpolator(SpringInterpolator()).start()
        // The tile changes colour once the notice has settled, with a small pop.
        ValueAnimator.ofFloat(0f, 1f).apply {
            startDelay = 520
            duration = 1000
            val argb = ArgbEvaluator()
            addUpdateListener {
                val f = it.animatedValue as Float
                tile.color = argb.evaluate(f, from, to) as Int
                tile.scaleX = 1f + 0.12f * kotlin.math.sin(f * Math.PI.toFloat())
                tile.scaleY = tile.scaleX
            }
            start()
        }
        pill.postDelayed({
            pill.animate().alpha(0f).translationY(Ui.dp(activity, 40).toFloat()).setDuration(260)
                .withEndAction { content.removeView(pill) }.start()
        }, HOLD_MS)
    }

    // A rounded square in the given colour with the icon's mark on it.
    private class TileView(context: Context, start: Int) : View(context) {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val rect = RectF()
        private val mark = context.getDrawable(R.drawable.ic_launcher_light_foreground)
        var color: Int = start
            set(value) {
                field = value
                invalidate()
            }

        override fun onDraw(canvas: Canvas) {
            paint.color = color
            rect.set(0f, 0f, width.toFloat(), height.toFloat())
            canvas.drawRoundRect(rect, width * 0.26f, width * 0.26f, paint)
            // The adaptive icon's canvas is larger than the tile it shows; draw the middle of it.
            val side = (width * 1.5f).toInt()
            val inset = (width - side) / 2
            mark?.setBounds(inset, inset, inset + side, inset + side)
            mark?.draw(canvas)
        }
    }
}
