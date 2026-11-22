package io.github.g_c_3.ardour

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.graphics.Canvas
import android.graphics.Outline
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.Editable
import android.text.TextWatcher
import android.util.TypedValue
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.view.animation.PathInterpolator
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sin

// A tap or a swipe down that starts on the view runs the same action (DECISIONS.md 104, 117). A swipe never also taps.
object Gestures {
    @SuppressLint("ClickableViewAccessibility")
    fun onSwipeDown(view: View, action: () -> Unit) {
        var downX = 0f
        var downY = 0f
        var swiped = false
        val distance = Ui.dp(view.context, 26).toFloat()
        view.setOnTouchListener { v, e ->
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = e.rawX
                    downY = e.rawY
                    swiped = false
                    v.parent?.requestDisallowInterceptTouchEvent(true)
                }
                MotionEvent.ACTION_MOVE -> if (!swiped) {
                    val dy = e.rawY - downY
                    val dx = e.rawX - downX
                    if (dy >= distance && dy > abs(dx) * 1.2f) {
                        swiped = true
                        val cancel = MotionEvent.obtain(e)
                        cancel.action = MotionEvent.ACTION_CANCEL
                        v.onTouchEvent(cancel)
                        cancel.recycle()
                        v.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
                        action()
                    }
                }
            }
            swiped
        }
    }

    fun tapOrSwipeDown(view: View, action: () -> Unit) {
        view.setOnClickListener { action() }
        onSwipeDown(view, action)
    }
}

// The search block of a fresh page (DECISIONS.md 117): a search box, and under it the voice language, the mic and the
// search engine. Every part works on a tap or a swipe down. Voice words fill the box and are searched.
class StartSearch(private val activity: Activity, private val onSubmit: (String) -> Unit) :
    LinearLayout(activity), VoiceInput.Callbacks {
    private val box = SearchBox(activity, ::submitTyped)
    private val slot = FrameLayout(activity).apply { clipChildren = false }
    private val bar = EditBar(
        activity,
        onCopy = { Clipboard.copy(activity, box.input.text.toString()) },
        onPaste = { pasteIntoBox() },
        onClear = { box.input.setText("") },
        onGo = { submitTyped(box.input.text.toString()) },
    )
    private val language = StartChip(activity, false)
    private val mic = MicButton(activity)
    private val engine = StartChip(activity, true)
    private val status = Ui.body(activity, "").apply { alpha = 0f; gravity = Gravity.CENTER }
    private val voice = VoiceInput(activity, this)
    private val suggestList = SuggestionList(activity) { pick ->
        closeSuggestions()
        submitTyped(pick)
    }
    private val suggest = Suggestions.Session { suggestList.showItems(it) }
    private var heard = false // spoken words fill the box: they are searched, never sent for suggestions
    private var voiceLanguage = VoiceLanguages.current(activity)
    private val hideStatus = Runnable { status.animate().alpha(0f).setDuration(240).start() }

    init {
        orientation = VERTICAL
        gravity = Gravity.CENTER_HORIZONTAL
        // Room above the box for Copy, Paste, Clear and Go while the box is being edited.
        slot.addView(bar, FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER))
        addView(slot, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Ui.sdp(activity, 54)))
        addView(box, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Ui.sdp(activity, 50)))
        box.onFocus = ::showBar
        box.input.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                bar.refresh(!s.isNullOrEmpty(), Clipboard.hasText(activity))
                if (box.input.hasFocus() && !heard) suggest.query(s?.toString().orEmpty()) else closeSuggestions()
            }
        })
        val row = LinearLayout(activity).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER
            clipChildren = false
        }
        language.label = voiceLanguage.short
        language.contentDescription = "Voice language"
        mic.contentDescription = "Voice search"
        engine.contentDescription = "Search engine"
        val gap = Ui.dp(activity, 2)
        row.addView(language)
        row.addView(mic, LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            marginStart = gap
            marginEnd = gap
        })
        row.addView(engine)
        // The suggestions cover the buttons row and run below it, over the title.
        val holder = FrameLayout(activity).apply { clipChildren = false }
        holder.addView(row, FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL))
        holder.addView(suggestList, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP))
        addView(holder, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = Ui.dp(activity, 6) })
        elevation = Ui.dp(activity, 2).toFloat()
        addView(status, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        clipChildren = false

        Gestures.onSwipeDown(box.input) { box.focusAndShowKeyboard() }
        Gestures.tapOrSwipeDown(language) { showLanguages() }
        Gestures.tapOrSwipeDown(mic) { toggleVoice() }
        Gestures.tapOrSwipeDown(engine) { showEngines() }
    }

    // Same rise-in as the address bar's bar, but above the box.
    private fun closeSuggestions() {
        suggest.stop()
        suggestList.hideList()
    }

    private fun showBar(show: Boolean) {
        if (!show) closeSuggestions()
        bar.animate().cancel()
        if (show) {
            bar.refresh(box.input.text.isNotEmpty(), Clipboard.hasText(activity))
            bar.visibility = View.VISIBLE
            bar.playIn()
            bar.translationY = Ui.dp(activity, 10).toFloat()
            bar.animate().alpha(1f).translationY(0f).setStartDelay(120).setDuration(380).setInterpolator(SpringInterpolator()).start()
        } else {
            bar.animate().alpha(0f).translationY(Ui.dp(activity, 6).toFloat()).setStartDelay(0).setDuration(160)
                .withEndAction { if (!box.input.hasFocus()) bar.visibility = View.GONE }.start()
        }
    }

    // The clipboard changed or the app came back: Paste follows what the clipboard holds.
    fun refreshClipboard() {
        if (bar.visibility == View.VISIBLE) bar.refresh(box.input.text.isNotEmpty(), Clipboard.hasText(activity))
    }

    private fun pasteIntoBox() {
        val text = Clipboard.text(activity)
        if (text.isEmpty()) {
            refreshClipboard()
            return
        }
        val field = box.input
        val a = field.selectionStart.coerceAtLeast(0)
        val b = field.selectionEnd.coerceAtLeast(0)
        field.text.replace(minOf(a, b), maxOf(a, b), text)
    }

    private fun submitTyped(text: String) {
        if (text.isBlank()) return
        box.hideKeyboard()
        onSubmit(text)
    }

    private fun toggleVoice() {
        if (voice.active) {
            voice.stop()
            return
        }
        showStatus(null)
        voice.start(voiceLanguage.tag)
    }

    private fun showLanguages() {
        ListSheet.show(
            activity,
            entries = VoiceLanguages.all.map { SheetEntry(it.tag, it.name) },
            selectedKey = { voiceLanguage.tag },
            recent = VoiceLanguages.recent(activity).map { SheetEntry(it.tag, it.name) },
            searchable = true,
        ) { picked ->
            VoiceLanguages.byTag(picked.key)?.let {
                voiceLanguage = it
                VoiceLanguages.use(activity, it)
                language.label = it.short
            }
        }
    }

    private fun showEngines() {
        ListSheet.show(
            activity,
            entries = SearchEngines.all.map { SheetEntry(it.id, it.name) },
            selectedKey = { SearchEngines.active.id },
            closeOnPick = false,
            footer = SheetFooter(
                label = { if (SearchEngines.default(activity).id == SearchEngines.active.id) "Default" else "Make default" },
                enabled = { SearchEngines.default(activity).id != SearchEngines.active.id },
                onTap = { SearchEngines.makeDefault(activity, SearchEngines.active) },
            ),
        ) { picked ->
            SearchEngines.all.firstOrNull { it.id == picked.key }?.let { SearchEngines.use(it) }
        }
    }

    fun onPermissionResult(granted: Boolean) = voice.onPermissionResult(granted)

    // The app leaves the screen: the mic never keeps listening.
    fun stopVoice() {
        if (voice.active) voice.cancel()
        mic.setListening(false)
        showStatus(null)
    }

    // The start page is hidden or shown again: nothing typed, heard or listening stays.
    fun reset() {
        stopVoice()
        closeSuggestions()
        box.input.setText("")
        box.hideKeyboard()
        bar.animate().cancel()
        bar.visibility = View.GONE
        bar.alpha = 0f
        showStatus(null)
    }

    private fun showStatus(message: String?) {
        status.removeCallbacks(hideStatus)
        status.animate().cancel()
        if (message == null) {
            status.alpha = 0f
            return
        }
        status.text = message
        status.animate().alpha(1f).setDuration(200).start()
    }

    override fun onListening() {
        mic.setListening(true)
        showStatus("Listening…")
    }

    override fun onLevel(level: Float) = mic.setLevel(level)

    override fun onText(text: String, isFinal: Boolean) {
        heard = true
        box.input.setText(text)
        box.input.setSelection(text.length)
        heard = false
        if (isFinal) {
            mic.setListening(false)
            showStatus(null)
            postDelayed({ submitTyped(text) }, 350)
        }
    }

    override fun onStopped(message: String?) {
        mic.setListening(false)
        showStatus(message)
        if (message != null) status.postDelayed(hideStatus, 3500)
    }
}

// Pill search box with the ember rim while it has focus.
class SearchBox(context: Context, private val onGo: (String) -> Unit) : FrameLayout(context) {
    val input = EditText(context)
    private val align = TypedAlign(input)
    private val radius = Ui.sdp(context, 26).toFloat()
    private val rim = GradientDrawable().apply {
        setColor(Ui.CARD)
        cornerRadius = radius
        setStroke(Ui.dp(context, 1), Ui.LINE)
    }
    private val ember = GradientDrawable().apply {
        cornerRadius = radius
        setStroke(Ui.dp(context, 2), Ui.EMBER)
        alpha = 0
    }
    private var rimAnim: ValueAnimator? = null
    var onFocus: ((Boolean) -> Unit)? = null

    init {
        background = rim
        foreground = ember
        elevation = Ui.dp(context, 4).toFloat()
        outlineSpotShadowColor = 0x59000000
        val pad = Ui.dp(context, 20)
        input.apply {
            background = null
            gravity = Gravity.CENTER_VERTICAL or Gravity.START
            hint = "Search or enter address"
            setHintTextColor(0xFF9A9CA6.toInt())
            setTextColor(Ui.INK)
            setTextSize(TypedValue.COMPLEX_UNIT_PX, Ui.textPx(context, 16f))
            isSingleLine = true
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_URI
            imeOptions = EditorInfo.IME_ACTION_GO or EditorInfo.IME_FLAG_NO_EXTRACT_UI
            highlightColor = 0x40E8451D
            setPadding(pad, 0, pad, 0)
            setOnEditorActionListener { _, _, _ ->
                onGo(text.toString())
                true
            }
            setOnFocusChangeListener { _, focused ->
                fadeRim(if (focused) 255 else 0)
                onFocus?.invoke(focused)
            }
        }
        addView(input, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        align.reset(false)
    }

    private fun fadeRim(target: Int) {
        rimAnim?.cancel()
        if (!ValueAnimator.areAnimatorsEnabled()) {
            ember.alpha = target
            return
        }
        rimAnim = ValueAnimator.ofInt(ember.alpha, target).apply {
            duration = 260
            interpolator = PathInterpolator(0.4f, 0f, 0.2f, 1f)
            addUpdateListener { ember.alpha = it.animatedValue as Int }
            start()
        }
    }

    fun focusAndShowKeyboard() {
        input.requestFocus()
        val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.showSoftInput(input, InputMethodManager.SHOW_IMPLICIT)
    }

    fun hideKeyboard() {
        input.clearFocus()
        val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(input.windowToken, 0)
    }
}

// Round chip drawn by Ardour: the voice language (three letters) or the search engine (magnifier and chevron).
class StartChip(context: Context, private val engine: Boolean) : View(context) {
    var label = ""
        set(value) {
            field = value
            requestLayout()
            invalidate()
        }
    private val unit = Ui.dp(context, 1).toFloat() * Ui.geoScale(context)
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Ui.CARD }
    private val edge = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = Ui.dp(context, 1).toFloat()
        color = Ui.LINE
    }
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Ui.INK
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        textSize = Ui.textPx(context, 14f)
        letterSpacing = 0.06f
        textAlign = Paint.Align.CENTER
    }
    private val glyph = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Ui.EMBER
        style = Paint.Style.STROKE
        strokeWidth = 2f * unit
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val box = RectF()
    private val chevron = android.graphics.Path()

    init {
        elevation = Ui.dp(context, 3).toFloat()
        outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(view: View, outline: Outline) {
                outline.setRoundRect(0, 0, view.width, view.height, view.height / 2f)
                outline.alpha = 1f
            }
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val minWidth = Ui.sdp(context, 64)
        val wanted = if (engine) minWidth else max(minWidth, (text.measureText(label) + 28f * unit).toInt())
        setMeasuredDimension(wanted, Ui.sdp(context, 42))
    }

    // Pill, then the three letters or the magnifier with a chevron.
    override fun onDraw(canvas: Canvas) {
        val half = edge.strokeWidth / 2f
        box.set(half, half, width - half, height - half)
        canvas.drawRoundRect(box, height / 2f, height / 2f, fill)
        canvas.drawRoundRect(box, height / 2f, height / 2f, edge)
        val cx = width / 2f
        val cy = height / 2f
        if (engine) {
            val mx = cx - 5f * unit
            canvas.drawCircle(mx - unit, cy - unit, 5.5f * unit, glyph)
            canvas.drawLine(mx + 3f * unit, cy + 3f * unit, mx + 7f * unit, cy + 7f * unit, glyph)
            chevron.rewind()
            chevron.moveTo(cx + 9f * unit, cy - 1.5f * unit)
            chevron.lineTo(cx + 12f * unit, cy + 1.5f * unit)
            chevron.lineTo(cx + 15f * unit, cy - 1.5f * unit)
            glyph.strokeWidth = 1.6f * unit
            canvas.drawPath(chevron, glyph)
            glyph.strokeWidth = 2f * unit
        } else {
            canvas.drawText(label, cx, cy - (text.ascent() + text.descent()) / 2f, text)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> animate().scaleX(0.95f).scaleY(0.95f).setDuration(90).start()
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL ->
                animate().scaleX(1f).scaleY(1f).setDuration(380).setInterpolator(SpringInterpolator()).start()
        }
        return super.onTouchEvent(event)
    }
}

// The mic. Idle: an ember mic on a white disc. Listening: the disc fills with ember, a moving wave replaces the mic,
// and an outer glow with two expanding rings follows how loud the voice is.
class MicButton(context: Context) : View(context) {
    private val core = Ui.sdp(context, 28).toFloat()
    private val reach = Ui.sdp(context, 20).toFloat()
    private val unit = core / 28f
    private var mix = 0f
    private var target = 0f
    private var level = 0f
    private var phase = 0f
    private var mixAnim: ValueAnimator? = null
    private var loop: ValueAnimator? = null
    private val halo = Paint(Paint.ANTI_ALIAS_FLAG)
    private val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val disc = Paint(Paint.ANTI_ALIAS_FLAG)
    private val edge = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = Ui.dp(context, 1).toFloat()
        color = Ui.LINE
    }
    private val ink = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2.2f * unit
        strokeCap = Paint.Cap.ROUND
    }
    private val solid = Paint(Paint.ANTI_ALIAS_FLAG)
    private val bar = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        strokeWidth = 3.6f * unit
        strokeCap = Paint.Cap.ROUND
        color = Ui.CARD
    }
    private val body = RectF()

    init {
        elevation = Ui.dp(context, 5).toFloat()
        outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(view: View, outline: Outline) {
                val r = core.toInt()
                val cx = view.width / 2
                val cy = view.height / 2
                outline.setOval(cx - r, cy - r, cx + r, cy + r)
                outline.alpha = 1f
            }
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val size = ((core + reach) * 2f).toInt()
        setMeasuredDimension(size, size)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        val outer = core + reach
        halo.shader = RadialGradient(
            w / 2f, h / 2f, outer,
            intArrayOf(withAlpha(Ui.EMBER, 1f), withAlpha(Ui.EMBER, 0f)),
            floatArrayOf(core * 0.8f / outer, 1f), Shader.TileMode.CLAMP,
        )
    }

    fun setLevel(value: Float) {
        target = value
    }

    fun setListening(on: Boolean) {
        val goal = if (on) 1f else 0f
        if (goal == mix && (on == (loop != null))) return
        mixAnim?.cancel()
        if (!on) target = 0f
        if (!ValueAnimator.areAnimatorsEnabled()) {
            mix = goal
            invalidate()
            return
        }
        if (on && loop == null) {
            loop = ValueAnimator.ofFloat(0f, 1f).apply {
                duration = 1600
                repeatCount = ValueAnimator.INFINITE
                interpolator = android.view.animation.LinearInterpolator()
                addUpdateListener {
                    phase = it.animatedValue as Float
                    level += (target - level) * 0.2f
                    invalidate()
                }
                start()
            }
        }
        mixAnim = ValueAnimator.ofFloat(mix, goal).apply {
            duration = if (on) 420 else 320
            interpolator = if (on) SpringInterpolator() else PathInterpolator(0.4f, 0f, 0.2f, 1f)
            addUpdateListener {
                mix = (it.animatedValue as Float).coerceIn(0f, 1f)
                invalidate()
            }
            addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: android.animation.Animator) {
                    if (!on) {
                        loop?.cancel()
                        loop = null
                        level = 0f
                        invalidate()
                    }
                }
            })
            start()
        }
    }

    override fun onDetachedFromWindow() {
        loop?.cancel()
        loop = null
        mixAnim?.cancel()
        super.onDetachedFromWindow()
    }

    private fun withAlpha(color: Int, fraction: Float): Int =
        ((fraction.coerceIn(0f, 1f) * 255).toInt() shl 24) or (color and 0x00FFFFFF)

    private fun blend(from: Int, to: Int, amount: Float): Int {
        fun channel(shift: Int): Int {
            val a = (from shr shift) and 0xFF
            val b = (to shr shift) and 0xFF
            return (a + (b - a) * amount).toInt()
        }
        return (0xFF shl 24) or (channel(16) shl 16) or (channel(8) shl 8) or channel(0)
    }

    // Glow and rings first, then the disc, then the mic or the wave; both icons are faded by the same amount.
    override fun onDraw(canvas: Canvas) {
        val cx = width / 2f
        val cy = height / 2f
        if (mix > 0.01f) {
            // One gradient built at the full size; the voice level scales it and sets how strong it shines.
            halo.alpha = (255 * 0.5f * mix * (0.6f + 0.4f * level)).toInt()
            val scale = 0.55f + 0.45f * level
            canvas.save()
            canvas.scale(scale, scale, cx, cy)
            canvas.drawCircle(cx, cy, core + reach, halo)
            canvas.restore()
            for (k in 0..1) {
                val p = (phase + k * 0.5f) % 1f
                ring.color = withAlpha(Ui.EMBER, (1f - p) * 0.4f * mix)
                ring.strokeWidth = (1f + 1.5f * (1f - p)) * unit
                canvas.drawCircle(cx, cy, core + reach * p, ring)
            }
        }
        disc.color = blend(Ui.CARD, Ui.EMBER, mix)
        canvas.drawCircle(cx, cy, core, disc)
        edge.alpha = (255 * (1f - mix)).toInt()
        canvas.drawCircle(cx, cy, core - edge.strokeWidth / 2f, edge)
        if (mix < 0.99f) {
            val a = (255 * (1f - mix)).toInt()
            ink.color = Ui.EMBER
            ink.alpha = a
            solid.color = Ui.EMBER
            solid.alpha = a
            body.set(cx - 5f * unit, cy - 14f * unit, cx + 5f * unit, cy + 2f * unit)
            canvas.drawRoundRect(body, 5f * unit, 5f * unit, solid)
            body.set(cx - 10f * unit, cy - 8f * unit, cx + 10f * unit, cy + 11f * unit)
            canvas.drawArc(body, 0f, 180f, false, ink)
            canvas.drawLine(cx, cy + 11f * unit, cx, cy + 16f * unit, ink)
            canvas.drawLine(cx - 5f * unit, cy + 16f * unit, cx + 5f * unit, cy + 16f * unit, ink)
        }
        if (mix > 0.01f) {
            bar.alpha = (255 * mix).toInt()
            for (i in 0..4) {
                val s = 0.5f + 0.5f * sin(phase * 2f * PI.toFloat() * 2f + i * 1.1f)
                val h = unit * (5f + s * (6f + level * 14f)) * mix
                val x = cx + (i - 2) * 7f * unit
                canvas.drawLine(x, cy - h, x, cy + h, bar)
            }
        }
    }
}
