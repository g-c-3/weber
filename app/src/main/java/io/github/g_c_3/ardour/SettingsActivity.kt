package io.github.g_c_3.ardour

import android.app.Activity
import android.app.role.RoleManager
import android.content.ActivityNotFoundException
import android.content.Intent
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.provider.Settings
import android.view.GestureDetector
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

class SettingsActivity : Activity() {
    private companion object {
        const val REQUEST_DEFAULT_BROWSER = 71
    }

    private var awaitingDefaults = false
    private var content: LinearLayout? = null
    private var scroller: ScrollView? = null
    private var diagnosticsShown = false

    @Suppress("DEPRECATION")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setBackgroundDrawable(ColorDrawable(Ui.PAPER))
        val pad = Ui.dp(this, 20)
        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, pad * 2)
        }
        val back = BackButton(this).apply {
            contentDescription = "Back"
            setOnClickListener { finish() }
        }
        column.addView(back, LinearLayout.LayoutParams(Ui.dp(this, 44), Ui.dp(this, 44)))
        content = column
        column.addView(Ui.title(this, "Settings").apply {
            setPadding(Ui.dp(this@SettingsActivity, 4), Ui.dp(this@SettingsActivity, 20), 0, 0)
            val taps = GestureDetector(this@SettingsActivity, object : GestureDetector.SimpleOnGestureListener() {
                override fun onDown(e: MotionEvent) = true
                override fun onDoubleTap(e: MotionEvent): Boolean {
                    revealDiagnostics()
                    return true
                }
            })
            setOnTouchListener { _, event -> taps.onTouchEvent(event) }
        })
        column.addView(Ui.sectionLabel(this, "About"))
        // Version row with the update button beside it (sideload build only); the line under it becomes the download bar.
        // Padding gives the button's glow room inside the slot.
        val updateSlot = FrameLayout(this).apply {
            clipChildren = false
            setPadding(Ui.dp(this@SettingsActivity, 6), Ui.dp(this@SettingsActivity, 6), Ui.dp(this@SettingsActivity, 6), Ui.dp(this@SettingsActivity, 6))
        }
        val divider = ProgressDivider(this)
        val versionRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(Ui.row(this@SettingsActivity, "Ardour version", appVersion()), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            addView(updateSlot, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                marginStart = Ui.dp(this@SettingsActivity, 12)
            })
        }
        val about = Ui.card(this).apply {
            addView(versionRow)
            addView(divider, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this@SettingsActivity, 8)))
            addView(Ui.row(this@SettingsActivity, "WebView version", EngineAdapter.webViewVersion(this@SettingsActivity) ?: "not found"))
        }
        UpdateFeature.attach(this, updateSlot, divider, appVersion())
        column.addView(about, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        column.addView(Ui.sectionLabel(this, "Browsing"))
        val browsing = Ui.card(this).apply {
            addView(switchRow("Pull to refresh", "Pull a page down from its top to reload it.", Prefs.PULL))
            addView(Ui.divider(this@SettingsActivity))
            addView(switchRow("Force zoom", "Pinch to zoom on sites that block it. Applies to pages you open next.", Prefs.FORCE_ZOOM))
            addView(Ui.divider(this@SettingsActivity))
            addView(switchRow("Wrap text when zoomed", "Pinch changes the text size and fits it to the screen width instead of scaling the page. Replaces force zoom.", Prefs.WRAP))
            addView(Ui.divider(this@SettingsActivity))
            addView(switchRow("Third-party cookies", "Lets content embedded in a page, such as ads and sign-in frames, set cookies. This also lets those sites recognise you across the pages that embed them. Off by default; some sites need them to work. Applies to pages you open next.", Prefs.THIRD_PARTY))
        }
        column.addView(browsing, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        column.addView(Ui.sectionLabel(this, "Private browsing"))
        column.addView(Ui.card(this).apply { addView(leaveChoiceRow()) }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        defaultBrowserButton()?.let { card ->
            column.addView(card, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                topMargin = Ui.dp(this@SettingsActivity, 20)
            })
        }

        val scroll = ScrollView(this).also { scroller = it }.apply {
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

    // Diagnostics stay out of sight (DECISIONS.md 84): a double tap on the title adds them at the end of the page.
    private fun revealDiagnostics() {
        val column = content ?: return
        if (diagnosticsShown) return
        diagnosticsShown = true
        val label = Ui.sectionLabel(this, "Diagnostics")
        val card = traceCard()
        val crash = crashCard()
        column.addView(label)
        column.addView(card, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        column.addView(crash, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            topMargin = Ui.dp(this@SettingsActivity, 12)
        })
        for (view in listOf(label, card, crash)) {
            view.alpha = 0f
            view.translationY = Ui.dp(this, 14).toFloat()
            view.animate().alpha(1f).translationY(0f).setDuration(280).setInterpolator(DecelerateInterpolator()).start()
        }
        card.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
        scroller?.post { scroller?.fullScroll(View.FOCUS_DOWN) }
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

    // Default browser: the system role dialog where Android shows it, otherwise the phone's Default apps screen.
    // Ardour changes nothing itself (DECISIONS.md 54). Only the button is shown, and not once Ardour already holds the role.
    private fun defaultBrowserButton(): View? {
        val roles = getSystemService(RoleManager::class.java)
        val available = roles != null && roles.isRoleAvailable(RoleManager.ROLE_BROWSER)
        if (available && roles.isRoleHeld(RoleManager.ROLE_BROWSER)) return null
        return Ui.card(this).apply {
            addView(Ui.secondaryButton(this@SettingsActivity, if (available) "Make Ardour the default" else "Open default apps").apply {
                setOnClickListener { if (available) requestRole(roles) else openDefaultApps() }
            }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                topMargin = Ui.dp(this@SettingsActivity, 8)
                bottomMargin = Ui.dp(this@SettingsActivity, 8)
            })
        }
    }

    @Suppress("DEPRECATION")
    private fun requestRole(roles: RoleManager) {
        try {
            startActivityForResult(roles.createRequestRoleIntent(RoleManager.ROLE_BROWSER), REQUEST_DEFAULT_BROWSER)
        } catch (_: ActivityNotFoundException) {
            openDefaultApps()
        }
    }

    private fun openDefaultApps() {
        awaitingDefaults = true
        try {
            startActivity(Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS))
        } catch (_: ActivityNotFoundException) {
            try {
                startActivity(Intent(Settings.ACTION_SETTINGS))
            } catch (_: ActivityNotFoundException) {
                awaitingDefaults = false
            }
        }
    }

    // Back from the phone's settings: redraw so the card shows the current state.
    override fun onResume() {
        super.onResume()
        if (awaitingDefaults) {
            awaitingDefaults = false
            recreate()
        }
    }

    @Suppress("DEPRECATION")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_DEFAULT_BROWSER) recreate()
    }

    // What leaving private mode does (DECISIONS.md 134, 136): three segments, the chosen one filled ember.
    private fun leaveChoiceRow(): View {
        val ctx = this
        val options = listOf(Prefs.LEAVE_ASK to "Ask every time", Prefs.LEAVE_DELETE to "Delete always", Prefs.LEAVE_KEEP to "Keep always")
        var chosen = Prefs.getString(ctx, Prefs.PRIVATE_LEAVE, Prefs.LEAVE_ASK)
        val notes = mapOf(
            Prefs.LEAVE_ASK to "Switching private off asks whether to delete what the private session added.",
            Prefs.LEAVE_DELETE to "Switching private off deletes the cookies, site data and cache the private session added. Sign-ins you already had stay.",
            Prefs.LEAVE_KEEP to "Switching private off keeps everything the private session added.",
        )
        val note = Ui.body(ctx, notes.getValue(chosen)).apply { setPadding(0, Ui.dp(ctx, 12), 0, 0) }
        val segments = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL }
        val labels = ArrayList<TextView>()
        fun paint() {
            options.forEachIndexed { i, (key, _) ->
                val on = key == chosen
                labels[i].setTextColor(if (on) 0xFFFFFFFF.toInt() else Ui.INK)
                labels[i].background = GradientDrawable().apply {
                    cornerRadius = Ui.dp(ctx, 22).toFloat()
                    if (on) setColor(Ui.EMBER) else {
                        setColor(Ui.CARD)
                        setStroke(Ui.dp(ctx, 1), Ui.LINE)
                    }
                }
            }
            note.text = notes.getValue(chosen)
        }
        options.forEachIndexed { i, (key, text) ->
            val label = TextView(ctx).apply {
                this.text = text
                textSize = 13f
                typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
                gravity = Gravity.CENTER
                setPadding(Ui.dp(ctx, 4), Ui.dp(ctx, 11), Ui.dp(ctx, 4), Ui.dp(ctx, 11))
                isClickable = true
                isFocusable = true
                setOnClickListener {
                    chosen = key
                    Prefs.setString(ctx, Prefs.PRIVATE_LEAVE, key)
                    paint()
                }
            }
            Ui.springPress(label)
            labels.add(label)
            segments.addView(label, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                if (i > 0) marginStart = Ui.dp(ctx, 8)
            })
        }
        paint()
        return LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, Ui.dp(ctx, 14), 0, Ui.dp(ctx, 14))
            addView(TextView(ctx).apply {
                text = "When leaving private"
                textSize = 16f
                typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
                setTextColor(Ui.INK)
                setPadding(0, 0, 0, Ui.dp(ctx, 10))
            })
            addView(segments, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            addView(note)
        }
    }

    // One option: label and note on the left, an ember switch on the right; a tap anywhere on the row flips it.
    private fun switchRow(label: String, note: String, key: String, default: Boolean = false): View {
        val ctx = this
        val row = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, Ui.dp(ctx, 14), 0, Ui.dp(ctx, 14))
            isClickable = true
            isFocusable = true
        }
        val text = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            addView(TextView(ctx).apply {
                this.text = label
                textSize = 16f
                typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
                setTextColor(Ui.INK)
            })
            addView(Ui.body(ctx, note).apply { setPadding(0, Ui.dp(ctx, 2), 0, 0) })
        }
        val switch = ToggleSwitch(ctx, Prefs.get(ctx, key, default))
        row.addView(text, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        row.addView(switch, LinearLayout.LayoutParams(Ui.dp(ctx, 44), Ui.dp(ctx, 26)).apply { marginStart = Ui.dp(ctx, 16) })
        row.setOnClickListener {
            switch.on = !switch.on
            Prefs.set(ctx, key, switch.on)
        }
        return row
    }

    @Suppress("DEPRECATION")
    private fun appVersion(): String = packageManager.getPackageInfo(packageName, 0).versionName ?: "unknown"
}
