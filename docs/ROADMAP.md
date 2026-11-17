# Roadmap

## Checklist

- [x] 0 Repo scaffold and docs
- [x] 1 CI, signing and versioning
- [x] 2 Hello WebView
- [ ] 3 Settings screen and in-app update
- [ ] 4 Basic browser
- [ ] 5 Dependency update pipeline and launch test
- [ ] 6 Tabs and private tabs
- [ ] 7 History, bookmarks, downloads
- [ ] 8 Content blocking and privacy controls (built by Ardour)
- [ ] 9 Theming and polish
- [ ] 10 Play Store preparation
- [ ] 11 Special features
- [ ] 12 Spaces
- [ ] 13 Customisation

## Detail

Build order: 2, 3, 4, 12, 5, 6, 7, 8, 11, 13, 9, 10 (decision 25). First public release after 8.
Engine: system WebView (decision 26). Quality bar: decision 30; targets below.
Approval: only Phase 2 is approved. Items marked (planned) are approved per phase when it starts.
Settings (decision 20): basic options by default; an Advanced section holds the full range. Fast and smooth is checked on device in every phase.

### 0 Repo scaffold and docs (done)
- `docs/` (six files), `version.properties`, `.gitignore`.
- Gradle files deferred to Phase 1 (decision 9).

### 1 CI, signing and versioning (done)
- Toolchain pinned (decision 12); `app` module; ABI splits (arm64, universal; replaced by one APK, decision 27); version wiring from `version.properties`.
- `build-android.yml`: jobs `build`, `plan`, `release`. Release is automatic on a new version (decision 16).
- `verification-bootstrap.yml`: manual; checksum file committed.
- Signing key and secrets (decision 15). License MPL 2.0 (decision 17). Application id (decision 18).
- DP0: release 0.0.1 installed and opened on device. Release 0.0.2 (new application id): device result pending.
- Launcher icon shipped early as 0.0.3 (decision 19); see Phase 9.

### 2 Hello WebView (approved)
- State: release 0.0.6 built green and runs on device (WebView, one APK, gate, adapter). APK 2.88 MB (release page). Releases 0.0.4 and 0.0.5 run GeckoView `159.0.20261102144711` (decision 24, superseded). DP1 passed on both: a page loads, below the status bar. Sizes: arm64 91.2 MB, universal 237 MB.
- Engine is now system WebView (decision 26). Remove from the repo: GeckoView dependency and `libs.versions.toml` entry, Mozilla Maven in `settings.gradle.kts`, Kotlin 2.4.20 classpath override in the root build file, compileSdk 37.2 and `useLegacyPackaging` in `app/build.gradle.kts`. Let the build decide whether `androidx.webkit` needs any of them.
- `androidx.webkit` verified on developer.android.com (session 9): stable 1.18.0; 1.19.0-alpha01 is alpha and not used. Pin 1.18.0 in `libs.versions.toml`; record its license (decision 11), its size cost and the compileSdk it needs from the build.
- API forms verified (session 9): `ProfileStore.getInstance()` with `getOrCreateProfile(name)`, `deleteProfile(name)`, `getAllProfileNames()`; `WebViewCompat.setProfile(webView, name)` and `getProfile(webView)`. All behind `WebViewFeature.MULTI_PROFILE`; without it they throw `UnsupportedOperationException`. `Profile.DEFAULT_PROFILE_NAME` is "Default". `WebViewBuilder` is experimental: not used.
- Rule for the adapter: `setProfile` runs before any other WebView call except attaching to the view hierarchy; `evaluateJavascript` before it throws `IllegalStateException`. Launch gate checks `isFeatureSupported(MULTI_PROFILE)` at run time; no WebView version number is claimed (the reference gives none).
- Verified (session 10, developer.android.com): 1.18.0 is still the newest stable; 1.19.0-alpha is not used. Library minSdk is 24 (no conflict with 29). 1.17.1 removed Kotlin from the library.
- Renderer-gone callback (session 10, "Manage WebView objects" guide, snippets partly read): `WebViewClient.onRenderProcessGone(view, detail)`; `detail.didCrash()` separates a crash from a system kill. To keep the app running: remove the view from its parent, destroy it, drop the reference, return true. Returning false ends the app. Lint (1.17.0) flags a missing override.
- Verified (session 11, "Manage WebView objects" guide): `WebViewCompat.getCurrentWebViewPackage(context)` returns a `PackageInfo`; the guide reads `versionName`. The platform method returns null when no WebView package exists; the gate treats null as unmet. `android.webkit.WebView.EnableSafeBrowsing` false and `android.webkit.WebView.MetricsOptOut` true confirmed as the manifest meta-data names.
- Verified (session 11, Android security guide "Cross-app scripting"): `setAllowFileAccess` defaults to true on API 29 and lower and false on 30 and higher; the guide sets it and `setAllowContentAccess` to false explicitly. Ardour sets both to false explicitly.
- Library history (session 11, release notes): minSdk 24 since 1.16.0; 1.16.0-alpha03 notes say the library keeps compileSdk 33; the required compileSdk for 1.18.0 is not stated. The build decides.
- Verified (session 12): mixed-content default and file-URL defaults (decision 32); `androidx.webkit` license Apache 2.0 (decision 32); newest stable still 1.18.0. Not verified: size cost and required compileSdk (first build), profile-name rules, `setJavaScriptEnabled` default read only from a third-party page.
- Method note: whole-page fetches of the `WebSettings` reference and of the release notes return mostly navigation text or very long text. Search for the named method and read the snippet; fetch a page only when the method name is in the search result.
- minSdk 29 (decision 28). Manifest: INTERNET (present), Safe Browsing off and metrics opt-out meta-data (decision 29), `allowBackup=false` (confirm).
- Launch gate (decision 28): WebView package version through `WebViewCompat.getCurrentWebViewPackage`; `WebViewFeature.isFeatureSupported` for profiles; full-screen message when unmet. Find the minimum WebView version for the features needed; record it in DECISIONS.md.
- Rewrite `EngineAdapter.kt` for WebView; one screen; one page loads. WebView settings per ARCHITECTURE.md, Security checks. Handle the renderer-gone callback as documented.
- Space profile: bind one profile before any other call on the WebView; verify the API form and feature flag in the documentation.
- CI: one APK `Ardour-X.Y.Z.apk`; versionCode D = 1 (decision 27); release notes line for the WebView requirement; regenerate `verification-metadata.xml` with `verification-bootstrap`, commit, then build.
- Measure on device and report in the session entry: APK size, cold start to first page, scroll smoothness. Set Quality targets from it.
- Order after delivery: commit the files (build turns red on checksums, expected), run `verification-bootstrap`, commit the metadata, build green, release 0.0.6.
- DP13: passed on 0.0.7 except the gate message (cannot be triggered on a current WebView). Page and link load; cold start measured (below).
- DP14: passed on 0.0.7 (owner screenshot): clock and status icons dark and readable. Navigation bar not visible in the screenshot, not confirmed.

### 3 Settings screen and in-app update
- DP15: 0.1.0 Settings look passed on a screenshot: cards, back button, dark pill button. Press and entrance motion, gate screen and the Settings button over a page not yet seen.
- DP16: glow, pulse and progress light (decision 40). Passed on a 0.9.2 screenshot: glowing download bar, greyed button. Pulse and sweep not yet seen (needs a recording).
- Settings screen: basic list plus Advanced section (decision 20); "Check for updates" (sideload only). About shows the app version and the phone's WebView version.
- Step 1 (0.0.8, released, run on device): `sideload` and `play` flavors, Settings screen with About (app and WebView version), temporary Settings button. Step 2 (0.0.9, released): updater per decision 36; check shows "up to date" on device. 0.1.0 restyles Settings, the gate screen and the Settings button to the polish rule (decision 38), not yet run through CI or on a device. Advanced section waits for its first setting.
- Version compare number by number; the one APK (decision 27); SHA-256 check; system installer.
- (planned) Local crash log, shared only on tap.
- DP3: passed (owner screenshots): 0.0.9 found 0.1.0, downloaded, passed the checksum, Android asked to update, 0.1.0 installed over it. The install path needs a newer release: 0.1.0 is the first test (check, download, checksum, confirm, install over 0.0.9). Also check the install permission screen and the rate-limit text if shown.
- 0.9.3 (released): `version.properties` removed; CI writes build errors as annotations (decision 42). DP16 still pending on a device.
- DP2: passed on 0.0.8 (owner recording): Settings opens, Ardour 0.0.8, WebView 157.0.8103.38 (com.google.android.webview). Scroll with the button present: median gap 16.4 ms, no fast-motion gap over 34 ms (about 3 flicks, short of the 10 the target asks). Start-up with the button not re-measured. DP3: update check and install.

### 4 Basic browser
- Hold lifted (owner, session 33): reader passed on the Times of India articles (owner: best and fastest so far).
- Step 7 (1.2.3, released, not run on a device, decisions 63 to 65): progress bar stays until the page finishes; pages laid out like a full browser (wide viewport, overview, text auto-sizing); pill and buttons 34 dp with a clear strip for the progress line.
- DP33: (a) open browserbench.org Speedometer in mobile mode: fitted to the screen without Desktop site; (b) a normal mobile site (news, search) still looks right, text not too small; (c) Desktop site switch still works; (d) load gsmarena: the bar stays until it finishes and nothing runs under the pill; (e) Reader, pinch zoom and Wrap text still work. Judge on screenshots.
- Approved (decision 43). Step 1 (0.9.5, released, not run on a device): address bar, back, forward, reload and stop, progress line, start screen. 0.9.7 adds the Ardour error screen (decision 46). Next: the planned items below.
- DP17: passed on 0.9.7 (owner screenshots): search and address load, back walks history then leaves the app, error screen. Error kinds passed on 0.9.9 (owner screenshots): offline in airplane mode, missing site with data on. Not yet seen: stop while loading, keyboard hiding the bottom bar. Original check: type an address and a search; page loads and the address shows the host; system back and gesture walk the history, then leave the app; reload turns into stop while loading; the bottom bar hides while typing. Judge on screenshots.
- Step 2 (1.0.0 and 1.0.1, released, not run on a device): the Settings button is now a menu card with share, desktop site and Settings (decision 49).
- DP18: menu opens above the toolbar and closes on outside tap; Share opens the share sheet; Desktop site switch reloads the page as desktop and back; Settings opens. Judge on screenshots. Result on 1.0.0 (owner screenshots): menu look passed; Share passed; the Desktop site switch toggles and the page reloads. In the switch-on screenshot the desktop page looks cut off at the right edge; owner confirmed it stayed cut off after loading. Fix in 1.0.1: passed (owner screenshot, google.com fits the screen). DP18 passed.
- DP19: Settings shows a Private DNS card; the button opens the phone's network settings. Judge on a screenshot. Result on 1.0.3 (owner screenshots): passed; the card looks finished and the button opens Network & Internet, where Private DNS is listed.
- Find in page (1.0.3, released, not run on a device): menu item opens a bar with query, match count, previous, next and close; back closes it.
- DP20: Find in page: open from the menu, type, count shows "1 of N", next and previous move the highlight, "No matches" for a missing word, close and back both close it, the bar sits above the keyboard. Judge on screenshots. Result on 1.0.3 (owner screenshot): bar above the keyboard, matches highlighted, "1 of 14" shown. Not yet seen: next and previous, no-match text, close and back.
- Step 3 (1.0.4, released, not run on a device, decision 51): pinch zoom on by default; Settings > Browsing has pull to refresh, force zoom, wrap text when zoomed (all off by default).
- DP21: pinch in and out on a normal page; switch on Pull to refresh, pull a page down from its top: chip fills, page reloads; Force zoom on a site that blocks zoom (open it after switching on); Wrap text: pinch changes text size and the text re-wraps. Judge on screenshots and a short recording.
- DP21 result on 1.0.4 (owner, recording on a cricket news site): pull to refresh passed; pinch zoom did not work; force zoom did not work; wrap text not tested. Cause not confirmed: the site may block zoom, and the force-zoom script may not have run (or was switched on after the page loaded). 1.0.5 also runs the script on the open page and after each load. Retest 1.0.5: a zoomable site (an encyclopedia page) with all options off, then the same blocked site with Force zoom on and the page reloaded.
- DP21 final: 1.0.5 passed (owner): zoom, force zoom and wrap text work.
- Step 4 (1.0.6, released, decision 52): menu has Print or save as PDF (system print screen) and, when Wrap text is on and the text size was changed, Reset text size.
- DP22: Print or save as PDF passed on 1.0.6 (owner screenshots: print screen opens, Save as PDF, six-page preview of a news page). Reset text size passed (owner, 1.0.7).
- Step 6 (1.0.9, released, not run on a device, decision 55): menu item Reader mode; twilight and paper themes, seven text sizes, reading-progress line; text only.
- Step 6 fix (1.1.0): clipped headings, no-article screen, front pages.
- Step 6 fix 2 (1.1.1): right-edge clipping of article text.
- Step 6 addition (1.1.2): justified text and button.
- Step 6 addition 2 (1.1.3, not run on a device): lazy text loaded first, author and date line, instant open with a preparing screen. DP25 result on 1.1.3 (owner screenshots): passed for the unscrolled Indian Express article (full text, headings, author and date, hyphenated justified lines, progress line); the Hindu article showed two paragraphs behind the site's registration wall, with author and date. Recording (owner, 1.1.3, Indian Express): the preparing screen shows with its pulsing dot, then the full text; the page is where it was after closing. The preparing screen lasted about 2.1 s on first open and about 0.7 s on a second open (read from the recording at 10 frames a second, so roughly 0.2 s either way). Original DP25: open a long article without scrolling it, menu > Reader mode: "Preparing the page" shows at once, then the full text; the page is back at its old position after closing; author and date appear under the title; check a short page still opens quickly.
- DP24 PASSED on 1.1.2 (screenshots: full article, justified, twilight, no clipping, no-article screen). Original steps (re-check on 1.1.2; 1.0.9 showed the three defects above): open a news or blog page, menu > Reader mode: text appears in twilight; A and A buttons change size; sun/moon button switches to paper and back (status bar icons stay readable); progress line moves while scrolling; back and the close button return to the page; a search or start-style page shows "No article here"; closing and reopening keeps theme and size. Judge on screenshots.
- Step 5 (1.0.7, released, not run on a device, decision 54): web links from other apps open in Ardour (http and https only); Settings > Links asks Android to make Ardour the default browser. Space choice for opened links added after Phase 12.
- DP23: tap a web link in another app and choose Ardour: it opens. Settings > Links > Make Ardour the default: Android's dialog appears; after confirming, the card says Ardour is the default. A link tapped while Ardour is open loads in the same window. Result on 1.0.7 (owner recording): a web link tapped in an email app opened in Ardour and the page loaded. Not yet seen: the default-browser dialog, a link while Ardour is open. Final on 1.0.7 (owner): the in-app button showed no confirmation dialog (the owner set the default in phone settings and links then opened in Ardour); a link while Ardour is open loads in the same window; Reset text size works. 1.0.8 adds an Open default apps button when the dialog is not offered. DP23 retest on 1.0.8. Final on 1.0.8 (owner screenshots): the Make Ardour the default button opens Android's browser dialog listing Ardour; DP23 passed. The earlier missing dialog was probably Android's Don't ask again box (unconfirmed).
- DP4: navigation works.

### 5 Dependency update pipeline and launch test
- `dependency-update.yml`: scheduled check for a newer stable `androidx.webkit` (stable only); one pull request that changes the pin, regenerates verification metadata and builds. Replaces the GeckoView engine pipeline.
- Launch test in CI (emulator installs the APK, loads a local page). Runs on an Android 10 image and on the current one. Gate check: an emulator with an old or missing WebView must show the gate message and its button (decisions 28, 34). Closes the gate-message item left open in Phase 2.
- Credential method for starting the build verified against current GitHub documentation; recorded in DECISIONS.md.

### 6 Tabs and private tabs
- Tab list, switching, closing; private tabs use a temporary profile removed on close and at next launch (decision 29).
- (planned) Recently closed tabs; tab groups and tab search; tab sleep.
- DP5: tabs and private tab behavior.

### 7 History, bookmarks, downloads
- On-device only; export on explicit action.
- (planned) Resumable download manager.
- (planned) Address-bar suggestions from local history only.
- (planned) Saved logins, encrypted per Space; cache clearing keeps them; separate clear-logins action (decisions 21, 22). Passkey support (verify WebView support first, decision 31).
- (planned) Import bookmarks and logins from a file; reminder to delete the export.
- (planned) Offline reading pages per Space; auto-clear history older than N days; offline password health check (no breach lookup).
- DP6: each works on device.

### 8 Content blocking and privacy controls (built by Ardour)
- Own content blocking on WebView (decision 31): request blocking, page-element hiding by script injected before page scripts. No request rewriting. Per-site controls; no connection beyond decision 23.
- (planned) Filter-list ad blocking: lists built in, tap and scheduled updates (decision 23). Verify the lists' licenses first.
- (planned) Do Not Track and Global Privacy Control (main-frame headers and a script flag); link-tracker stripping; HTTPS-only upgrade at navigation.
- (planned) Local data saver: image blocking, no autoplay, no web fonts, no prefetch.
- (planned) Site permissions manager and pop-up blocker; per-site cookie auto-delete; local privacy report; site info panel.
- DP7: blocking and controls on device.

### 9 Theming and polish
- Launcher icon done early (decision 19). DP9: icon looks right on the launcher (0.0.3).
- Remaining: light and dark themes, layout; custom-drawn UI and spring animation (decision 30). Status bar clock and icons are light on a light bar in 0.0.4 and 0.0.5; fix here.
- Monochrome icon layer; 512 px store icon (Phase 10).
- (planned) Smooth transitions and graphics; follow the system reduce-animations setting.
- (planned) Picture-in-picture; add to home screen; background audio with media controls; tablet and foldable layouts.
- DP8: appearance on device.

### 10 Play Store preparation
- `play` flavor, App Bundle (one bundle, D = 0), listing, policy forms, name and trademark check. Listing claims state measured results only (decision 30); the engine is Google's WebView (decision 29).
- Record the signature difference again in DECISIONS.md.

### 11 Special features
- (planned) Wipe on leaving a Space; panic button and home-screen shortcut (no prompt).
- (planned) Link routing to a Space; open a link in another Space.
- (planned) Hidden Space.
- (planned) Per-Space privacy level, including best-effort fingerprint reduction at script level (off by default; decision 31).
- (planned) Screenshot and app-switcher preview block in private and hidden Spaces.
- (planned) Encrypted Space backup to a file, on tap.
- (planned) Space shortcuts on the app icon (lock still applies); site time limits per Space.
- DP11: each works on device.

### 12 Spaces
- (planned) Six built-in Spaces, up to ten; add and delete behind device authentication (decision 21).
- (planned) Biometric or phone-password switch; open with default or last-used Space.
- (planned) Private tab per Space; clear data per Space; name, colour and icon.
- (planned) Per-Space settings: search engine, home page, desktop-site default, text size.
- (planned) Auto-lock timeout (hidden Space locks immediately); first-run setup.
- DP10: Spaces, lock and clearing on device.

### 13 Customisation
- (planned) In order: looks, layout (including bottom toolbar), behaviour.
- (planned) Keyword shortcuts and custom search engines; per-Space theme or wallpaper; swipe gestures.
- DP12: customisation on device.

### Quality targets
- Size: 2.88 MB measured at 0.0.6 (release page), under the 5 MB aim of decision 30; target set after the shrinking decision. Each dependency states its size cost.
- Start-up and frame rate: measured session 15 on moto g34 5G, Android 15, 0.0.7, screen recording (accuracy about one video frame, 17 ms). Cold start, first launch frame to first page: 0.51, 0.56, 0.60 s (mean 0.56 s); example.com, network and cache unknown. Scroll, same IANA page, same method: Ardour 0.0.7 median frame gap 16.8 ms (5 flicks, 5 of 161 gaps over 34 ms; two later recordings, probably Ardour: 16.9 and 16.8 ms); reference browser 16.8 ms (6 flicks, 5 of 255 over 34 ms). No gap over 34 ms during fast motion in any recording; every longer gap sits where motion slows or the finger changes direction. Ardour matches the reference. Targets (approved, decision 35): cold start to first page 0.50 s or less, mean of 3, and never above 0.60 s in any phase without a decision entry; no white flash, Ardour's own themed window shown before the page; median scroll gap 17 ms or less with no mid-flick gap over 34 ms in 10 flicks, and never worse than the same page in a reference browser on the same phone; APK 5 MB or less through Phase 8. Re-measured every phase.
- Visual quality: custom-drawn views and spring motion; judged on device by the owner each phase.

### Later (each needs its own approval)
- Forced dark mode on every page; page translation.
- Anything needing a new connection: web push, add-on store, cookie-banner lists, encrypted DNS, search suggestions, safe-browsing lookups.
- Dropped (decision 50): Tor, Orbot and Quad9 in the app. Private DNS shortcut in Settings built (1.0.2).
- Findings kept for reference (session 31, web sources): Private DNS is Android's phone-wide encrypted-lookup setting and does not hide the IP address. WebRTC cannot be switched off in WebView (old reports only). Tor trademark policy: the Tor name is not used in a product or feature name without written permission.
- Owner approved the direction (per-Space user-agent string, best-effort script-level fingerprint reduction, Phase 11; built when the phase starts): Spaces that look like separate browsers to a site: separate cookies and storage are built in (decision 21); a per-Space user-agent string and script-level fingerprint reduction are possible (Phase 11); a different IP address per Space is not possible with the app-wide proxy setting.
- Excluded: data-saver proxy, voice search, cloud sync and backup, crypto wallet.
