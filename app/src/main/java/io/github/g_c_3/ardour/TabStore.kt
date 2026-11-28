package io.github.g_c_3.ardour

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

// The open tabs kept for one restart, when the Dark app icon switch makes Android close Ardour (DECISIONS.md 185). Nothing
// else writes tabs: a normal close still starts fresh. The file is in the app's private folder, holds the address, title and
// start-screen state of each tab that is not a Shield tab, is read once at the next start and removed then.
object TabStore {
    private const val FILE = "tabs-restart.json"

    // Set by the browser screen: brings the open tab's start-screen state into its tab before it is saved.
    var flush: (() -> Unit)? = null

    fun save(context: Context) {
        flush?.invoke()
        val list = JSONArray()
        var active = 0
        val open = EngineAdapter.activeTab()
        for (tab in EngineAdapter.tabs) {
            if (tab.shield) continue
            if (tab === open) active = list.length()
            list.put(
                JSONObject()
                    .put("url", tab.url ?: "")
                    .put("title", tab.title ?: "")
                    .put("blank", tab.blank)
                    .put("home", tab.home)
                    .put("startBack", tab.startBack),
            )
        }
        val file = context.getFileStreamPath(FILE)
        if (list.length() == 0) {
            file.delete()
            return
        }
        context.openFileOutput(FILE, Context.MODE_PRIVATE).use {
            it.write(JSONObject().put("active", active).put("tabs", list).toString().toByteArray())
        }
    }

    // Puts the saved tabs back on a fresh start, then removes the file. Does nothing when tabs already exist.
    fun restore(context: Context) {
        val file = context.getFileStreamPath(FILE)
        if (!file.exists()) return
        val text = try {
            file.readText()
        } catch (e: Exception) {
            null
        }
        file.delete()
        if (text == null || EngineAdapter.tabs.isNotEmpty()) return
        try {
            val root = JSONObject(text)
            val arr = root.getJSONArray("tabs")
            val tabs = (0 until arr.length()).map {
                val o = arr.getJSONObject(it)
                Tab().apply {
                    url = o.optString("url").ifEmpty { null }
                    title = o.optString("title").ifEmpty { null }
                    blank = o.optBoolean("blank", true)
                    home = o.optBoolean("home", false)
                    startBack = o.optBoolean("startBack", false)
                }
            }
            EngineAdapter.restoreTabs(tabs, root.optInt("active", 0))
        } catch (e: Exception) {
            Trace.log("TABS", "restore failed: ${e.javaClass.simpleName}")
        }
    }
}
