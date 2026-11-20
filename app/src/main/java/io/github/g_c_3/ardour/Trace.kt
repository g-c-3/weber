package io.github.g_c_3.ardour

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.SystemClock
import android.provider.MediaStore
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// Page load trace (DECISIONS.md 84): the last events of page loading, kept in memory only and written to a text file
// in Downloads only when the owner taps Save in Settings. Addresses are reduced to host and path (query values and
// long path parts removed), no cookie values, no page content. Never sent anywhere.
object Trace {
    private const val MAX = 3000
    private val lines = ArrayDeque<String>()
    private val started = SystemClock.elapsedRealtime()
    private val clock = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)
    private var total = 0L
    private var lastProgress = -1

    @Synchronized
    fun log(tag: String, text: String) {
        val ms = SystemClock.elapsedRealtime() - started
        lines.addLast("${clock.format(Date())} +${ms}ms ${tag.padEnd(9)} $text")
        if (lines.size > MAX) lines.removeFirst()
        total++
    }

    // Engine progress: each new value once; a load start resets the memory (see `progressReset`).
    fun progress(percent: Int) {
        if (percent == lastProgress) return
        lastProgress = percent
        log("PROGRESS", percent.toString())
    }

    fun progressReset() {
        lastProgress = -1
    }

    @Synchronized
    fun size(): Int = lines.size

    @Synchronized
    fun clear() {
        lines.clear()
        total = 0
    }

    // Host and path only: query values and long path parts can carry tokens and are removed.
    fun safe(url: String?): String {
        if (url.isNullOrEmpty()) return "-"
        val uri = Uri.parse(url)
        val scheme = uri.scheme ?: return url.take(30)
        if (scheme != "http" && scheme != "https") return "$scheme:..."
        val port = if (uri.port != -1) ":" + uri.port else ""
        val path = (uri.encodedPath ?: "").split('/').joinToString("/") { if (it.length > 24) it.take(24) + "~" else it }
        val query = uri.encodedQuery?.let { q ->
            "?" + q.split('&').joinToString("&") { it.substringBefore('=') + if ('=' in it) "=~" else "" }
        } ?: ""
        val fragment = uri.encodedFragment?.let { f ->
            if (f.length <= 24 && f.all { it.isLetterOrDigit() || it == '-' || it == '_' || it == '/' }) "#$f" else "#~"
        } ?: ""
        return "$scheme://${uri.host ?: "?"}$port$path$query$fragment"
    }

    // Page script messages can hold addresses: every address in them is reduced the same way.
    fun scrub(text: String): String =
        Regex("https?://[^\\s'\"<>)]+").replace(text) { safe(it.value) }.replace('\n', ' ').take(160)

    private fun versionName(context: Context): String =
        try {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "unknown"
        } catch (_: Exception) {
            "unknown"
        }

    private fun guide(): String = """
        # Ardour page load trace

        ## How to read this file
        Each event line: wall clock time, milliseconds since the app process began recording, TAG, detail.
        - ACTION: something the user or the app did that starts or changes a load (typed address, reload, stop, back, forward, Desktop site switch, Clear site data). Shows the address handed to the engine.
        - NAV: a navigation request seen by the engine callback. main=true is the page itself, main=false is a frame inside it. redirect=true means a server or script redirect. gesture=true means a tap.
        - STARTED: the engine began loading a main-frame address (can repeat on redirects).
        - COMMIT: the new page is on screen (the old one is no longer drawn).
        - PROGRESS: the engine's own percent, each new value once.
        - FINISHED: the engine says the load ended.
        - HISTORY: the back and forward list changed (reload=true for a reload).
        - UI: the app's loading flag. loading=true shows the stop button and the loading line; loading=false shows reload.
        - POLL: after 90 percent the app asks the page whether its document is parsed (decision 66).
        - HEARTBEAT: every 2 s while loading: progress, the address the engine reports now and the one it was asked for first.
        - PROBE: with each heartbeat the page is asked for its document state (loading, interactive, complete). "no answer" means the page's own thread is busy (a script); "loading" for many beats means it waits for the server.
        - RES: a resource the page asks the engine to load (up to 80 per page, no result). The last RES lines before a stall show what the page waits on.
        - HTTPERR: HTTP status 400 or more (main frame always; other resources up to 30 per page).
        - ERROR / SSLERR: load failures. RENDERER: the engine process ended.
        - CONSOLE: page script error messages (addresses reduced, 160 characters).
        - COOKIES: cookie names for the open address (values never recorded).
        A normal load reads: ACTION, UI loading=true, STARTED, NAV redirects, PROGRESS ..., COMMIT, PROGRESS 100, FINISHED, UI loading=false.
        Look for: STARTED with no COMMIT or FINISHED after it (a stall; then read the last RES lines), the same addresses repeating (a redirect loop), HEARTBEAT lines with a progress value that never changes, an ERROR code, or a load the engine never reported at all (ACTION without STARTED).

        ## Privacy
        Addresses are host and path only: query values and long path parts are removed; fragments are kept only when short. No cookie values, no page content, no titles. Created only because Save was tapped; nothing is sent anywhere.
    """.trimIndent()

    @Synchronized
    fun render(context: Context): String {
        val b = StringBuilder(guide())
        b.append("\n\n## Device and app\n")
        b.append("Ardour ${versionName(context)}\n")
        b.append("Phone: ${Build.MANUFACTURER} ${Build.MODEL}, Android ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})\n")
        b.append("WebView: ${EngineAdapter.webViewVersion(context) ?: "not found"}\n")
        b.append("Settings: pull to refresh=${Prefs.get(context, Prefs.PULL)}, force zoom=${Prefs.get(context, Prefs.FORCE_ZOOM)}, ")
        b.append("wrap text=${Prefs.get(context, Prefs.WRAP)}, third-party cookies=${Prefs.get(context, Prefs.THIRD_PARTY)}\n")
        b.append("Saved: ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())}; ${lines.size} events kept of $total recorded\n")
        b.append("\n## State when saved\n")
        b.append(EngineAdapter.traceState())
        b.append("\n\n## Events (oldest first)\n")
        for (line in lines) b.append(line).append('\n')
        return b.toString()
    }

    @Synchronized
    fun tail(count: Int): List<String> = lines.toList().takeLast(count)

    // Writes a text file to Downloads through the media store (no storage permission is needed). True when written.
    fun writeDownload(context: Context, name: String, text: String): Boolean {
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, name)
            put(MediaStore.Downloads.MIME_TYPE, "text/plain")
            put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
            put(MediaStore.Downloads.IS_PENDING, 1)
        }
        val resolver = context.contentResolver
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: return false
        return try {
            resolver.openOutputStream(uri)?.use { it.write(text.toByteArray(Charsets.UTF_8)) } ?: throw java.io.IOException("no stream")
            values.clear()
            values.put(MediaStore.Downloads.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
            true
        } catch (_: Exception) {
            resolver.delete(uri, null, null)
            false
        }
    }

    // Saves the trace as a file in Downloads. Returns its name, or null.
    fun save(context: Context): String? {
        val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
        val name = "Ardour-trace-${versionName(context)}-$stamp.txt"
        return if (writeDownload(context, name, render(context))) name else null
    }
}
