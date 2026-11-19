package io.github.g_c_3.ardour

import android.content.Context

// User options kept on the device, in the app's private storage (DECISIONS.md 51). Switches default to off.
object Prefs {
    const val PULL = "pull_to_refresh"
    const val FORCE_ZOOM = "force_zoom"
    const val WRAP = "wrap_on_zoom"
    const val THIRD_PARTY = "third_party_cookies"
    const val READER_PAPER = "reader_paper"
    const val READER_TEXT = "reader_text_tenths"
    const val READER_LEFT = "reader_left_aligned"

    // Diagnostics only (hidden card in Settings): the desktop viewport script stays off, to test the Gmail stall.
    const val DIAG_NO_DESKTOP_SCRIPT = "diag_no_desktop_script"

    private fun store(context: Context) = context.getSharedPreferences("ardour_options", Context.MODE_PRIVATE)

    // `default` applies until the user flips the switch; most switches start off.
    fun get(context: Context, key: String, default: Boolean = false): Boolean = store(context).getBoolean(key, default)

    fun set(context: Context, key: String, value: Boolean) = store(context).edit().putBoolean(key, value).apply()

    fun getInt(context: Context, key: String, default: Int): Int = store(context).getInt(key, default)

    fun setInt(context: Context, key: String, value: Int) = store(context).edit().putInt(key, value).apply()
}
