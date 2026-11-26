package io.github.g_c_3.ardour

import android.app.Activity
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

// The Ardour help page (DECISIONS.md 179), opened by the Ardour button in Settings. Its text is in HelpContent.
class HelpActivity : Activity() {
    @Suppress("DEPRECATION")
    override fun onCreate(savedInstanceState: Bundle?) {
        Ui.applyTheme(this)
        setTheme(if (Ui.dark) R.style.Theme_Ardour_Dark else R.style.Theme_Ardour)
        super.onCreate(savedInstanceState)
        window.setBackgroundDrawable(ColorDrawable(Ui.PAPER))
        val pad = Ui.dp(this, 20)
        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, pad * 2)
        }
        column.addView(PageHeader(this, "Ardour") { finish() }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, 44)))
        column.addView(Ui.body(this, HelpContent.INTRO).apply {
            setPadding(Ui.dp(this@HelpActivity, 4), Ui.dp(this@HelpActivity, 16), Ui.dp(this@HelpActivity, 4), 0)
        })
        for (section in HelpContent.sections) {
            column.addView(sectionCard(section), LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                topMargin = Ui.dp(this@HelpActivity, 16)
            })
        }
        val scroll = ScrollView(this).apply {
            isVerticalScrollBarEnabled = false
            addView(column, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            // Edge to edge at target SDK 36: keep content clear of the system bars.
            setOnApplyWindowInsetsListener { v, insets ->
                v.setPadding(
                    insets.systemWindowInsetLeft,
                    insets.systemWindowInsetTop,
                    insets.systemWindowInsetRight,
                    insets.systemWindowInsetBottom,
                )
                insets
            }
        }
        setContentView(scroll)
        Ui.styleWindow(window)
        if (savedInstanceState == null) Ui.enter((0 until column.childCount).map { column.getChildAt(it) })
    }

    // A card: the section title, then one line per point, each behind a small ember dot.
    private fun sectionCard(section: HelpSection): View {
        val ctx = this
        return Ui.card(ctx).apply {
            addView(TextView(ctx).apply {
                text = section.title
                textSize = 17f
                typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
                setTextColor(Ui.INK)
                setPadding(0, Ui.dp(ctx, 12), 0, Ui.dp(ctx, 6))
            })
            for (line in section.lines) {
                addView(LinearLayout(ctx).apply {
                    orientation = LinearLayout.HORIZONTAL
                    setPadding(0, Ui.dp(ctx, 5), 0, Ui.dp(ctx, 5))
                    addView(Dot(ctx), LinearLayout.LayoutParams(Ui.dp(ctx, 14), Ui.dp(ctx, 22)))
                    addView(Ui.body(ctx, line), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                })
            }
            setPadding(paddingLeft, paddingTop, paddingRight, paddingBottom + Ui.dp(ctx, 6))
        }
    }
}

// The small ember dot before a help line, level with the first line of text.
private class Dot(context: Context) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    override fun onDraw(canvas: Canvas) {
        paint.color = Ui.EMBER
        canvas.drawCircle(Ui.dp(context, 4).toFloat(), height / 2f, Ui.dp(context, 2.5f).toFloat(), paint)
    }
}
