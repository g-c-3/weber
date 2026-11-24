package io.github.g_c_3.ardour

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.content.res.Configuration
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.WindowInsetsController
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.view.animation.Interpolator
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin
import kotlin.math.sqrt

// Hand-built look shared by every screen (DECISIONS.md 30, 38): plain views, drawn here, spring motion.
object Ui {
    // Light or dark (DECISIONS.md 153): read once per screen from the Settings choice and the phone's setting, before any
    // view is built. Ember and amber are the same in both.
    var dark = false
        private set

    fun applyTheme(context: Context) {
        dark = when (Prefs.getString(context, Prefs.THEME, Prefs.THEME_SYSTEM)) {
            Prefs.THEME_DARK -> true
            Prefs.THEME_LIGHT -> false
            else -> (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        }
    }

    val INK: Int get() = if (dark) 0xFFECECEF.toInt() else 0xFF242630.toInt()
    val EMBER = 0xFFE8451D.toInt()
    val PAPER: Int get() = if (dark) 0xFF111113.toInt() else 0xFFF6F5F3.toInt()
    val CARD: Int get() = if (dark) 0xFF1F2026.toInt() else 0xFFFFFFFF.toInt()
    val LINE: Int get() = if (dark) 0xFF3B3E49.toInt() else 0xFFE8E6E2.toInt()
    val MUTED: Int get() = if (dark) 0xFF9A9CA8.toInt() else 0xFF646875.toInt()
    val AMBER = 0xFFF2791A.toInt()

    // Look of one button in a row of choices (DECISIONS.md 154). Selected: ember fill with white text in light; in dark an
    // ember outline and ember text on a near-black fill, so the choice does not glare. Others: card with a thin line.
    fun choiceLook(context: Context, button: TextView, selected: Boolean) {
        button.setTextColor(if (selected) (if (dark) EMBER else 0xFFFFFFFF.toInt()) else INK)
        button.background = GradientDrawable().apply {
            cornerRadius = dp(context, 22).toFloat()
            when {
                selected && dark -> {
                    setColor(0xFF0B0B0D.toInt())
                    setStroke(dp(context, 2), EMBER)
                }
                selected -> setColor(EMBER)
                else -> {
                    setColor(CARD)
                    setStroke(dp(context, 1), LINE)
                }
            }
        }
    }

    // System bars and window (DECISIONS.md 155): transparent over the page, the platform's contrast scrims off, the window
    // painted in the page colour and the bar icons set for it, so no grey strip shows behind the clock after the screen was
    // built again for another theme. Called after setContentView on every screen.
    @Suppress("DEPRECATION")
    fun styleWindow(window: Window) {
        window.setBackgroundDrawable(ColorDrawable(PAPER))
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.TRANSPARENT
        window.isStatusBarContrastEnforced = false
        window.isNavigationBarContrastEnforced = false
        if (Build.VERSION.SDK_INT >= 30) {
            val light = WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS or WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS
            window.insetsController?.setSystemBarsAppearance(if (dark) 0 else light, light)
        }
    }

    // The card colour with its own opacity, for bars floating over the page.
    fun cardAlpha(alpha: Int): Int = (alpha shl 24) or (CARD and 0x00FFFFFF)

    fun dp(context: Context, n: Int): Int = (n * context.resources.displayMetrics.density + 0.5f).toInt()

    fun dp(context: Context, n: Float): Float = n * context.resources.displayMetrics.density

    // The system font size, capped at 1.3 so the top row never takes the page (DECISIONS.md 111). Text follows it down
    // to the phone's smallest size; sizes of icons and boxes only grow.
    private fun textScale(context: Context): Float = minOf(context.resources.configuration.fontScale, 1.3f)

    fun geoScale(context: Context): Float = maxOf(textScale(context), 1f)

    // dp that grows with the system font size.
    fun sdp(context: Context, n: Int): Int = (n * context.resources.displayMetrics.density * geoScale(context) + 0.5f).toInt()

    // Text size in pixels for TextView.setTextSize(COMPLEX_UNIT_PX, ...), from an sp value, with the same cap.
    fun textPx(context: Context, sp: Float): Float = sp * context.resources.displayMetrics.density * textScale(context)

    private fun medium(): Typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)

    fun title(context: Context, text: String) = TextView(context).apply {
        this.text = text
        textSize = 32f
        typeface = Typeface.create("sans-serif", Typeface.BOLD)
        setTextColor(INK)
        letterSpacing = -0.02f
    }

    fun sectionLabel(context: Context, text: String) = TextView(context).apply {
        this.text = text.uppercase()
        textSize = 12f
        typeface = medium()
        setTextColor(MUTED)
        letterSpacing = 0.08f
        setPadding(dp(context, 4), dp(context, 28), 0, dp(context, 10))
    }

    fun body(context: Context, text: String) = TextView(context).apply {
        this.text = text
        textSize = 14f
        setTextColor(MUTED)
        setLineSpacing(0f, 1.25f)
    }

    fun card(context: Context) = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        background = GradientDrawable().apply {
            setColor(CARD)
            cornerRadius = dp(context, 20).toFloat()
            setStroke(dp(context, 1), LINE)
        }
        setPadding(dp(context, 20), dp(context, 8), dp(context, 20), dp(context, 8))
    }

    fun row(context: Context, label: String, value: String) = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(0, dp(context, 14), 0, dp(context, 14))
        addView(TextView(context).apply {
            text = label
            textSize = 16f
            typeface = medium()
            setTextColor(INK)
        })
        addView(TextView(context).apply {
            text = value
            textSize = 14f
            setTextColor(MUTED)
            setPadding(0, dp(context, 2), 0, 0)
        })
    }

    fun divider(context: Context) = View(context).apply {
        setBackgroundColor(LINE)
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 1)
    }

    // Ink gradient with a faint ember rim and an ember-tinted shadow: the glow marks the primary action.
    private fun glowPill(context: Context, radiusDp: Int) = GradientDrawable(
        GradientDrawable.Orientation.TOP_BOTTOM,
        if (dark) intArrayOf(0xFF4A4D66.toInt(), 0xFF2F3145.toInt()) else intArrayOf(0xFF33354A.toInt(), 0xFF1E202B.toInt()),
    ).apply {
        cornerRadius = dp(context, radiusDp).toFloat()
        setStroke(dp(context, 1), 0x40FF6A3D)
    }

    private fun styleGlow(view: View, elevationDp: Int, glowAlpha: Int) {
        view.elevation = dp(view.context, elevationDp).toFloat()
        view.outlineSpotShadowColor = (glowAlpha shl 24) or (EMBER and 0x00FFFFFF)
        view.outlineAmbientShadowColor = 0x33000000
    }

    fun pillButton(context: Context, text: String) = TextView(context).apply {
        this.text = text
        textSize = 16f
        typeface = medium()
        setTextColor(0xFFFFFFFF.toInt())
        gravity = Gravity.CENTER
        setPadding(dp(context, 24), dp(context, 15), dp(context, 24), dp(context, 15))
        background = glowPill(context, 28)
        styleGlow(this, 8, 0xB0)
        isClickable = true
        isFocusable = true
        springPress(this)
    }

    // Small pill floating over a page: quieter glow.
    fun floatingPill(context: Context, text: String) = TextView(context).apply {
        this.text = text
        textSize = 14f
        typeface = medium()
        setTextColor(0xFFFFFFFF.toInt())
        gravity = Gravity.CENTER
        setPadding(dp(context, 18), dp(context, 11), dp(context, 18), dp(context, 11))
        background = glowPill(context, 24)
        styleGlow(this, 6, 0x80)
        isClickable = true
        isFocusable = true
        springPress(this)
    }

    // Quiet outlined pill for a secondary action; the glowing pill stays for the one primary action (DECISIONS.md 40).
    fun secondaryButton(context: Context, text: String) = TextView(context).apply {
        this.text = text
        textSize = 15f
        typeface = medium()
        setTextColor(INK)
        gravity = Gravity.CENTER
        setPadding(dp(context, 24), dp(context, 13), dp(context, 24), dp(context, 13))
        background = GradientDrawable().apply {
            setColor(CARD)
            cornerRadius = dp(context, 28).toFloat()
            setStroke(dp(context, 1), LINE)
        }
        isClickable = true
        isFocusable = true
        springPress(this)
    }

    fun setBusy(view: View, busy: Boolean) {
        view.isEnabled = !busy
        view.alpha = if (busy) 0.55f else 1f
    }

    // Settles with a small overshoot. Animator duration scale 0 (system reduce-animations) ends it at once.
    fun enter(views: List<View>) {
        views.forEachIndexed { i, v ->
            v.alpha = 0f
            v.translationY = dp(v.context, 18).toFloat()
            v.animate().alpha(1f).translationY(0f)
                .setStartDelay(i * 60L).setDuration(560).setInterpolator(SpringInterpolator()).start()
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    fun springPress(view: View, onPress: ((Boolean) -> Unit)? = null) {
        view.setOnTouchListener { v, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    onPress?.invoke(true)
                    v.animate().scaleX(0.96f).scaleY(0.96f).setDuration(90)
                        .setInterpolator(DecelerateInterpolator()).start()
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    onPress?.invoke(false)
                    v.animate().scaleX(1f).scaleY(1f).setDuration(420)
                        .setInterpolator(SpringInterpolator()).start()
                }
            }
            false
        }
    }
}

// Underdamped spring (damping ratio 0.62): about 8% overshoot, settled by t = 1.
class SpringInterpolator : Interpolator {
    override fun getInterpolation(t: Float): Float {
        if (t >= 1f) return 1f
        val zeta = 0.62f
        val wn = 14f
        val wd = wn * sqrt(1f - zeta * zeta)
        val k = zeta / sqrt(1f - zeta * zeta)
        return 1f - exp(-zeta * wn * t) * (cos(wd * t) + k * sin(wd * t))
    }
}

// Slim bar with an ember-to-amber gradient. Determinate: a soft glow rides the leading edge.
// Indeterminate: a light sweeps along the track while something is being checked.
class ThinProgress(context: Context) : View(context) {
    private val barHeight = Ui.dp(context, 4).toFloat()
    private val glowRadius = Ui.dp(context, 10).toFloat()
    private val track = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Ui.LINE }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val glow = Paint(Paint.ANTI_ALIAS_FLAG)
    private val glowShader = RadialGradient(
        0f, 0f, glowRadius, intArrayOf(0x80F2791A.toInt(), 0x00F2791A), null, Shader.TileMode.CLAMP,
    )
    private val glowMatrix = Matrix()
    private val rect = RectF()
    private val clip = Path()
    private var phase = 0.5f
    private var sweeper: ValueAnimator? = null

    var value = 0f
        set(v) {
            field = v.coerceIn(0f, 1f)
            invalidate()
        }

    var indeterminate = false
        set(v) {
            field = v
            if (v) startSweep() else stopSweep()
            invalidate()
        }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        fill.shader = LinearGradient(0f, 0f, w.toFloat(), 0f, Ui.EMBER, Ui.AMBER, Shader.TileMode.CLAMP)
    }

    private fun startSweep() {
        if (sweeper != null) return
        if (!ValueAnimator.areAnimatorsEnabled()) {
            phase = 0.5f
            return
        }
        sweeper = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 1200
            repeatCount = ValueAnimator.INFINITE
            interpolator = AccelerateDecelerateInterpolator()
            addUpdateListener {
                phase = it.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    private fun stopSweep() {
        sweeper?.cancel()
        sweeper = null
    }

    override fun onDetachedFromWindow() {
        stopSweep()
        super.onDetachedFromWindow()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (indeterminate) startSweep()
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
            val seg = w * 0.35f
            val x = -seg + (w + seg) * phase
            clip.reset()
            clip.addRoundRect(rect, r, r, Path.Direction.CW)
            canvas.save()
            canvas.clipPath(clip)
            rect.set(x, top, x + seg, bottom)
            canvas.drawRoundRect(rect, r, r, fill)
            canvas.restore()
        } else if (value > 0f) {
            val end = w * value
            rect.set(0f, top, end, bottom)
            canvas.drawRoundRect(rect, r, r, fill)
            glowMatrix.setTranslate(end, cy)
            glowShader.setLocalMatrix(glowMatrix)
            glow.shader = glowShader
            canvas.drawCircle(end, cy, glowRadius, glow)
        }
    }
}

// Slow breathing light on one element that needs attention; never more than one per screen (DECISIONS.md 40).
class Pulse(private val view: View) {
    private val base = view.elevation
    private var anim: ObjectAnimator? = null

    fun start() {
        if (anim != null || !ValueAnimator.areAnimatorsEnabled()) return
        anim = ObjectAnimator.ofFloat(view, "elevation", base, base + Ui.dp(view.context, 10)).apply {
            duration = 1300
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.REVERSE
            interpolator = AccelerateDecelerateInterpolator()
            start()
        }
    }

    fun stop() {
        anim?.cancel()
        anim = null
        view.elevation = base
    }
}

class BackButton(context: Context) : View(context) {
    private val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Ui.CARD
        setShadowLayer(Ui.dp(context, 3).toFloat(), 0f, Ui.dp(context, 1).toFloat(), 0x22000000)
    }
    private val edge = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Ui.LINE
        style = Paint.Style.STROKE
        strokeWidth = Ui.dp(context, 1).toFloat()
    }
    private val chevron = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Ui.INK
        style = Paint.Style.STROKE
        strokeWidth = Ui.dp(context, 2).toFloat()
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val path = Path()

    init {
        setLayerType(LAYER_TYPE_SOFTWARE, null)
        isClickable = true
        isFocusable = true
        Ui.springPress(this)
    }

    override fun onDraw(canvas: Canvas) {
        val cx = width / 2f
        val cy = height / 2f
        val radius = minOf(width, height) / 2f - Ui.dp(context, 4)
        canvas.drawCircle(cx, cy, radius, ring)
        canvas.drawCircle(cx, cy, radius, edge)
        val s = radius * 0.32f
        path.reset()
        path.moveTo(cx + s * 0.5f, cy - s)
        path.lineTo(cx - s * 0.5f, cy)
        path.lineTo(cx + s * 0.5f, cy + s)
        canvas.drawPath(path, chevron)
    }
}

// A thin separator that turns into the amber glowing progress bar while a download runs.
class ProgressDivider(context: Context) : FrameLayout(context) {
    val bar = ThinProgress(context).apply { visibility = View.GONE }
    private val line = View(context).apply { setBackgroundColor(Ui.LINE) }

    init {
        clipChildren = false
        addView(line, LayoutParams(LayoutParams.MATCH_PARENT, 1, Gravity.CENTER_VERTICAL))
        addView(bar, LayoutParams(LayoutParams.MATCH_PARENT, Ui.dp(context, 20), Gravity.CENTER_VERTICAL))
    }

    fun showProgress(show: Boolean) {
        line.visibility = if (show) View.INVISIBLE else View.VISIBLE
        bar.visibility = if (show) View.VISIBLE else View.GONE
        if (!show) bar.indeterminate = false
    }
}
