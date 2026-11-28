package io.github.g_c_3.ardour

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager

// Launcher icon (DECISIONS.md 152): two launcher entries for the same screen, one enabled at a time. The new one is
// enabled before the old one is disabled, so the app never has no launcher entry. Entry names are class names of the code
// namespace, which differs from the installed package name (DECISIONS.md 154).
object AppIcon {
    private val namespace = MainActivity::class.java.name.substringBeforeLast('.')

    // The launcher entry is switched at once when the Settings switch is flipped (DECISIONS.md 183), and otherwise when the app
    // is closed. Android ends the app on the screen when its entry is switched off (DECISIONS.md 166, 185); the Settings switch
    // asks first and saves the open tabs for the restart (TabStore).
    // Returns whether the entries are now as wanted (checked by reading them back), or true when nothing had to change.
    fun applyPending(context: Context): Boolean {
        val wanted = Prefs.get(context, Prefs.ICON_DARK, true)
        if (wanted == Prefs.get(context, Prefs.ICON_APPLIED, true)) return true
        val ok = set(context, wanted)
        if (ok) Prefs.set(context, Prefs.ICON_APPLIED, wanted)
        return ok
    }

    private fun set(context: Context, dark: Boolean): Boolean {
        val manager = context.packageManager
        val on = ComponentName(context, "$namespace.${if (dark) "IconDark" else "IconLight"}")
        val off = ComponentName(context, "$namespace.${if (dark) "IconLight" else "IconDark"}")
        fun asWanted() = manager.getComponentEnabledSetting(on) == PackageManager.COMPONENT_ENABLED_STATE_ENABLED &&
            manager.getComponentEnabledSetting(off) == PackageManager.COMPONENT_ENABLED_STATE_DISABLED
        // Twice at most: the second try runs only if the entries read back wrong after the first.
        for (attempt in 1..2) {
            try {
                manager.setComponentEnabledSetting(on, PackageManager.COMPONENT_ENABLED_STATE_ENABLED, PackageManager.DONT_KILL_APP)
                manager.setComponentEnabledSetting(off, PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP)
                if (asWanted()) {
                    Trace.log("ACTION", "icon changed (try $attempt)")
                    return true
                }
            } catch (e: IllegalArgumentException) {
                Trace.log("ACTION", "icon change failed: ${e.javaClass.simpleName}")
            } catch (e: SecurityException) {
                Trace.log("ACTION", "icon change failed: ${e.javaClass.simpleName}")
            }
        }
        return false
    }
}
