package io.github.g_c_3.ardour

import android.net.Uri
import android.os.Handler
import android.os.Looper
import org.json.JSONArray
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.net.URL
import java.util.concurrent.Executors
import javax.net.ssl.HttpsURLConnection

// Search suggestions (DECISIONS.md 119, 125): while a search field or the address bar is edited, the typed text goes
// over HTTPS to the suggestion address of the engine in use. No cookie, no identifier, no referrer, no redirect;
// nothing is kept. Google and Startpage have no usable address, so their typed text goes to another engine's service
// (DECISIONS.md 126); the search itself still goes to the engine in use.
object Suggestions {
    // Addresses from Chromium's engine list (DECISIONS.md 125). The typed text is appended, URL-encoded.
    private val ENDPOINTS = mapOf(
        "ddg" to "https://duckduckgo.com/ac/?type=list&q=",
        "bing" to "https://www.bing.com/osjson.aspx?query=",
        "brave" to "https://search.brave.com/api/suggest?q=",
    )
    // Engine in use -> engine whose suggestion service answers (DECISIONS.md 126).
    private val SOURCE = mapOf("google" to "brave", "startpage" to "ddg")
    private const val MAX_ITEMS = 5
    private const val MAX_TEXT = 100
    private const val MAX_BODY = 32768
    private const val DELAY_MS = 150L
    private const val TIMEOUT_MS = 3000

    private val pool = Executors.newSingleThreadExecutor { r -> Thread(r, "suggest").apply { isDaemon = true } }

    // An address is never sent: only text that looks like words to search for.
    fun eligible(text: String): Boolean {
        val t = text.trim()
        if (t.isEmpty() || t.length > MAX_TEXT) return false
        return "://" !in t
    }

    fun parse(body: String, typed: String): List<String> {
        val items = JSONArray(body.trim()).optJSONArray(1) ?: return emptyList()
        val out = ArrayList<String>()
        for (i in 0 until items.length()) {
            val s = items.optString(i, "").trim()
            if (s.isEmpty() || s.length > MAX_TEXT || s.equals(typed, ignoreCase = true) || s in out) continue
            out.add(s)
            if (out.size == MAX_ITEMS) break
        }
        return out
    }

    private fun readCapped(stream: InputStream): String {
        val out = ByteArrayOutputStream()
        val buf = ByteArray(4096)
        while (out.size() < MAX_BODY) {
            val n = stream.read(buf)
            if (n < 0) break
            out.write(buf, 0, n)
        }
        return out.toString("UTF-8")
    }

    // One per field. query() drops any request still waiting or running, then asks again after a short pause.
    class Session(private val onResult: (List<String>) -> Unit) {
        private val main = Handler(Looper.getMainLooper())
        private val lock = Any()
        private var generation = 0
        private var waiting: Runnable? = null
        private var running: HttpsURLConnection? = null

        fun query(text: String) {
            stop()
            val typed = text.trim()
            val source = SOURCE[SearchEngines.active.id] ?: SearchEngines.active.id
            val base = ENDPOINTS[source]
            if (base == null || !eligible(typed)) {
                onResult(emptyList())
                return
            }
            val mine = synchronized(lock) { generation }
            val run = Runnable {
                waiting = null
                Trace.log("SUGGEST", "asked $source, ${typed.length} characters")
                pool.execute { fetch(base, typed, mine) }
            }
            waiting = run
            main.postDelayed(run, DELAY_MS)
        }

        // Cancels what is waiting or running; a late answer is dropped.
        fun stop() {
            waiting?.let { main.removeCallbacks(it) }
            waiting = null
            synchronized(lock) {
                generation++
                running?.disconnect()
                running = null
            }
        }

        private fun fetch(base: String, typed: String, mine: Int) {
            var conn: HttpsURLConnection? = null
            try {
                conn = URL(base + Uri.encode(typed)).openConnection() as? HttpsURLConnection ?: return
                conn.connectTimeout = TIMEOUT_MS
                conn.readTimeout = TIMEOUT_MS
                conn.instanceFollowRedirects = false
                conn.useCaches = false
                conn.requestMethod = "GET"
                conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Android)")
                conn.setRequestProperty("Accept", "application/json")
                synchronized(lock) {
                    if (mine != generation) return
                    running = conn
                }
                if (conn.responseCode != 200) return
                val items = parse(conn.inputStream.use { readCapped(it) }, typed)
                main.post {
                    synchronized(lock) { if (mine != generation) return@post }
                    Trace.log("SUGGEST", "got ${items.size}")
                    onResult(items)
                }
            } catch (e: Exception) {
                // A failed request shows nothing.
            } finally {
                synchronized(lock) { if (running === conn) running = null }
                conn?.disconnect()
            }
        }
    }
}
