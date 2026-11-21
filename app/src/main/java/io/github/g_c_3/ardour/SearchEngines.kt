package io.github.g_c_3.ardour

import android.content.Context

class SearchEngine(val id: String, val name: String, val base: String)

// Search engines offered on the start page, used for every search typed or spoken (DECISIONS.md 117). A search is an
// ordinary page the user visits; nothing is sent from here. DuckDuckGo stays the default until the user changes it.
object SearchEngines {
    val all = listOf(
        SearchEngine("ddg", "DuckDuckGo", "https://duckduckgo.com/?q="),
        SearchEngine("google", "Google", "https://www.google.com/search?q="),
        SearchEngine("bing", "Bing", "https://www.bing.com/search?q="),
        SearchEngine("brave", "Brave Search", "https://search.brave.com/search?q="),
        SearchEngine("startpage", "Startpage", "https://www.startpage.com/do/search?q="),
    )
    private const val KEY = "search_engine"

    // The engine in use now: the saved default at launch, or the one picked since.
    var active: SearchEngine = all[0]
        private set

    fun default(context: Context): SearchEngine {
        val id = Prefs.getString(context, KEY, all[0].id)
        return all.firstOrNull { it.id == id } ?: all[0]
    }

    fun load(context: Context) {
        active = default(context)
    }

    fun use(engine: SearchEngine) {
        active = engine
    }

    fun makeDefault(context: Context, engine: SearchEngine) = Prefs.setString(context, KEY, engine.id)
}

class VoiceLanguage(val tag: String, val name: String) {
    // First three letters of the language name, shown on the start page button.
    val short: String get() = name.take(3).uppercase()
}

// Languages the voice button listens in. English (UK) is the default.
object VoiceLanguages {
    val all = listOf(
        VoiceLanguage("en-GB", "English (UK)"),
        VoiceLanguage("en-US", "English (US)"),
        VoiceLanguage("en-IN", "English (India)"),
        VoiceLanguage("hi-IN", "Hindi"),
        VoiceLanguage("bn-IN", "Bengali"),
        VoiceLanguage("ta-IN", "Tamil"),
        VoiceLanguage("te-IN", "Telugu"),
        VoiceLanguage("mr-IN", "Marathi"),
        VoiceLanguage("es-ES", "Spanish"),
        VoiceLanguage("fr-FR", "French"),
        VoiceLanguage("de-DE", "German"),
        VoiceLanguage("ar-SA", "Arabic"),
    )
    private const val KEY = "voice_language"

    fun current(context: Context): VoiceLanguage {
        val tag = Prefs.getString(context, KEY, all[0].tag)
        return all.firstOrNull { it.tag == tag } ?: all[0]
    }

    fun set(context: Context, language: VoiceLanguage) = Prefs.setString(context, KEY, language.tag)
}
