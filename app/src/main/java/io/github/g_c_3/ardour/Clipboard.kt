package io.github.g_c_3.ardour

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build

// The clipboard as the address bars use it (DECISIONS.md 118). Android 12 and newer tell the user whenever an app reads
// the clipboard, so there the content is read only on a paste; a paste that finds nothing marks the clipboard empty
// until it changes. Before Android 12 the content is read silently, so an empty clip is seen at once.
object Clipboard {
    private var emptySeen = false

    private fun manager(context: Context) = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager

    fun hasText(context: Context): Boolean {
        if (emptySeen) return false
        val cm = manager(context)
        if (!cm.hasPrimaryClip() || cm.primaryClipDescription?.hasMimeType("text/*") != true) return false
        return if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) text(context).isNotEmpty() else true
    }

    fun text(context: Context): String {
        val clip = manager(context).primaryClip ?: return ""
        if (clip.itemCount == 0) return ""
        return clip.getItemAt(0).coerceToText(context)?.toString().orEmpty()
    }

    fun copy(context: Context, text: String) {
        if (text.isEmpty()) return
        emptySeen = false
        manager(context).setPrimaryClip(ClipData.newPlainText("address", text))
    }

    fun markEmpty() {
        emptySeen = true
    }

    fun changed() {
        emptySeen = false
    }

    fun watch(context: Context, listener: ClipboardManager.OnPrimaryClipChangedListener) = manager(context).addPrimaryClipChangedListener(listener)

    fun unwatch(context: Context, listener: ClipboardManager.OnPrimaryClipChangedListener) = manager(context).removePrimaryClipChangedListener(listener)
}
