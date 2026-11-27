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
    // is closed. With the entry that started the running task disabled, Android may end the app on the screen (DECISIONS.md 166);
    // its tabs are saved and come back at the next start.
    fun applyPending(context: Context) {
        val wanted = Prefs.get(context, Prefs.ICON_DARK, true)
        if (wanted == Prefs.get(context, Prefs.ICON_APPLIED, true)) return
        set(context, wanted)
        Prefs.set(context, Prefs.ICON_APPLIED, wanted)
    }

    private fun set(context: Context, dark: Boolean) {
        val manager = context.packageManager
        val on = ComponentName(context, "$namespace.${if (dark) "IconDark" else "IconLight"}")
        val off = ComponentName(context, "$namespace.${if (dark) "IconLight" else "IconDark"}")
        try {
            manager.setComponentEnabledSetting(on, PackageManager.COMPONENT_ENABLED_STATE_ENABLED, PackageManager.DONT_KILL_APP)
            manager.setComponentEnabledSetting(off, PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP)
        } catch (e: IllegalArgumentException) {
            Trace.log("ACTION", "icon change failed: ${e.javaClass.simpleName}")
        } catch (e: SecurityException) {
            Trace.log("ACTION", "icon change failed: ${e.javaClass.simpleName}")
        }
    }
}
