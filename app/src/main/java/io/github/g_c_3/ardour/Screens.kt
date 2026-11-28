package io.github.g_c_3.ardour

// Which secondary screen the person left open when Ardour went to the background (DECISIONS.md 186). The browser screen is
// `singleTask`, so opening Ardour from its icon clears Settings from the stack; it uses this to put Settings back.
object Screens {
    var settingsOpen = false
}
