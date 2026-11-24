package io.github.g_c_3.ardour

import android.net.Uri

// Turns what the user typed into a page address or a search. Pure text rules, nothing is sent from here.
object UrlInput {
    private val HOST = Regex("^([a-zA-Z0-9-]+\\.)+[a-zA-Z]{2,}(:\\d{1,5})?([/?#].*)?$")
    private val LOCAL = Regex("^(localhost|(\\d{1,3}\\.){3}\\d{1,3})(:\\d{1,5})?([/?#].*)?$")

    fun resolve(raw: String): String? {
        val text = raw.trim()
        if (text.isEmpty()) return null
        if (' ' in text) return search(text)
        val lower = text.lowercase()
        if (lower.startsWith("https://") || lower.startsWith("http://")) return text
        if (HOST.matches(text) || LOCAL.matches(text)) return "https://$text"
        return search(text)
    }

    // The search page of the engine in use (DECISIONS.md 43, 117). The search is an ordinary page the user visits.
    fun searchUrl(query: String) = SearchEngines.active.base + Uri.encode(query)

    private fun search(query: String) = searchUrl(query)

    // Short form shown while the address bar is not being edited.
    fun display(url: String): String {
        val host = Uri.parse(url).host?.removePrefix("www.") ?: return url
        return if (url.startsWith("http://")) "Not secure · $host" else host
    }

    // Full address for the idle bar when the Settings switch is on (DECISIONS.md 146): the address without its scheme.
    fun displayFull(url: String): String {
        val plain = url.startsWith("http://")
        if (!plain && !url.startsWith("https://")) return url
        val rest = url.substringAfter("://").removeSuffix("/")
        return if (plain) "Not secure · $rest" else rest
    }

    private val SECOND_LEVEL = setOf("co", "com", "org", "net", "gov", "edu", "ac", "ne", "or")

    // Where the cursor goes when the address is opened for editing: right after the site name, before the dot that
    // leads to .com, .org or .co.uk (www.google.com puts it after "google"). Text with no web address: its end.
    fun nameEnd(text: String): Int {
        val start = text.indexOf("://").let { if (it >= 0) it + 3 else 0 }
        var end = start
        while (end < text.length && text[end] !in "/?#:") end++
        val host = text.substring(start, end)
        val labels = host.split('.')
        if (labels.size < 2 || host.all { it.isDigit() || it == '.' }) return end
        var dot = host.lastIndexOf('.')
        if (labels.size >= 3 && labels.last().length == 2 && labels[labels.size - 2] in SECOND_LEVEL) {
            dot = host.lastIndexOf('.', dot - 1)
        }
        return start + dot
    }
}
