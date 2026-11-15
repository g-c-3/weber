package io.github.g_c_3.ardour

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

class SettingsActivity : Activity() {
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
        column.addView(Ui.title(this, "Settings").apply {
            setPadding(Ui.dp(this@SettingsActivity, 4), Ui.dp(this@SettingsActivity, 20), 0, 0)
        })
        column.addView(Ui.sectionLabel(this, "About"))
        val about = Ui.card(this).apply {
            addView(Ui.row(this@SettingsActivity, "Ardour version", appVersion()))
            addView(Ui.divider(this@SettingsActivity))
            addView(Ui.row(this@SettingsActivity, "WebView version", EngineAdapter.webViewVersion(this@SettingsActivity) ?: "not found"))
        }
        column.addView(about, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        column.addView(Ui.sectionLabel(this, "Browsing"))
        val browsing = Ui.card(this).apply {
            addView(switchRow("Pull to refresh", "Pull a page down from its top to reload it.", Prefs.PULL))
            addView(Ui.divider(this@SettingsActivity))
            addView(switchRow("Force zoom", "Pinch to zoom on sites that block it. Applies to pages you open next.", Prefs.FORCE_ZOOM))
            addView(Ui.divider(this@SettingsActivity))
            addView(switchRow("Wrap text when zoomed", "Pinch changes the text size and fits it to the screen width instead of scaling the page. Replaces force zoom.", Prefs.WRAP))
        }
        column.addView(browsing, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        column.addView(Ui.sectionLabel(this, "Privacy"))
        val dns = Ui.card(this).apply {
            addView(Ui.row(this@SettingsActivity, "Private DNS", "Encrypts website-name lookups for every app on the phone, not only Ardour. It does not hide your IP address. In Android's network settings, look for Private DNS (the path differs by phone) and enter a provider's hostname."))
            addView(Ui.secondaryButton(this@SettingsActivity, "Open network settings").apply {
                setOnClickListener { openNetworkSettings() }
            }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                topMargin = Ui.dp(this@SettingsActivity, 4)
                bottomMargin = Ui.dp(this@SettingsActivity, 12)
            })
        }
        column.addView(dns, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        UpdateFeature.addRows(this, column, appVersion())

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

    // One option: label and note on the left, an ember switch on the right; a tap anywhere on the row flips it.
    private fun switchRow(label: String, note: String, key: String): View {
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
        val switch = ToggleSwitch(ctx, Prefs.get(ctx, key))
        row.addView(text, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        row.addView(switch, LinearLayout.LayoutParams(Ui.dp(ctx, 44), Ui.dp(ctx, 26)).apply { marginStart = Ui.dp(ctx, 16) })
        row.setOnClickListener {
            switch.on = !switch.on
            Prefs.set(ctx, key, switch.on)
        }
        return row
    }

    // Hands off to Android's own settings on the user's tap; Ardour changes nothing and sends nothing (DECISIONS.md 50).
    private fun openNetworkSettings() {
        try {
            startActivity(Intent(Settings.ACTION_WIRELESS_SETTINGS))
        } catch (_: ActivityNotFoundException) {
            try {
                startActivity(Intent(Settings.ACTION_SETTINGS))
            } catch (_: ActivityNotFoundException) {
                // No settings app can open; the text above still says where to look.
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun appVersion(): String = packageManager.getPackageInfo(packageName, 0).versionName ?: "unknown"
}
