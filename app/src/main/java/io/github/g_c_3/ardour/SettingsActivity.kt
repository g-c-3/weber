package io.github.g_c_3.ardour

import android.annotation.TargetApi
import android.app.Activity
import android.app.role.RoleManager
import android.content.ActivityNotFoundException
import android.content.Intent
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.window.OnBackInvokedCallback
import android.window.OnBackInvokedDispatcher

class SettingsActivity : Activity() {
    private companion object {
        const val REQUEST_DEFAULT_BROWSER = 71
    }

    private var awaitingDefaults = false
    private val infoSheet = ConfirmSheet(this) { refreshBack() }
    private var backCallback: Any? = null
    private var searchField: SearchField? = null

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
        val header = PageHeader(this, "Settings") { finish() }
        var filter: SettingsFilter? = null
        val field = SearchField(this, { filter?.update(it) }, { header.foldTitle(it) }, { refreshBack() })
        searchField = field
        header.attach(field)
        column.addView(header, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, 44)).apply {
            bottomMargin = Ui.dp(this@SettingsActivity, 4)
        })
        val firstGroup = column.childCount
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
            addView(versionRow("Ardour version", appVersion(), null), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            addView(updateSlot, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                marginStart = Ui.dp(this@SettingsActivity, 12)
            })
        }
        val about = Ui.card(this).apply {
            addView(versionRow)
            addView(divider, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this@SettingsActivity, 8)))
            addView(webViewRow())
        }
        UpdateFeature.attach(this, updateSlot, divider, appVersion())
        column.addView(about, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        column.addView(Ui.sectionLabel(this, "Browsing"))
        val browsing = Ui.card(this).apply {
            addView(switchRow("Pull to refresh", "Pull a page down from its top to reload it.", Prefs.PULL))
            addView(Ui.divider(this@SettingsActivity))
            addView(switchRow("Show full address", "Shows the whole web address in the bar instead of only the site name.", Prefs.FULL_URL))
            addView(Ui.divider(this@SettingsActivity))
            addView(switchRow("Fade pages in", "A new page fades in when it first appears. Follows the phone's animation setting.", Prefs.FADE_PAGES, true))
            addView(Ui.divider(this@SettingsActivity))
            addView(switchRow("Force zoom", "Pinch to zoom on sites that block it. Applies to pages you open next.", Prefs.FORCE_ZOOM))
            addView(Ui.divider(this@SettingsActivity))
            addView(switchRow("Wrap text when zoomed", "Pinch changes the text size and fits it to the screen width instead of scaling the page. Replaces force zoom.", Prefs.WRAP))
            addView(Ui.divider(this@SettingsActivity))
            addView(switchRow("Third-party cookies", "Lets content embedded in a page, such as ads and sign-in frames, set cookies. This also lets those sites recognise you across the pages that embed them. Off by default; some sites need them to work. Applies to pages you open next.", Prefs.THIRD_PARTY))
        }
        column.addView(browsing, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        column.addView(Ui.sectionLabel(this, "Shield Mode"))
        column.addView(Ui.card(this).apply {
            addView(leaveChoiceRow())
            addView(Ui.divider(this@SettingsActivity))
            addView(switchRow("Delete when the app closes", "While Shield Mode is on, closing the app deletes what the Shield Mode session added, as Delete all does. If Android ends the app without telling it, the deletion runs the next time Ardour opens. While this switch and Shield Mode are both on, the list of sites opened in Shield Mode (addresses of sites and cookie names, no passwords or page content) is kept in the app's private storage until the deletion has run. Off by default.", Prefs.PRIVATE_CLOSE))
            addView(Ui.divider(this@SettingsActivity))
            addView(switchRow("Delete all also removes earlier sign-ins", "Delete all, when leaving Shield Mode or closing the app, then removes every cookie and all saved data of the sites opened in the session, sign-ins you already had included. You are signed out of those sites on this phone.", Prefs.DELETE_ALL_SIGNINS))
            addView(Ui.divider(this@SettingsActivity))
            addView(switchRow("Quick Sweep also removes earlier sign-ins", "A long press on the shield sweeps the open page's site at once, Shield Mode on or off. Off: it removes only what the site added since you first opened it in this app run. On: every cookie and all saved data of the site, sign-ins included.", Prefs.QUICK_CLEAN_SIGNINS))
            addView(Ui.divider(this@SettingsActivity))
            addView(switchRow("Allow screenshots in Shield Mode", "While Shield Mode is on, screenshots, screen recordings and casting show nothing. Turn this on to allow them. Off by default; Shield Mode off is never blocked.", Prefs.SHIELD_SHOTS))
            addView(Ui.divider(this@SettingsActivity))
            addView(switchRow("Shield tab preview", "Show the page picture of Shield tabs in the tab list. Off: they show no picture, and none is taken while Shield Mode is on. Pictures stay in memory only and are gone when the app closes. Off by default.", Prefs.SHIELD_PREVIEW))
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        column.addView(Ui.sectionLabel(this, "Appearance"))
        column.addView(Ui.card(this).apply {
            addView(themeChoiceRow())
            addView(Ui.divider(this@SettingsActivity))
            addView(switchRow("Dark app icon", "On: the dark icon. Off: the light (white) icon. The icon changes when you close Ardour, and the launcher may take a moment; it may drop an icon you placed on the home screen: add it again from the app list.", Prefs.ICON_DARK, true) {
                IconToast.show(this@SettingsActivity, it)
            })
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        column.addView(NavCard.build(this, "Diagnostics", "Page load trace and crash log.") {
            startActivity(Intent(this, DiagnosticsActivity::class.java))
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            topMargin = Ui.dp(this@SettingsActivity, 20)
        })
        defaultBrowserButton()?.let { card ->
            column.addView(card, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                topMargin = Ui.dp(this@SettingsActivity, 20)
            })
        }

        val nothingFound = Ui.body(this, "Nothing found.").apply {
            gravity = Gravity.CENTER
            visibility = View.GONE
            setPadding(0, Ui.dp(this@SettingsActivity, 32), 0, Ui.dp(this@SettingsActivity, 8))
        }
        filter = SettingsFilter(column, firstGroup, column.childCount, nothingFound)
        column.addView(nothingFound)
        // Keep the search field from taking focus when the page opens.
        column.isFocusableInTouchMode = true

        val scroll = ScrollView(this).apply {
            // A fixed id lets the system keep the scroll position when the page is built again for a theme change.
            id = 0x00A4D001
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

    // What leaving Shield Mode does (DECISIONS.md 134, 136, 139): three segments, the chosen one filled ember.
    // Appearance (DECISIONS.md 153): System follows the phone; Light and Dark are fixed. The screen is built again at once.
    private fun themeChoiceRow(): View {
        val ctx = this
        val options = listOf(Prefs.THEME_SYSTEM to "System", Prefs.THEME_LIGHT to "Light", Prefs.THEME_DARK to "Dark")
        val chosen = Prefs.getString(ctx, Prefs.THEME, Prefs.THEME_SYSTEM)
        val segments = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL }
        options.forEachIndexed { i, (key, text) ->
            val on = key == chosen
            val label = TextView(ctx).apply {
                this.text = text
                textSize = 13f
                typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
                gravity = Gravity.CENTER
                setPadding(Ui.dp(ctx, 4), Ui.dp(ctx, 11), Ui.dp(ctx, 4), Ui.dp(ctx, 11))
                Ui.choiceLook(ctx, this, on)
                isClickable = true
                isFocusable = true
                setOnClickListener {
                    if (key == chosen) return@setOnClickListener
                    Prefs.setString(ctx, Prefs.THEME, key)
                    recreate()
                }
            }
            Ui.springPress(label)
            segments.addView(label, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                if (i > 0) marginStart = Ui.dp(ctx, 8)
            })
        }
        return LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, Ui.dp(ctx, 4), 0, Ui.dp(ctx, 12))
            addView(titleLine("Theme", "System follows the phone's light or dark setting. Light and Dark stay as chosen. Pages that offer a dark version follow it; other pages are not recoloured. Changing it loads the open page again.", 6))
            addView(segments, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }
    }

    private fun leaveChoiceRow(): View {
        val ctx = this
        val options = listOf(Prefs.LEAVE_ASK to "Ask every time", Prefs.LEAVE_DELETE to "Delete always", Prefs.LEAVE_KEEP to "Keep always")
        var chosen = Prefs.getString(ctx, Prefs.PRIVATE_LEAVE, Prefs.LEAVE_ASK)
        val notes = mapOf(
            Prefs.LEAVE_ASK to "Switching Shield Mode off asks whether to delete what the Shield Mode session added.",
            Prefs.LEAVE_DELETE to "Switching Shield Mode off deletes the cookies, site data and cache the Shield Mode session added. Sign-ins you already had stay.",
            Prefs.LEAVE_KEEP to "Switching Shield Mode off keeps everything the Shield Mode session added.",
        )
        val segments = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL }
        val labels = ArrayList<TextView>()
        fun paint() {
            options.forEachIndexed { i, (key, _) ->
                Ui.choiceLook(ctx, labels[i], key == chosen)
            }
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
            setPadding(0, Ui.dp(ctx, 4), 0, Ui.dp(ctx, 12))
            addView(titleLine("When leaving Shield Mode", options.joinToString("\n\n") { (key, name) -> name + ": " + notes.getValue(key) }, 6))
            addView(segments, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }
    }

    // A heading with its value centred under it; the optional info button sits beside the heading, as in the other rows.
    private fun versionRow(label: String, value: String, info: String?): View {
        val ctx = this
        return LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.TOP
            setPadding(0, Ui.dp(ctx, 10), 0, Ui.dp(ctx, 10))
            addView(LinearLayout(ctx).apply {
                orientation = LinearLayout.VERTICAL
                addView(TextView(ctx).apply {
                    text = label
                    textSize = 16f
                    typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
                    setTextColor(Ui.INK)
                })
                addView(TextView(ctx).apply {
                    text = value
                    textSize = 14f
                    setTextColor(Ui.MUTED)
                    gravity = Gravity.CENTER_HORIZONTAL
                    setPadding(0, Ui.dp(ctx, 1), 0, 0)
                })
            })
            if (info != null) {
                addView(InfoButton(ctx).apply {
                    contentDescription = "About $label"
                    setOnClickListener { infoSheet.showInfo(label, info) }
                }, LinearLayout.LayoutParams(Ui.dp(ctx, 30), Ui.dp(ctx, 30)).apply { topMargin = -Ui.dp(ctx, 4) })
            }
        }
    }

    // The WebView row shows the version; its info card names the package and what the component is.
    private fun webViewRow(): View {
        val full = EngineAdapter.webViewVersion(this) ?: "not found"
        val version = full.substringBefore(" (")
        val pack = full.substringAfter("(", "").removeSuffix(")")
        val info = (if (pack.isEmpty()) "" else "Package: $pack.\n\n") +
            "Ardour draws web pages with the Android System WebView installed on your phone; it has no engine of its own. Updating that component through the Play Store updates the engine."
        return versionRow("WebView version", version, info)
    }

    // A title with the info button beside it; the button opens the full explanation on a card.
    private fun titleLine(label: String, info: String, bottomDp: Int = 0): View {
        val ctx = this
        return LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 0, 0, Ui.dp(ctx, bottomDp))
            addView(TextView(ctx).apply {
                text = label
                textSize = 16f
                typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
                setTextColor(Ui.INK)
            })
            addView(InfoButton(ctx).apply {
                contentDescription = "About $label"
                setOnClickListener { infoSheet.showInfo(label, info) }
            }, LinearLayout.LayoutParams(Ui.dp(ctx, 30), Ui.dp(ctx, 30)))
        }
    }

    // One option: title and info button on the left, a switch on the right; a tap anywhere else on the row flips it.
    private fun switchRow(label: String, note: String, key: String, default: Boolean = false, onChange: ((Boolean) -> Unit)? = null): View {
        val ctx = this
        val row = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, Ui.dp(ctx, 3), 0, Ui.dp(ctx, 3))
            isClickable = true
            isFocusable = true
        }
        val switch = ToggleSwitch(ctx, Prefs.get(ctx, key, default))
        row.addView(titleLine(label, note), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        row.addView(switch, LinearLayout.LayoutParams(Ui.dp(ctx, 40), Ui.dp(ctx, 22)).apply { marginStart = Ui.dp(ctx, 12) })
        row.setOnClickListener {
            switch.on = !switch.on
            Prefs.set(ctx, key, switch.on)
            onChange?.invoke(switch.on)
        }
        return row
    }

    // Back closes an open explanation card first, then an unfolded search; Android 13 and newer need a registered callback for that.
    private fun refreshBack() {
        if (Build.VERSION.SDK_INT < 33) return
        val needed = infoSheet.showing || searchField?.isOpen == true
        if (needed && backCallback == null) registerBack() else if (!needed && backCallback != null) unregisterBack()
    }

    private fun handleBack(): Boolean {
        if (infoSheet.showing) {
            infoSheet.dismiss()
            return true
        }
        if (searchField?.isOpen == true) {
            searchField?.fold()
            return true
        }
        return false
    }

    @TargetApi(33)
    private fun registerBack() {
        val callback = OnBackInvokedCallback { handleBack() }
        onBackInvokedDispatcher.registerOnBackInvokedCallback(OnBackInvokedDispatcher.PRIORITY_DEFAULT, callback)
        backCallback = callback
    }

    @TargetApi(33)
    private fun unregisterBack() {
        (backCallback as? OnBackInvokedCallback)?.let { onBackInvokedDispatcher.unregisterOnBackInvokedCallback(it) }
        backCallback = null
    }

    @Suppress("DEPRECATION")
    @Deprecated("Used below API 33; newer versions use the back callback above.")
    override fun onBackPressed() {
        if (!handleBack()) super.onBackPressed()
    }

    @Suppress("DEPRECATION")
    private fun appVersion(): String = packageManager.getPackageInfo(packageName, 0).versionName ?: "unknown"
}
