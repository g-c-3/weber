package io.github.g_c_3.ardour

// The text of the Ardour help page (DECISIONS.md 179): one section per area, each line one thing that can be done or that
// works differently from other browsers. Every new or changed feature that a person can see or use is written here in the
// same commit as the feature.
class HelpSection(val title: String, val lines: List<String>)

object HelpContent {
    const val INTRO = "Ardour is a light, private browser built on the Android System WebView already on your phone. " +
        "It has no accounts, no sync and no tracking. This page shows what it can do and where it works differently from other browsers."

    val sections: List<HelpSection> = listOf(
        HelpSection(
            "What is different",
            listOf(
                "Shield Mode removes what a session added, as you choose, and leaves your earlier sign-ins alone.",
                "A long press on the shield is the Quick Sweep: it cleans the open site at once.",
                "Tabs can be opened, closed and moved with swipes on a strip above the address bar (switch it on in Settings).",
                "One search field at a time: the start page box and the address bar do the same job, so the one you do not use fades away.",
                "Everything stays on your phone. Ardour sends nothing about you anywhere.",
            ),
        ),
        HelpSection(
            "Search and address",
            listOf(
                "Type a word to search or an address to open it. Suggestions appear as you type; only the text you typed is sent to the suggestion service of your search engine, with no cookie or identifier, and nothing is kept.",
                "On the start page, select the box in the middle or the address bar at the top: the other one fades away until you leave the first.",
                "Eng chooses the voice language, the microphone starts voice search after your tap, and the magnifier with the small arrow chooses the search engine.",
                "A tap or a swipe down on the address bar opens it for typing; the address is centred while it fits. "+
                "Paste lights up when the clipboard holds text. Settings > Show full address shows the whole address instead of the site name.",
            ),
        ),
        HelpSection(
            "Tabs",
            listOf(
                "The button with a number opens all your tabs as cards. The sheet opens on the tab you are using.",
                "In the sheet: the square button shows all tabs, the shield button only Shield tabs; the switch beside + changes between cards and a list; + opens a new tab.",
                "A long press on the number button opens a new tab at once.",
                "Settings > Tabs has the Top bar tab header switch, the most tabs open at once (1 to 26, 12 by default) and the width and height of the header's tabs, XS to L, which work while the header is on.",
                "The Top bar tab header (Settings > Tabs) shows your tabs above the address bar. Slide it sideways, tap a tab or swipe it down to open it, swipe it up to close it, or press and hold a tab and drag it to a new place. It glides back to your open tab after 3 seconds.",
            ),
        ),
        HelpSection(
            "Shield Mode",
            listOf(
                "Tap the shield to turn Shield Mode on for the tab you are in. Each tab has its own Shield setting.",
                "While it is on, Ardour notes the cookies each site already had. Leaving Shield Mode deletes only what the session added: cookies, saved site data and the cache. Your earlier sign-ins stay.",
                "Settings > Shield Mode chooses what leaving does: ask every time, delete always, or keep always (the default). It can also delete when the app closes and remove earlier sign-ins too.",
                "While Shield Mode is on, screenshots and screen recordings show nothing, unless you allow them in Settings.",
            ),
        ),
        HelpSection(
            "Quick Sweep",
            listOf(
                "Press and hold the shield. The open page's site is cleaned at once, whether Shield Mode is on or off, with no question. A short shredder animation plays.",
                "It works in any tab that has held a page, also one that now shows the start page. "+
                "It removes what the site added since you first opened it in this run of the app. A Settings switch makes it remove earlier sign-ins too.",
            ),
        ),
        HelpSection(
            "Menu",
            listOf(
                "Tap the three dots, or swipe down on them, to open the menu.",
                "Share, Find in page, Reader mode for a clean reading view, and Print or save as PDF work on the open page.",
                "Desktop site asks sites for their desktop version. Clear site data removes the cookies and saved data of the open site after you confirm.",
                "Pull a page down from its top to reload it (Settings > Pull to refresh).",
            ),
        ),
        HelpSection(
            "Settings and updates",
            listOf(
                "Theme follows the phone or stays light or dark. Dark app icon switches the launcher icon at once; Android may close Ardour while it does, and your tabs are there when you open it again.",
                "Update Check asks GitHub for a newer release only when you tap it. The download is checked against its published checksum, and Android asks you to confirm the install.",
                "Default App makes Ardour the browser that opens links from other apps.",
                "Diagnostics holds a page load trace and a crash log. They stay on your phone and are written to Downloads only when you tap Save.",
            ),
        ),
        HelpSection(
            "Privacy",
            listOf(
                "No telemetry, analytics, crash reports, accounts or cloud sync in Ardour's own code.",
                "Besides the pages you visit, Ardour connects only for the update check (on your tap), filter list updates and search suggestions (as you type).",
                "The web engine is Google's Android System WebView; its own traffic is outside Ardour's control.",
            ),
        ),
    )
}
