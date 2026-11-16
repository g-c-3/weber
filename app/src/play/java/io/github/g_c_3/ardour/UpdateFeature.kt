package io.github.g_c_3.ardour

import android.app.Activity
import android.widget.FrameLayout

// The play flavor has no updater: Play delivers updates (DECISIONS.md 8).
object UpdateFeature {
    @Suppress("UNUSED_PARAMETER")
    fun attach(activity: Activity, slot: FrameLayout, divider: ProgressDivider, currentVersion: String) = Unit
}
