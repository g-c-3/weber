package io.github.g_c_3.ardour

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build

// The clipboard as the address bars use it (DECISIONS.md 124). Paste is lit only when the clipboard holds text. The
// text is read once per copied clip, when a bar is open and the window has focus, and the answer is kept by the clip's
// copy time: Android 12 and newer show their clipboard notice on that read. Nothing is stored or sent.
object Clipboard {
    private var readStamp = -1L
    private var readHasText = false
    private var lastState = ""

    private fun manager(context: Context) = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager

    fun hasText(context: Context): Boolean {
        val cm = manager(context)
        val has = cm.hasPrimaryClip()
        val description = if (has) cm.primaryClipDescription else null
        val textual = description?.hasMimeType("text/*") == true
        var result = false
        if (description != null && textual) {
            val stamp = description.timestamp
            if (stamp != readStamp) {
                val clip = cm.primaryClip
                if (clip != null) {
                    readHasText = clip.itemCount > 0 && clip.getItemAt(0).coerceToText(context)?.isNotEmpty() == true
                    readStamp = stamp
                }
            }
            result = stamp == readStamp && readHasText
        }
        val state = "clip=$has text=$textual paste=$result sdk=${Build.VERSION.SDK_INT}"
        if (state != lastState) {
            lastState = state
            Trace.log("CLIP", state)
        }
        return result
    }

    fun text(context: Context): String {
        val clip = manager(context).primaryClip ?: return ""
        if (clip.itemCount == 0) return ""
        return clip.getItemAt(0).coerceToText(context)?.toString().orEmpty()
    }

    fun copy(context: Context, text: String) {
        if (text.isEmpty()) return
        manager(context).setPrimaryClip(ClipData.newPlainText("address", text))
    }

    fun watch(context: Context, listener: ClipboardManager.OnPrimaryClipChangedListener) = manager(context).addPrimaryClipChangedListener(listener)

    fun unwatch(context: Context, listener: ClipboardManager.OnPrimaryClipChangedListener) = manager(context).removePrimaryClipChangedListener(listener)
}
