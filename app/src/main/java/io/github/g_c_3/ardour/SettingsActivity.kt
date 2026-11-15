package io.github.g_c_3.ardour

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.provider.Settings
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView

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
