package io.github.g_c_3.ardour

import android.app.Activity
import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.text.Editable
import android.text.TextWatcher
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import kotlin.math.max
import kotlin.math.min

class SheetEntry(val key: String, val label: String)

// Bottom action of a sheet: its words and its enabled state are read again after every change.
class SheetFooter(val label: () -> String, val enabled: () -> Boolean, val onTap: () -> Unit)

// Centred card for choosing one entry (DECISIONS.md 118): optional search field, an optional group of recent entries
// above a separator, then the scrolling list; the current choice sits in an amber box. A footer action can sit below.
object ListSheet {
    private class CappedScroll(context: Context, private val cap: Int) : ScrollView(context) {
        override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
            super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(cap, MeasureSpec.AT_MOST))
        }
    }

    fun show(
        activity: Activity,
        entries: List<SheetEntry>,
        selectedKey: () -> String,
        recent: List<SheetEntry> = emptyList(),
        searchable: Boolean = false,
        closeOnPick: Boolean = true,
        footer: SheetFooter? = null,
        scrollToSelected: Boolean = true,
        onPick: (SheetEntry) -> Unit,
    ) {
        val context: Context = activity
        val metrics = context.resources.displayMetrics
        val dialog = Dialog(context)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        var closing = false
        val card = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                setColor(Ui.CARD)
                cornerRadius = Ui.dp(context, 22).toFloat()
                setStroke(Ui.dp(context, 1), Ui.LINE)
            }
            elevation = Ui.dp(context, 12).toFloat()
            isClickable = true
            setPadding(0, Ui.dp(context, 8), 0, Ui.dp(context, 8))
        }
        val root = FrameLayout(context)
        fun close() {
            if (closing) return
            closing = true
            card.animate().alpha(0f).scaleX(0.96f).scaleY(0.96f).setDuration(140).withEndAction { dialog.dismiss() }.start()
        }
        root.setOnClickListener { close() }
        root.addView(card, FrameLayout.LayoutParams(min(metrics.widthPixels - Ui.dp(context, 56), Ui.dp(context, 340)), ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER))

        val search = EditText(context)
        if (searchable) {
            search.apply {
                hint = "Search"
                setHintTextColor(0xFF9A9CA6.toInt())
                setTextColor(Ui.INK)
                setTextSize(TypedValue.COMPLEX_UNIT_PX, Ui.textPx(context, 15f))
                isSingleLine = true
                imeOptions = EditorInfo.IME_ACTION_DONE or EditorInfo.IME_FLAG_NO_EXTRACT_UI
                setPadding(Ui.dp(context, 18), 0, Ui.dp(context, 18), 0)
                background = GradientDrawable().apply {
                    setColor(Ui.PAPER)
                    cornerRadius = Ui.dp(context, 24).toFloat()
                }
            }
            card.addView(search, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Ui.sdp(context, 44)).apply {
                marginStart = Ui.dp(context, 12)
                marginEnd = Ui.dp(context, 12)
                bottomMargin = Ui.dp(context, 6)
            })
        }
        val list = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        val cap = (metrics.heightPixels * (if (searchable) 0.42f else 0.6f)).toInt()
        val scroll = CappedScroll(context, cap).apply { isVerticalScrollBarEnabled = false; addView(list) }
        card.addView(scroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        val footerRow = TextView(context)
        val footerDivider = View(context).apply { setBackgroundColor(Ui.LINE) }
        if (footer != null) {
            card.addView(footerDivider, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(context, 1)).apply {
                marginStart = Ui.dp(context, 18)
                marginEnd = Ui.dp(context, 18)
                topMargin = Ui.dp(context, 4)
            })
            card.addView(footerRow, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Ui.sdp(context, 46)))
        }

        fun rowBackground(selected: Boolean): Drawable {
            val radius = Ui.dp(context, 14).toFloat()
            if (selected) {
                return GradientDrawable().apply {
                    cornerRadius = radius
                    setColor(0x26F2791A)
                    setStroke(Ui.dp(context, 1), Ui.AMBER)
                }
            }
            return StateListDrawable().apply {
                addState(intArrayOf(android.R.attr.state_pressed), GradientDrawable().apply { cornerRadius = radius; setColor(Ui.PAPER) })
                addState(intArrayOf(), ColorDrawable(0))
            }
        }

        fun label(text: String, style: Int = Typeface.NORMAL) = TextView(context).apply {
            this.text = text
            setTextSize(TypedValue.COMPLEX_UNIT_PX, Ui.textPx(context, 15f))
            typeface = Typeface.create("sans-serif-medium", style)
            gravity = Gravity.CENTER
            setTextColor(Ui.INK)
        }

        fun header(text: String) = TextView(context).apply {
            this.text = text.uppercase()
            setTextSize(TypedValue.COMPLEX_UNIT_PX, Ui.textPx(context, 11f))
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            letterSpacing = 0.1f
            setTextColor(Ui.MUTED)
            setPadding(Ui.dp(context, 22), Ui.dp(context, 8), Ui.dp(context, 22), Ui.dp(context, 4))
        }

        var selectedInList: View? = null

        fun render() {
            list.removeAllViews()
            selectedInList = null
            val current = selectedKey()
            val query = search.text.toString().trim()
            val shown = if (query.isEmpty()) entries else entries.filter { it.label.contains(query, ignoreCase = true) }
            fun row(entry: SheetEntry, inList: Boolean) {
                val selected = entry.key == current
                val view = label(entry.label).apply {
                    minimumHeight = Ui.sdp(context, 46)
                    background = rowBackground(selected)
                    isClickable = true
                    setOnClickListener {
                        onPick(entry)
                        if (closeOnPick) {
                            background = rowBackground(true)
                            postDelayed({ close() }, 160)
                        } else {
                            render()
                        }
                    }
                }
                if (selected && inList) selectedInList = view
                list.addView(view, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                    marginStart = Ui.dp(context, 10)
                    marginEnd = Ui.dp(context, 10)
                    topMargin = Ui.dp(context, 1)
                    bottomMargin = Ui.dp(context, 1)
                })
            }
            if (query.isEmpty() && recent.isNotEmpty()) {
                list.addView(header("Recent"))
                recent.forEach { row(it, false) }
                list.addView(View(context).apply { setBackgroundColor(Ui.LINE) }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(context, 1)).apply {
                    marginStart = Ui.dp(context, 18)
                    marginEnd = Ui.dp(context, 18)
                    topMargin = Ui.dp(context, 6)
                })
                list.addView(header("All languages"))
            }
            shown.forEach { row(it, true) }
            if (shown.isEmpty()) list.addView(header("No match"))
            if (footer != null) {
                val on = footer.enabled()
                footerRow.apply {
                    text = footer.label()
                    setTextSize(TypedValue.COMPLEX_UNIT_PX, Ui.textPx(context, 15f))
                    typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
                    gravity = Gravity.CENTER
                    setTextColor(if (on) Ui.AMBER else Ui.INK)
                    alpha = if (on) 1f else 0.35f
                    isEnabled = on
                    isClickable = on
                    setOnClickListener {
                        footer.onTap()
                        render()
                    }
                }
            }
        }
        render()
        if (searchable) {
            search.addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                override fun afterTextChanged(s: Editable?) = render()
            })
        }

        dialog.setContentView(root)
        dialog.window?.apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            setDimAmount(0.45f)
            setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        }
        card.alpha = 0f
        card.scaleX = 0.92f
        card.scaleY = 0.92f
        dialog.show()
        card.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(380).setInterpolator(SpringInterpolator()).start()
        scroll.post {
            if (!scrollToSelected) return@post
            val target = selectedInList ?: return@post
            scroll.scrollTo(0, max(0, target.top - Ui.dp(context, 110)))
        }
    }
}
