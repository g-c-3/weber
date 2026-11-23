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
        SearchEngine("ecosia", "Ecosia", "https://www.ecosia.org/search?q="),
    )
    private const val KEY = "search_engine"

    // Hint of the address bar and the start box: the engine in use by name, without the word "Search" (DECISIONS.md 130, 142).
    val searchHint: String get() = active.name.removeSuffix(" Search")

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
    // First three letters of the language name, the first one capital, shown on the start page button.
    val short: String get() = name.take(3).lowercase().replaceFirstChar { it.uppercase() }
}

// Languages the voice button can listen in, A to Z (DECISIONS.md 118). The list is the set Google's speech service is
// known to offer, from memory; the phone's service decides what it can really recognise. English (UK) is the default.
object VoiceLanguages {
    val all = listOf(
        VoiceLanguage("af-ZA", "Afrikaans (South Africa)"),
        VoiceLanguage("sq-AL", "Albanian"),
        VoiceLanguage("am-ET", "Amharic"),
        VoiceLanguage("ar-EG", "Arabic (Egypt)"),
        VoiceLanguage("ar-SA", "Arabic (Saudi Arabia)"),
        VoiceLanguage("ar-AE", "Arabic (United Arab Emirates)"),
        VoiceLanguage("hy-AM", "Armenian"),
        VoiceLanguage("az-AZ", "Azerbaijani"),
        VoiceLanguage("eu-ES", "Basque"),
        VoiceLanguage("bn-BD", "Bengali (Bangladesh)"),
        VoiceLanguage("bn-IN", "Bengali (India)"),
        VoiceLanguage("bg-BG", "Bulgarian"),
        VoiceLanguage("my-MM", "Burmese"),
        VoiceLanguage("yue-HK", "Cantonese (Hong Kong)"),
        VoiceLanguage("ca-ES", "Catalan"),
        VoiceLanguage("zh-CN", "Chinese (Simplified)"),
        VoiceLanguage("zh-TW", "Chinese (Traditional)"),
        VoiceLanguage("hr-HR", "Croatian"),
        VoiceLanguage("cs-CZ", "Czech"),
        VoiceLanguage("da-DK", "Danish"),
        VoiceLanguage("nl-BE", "Dutch (Belgium)"),
        VoiceLanguage("nl-NL", "Dutch (Netherlands)"),
        VoiceLanguage("en-AU", "English (Australia)"),
        VoiceLanguage("en-CA", "English (Canada)"),
        VoiceLanguage("en-GH", "English (Ghana)"),
        VoiceLanguage("en-HK", "English (Hong Kong)"),
        VoiceLanguage("en-IN", "English (India)"),
        VoiceLanguage("en-IE", "English (Ireland)"),
        VoiceLanguage("en-KE", "English (Kenya)"),
        VoiceLanguage("en-NZ", "English (New Zealand)"),
        VoiceLanguage("en-NG", "English (Nigeria)"),
        VoiceLanguage("en-PH", "English (Philippines)"),
        VoiceLanguage("en-SG", "English (Singapore)"),
        VoiceLanguage("en-ZA", "English (South Africa)"),
        VoiceLanguage("en-TZ", "English (Tanzania)"),
        VoiceLanguage("en-GB", "English (UK)"),
        VoiceLanguage("en-US", "English (US)"),
        VoiceLanguage("et-EE", "Estonian"),
        VoiceLanguage("fil-PH", "Filipino"),
        VoiceLanguage("fi-FI", "Finnish"),
        VoiceLanguage("fr-BE", "French (Belgium)"),
        VoiceLanguage("fr-CA", "French (Canada)"),
        VoiceLanguage("fr-FR", "French (France)"),
        VoiceLanguage("fr-CH", "French (Switzerland)"),
        VoiceLanguage("gl-ES", "Galician"),
        VoiceLanguage("ka-GE", "Georgian"),
        VoiceLanguage("de-AT", "German (Austria)"),
        VoiceLanguage("de-DE", "German (Germany)"),
        VoiceLanguage("de-CH", "German (Switzerland)"),
        VoiceLanguage("el-GR", "Greek"),
        VoiceLanguage("gu-IN", "Gujarati"),
        VoiceLanguage("he-IL", "Hebrew"),
        VoiceLanguage("hi-IN", "Hindi"),
        VoiceLanguage("hu-HU", "Hungarian"),
        VoiceLanguage("is-IS", "Icelandic"),
        VoiceLanguage("id-ID", "Indonesian"),
        VoiceLanguage("it-IT", "Italian (Italy)"),
        VoiceLanguage("it-CH", "Italian (Switzerland)"),
        VoiceLanguage("ja-JP", "Japanese"),
        VoiceLanguage("jv-ID", "Javanese"),
        VoiceLanguage("kn-IN", "Kannada"),
        VoiceLanguage("kk-KZ", "Kazakh"),
        VoiceLanguage("km-KH", "Khmer"),
        VoiceLanguage("ko-KR", "Korean"),
        VoiceLanguage("lo-LA", "Lao"),
        VoiceLanguage("lv-LV", "Latvian"),
        VoiceLanguage("lt-LT", "Lithuanian"),
        VoiceLanguage("mk-MK", "Macedonian"),
        VoiceLanguage("ms-MY", "Malay"),
        VoiceLanguage("ml-IN", "Malayalam"),
        VoiceLanguage("mr-IN", "Marathi"),
        VoiceLanguage("mn-MN", "Mongolian"),
        VoiceLanguage("ne-NP", "Nepali"),
        VoiceLanguage("nb-NO", "Norwegian"),
        VoiceLanguage("fa-IR", "Persian"),
        VoiceLanguage("pl-PL", "Polish"),
        VoiceLanguage("pt-BR", "Portuguese (Brazil)"),
        VoiceLanguage("pt-PT", "Portuguese (Portugal)"),
        VoiceLanguage("pa-IN", "Punjabi"),
        VoiceLanguage("ro-RO", "Romanian"),
        VoiceLanguage("ru-RU", "Russian"),
        VoiceLanguage("sr-RS", "Serbian"),
        VoiceLanguage("si-LK", "Sinhala"),
        VoiceLanguage("sk-SK", "Slovak"),
        VoiceLanguage("sl-SI", "Slovenian"),
        VoiceLanguage("es-AR", "Spanish (Argentina)"),
        VoiceLanguage("es-BO", "Spanish (Bolivia)"),
        VoiceLanguage("es-CL", "Spanish (Chile)"),
        VoiceLanguage("es-CO", "Spanish (Colombia)"),
        VoiceLanguage("es-CR", "Spanish (Costa Rica)"),
        VoiceLanguage("es-DO", "Spanish (Dominican Republic)"),
        VoiceLanguage("es-EC", "Spanish (Ecuador)"),
        VoiceLanguage("es-SV", "Spanish (El Salvador)"),
        VoiceLanguage("es-GT", "Spanish (Guatemala)"),
        VoiceLanguage("es-HN", "Spanish (Honduras)"),
        VoiceLanguage("es-MX", "Spanish (Mexico)"),
        VoiceLanguage("es-NI", "Spanish (Nicaragua)"),
        VoiceLanguage("es-PA", "Spanish (Panama)"),
        VoiceLanguage("es-PY", "Spanish (Paraguay)"),
        VoiceLanguage("es-PE", "Spanish (Peru)"),
        VoiceLanguage("es-PR", "Spanish (Puerto Rico)"),
        VoiceLanguage("es-ES", "Spanish (Spain)"),
        VoiceLanguage("es-US", "Spanish (United States)"),
        VoiceLanguage("es-UY", "Spanish (Uruguay)"),
        VoiceLanguage("es-VE", "Spanish (Venezuela)"),
        VoiceLanguage("su-ID", "Sundanese"),
        VoiceLanguage("sw-KE", "Swahili (Kenya)"),
        VoiceLanguage("sw-TZ", "Swahili (Tanzania)"),
        VoiceLanguage("sv-SE", "Swedish"),
        VoiceLanguage("ta-IN", "Tamil (India)"),
        VoiceLanguage("ta-MY", "Tamil (Malaysia)"),
        VoiceLanguage("ta-SG", "Tamil (Singapore)"),
        VoiceLanguage("ta-LK", "Tamil (Sri Lanka)"),
        VoiceLanguage("te-IN", "Telugu"),
        VoiceLanguage("th-TH", "Thai"),
        VoiceLanguage("tr-TR", "Turkish"),
        VoiceLanguage("uk-UA", "Ukrainian"),
        VoiceLanguage("ur-IN", "Urdu (India)"),
        VoiceLanguage("ur-PK", "Urdu (Pakistan)"),
        VoiceLanguage("uz-UZ", "Uzbek"),
        VoiceLanguage("vi-VN", "Vietnamese"),
        VoiceLanguage("zu-ZA", "Zulu"),
    ).sortedBy { it.name }
    private const val KEY = "voice_language"
    private const val RECENT = "voice_recent"
    private const val RECENT_MAX = 5

    fun byTag(tag: String): VoiceLanguage? = all.firstOrNull { it.tag == tag }

    fun current(context: Context): VoiceLanguage = byTag(Prefs.getString(context, KEY, "en-GB")) ?: byTag("en-GB") ?: all[0]

    // Up to the last five languages used, newest first; the one in use counts as used.
    fun recent(context: Context): List<VoiceLanguage> {
        val saved = Prefs.getString(context, RECENT, "").split(',').mapNotNull { byTag(it) }
        return if (saved.isEmpty()) listOf(current(context)) else saved
    }

    fun use(context: Context, language: VoiceLanguage) {
        Prefs.setString(context, KEY, language.tag)
        val next = (listOf(language) + recent(context).filter { it.tag != language.tag }).take(RECENT_MAX)
        Prefs.setString(context, RECENT, next.joinToString(",") { it.tag })
    }
}
