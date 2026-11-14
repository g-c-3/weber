package io.github.g_c_3.ardour

import android.net.Uri

// Turns what the user typed into a page address or a search. Pure text rules, nothing is sent from here.
object UrlInput {
    // Default search page (DECISIONS.md 43). The search is an ordinary page the user visits.
    private const val SEARCH = "https://duckduckgo.com/?q="
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

    fun searchUrl(query: String) = SEARCH + Uri.encode(query)

    private fun search(query: String) = searchUrl(query)

    // Short form shown while the address bar is not being edited.
    fun display(url: String): String {
        val host = Uri.parse(url).host?.removePrefix("www.") ?: return url
        return if (url.startsWith("http://")) "Not secure · $host" else host
    }
}
