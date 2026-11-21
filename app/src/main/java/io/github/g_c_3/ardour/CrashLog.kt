package io.github.g_c_3.ardour

import android.content.Context
import android.os.Build
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// Crash log (DECISIONS.md 103): when Ardour's own code ends with an uncaught error, a short record is written to a
// private folder of the app (version, phone, error type and place, the last page load events). It stays there until the
// owner taps Save in Settings > Diagnostics, which writes one text file to Downloads. Never sent anywhere. The file's first
// line names CRASHLOG.md in the docs repository, which explains how to read it (DECISIONS.md 109). Not covered:
// the engine's own process (Trace shows RENDERER), the system killing the app, and freezes.
object CrashLog {
    private const val KEEP = 5
    private const val FRAMES = 40
    private const val CAUSES = 4
    private const val EVENTS = 60
    private var installed = false

    fun install(context: Context) {
        if (installed) return
        installed = true
        val app = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            try {
                record(app, thread, error)
            } catch (_: Throwable) {
                // A failure while recording must not hide the crash itself.
            }
            previous?.uncaughtException(thread, error)
        }
    }

    private fun folder(context: Context) = File(context.filesDir, "crash")

    private fun files(context: Context): List<File> =
        (folder(context).listFiles { f -> f.name.startsWith("crash-") && f.name.endsWith(".txt") } ?: emptyArray())
            .sortedByDescending { it.name }

    private fun stamp(time: Long = System.currentTimeMillis()): String =
        SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date(time))

    private fun versionName(context: Context): String =
        try {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "unknown"
        } catch (_: Exception) {
            "unknown"
        }

    // Error type and place only; the message is reduced like trace text (addresses cut to host and path, 160 characters).
    private fun describe(error: Throwable): String {
        val b = StringBuilder()
        var current: Throwable? = error
        var depth = 0
        while (current != null && depth < CAUSES) {
            b.append(if (depth == 0) "" else "Caused by: ").append(current.javaClass.name)
            current.message?.let { b.append(": ").append(Trace.scrub(it)) }
            b.append('\n')
            for (frame in current.stackTrace.take(FRAMES)) b.append("    at ").append(frame).append('\n')
            current = current.cause?.takeIf { it !== current }
            depth++
        }
        return b.toString()
    }

    private fun record(context: Context, thread: Thread, error: Throwable) {
        val now = System.currentTimeMillis()
        val b = StringBuilder()
        b.append("Time: ").append(stamp(now)).append('\n')
        b.append("Ardour ").append(versionName(context)).append('\n')
        b.append("Phone: ${Build.MANUFACTURER} ${Build.MODEL}, Android ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})\n")
        b.append("WebView: ").append(try { EngineAdapter.webViewVersion(context) ?: "not found" } catch (_: Throwable) { "unknown" }).append('\n')
        b.append("Thread: ").append(thread.name).append("\n\n")
        b.append(describe(error))
        b.append("\nLast page load events:\n")
        for (line in Trace.tail(EVENTS)) b.append(line).append('\n')
        val dir = folder(context)
        dir.mkdirs()
        File(dir, "crash-$now.txt").writeText(b.toString(), Charsets.UTF_8)
        for (old in files(context).drop(KEEP)) old.delete()
    }

    fun count(context: Context): Int = files(context).size

    // One line for the Settings card: how many records and the newest one's error type.
    fun summary(context: Context): String {
        val all = files(context)
        if (all.isEmpty()) return "No crash recorded."
        val newest = all.first()
        val first = try {
            newest.useLines { lines -> lines.dropWhile { !it.startsWith("Thread:") }.drop(2).firstOrNull() }
        } catch (_: Exception) {
            null
        }
        val type = first?.substringBefore(':')?.substringAfterLast('.') ?: "error"
        val time = stamp(newest.name.removePrefix("crash-").removeSuffix(".txt").toLongOrNull() ?: newest.lastModified())
        return "${all.size} recorded. Last: $type, $time."
    }

    fun render(context: Context): String {
        val b = StringBuilder("CRASHLOG.md\n")
        for ((i, f) in files(context).withIndex()) {
            b.append("\n=== Record ${i + 1} ===\n").append(f.readText(Charsets.UTF_8))
        }
        return b.toString()
    }

    // Writes one text file to Downloads. Returns its name, or null when there is nothing to save or the write failed.
    fun save(context: Context): String? {
        if (count(context) == 0) return null
        val name = "Ardour-crash-${versionName(context)}-${SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())}.txt"
        return if (Trace.writeDownload(context, name, render(context))) name else null
    }

    fun clear(context: Context) {
        for (f in files(context)) f.delete()
    }
}
