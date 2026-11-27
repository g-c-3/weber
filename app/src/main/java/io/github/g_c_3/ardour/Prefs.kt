package io.github.g_c_3.ardour

import android.content.Context

// User options kept on the device, in the app's private storage (DECISIONS.md 51). Switches default to off.
object Prefs {
    const val PULL = "pull_to_refresh"
    const val FULL_URL = "show_full_url"
    const val FADE_PAGES = "fade_pages"
    const val ICON_DARK = "icon_dark"
    const val TAB_MAX = "tab_max" // the most tabs open at once, 1 to 26 (DECISIONS.md 181)
    const val TAB_WIDTH = "tab_width" // 0 XS, 1 S, 2 M, 3 L: the tabs of the top bar tab strip
    const val TAB_HEIGHT = "tab_height"
    const val TOP_TABS = "top_bar_tabs" // the tab strip above the address bar (DECISIONS.md 173)
    const val TAB_LIST = "tab_list_view" // the tab list as rows instead of cards (DECISIONS.md 167)
    const val ICON_APPLIED = "icon_applied" // the launcher entry that is enabled now (DECISIONS.md 166)
    const val THEME = "theme"
    const val THEME_SYSTEM = "system"
    const val THEME_LIGHT = "light"
    const val THEME_DARK = "dark"
    const val FORCE_ZOOM = "force_zoom"
    const val WRAP = "wrap_on_zoom"
    const val THIRD_PARTY = "third_party_cookies"
    const val READER_PAPER = "reader_paper"
    const val READER_TEXT = "reader_text_tenths"
    const val READER_LEFT = "reader_left_aligned"

    // What leaving private mode does (DECISIONS.md 134, 136).
    const val PRIVATE_LEAVE = "private_leave"
    const val LEAVE_ASK = "ask"
    const val LEAVE_DELETE = "delete"
    const val LEAVE_KEEP = "keep"

    // Closing the app while private is on (DECISIONS.md 137): off by default. The record is host and cookie names only.
    const val PRIVATE_CLOSE = "private_delete_on_close"
    const val DELETE_ALL_SIGNINS = "delete_all_signins"
    const val QUICK_CLEAN_SIGNINS = "quick_clean_signins"
    const val SHIELD_SHOTS = "shield_screenshots"
    const val SHIELD_PREVIEW = "shield_tab_preview"
    const val PRIVATE_RECORD = "private_record"

    // Diagnostics only (hidden card in Settings): the desktop viewport script stays off, to test the Gmail stall.
    const val DIAG_NO_DESKTOP_SCRIPT = "diag_no_desktop_script"

    private fun store(context: Context) = context.getSharedPreferences("ardour_options", Context.MODE_PRIVATE)

    // `default` applies until the user flips the switch; most switches start off.
    fun get(context: Context, key: String, default: Boolean = false): Boolean = store(context).getBoolean(key, default)

    fun set(context: Context, key: String, value: Boolean) = store(context).edit().putBoolean(key, value).apply()

    fun getInt(context: Context, key: String, default: Int): Int = store(context).getInt(key, default)

    fun setInt(context: Context, key: String, value: Int) = store(context).edit().putInt(key, value).apply()

    fun getString(context: Context, key: String, default: String): String = store(context).getString(key, default) ?: default

    fun setString(context: Context, key: String, value: String) = store(context).edit().putString(key, value).apply()
}
