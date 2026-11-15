package io.github.g_c_3.ardour

import android.content.Context
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import org.json.JSONException
import org.json.JSONObject
import org.json.JSONTokener
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

// One piece of the article. kind: '1' '2' '3' headings, 'p' paragraph, 'q' quote, 'l' list item, 'c' code.
class ReaderBlock(val kind: Char, val text: String, val marker: String)

class Article(val title: String, val byline: String, val site: String, val blocks: List<ReaderBlock>) {
    // Too little running text means the page is not an article (a search page, an app, a front page of headlines).
    val readable: Boolean get() = blocks.filter { it.kind == 'p' || it.kind == 'q' }.sumOf { it.text.length } >= 200

    companion object {
        private const val MAX_BLOCKS = 600
        private const val MAX_TEXT = 5000

        // `raw` is what the engine returned: a JSON string literal holding the article JSON, or "null".
        // Everything is read as plain text; a page cannot put markup or views into the reader.
        fun parse(raw: String?): Article? {
            if (raw == null || raw == "null") return null
            return try {
                val inner = JSONTokener(raw).nextValue() as? String ?: return null
                val json = JSONObject(inner)
                val array = json.optJSONArray("b") ?: return null
                val blocks = ArrayList<ReaderBlock>()
                for (i in 0 until min(array.length(), MAX_BLOCKS)) {
                    val e = array.optJSONArray(i) ?: continue
                    val kind = e.optString(0).firstOrNull() ?: continue
                    if (kind !in "123pqlc") continue
                    val text = e.optString(1).take(MAX_TEXT)
                    if (text.isEmpty()) continue
                    blocks.add(ReaderBlock(kind, text, e.optString(2).take(6)))
                }
                Article(json.optString("t").take(300), json.optString("a").take(80), json.optString("s").take(80), blocks)
            } catch (_: JSONException) {
                null
            }
        }
    }
}

// Colours of one reading theme. Twilight is the default: a warm dusk with cream text and an amber accent.
private class Look(
    val bg: Int,
    val text: Int,
    val muted: Int,
    val accent: Int,
    val chip: Int,
    val edge: Int,
    val light: Boolean,
)

private val TWILIGHT = Look(
    bg = 0xFF1C1B26.toInt(),
    text = 0xFFE8DFD0.toInt(),
    muted = 0xFF9C95A0.toInt(),
    accent = 0xFFF2A65A.toInt(),
    chip = 0xFF2A2937.toInt(),
    edge = 0xFF3A394A.toInt(),
    light = false,
)

private val PAPER = Look(
    bg = Ui.PAPER,
    text = Ui.INK,
    muted = Ui.MUTED,
    accent = Ui.EMBER,
    chip = Ui.CARD,
    edge = Ui.LINE,
    light = true,
)

private val SIZES = floatArrayOf(15f, 16f, 17f, 18f, 20f, 22f, 25f)
private const val DEFAULT_STEP = 3

// Round control in the reader's top bar, drawn by hand: close, smaller text, bigger text, theme.
private class ReaderButton(context: Context, private val kind: Kind) : View(context) {
    enum class Kind { CLOSE, SMALLER, BIGGER, THEME }

    var look: Look = TWILIGHT
        set(value) {
            field = value
            invalidate()
        }

    private val chip = Paint(Paint.ANTI_ALIAS_FLAG)
    private val edge = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = Ui.dp(context, 1).toFloat()
    }
    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = Ui.dp(context, 2).toFloat()
        strokeCap = Paint.Cap.ROUND
    }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val letter = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create("serif", Typeface.BOLD)
    }
    private val moon = Path()
    private val cut = Path()

    init {
        isClickable = true
        isFocusable = true
        Ui.springPress(this)
    }

    private fun sp(v: Float) = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, v, resources.displayMetrics)

    override fun onDraw(canvas: Canvas) {
        val cx = width / 2f
        val cy = height / 2f
        val r = min(width, height) / 2f - Ui.dp(context, 3)
        chip.color = look.chip
        edge.color = look.edge
        canvas.drawCircle(cx, cy, r, chip)
        canvas.drawCircle(cx, cy, r, edge)
        line.color = look.text
        fill.color = look.text
        when (kind) {
            Kind.CLOSE -> {
                val s = r * 0.34f
                canvas.drawLine(cx - s, cy - s, cx + s, cy + s, line)
                canvas.drawLine(cx - s, cy + s, cx + s, cy - s, line)
            }
            Kind.SMALLER, Kind.BIGGER -> {
                letter.color = look.text
                letter.textSize = sp(if (kind == Kind.SMALLER) 14f else 21f)
                canvas.drawText("A", cx, cy - (letter.descent() + letter.ascent()) / 2f, letter)
            }
            Kind.THEME -> if (look.light) drawSun(canvas, cx, cy, r) else drawMoon(canvas, cx, cy, r)
        }
    }

    private fun drawSun(canvas: Canvas, cx: Float, cy: Float, r: Float) {
        canvas.drawCircle(cx, cy, r * 0.24f, fill)
        for (i in 0 until 8) {
            val a = i * Math.PI / 4
            val c = cos(a).toFloat()
            val s = sin(a).toFloat()
            canvas.drawLine(cx + c * r * 0.42f, cy + s * r * 0.42f, cx + c * r * 0.58f, cy + s * r * 0.58f, line)
        }
    }

    private fun drawMoon(canvas: Canvas, cx: Float, cy: Float, r: Float) {
        val m = r * 0.36f
        moon.reset()
        moon.addCircle(cx, cy, m, Path.Direction.CW)
        cut.reset()
        cut.addCircle(cx + m * 0.55f, cy - m * 0.45f, m * 0.85f, Path.Direction.CW)
        moon.op(cut, Path.Op.DIFFERENCE)
        canvas.drawPath(moon, fill)
    }
}

// Thin reading-progress line under the top bar: ember to amber, like the page progress bar.
private class ReadLine(context: Context) : View(context) {
    var look: Look = TWILIGHT
        set(value) {
            field = value
            invalidate()
        }
    var fraction = 0f
        set(value) {
            field = value.coerceIn(0f, 1f)
            invalidate()
        }
    private val track = Paint()
    private val bar = Paint()

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        bar.shader = LinearGradient(0f, 0f, w.toFloat(), 0f, Ui.EMBER, Ui.AMBER, Shader.TileMode.CLAMP)
    }

    override fun onDraw(canvas: Canvas) {
        track.color = look.edge
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), track)
        canvas.drawRect(0f, 0f, width * fraction, height.toFloat(), bar)
    }
}

// Full-screen reader: the article as plain text in a serif face. No images, links or page scripts.
// `article` null or too short shows a quiet "no article" screen. Theme and size are remembered (Prefs).
class ReaderView(
    context: Context,
    private val article: Article?,
    private val onClose: () -> Unit,
    private val onStyle: (background: Int, lightBackground: Boolean) -> Unit,
) : FrameLayout(context) {
    private var paper = Prefs.get(context, Prefs.READER_PAPER)
    private var step = Prefs.getInt(context, Prefs.READER_SIZE, DEFAULT_STEP).coerceIn(0, SIZES.size - 1)
    private val look: Look get() = if (paper) PAPER else TWILIGHT
    private val body: Float get() = SIZES[step]
    private val restyle = ArrayList<() -> Unit>()
    private val serif = Typeface.create("serif", Typeface.NORMAL)
    private val serifBold = Typeface.create("serif", Typeface.BOLD)

    private val column = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
    private val scroll = ScrollView(context).apply {
        isVerticalScrollBarEnabled = false
        overScrollMode = OVER_SCROLL_NEVER
    }
    private val bar = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(Ui.dp(context, 10), Ui.dp(context, 6), Ui.dp(context, 10), Ui.dp(context, 6))
    }
    private val readLine = ReadLine(context)
    private val closeButton = ReaderButton(context, ReaderButton.Kind.CLOSE)
    private val smallerButton = ReaderButton(context, ReaderButton.Kind.SMALLER)
    private val biggerButton = ReaderButton(context, ReaderButton.Kind.BIGGER)
    private val themeButton = ReaderButton(context, ReaderButton.Kind.THEME)
    private val readable = article != null && article.readable
    private val emptyBox = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER
        setPadding(Ui.dp(context, 36), 0, Ui.dp(context, 36), Ui.dp(context, 72))
    }
    private val content: View get() = if (readable) scroll else emptyBox

    init {
        closeButton.contentDescription = "Close reader"
        smallerButton.contentDescription = "Smaller text"
        biggerButton.contentDescription = "Bigger text"
        themeButton.contentDescription = "Switch theme"
        closeButton.setOnClickListener { onClose() }
        smallerButton.setOnClickListener { changeSize(-1) }
        biggerButton.setOnClickListener { changeSize(1) }
        themeButton.setOnClickListener { toggleTheme() }
        val size = Ui.dp(context, 44)
        val gap = Ui.dp(context, 4)
        bar.addView(closeButton, LinearLayout.LayoutParams(size, size))
        bar.addView(View(context), LinearLayout.LayoutParams(0, 1, 1f))
        if (readable) {
            bar.addView(smallerButton, LinearLayout.LayoutParams(size, size).apply { marginEnd = gap })
            bar.addView(biggerButton, LinearLayout.LayoutParams(size, size).apply { marginEnd = gap })
        }
        bar.addView(themeButton, LinearLayout.LayoutParams(size, size))

        scroll.addView(column, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        scroll.setOnScrollChangeListener { _, _, y, _, _ ->
            val range = column.height - scroll.height
            readLine.fraction = if (range > 0) y.toFloat() / range else 0f
        }
        val page = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            addView(bar, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            addView(readLine, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(context, 2)))
            addView(content, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        }
        addView(page, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        // Side padding is set before any text is built: text laid out first and padded later kept its old line breaks
        // and was clipped at the right edge. Wide screens get a comfortable line length.
        val side = max(Ui.dp(context, 22), (resources.displayMetrics.widthPixels - Ui.dp(context, 680)) / 2)
        column.setPadding(side, Ui.dp(context, 18), side, Ui.dp(context, 72))
        if (readable) buildArticle(article!!) else buildEmpty()
    }

    // Called once the view is in place: applies the theme to the screen around it and plays the entrance.
    fun start() {
        applyLook()
        Ui.enter(listOf(bar, content))
    }

    private fun changeSize(delta: Int) {
        val next = (step + delta).coerceIn(0, SIZES.size - 1)
        if (next == step) return
        step = next
        Prefs.setInt(context, Prefs.READER_SIZE, step)
        applyLook()
    }

    private fun toggleTheme() {
        paper = !paper
        Prefs.set(context, Prefs.READER_PAPER, paper)
        applyLook()
    }

    private fun applyLook() {
        val l = look
        setBackgroundColor(l.bg)
        readLine.look = l
        for (b in listOf(closeButton, smallerButton, biggerButton, themeButton)) b.look = l
        smallerButton.alpha = if (step == 0) 0.35f else 1f
        biggerButton.alpha = if (step == SIZES.size - 1) 0.35f else 1f
        smallerButton.isEnabled = step > 0
        biggerButton.isEnabled = step < SIZES.size - 1
        for (r in restyle) r()
        onStyle(l.bg, l.light)
    }

    private fun reg(block: () -> Unit) {
        restyle.add(block)
        block()
    }

    private fun params(top: Int = 0, bottom: Int = 0) =
        LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            topMargin = Ui.dp(context, top)
            bottomMargin = Ui.dp(context, bottom)
        }

    private fun rounded(color: Int, radiusDp: Int) = GradientDrawable().apply {
        setColor(color)
        cornerRadius = Ui.dp(context, radiusDp).toFloat()
    }

    private fun buildArticle(a: Article) {
        if (a.site.isNotEmpty()) {
            column.addView(TextView(context).apply {
                text = a.site.uppercase()
                typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
                letterSpacing = 0.1f
                reg { textSize = 12f; setTextColor(look.accent) }
            }, params(bottom = 10))
        }
        column.addView(TextView(context).apply {
            text = a.title
            typeface = serifBold
            setLineSpacing(0f, 1.12f)
            letterSpacing = -0.01f
            reg { textSize = body * 1.55f; setTextColor(look.text) }
        }, params(bottom = 10))
        if (a.byline.isNotEmpty()) {
            column.addView(TextView(context).apply {
                text = a.byline
                typeface = Typeface.create("sans-serif", Typeface.NORMAL)
                reg { textSize = 14f; setTextColor(look.muted) }
            }, params(bottom = 4))
        }
        val rule = View(context)
        reg { rule.background = rounded(look.accent, 2) }
        column.addView(rule, LinearLayout.LayoutParams(Ui.dp(context, 44), Ui.dp(context, 3)).apply {
            topMargin = Ui.dp(context, 14)
            bottomMargin = Ui.dp(context, 22)
        })
        for (b in a.blocks) addBlock(b)
    }

    private fun addBlock(b: ReaderBlock) {
        when (b.kind) {
            '1', '2', '3' -> {
                val scale = when (b.kind) {
                    '1' -> 1.4f
                    '2' -> 1.25f
                    else -> 1.12f
                }
                column.addView(TextView(context).apply {
                    text = b.text
                    typeface = Typeface.create(Typeface.create("sans-serif", Typeface.NORMAL), 700, false)
                    setLineSpacing(0f, 1.15f)
                    reg { textSize = body * scale; setTextColor(look.text) }
                }, params(top = 26, bottom = 8))
            }
            'q' -> {
                val barView = View(context)
                reg { barView.background = rounded(look.accent, 2) }
                val row = LinearLayout(context).apply {
                    orientation = LinearLayout.HORIZONTAL
                    addView(barView, LinearLayout.LayoutParams(Ui.dp(context, 3), ViewGroup.LayoutParams.MATCH_PARENT))
                    addView(TextView(context).apply {
                        text = b.text
                        typeface = Typeface.create("serif", Typeface.ITALIC)
                        setLineSpacing(0f, 1.45f)
                        reg { textSize = body; setTextColor(look.text) }
                    }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { marginStart = Ui.dp(context, 14) })
                }
                column.addView(row, params(top = 6, bottom = 18))
            }
            'l' -> {
                val row = LinearLayout(context).apply {
                    orientation = LinearLayout.HORIZONTAL
                    addView(TextView(context).apply {
                        text = b.marker
                        typeface = serifBold
                        minWidth = Ui.dp(context, 26)
                        reg { textSize = body; setTextColor(look.accent) }
                    }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))
                    addView(TextView(context).apply {
                        text = b.text
                        typeface = serif
                        setLineSpacing(0f, 1.45f)
                        reg { textSize = body; setTextColor(look.text) }
                    }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                }
                column.addView(row, params(bottom = 8))
            }
            'c' -> column.addView(TextView(context).apply {
                text = b.text
                typeface = Typeface.MONOSPACE
                val pad = Ui.dp(context, 14)
                setPadding(pad, pad, pad, pad)
                reg {
                    textSize = body * 0.8f
                    setTextColor(look.text)
                    background = rounded(look.chip, 12)
                }
            }, params(top = 4, bottom = 18))
            else -> column.addView(TextView(context).apply {
                text = b.text
                typeface = serif
                setLineSpacing(0f, 1.5f)
                reg { textSize = body; setTextColor(look.text) }
            }, params(bottom = 18))
        }
    }

    // Shown when the page has no article text.
    private fun buildEmpty() {
        emptyBox.addView(TextView(context).apply {
            text = "No article here"
            typeface = serifBold
            gravity = Gravity.CENTER
            reg { textSize = 26f; setTextColor(look.text) }
        }, params(bottom = 12))
        emptyBox.addView(TextView(context).apply {
            text = "Reader mode works on news, blogs and other long text. This page has too little to read."
            typeface = Typeface.create("sans-serif", Typeface.NORMAL)
            gravity = Gravity.CENTER
            setLineSpacing(0f, 1.3f)
            reg { textSize = 16f; setTextColor(look.muted) }
        }, params(bottom = 28))
        emptyBox.addView(TextView(context).apply {
            text = "Back to the page"
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            gravity = Gravity.CENTER
            val h = Ui.dp(context, 26)
            val v = Ui.dp(context, 13)
            setPadding(h, v, h, v)
            reg {
                textSize = 15f
                setTextColor(look.text)
                background = GradientDrawable().apply {
                    setColor(look.chip)
                    cornerRadius = Ui.dp(context, 28).toFloat()
                    setStroke(Ui.dp(context, 1), look.edge)
                }
            }
            isClickable = true
            isFocusable = true
            Ui.springPress(this)
            setOnClickListener { onClose() }
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))
    }
}
