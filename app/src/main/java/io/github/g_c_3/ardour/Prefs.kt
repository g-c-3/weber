package io.github.g_c_3.ardour

import android.content.Context

// User options kept on the device, in the app's private storage (DECISIONS.md 51). All default to off.
object Prefs {
    const val PULL = "pull_to_refresh"
    const val FORCE_ZOOM = "force_zoom"
    const val WRAP = "wrap_on_zoom"

    private fun store(context: Context) = context.getSharedPreferences("ardour_options", Context.MODE_PRIVATE)

    fun get(context: Context, key: String): Boolean = store(context).getBoolean(key, false)

    fun set(context: Context, key: String, value: Boolean) = store(context).edit().putBoolean(key, value).apply()
}
