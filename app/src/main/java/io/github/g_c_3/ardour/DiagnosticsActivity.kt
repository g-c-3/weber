package io.github.g_c_3.ardour

import android.app.Activity
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView

// Settings > Diagnostics (DECISIONS.md 145): the page load trace (84) and the crash log (103), each written to
// Downloads only on a tap.
class DiagnosticsActivity : Activity() {
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
        column.addView(PageHeader(this, "Diagnostics") { finish() }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, 44)))
        column.addView(Ui.body(this, "Files that show what happened when a page stalled or the app stopped. Nothing is sent anywhere.").apply {
            setPadding(Ui.dp(this@DiagnosticsActivity, 4), Ui.dp(this@DiagnosticsActivity, 16), Ui.dp(this@DiagnosticsActivity, 4), 0)
        })
        column.addView(traceCard(), LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            topMargin = Ui.dp(this@DiagnosticsActivity, 24)
        })
        column.addView(crashCard(), LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            topMargin = Ui.dp(this@DiagnosticsActivity, 12)
        })
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
        if (savedInstanceState == null) Ui.enter((0 until column.childCount).map { column.getChildAt(it) })
    }

    // Page load trace (DECISIONS.md 84): kept in memory, written to Downloads only on Save.
    private fun traceCard(): View {
        val ctx = this
        val status = Ui.body(ctx, traceNote()).apply { setPadding(0, Ui.dp(ctx, 2), 0, 0) }
        val save = Ui.secondaryButton(ctx, "Save trace")
        val clear = Ui.secondaryButton(ctx, "Clear")
        save.setOnClickListener {
            val name = Trace.save(ctx)
            status.text = if (name != null) "Saved to Downloads: $name" else "Could not save the file."
        }
        clear.setOnClickListener {
            Trace.clear()
            status.text = traceNote()
        }
        val buttons = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(save, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { marginEnd = Ui.dp(ctx, 6) })
            addView(clear, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { marginStart = Ui.dp(ctx, 6) })
        }
        fun scriptLabel() = if (Prefs.get(ctx, Prefs.DIAG_NO_DESKTOP_SCRIPT)) "Desktop viewport script: off" else "Desktop viewport script: on"
        val script = Ui.secondaryButton(ctx, scriptLabel())
        script.setOnClickListener {
            Prefs.set(ctx, Prefs.DIAG_NO_DESKTOP_SCRIPT, !Prefs.get(ctx, Prefs.DIAG_NO_DESKTOP_SCRIPT))
            script.text = scriptLabel()
        }
        return Ui.card(ctx).apply {
            addView(Ui.row(ctx, "Page load trace", "Addresses, progress, redirects and errors of the last page loads."))
            addView(status)
            addView(buttons, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                topMargin = Ui.dp(ctx, 12)
                bottomMargin = Ui.dp(ctx, 8)
            })
            addView(script, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = Ui.dp(ctx, 8)
            })
        }
    }

    // Crash log (DECISIONS.md 103): records stay in the app's private folder, written to Downloads only on Save.
    private fun crashCard(): View {
        val ctx = this
        val status = Ui.body(ctx, crashNote()).apply { setPadding(0, Ui.dp(ctx, 2), 0, 0) }
        val save = Ui.secondaryButton(ctx, "Save crash log")
        val clear = Ui.secondaryButton(ctx, "Clear")
        save.setOnClickListener {
            val name = CrashLog.save(ctx)
            status.text = if (name != null) "Saved to Downloads: $name" else "Could not save the file."
        }
        clear.setOnClickListener {
            CrashLog.clear(ctx)
            status.text = crashNote()
        }
        val buttons = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(save, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { marginEnd = Ui.dp(ctx, 6) })
            addView(clear, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { marginStart = Ui.dp(ctx, 6) })
        }
        return Ui.card(ctx).apply {
            addView(Ui.row(ctx, "Crash log", "Errors that ended the app, with the last page load events."))
            addView(status)
            addView(buttons, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                topMargin = Ui.dp(ctx, 12)
                bottomMargin = Ui.dp(ctx, 8)
            })
        }
    }

    private fun crashNote(): String = "${CrashLog.summary(this)} Kept on this phone; Save writes a text file. Nothing is sent anywhere."

    private fun traceNote(): String =
        "${Trace.size().let { if (it == 1) "1 event" else "$it events" }} in memory. Save writes a text file with addresses reduced to host and path. Nothing is sent anywhere."
}
