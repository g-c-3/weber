package io.github.g_c_3.ardour

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager

// Launcher icon (DECISIONS.md 152): two launcher entries for the same screen, one enabled at a time. The new one is
// enabled before the old one is disabled, so the app never has no launcher entry.
object AppIcon {
    fun set(context: Context, dark: Boolean) {
        val manager = context.packageManager
        val on = ComponentName(context, if (dark) "${context.packageName}.IconDark" else "${context.packageName}.IconLight")
        val off = ComponentName(context, if (dark) "${context.packageName}.IconLight" else "${context.packageName}.IconDark")
        manager.setComponentEnabledSetting(on, PackageManager.COMPONENT_ENABLED_STATE_ENABLED, PackageManager.DONT_KILL_APP)
        manager.setComponentEnabledSetting(off, PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP)
    }
}
