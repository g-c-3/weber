# Decisions

Numbered, never dated. Dates live in TRACK.md.

## 1. Public repo, source and docs only
Rule: no secret of any kind in any file, comment, workflow or commit message. Secrets live only in repository secrets.

## 2. Privacy by default
Rule: no telemetry, analytics, crash reporting, accounts, cloud sync or automated inference. Mozilla components with telemetry code (Glean and others) are disabled or not included; each session that adds one checks it opens no network connection of its own.
Allowed network traffic: pages the user visits; the tap-only update check (sideload flavor); filter-list updates (23).
Any new connection is flagged first and needs approval, with its data stated.
Browsing data (history, bookmarks, cookies, passwords, downloads, tab state) stays on-device. Private tabs leave nothing on disk.

## 3. Engine (superseded by 26; the adapter rule lives on there)
Rule: GeckoView, release channel only. Exact versions pinned in `gradle/libs.versions.toml`; no dynamic versions. Version verified against Mozilla's documentation and Maven listing before any dependency line is written.
All engine calls go through one adapter file. Fewest possible Mozilla Android Components.
Each session checks for a newer stable GeckoView and reports it. A bump is its own task with its own CI check.

## 4. Versioning (D digit and two-APK parts superseded by 27; Z range by 37)
Rule: `X.Y.Z`, X unbounded, Y 0 to 9, Z 0 to 99. Start `0.0.1`. Set only in `version.properties`.
`versionCode = (X*10000 + Y*100 + Z)*10 + D`; D = 1 universal, 2 arm64, 0 Play bundle.
Compared number by number, never as text. Every shipped change bumps Z by one. Tag `vX.Y.Z` triggers the release.

## 5. Releases (two-APK part superseded by 27)
Rule: two signed APKs per release (arm64, universal). Release notes carry engine version, advisory link for engine updates, and each APK's SHA-256.
Releases are never deleted. A faulty release is marked pre-release; the previous good release stays downloadable.

## 6. Signing
Rule: one key for every release. Keystore and passwords only in repository secrets. The owner keeps a separate backup outside GitHub; losing it means no in-place updates.
Signing is its own phase (1).

## 7. Dependency verification
Rule: `gradle/verification-metadata.xml` is on. A checksum mismatch fails the build. Never disabled to get a build through.

## 8. Flavors
Rule: `sideload` (GitHub releases; update button; install permission) and `play` (neither). Updater code is never compiled into `play`.
Play shows one listing named Ardour. Play-signed and sideloaded builds carry different signatures and cannot update over each other; recorded again when Phase 10 starts.

## 9. Phase 0 scope
Rule: Phase 0 delivers docs, `version.properties` and `.gitignore` only.
Cause: Gradle files need plugin and wrapper versions, which must be verified first. They arrive in Phase 1.

## 10. Doc size
Rule: any of ROADMAP.md, TRACK.md, SESSIONS.md, DECISIONS.md passing about 60 KB is reported at "Go" with a proposed archive. Archive only on approval. Numbering never changes.

## 11. Licensing
Rule: each added library's license is recorded here when adopted. The app's own code is MPL 2.0 (17); no engine code is in the app after 26.
GeckoView (MPL 2.0) was in releases 0.0.4 and 0.0.5 and is removed (26).
`androidx.webkit`: Apache 2.0 per Android's library listing; confirm when adopted in Phase 2. Apache 2.0 is compatible with a public MPL 2.0 repo.

## 12. Build toolchain (verified in session 2)
Rule: Gradle 9.9.1 (wrapper, distribution checksum pinned), Android Gradle Plugin 9.5.1, JDK 17. Kotlin comes from AGP's built-in Kotlin; no separate Kotlin plugin.
Sources: Gradle version list (services.gradle.org/versions/9), AGP 9.5 release notes (AGP 9.5 needs Gradle 9.6.0 or newer, JDK 17), runner image readme (JDK 17 default, Android platform 36 and 37.0 installed).
Workflow actions pinned by commit SHA when `build-android.yml` is written. Latest tags seen: checkout v7.0.1, setup-java v6.2.0, gradle/actions v6.4.1, upload-artifact v7.0.2, softprops/action-gh-release v3.0.4.
SDK levels are provisional until Phase 2 verifies the engine's requirement.

## 13. Signing material never leaves secrets
Rule: no keystore, password or key in a workflow artifact, log, input or summary.
Cause: artifacts of a public repo can be downloaded by any signed-in GitHub user; workflow inputs and logs are public.

## 14. Workflow hygiene
Rule: every third-party action pinned by full commit SHA with its tag in a comment. Wrapper validated in CI. No caching action (its license and data handling are unverified); builds use the wrapper directly.
Workflows request `contents: read` only.

## 15. Release key made outside GitHub (exception to 6 and 13)
Rule: one RSA 4096 key, alias `ardour`, valid 100 years, generated once outside GitHub and delivered to the owner as files. The owner loaded it into repository secrets (`KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`). The owner keeps the keystore file and its password as the backup outside GitHub.
Cause: owner decision; the key could not be generated on a phone.
Risk: the key and password passed through a chat transcript. Rotate before the Play listing if that is a concern; nothing signed is in use beyond the owner's device.
Certificate SHA-256 (public): `0d11cb47bebabb3197d38fed88e6c2f3c5cc5863e3931cdb6a4e976eda591c03`. The release job fails if an APK carries another certificate.
Standing for 6 and 13: keystore and passwords never in the repo, workflow artifacts, logs or inputs.

## 16. Automatic release
Rule: a push to `main` whose `version.properties` holds a version with no tag `vX.Y.Z` builds, signs and publishes that release. The release creates the tag on the pushed commit. This replaces "a tag triggers the release" in 4 and 5.
Release job: only job with `contents: write` and with secrets; keystore file removed after the build; APK names `Ardour-X.Y.Z-arm64.apk` and `Ardour-X.Y.Z-universal.apk`; notes carry engine, certificate SHA-256 and each APK's SHA-256. No third-party release action; GitHub's own `gh` command.
A code change without a version bump releases nothing.
Cause of the first defect: the tag was taken from the branch name. Fixed: `v<version>`. The mistaken release and tag `main` were removed; an exception to "never deleted", since nobody had installed it.

## 17. App license
Rule: the app's own code is MPL 2.0 (matches Gecko). `LICENSE` at the repo root, text copied from Mozilla's geckodriver repository. No per-file headers.
GeckoView is MPL 2.0; recorded under 11 when adopted in Phase 2.

## 18. Application id and SDK levels
Rule: application id `io.github.gc3.ardour`. The source package and `namespace` stay `io.github.g_c_3.ardour` (internal only).
Release 0.0.1 carries the old id `io.github.g_c_3.ardour`; it cannot update to 0.0.2 and is superseded. It stays downloadable.
minSdk is 29 from 28. SDK levels: compileSdk follows what `androidx.webkit` needs, checked in Phase 2.
The id cannot change after a Play listing exists.

## 19. Launcher icon
Rule: adaptive icon `mipmap-anydpi/ic_launcher.xml`. Foreground `drawable-nodpi/ic_launcher_foreground.png` (432 px, transparent, the flame "A" only, no wordmark). Background color `#242630`. Referenced by `android:icon`. No legacy raster icons (minSdk 26); no monochrome layer yet.
Source: logo supplied by the owner, made with an external image tool; the owner keeps the original file.
Usage rights and the trademark check are settled before the Play listing (Phase 10). The 512 px store icon is made then from the same logo.
Shipped as 0.0.3.

## 20. Product direction
Rule: Ardour is a private, light, customisable browser built around Spaces (21). Settings open with basic options only; an Advanced section holds the full range. Fast and smooth is a target of every phase, checked on device.
Advanced options are curated: none weakens the engine, bypasses certificate checks or allows cleartext traffic. A raw engine preference editor is not included unless approved.
Features are planned in ROADMAP.md; each phase is approved when it starts (25).

## 21. Spaces
Rule: a Space is an isolated browsing area: own cookies, cache, site storage, saved logins, history, private tabs. The term "Space" is used, not "account", to avoid a clash with 2.
Six built-in Spaces. Space 1 is the default and can be neither deleted nor hidden. Built-in Spaces can be renamed and given a colour and icon. Up to ten in total: Spaces 7 to 10 are added by the user and can be deleted.
Switching Space, adding or deleting a Space, and the setting "open with default or last-used Space" need device authentication (biometric or phone password). Deleting erases the Space's data after a confirmation.
Clearing cache and site data per Space keeps saved logins; a separate action clears logins.
Spaces are stored as a list with ids, never as fixed numbers. A Space holds no data until first opened.
Engine: one WebView profile per Space, bound before any other call on that WebView (26); the API form is verified against the documentation in Phase 2.

## 22. Space lock and saved logins
Rule: the lock is an app-level lock, not file encryption; Settings says so. Saved logins are encrypted with a key in Android's secure key store, one per Space.
Authentication uses `androidx.biometric` (Apache 2.0) or the platform equivalent; the license is recorded under 11 when adopted.
Shortcuts to a Space still require authentication. The panic shortcut wipes and locks without a prompt.

## 23. Second approved connection: filter-list updates
Rule: ad-filter lists are built into each release, so blocking works with no connection. Updates: a tap-only button, and a scheduled background update at most once a day, on Wi-Fi only, with a Settings switch that turns it off completely.
Lists come only from the GitHub release host used by the update check, built and checksummed by CI. The app verifies SHA-256 before use.
Data: the host sees the device's internet address and the request time, at regular intervals. Nothing else is sent: no token, no identifier, no page visited.
Amends 2. Phase 8 verifies the license of the lists against MPL 2.0 (11, 17) before building; a blocker returns here for approval. Blocking is built by Ardour on WebView (31).

## 24. Engine target (superseded by 26; GeckoView 159.0.20261102144711 was pinned in 0.0.4 and 0.0.5)
Rule: Phase 2 pins GeckoView 159.0.x (stable). 160.0 is beta; the earlier candidate `160.0.20261104181552` is dropped.
Candidates and fallback: ROADMAP.md, Phase 2. CI resolution confirms the pin.
Evidence: Firefox for Android release notes (stable 159.0.1; 160.0 in beta), Maven mirror listing, a third-party release naming a 159.0 build. Mozilla's Maven not read directly (unreachable from the sandbox).

## 25. Feature plan and approval
Rule: two-step approval. Only Phase 2 is approved; other features are planned in ROADMAP.md and approved per phase.
Build order: 2, 3, 4, 12, 5, 6, 7, 8, 11, 13, 9, 10. First public release after Phase 8.
Not included: data-saver proxy, voice search, cloud sync and backup, crypto wallet.
Needs its own approval: forced dark mode on every page, page translation, web push, add-on store, cookie-banner lists, encrypted DNS, search suggestions, safe-browsing lookups.
Similar Gecko-based browsers exist. No code is copied from them; one is AGPL-3.0, which conflicts with 17.

## 26. Engine: system WebView
Rule: Ardour is built on Android System WebView through `androidx.webkit`. GeckoView is dropped. Supersedes 3 and 24. Owner decision.
Cause: GeckoView's floor is about 91 MB (arm64, libraries compressed, release 0.0.5; its main library alone is about 65 MB compressed). WebView keeps the app to a few MB.
Kept from 3: every engine call goes through one adapter file; exact pins in `gradle/libs.versions.toml`, no dynamic versions; each API and version checked against the documentation before use; each session reports the newest stable `androidx.webkit`.
Added: stable APIs only (experimental APIs may disappear after one library release). Security posture is the phone's WebView version; Settings shows it.
Accepted costs: the engine and its updates are Google's; behaviour varies by phone and WebView version; features Gecko had natively are built by Ardour or reduced (31).
Removed in Phase 2: the GeckoView dependency, Mozilla's Maven repository, the Kotlin 2.4.20 classpath override, compileSdk 37.2 and compressed-library packaging, all added for GeckoView; the build decides what `androidx.webkit` needs.
Releases 0.0.4 and 0.0.5 carry GeckoView and stay downloadable. The first WebView release installs over them (same id and key, higher versionCode).
Evidence: `androidx.webkit` release notes and API reference (profiles since 1.9.0; need multi-process mode); sizes measured from the 0.0.4 and 0.0.5 APKs.

## 27. One APK
Rule: one signed APK per release, `Ardour-X.Y.Z.apk`, for every phone. Kotlin and Java only: no native code, so no ABI splits. Native code needs approval and returns here.
`versionCode = (X*10000 + Y*100 + Z)*10 + D`; D = 1 sideload APK, 0 Play bundle; 2 retired (was arm64). The code only increases: 0.0.5 arm64 was 52, 0.0.6 gives 61.
Release notes carry the WebView requirement (28), certificate SHA-256 and the APK's SHA-256. The updater downloads that one APK; no device matching.
Supersedes the two-APK parts of 4, 5 and 16.

## 28. Minimum phone and WebView gate
Rule: minSdk 29 (Android 10); amends 18. At every launch the app reads the WebView package version and checks the features it needs (profiles). If unmet it shows a full-screen message to update Android System WebView and loads nothing. No code for old engines.
Minimum WebView version: set in Phase 2 from the documentation of the features Ardour needs; recorded here then.
Cause: Chrome 139 reportedly requires Android 10 or newer, so WebView on Android 8 and 9 stays on an old version. Source: news reports of Google's announcement, which conflict in detail; not read directly. Confirm in Phase 2.

## 29. Privacy under WebView
Rule: Ardour's own code sends nothing (2 holds). Manifest opt-outs, both documented by Android: Safe Browsing off (`android.webkit.WebView.EnableSafeBrowsing` false) and WebView diagnostic reporting off (`android.webkit.WebView.MetricsOptOut` true). A Safe Browsing switch in Settings is a new connection and needs approval (25).
Limit: the WebView component is Google's. Its own traffic (updates, component data) is outside Ardour's control and not audited. The listing and Settings never claim otherwise.
Private tabs: a temporary profile, deleted when the tab closes and at next launch. Wording is "removed on close", not "never written to disk".
Encrypted DNS: only the phone's Private DNS setting.
Amends 2: its Mozilla-components clause no longer applies.

## 30. UI toolkit and quality bar
Rule: plain Android views, custom drawn, with spring-based animation. No Jetpack Compose, no Mozilla components. Premium look and motion are the goal; size and speed are measured, never only claimed.
Size target about 5 MB, confirmed by the first WebView build. Every added dependency states its size cost; each session entry reports the APK size. Start-up and frame-rate targets are set after the first on-device measurement (ROADMAP, Quality targets). Public claims state measured results only.
Amends 20: "light" means small and lean in fact.

## 31. Features under WebView
Rule: planned features (ROADMAP) stay, built on WebView.
Native or simple: tabs, Spaces, find in page, desktop site, text size, print, pull-to-refresh, fullscreen video and picture-in-picture, permissions, popup blocking, history, bookmarks, downloads, screenshot block, panic wipe.
Built by Ardour: content blocking (request blocking; page-element hiding by script injected before page scripts), reader mode, saved logins, private tabs, resumable downloads, privacy report, tab sleep, data saver.
Reduced: fingerprint protection (script level, best effort, off by default); Do Not Track and Global Privacy Control (main-frame headers and a script flag, not a header on every request); private tabs (29); passkeys (verify WebView support before promising).
Not used: rewriting requests (needs Ardour's own fetch; breaks caching and cookies).
Amends 23.

## 32. Adoption of `androidx.webkit` 1.18.0 (Phase 2)
Rule: pinned exactly in `gradle/libs.versions.toml`. Newest stable per developer.android.com release notes (1.18.0, 21 October 2026); no stable above it seen. License: Apache 2.0 per Maven metadata; confirms 11. The library brings androidx annotation and core; size cost is read from the first build.
compileSdk 36 is provisional; the build shows whether the library needs more.
Launch gate (amends 28): no minimum WebView version number is claimed. The gate is the run-time check that a WebView package exists and `WebViewFeature.MULTI_PROFILE` is supported.
Phase 2 binds the Default profile. Named Space profiles wait for Phase 12, after the profile-name rules are verified.
Code shrinking (R8) is not enabled until the first size measurement.
WebView settings verified: mixed content defaults to never-allow for apps targeting API 21 and higher (platform source); file-URL access defaults false since API 16, universal access deprecated from API 30 (developer.android.com, unsafe file inclusion). All set explicitly.

## 33. Repository name
Rule: the repository is `g-c-3/weber`. The update check (Phase 3) uses `https://api.github.com/repos/g-c-3/weber/releases/latest`. The old name redirects only while no new repository takes it; none is created. The Gradle project name stays `Gecko` (internal, never shown).

## 34. Gate screen links to WebView in the Play Store
Rule: when the launch gate (28) is unmet, the screen shows what to do and one button that opens `https://play.google.com/store/apps/details?id=com.google.android.webview`. On Android 10 and higher WebView is the separate Android System WebView app, and Chrome can no longer provide it (Chromium WebView documentation).
The button is an intent on the user's tap. Ardour opens no connection and sends nothing; the Play Store or browser makes the request. Not a third app-originated connection (IN-APP UPDATE), recorded here so it is not mistaken for one.
Phones without Play Store updates get the text only: update through the phone maker's store or system update.

## 35. Quality targets set; Phase 3 approved
Targets (measured baselines in ROADMAP, Quality targets): cold start to first page 0.50 s or less (mean of 3) and never above 0.60 s in any phase without a decision entry; no white flash, Ardour's own themed window before the page; median scroll gap 17 ms or less, no mid-flick gap over 34 ms in 10 flicks, never worse than a reference browser on the same page and phone; APK 5 MB or less through Phase 8.
Phase 3 is built in two steps: flavors and Settings first, the updater second. The Settings button over the page is temporary until the browser menu (Phase 4); start-up and scroll are re-measured with it.

## 36. In-app updater (Phase 3 step 2, sideload only)
Rule: one GET to the latest release (33), then the APK `Ardour-X.Y.Z.apk` from `https://github.com/g-c-3/weber/releases/download/` only, on the button tap. Fixed `User-Agent: Ardour`; the platform default from memory carries device details.
SHA-256 is read from the release notes and checked before handoff. Notes and APK share a release, so this proves the download is intact, not who made it; the Android same-signature rule on update is the authenticity check.
Install: `PackageInstaller` session, the user confirms in Android; no new library, no file provider. `REQUEST_INSTALL_PACKAGES` is declared in the sideload manifest only. The status receiver is a mutable `PendingIntent` (platform requirement from target SDK 35).
Open: Android's developer-verification rules for sideloaded apps (2026) may add prompts; not verified.

## 37. Version scheme amended: Y and Z each 0 to 9
Amends 4. `X.Y.Z`: X unbounded, Y and Z each 0 to 9. `0.0.9` is followed by `0.1.0`, `0.9.9` by `1.0.0`, `9.9.9` by `10.0.0`. Z was 0 to 99; releases up to 0.0.9 fit both rules, no number is skipped or reused.
`versionCode` formula unchanged (monotonic with the narrower range). The build rejects a PATCH above 9.

## 38. Polish rule
Rule: every screen and control looks finished and stylish, however small: gates, error states, placeholder buttons, status text. A plain system default is a defect, not a placeholder.
Mechanics (extends 30): shared helpers in `Ui.kt`; palette ink `#242630`, ember `#E8451D`, paper `#F6F5F3` (from the launcher icon); cards, pill buttons, thin progress and back button drawn by Ardour; spring motion (damping ratio 0.62) for press and entrance, which follows the system animation scale. Text meets contrast on its surface. Light only until theming (Phase 9).
Check: each device-visible change is judged on a screenshot against this rule before it is called done.

## 39. Version is the build run number
Amends 4, 16, 37. Rule: with N the run number of `build-android`, `X.Y.Z` is N's digits: X = N div 100, Y = tens digit, Z = ones digit. 87 is 0.8.7; 100 is 1.0.0; 1016 is 10.1.6.
Derived in CI and passed to Gradle as `-PardourVersion`; `version.properties` is removed. Outside CI the build reads 0.0.0. `versionCode` formula unchanged.
Release: a successful run on `main` publishes `vX.Y.Z` unless that tag exists (a re-run keeps its number). Pushes touching only `docs/` or `*.md` do not run, so use no number.
Numbers are used by every run, including failed, pull request and manual runs, so released versions can skip numbers. A skipped number is not an error. Releases 0.0.1 to 0.1.0 stay; every run-number release is higher.
One upload commit per session: each commit touching code runs a build and releases. Phase 5 pull requests no longer bump a version.

## 40. Light and gradient, with restraint
Extends 38. Light effects are used only where they carry meaning, never as decoration: none on cards or body text.
Ember glow (tinted shadow) and a faint ember rim mark the primary action. A slow pulse marks the one element that needs attention (an update waiting to be installed); at most one pulsing element per screen. A sweeping light on the progress bar means something is being checked; a gradient bar with a glowing leading edge shows download progress.
Effects follow the system animation scale: with animations off they are static. Tinted shadows use platform shadow colors (API 28); how they render on every phone is unverified.

## 41. Direct push access
Amends the manual-commit rule. Rule: the assistant pushes each task's files to `main` with a GitHub token the owner pastes into the conversation.
Token: fine-grained, this repository only; Contents read and write, Actions read, Workflows read and write while workflow files change; short expiry. It is used only in sandbox calls to github.com and api.github.com. It never appears in the repo, a file, a commit message, a note or a reply. The owner revokes or regenerates it after the session, because a pasted token stays in the conversation.
Pushes: `main` only, one commit per task (one build, one release), complete files, never a force push or history rewrite, no other repository, no settings, secrets, tags or releases. Ask first before deleting files or changing workflows, signing, manifest permissions or dependencies. Each push is checked against the repo, then the run result is read through the API; a red run is fixed in the next commit.
Limit: run logs download from `results-receiver.actions.githubusercontent.com`, which the sandbox network blocks. The API still gives the failing step. Error text needs that host allowed in the sandbox network settings, or a workflow step that writes build errors as annotations (needs approval).

## 42. Build errors as annotations
Rule: the `build` job writes Gradle output to `gradle.log`; on failure `.github/ci/annotate-errors.sh` writes compiler errors (up to 10), the "What went wrong" block, or the last 15 log lines as workflow annotations. The check-runs annotations API returns them to the token (decision 41), so the log download host is not needed.
Scope: the debug build only. The `release` job is not annotated: it handles signing material (decision 13).
Checked: script run on sample logs; annotations endpoint answers 200 with the token and 403 without. Not checked: annotations from a real failed run.
`version.properties` is deleted; nothing has read it since decision 39.

## 43. Phase 4 started; step 1 scope
Phase 4 approved by the owner ("your call") after Phase 3 step 2. Step 1 (0.9.5): address bar, back, forward, reload and stop, progress line, a native start screen in place of the placeholder page.
Typed text: web address if it has a host-like form or an http(s) scheme, otherwise a search. Default search is DuckDuckGo, an ordinary page the user visits, no connection from Ardour itself; the owner can change it.
Only http and https load in the view; other schemes are dropped until Ardour handles them itself.
Back: target SDK 36 no longer calls `onBackPressed` (Android 13+), so a back callback is registered only while there is history or the address field is being edited. Below API 33 the old override is used. Not yet run on a device.

## 44. Release build annotated too
Amends 42: the `release` job writes its Gradle output to `gradle-release.log` and annotates failures the same way. Lines that mention signing material are filtered out of every annotation.
Reason: run 94 failed in the release build and the annotation had nothing to say because that job was excluded.

## 45. DOM storage on
`domStorageEnabled` is set in the adapter. It is off by default in WebView and many sites do not finish loading without it (0.9.5 showed cricinfo without images and with times in UTC). The data is site storage inside the profile, on the device, removed with the profile.
Not changed: user-agent string (it still contains the WebView marker; no page-request rewriting, decision 31), third-party cookies (WebView default, off).

## 46. Own error screen
Main-frame load failures show an Ardour screen over the view instead of the engine's page: offline, certificate, unencrypted (http) and other. "Try again" reloads the failed address; it is not offered for http. Certificate errors are cancelled, never bypassed (Security rule). Sub-frame and image errors are ignored.
Not yet seen on a device.

## 47. Error kinds by cause
Amends 46. The engine's error text (`net::ERR_...`) is used to tell causes apart: name not found ("This site can't be found", with "Search instead" using the host as the query), no internet, certificate, http, and a neutral message when the cause is unknown. A missing site is no longer reported as a connection problem.
No network-state permission is added; the engine's own text decides. Not run on a device for each cause.

## 48. Network-state permission
Amends 47, whose note that no permission was needed was wrong: with airplane mode on, 0.9.8 still said "This site can't be found".
`ACCESS_NETWORK_STATE` is added (owner approved). Normal install-time permission, no prompt; it reads whether a validated network exists and sends nothing. A name-lookup or connection failure with no validated network is shown as "No internet connection". Not a web-page permission, so the Security rule on page grants is unaffected.

## 49. Browser menu and desktop site
Rule: the toolbar's last button opens a menu card (share, desktop site, Settings), drawn by Ardour (`MenuPopup.kt`). Share hands the page address to the system share sheet; Ardour sends nothing.
Desktop site is a user switch: the phone's own user-agent string with the Android token and "Mobile" removed, plus a wide viewport, then a reload. Off restores the default string. A WebView setting, not request rewriting (31); the string is sent with the page's requests, as any user-agent is. The switch loads the address afresh (initial scale reset) instead of reloading, so the page fits the width. Kept until the app process ends; a saved default waits for Settings and Spaces.
Verified: `setUserAgentString` and `setUseWideViewPort` exist (developer.android.com API diff). Not verified from a primary page: empty string restores the default. Not run on a device.

## 50. No Tor or Quad9 in the app; Private DNS shortcut
Rule: Ardour does not bundle or integrate Tor, Orbot or a named DNS provider (owner decision). Settings has a Private DNS card that explains the phone's setting and opens Android's network settings on tap. Ardour changes no setting, adds no connection and names no provider.
Cause: bundling Tor is native code (27) and a size cost; a proxy setting is app-wide, so it cannot differ per Space; WebRTC cannot be switched off in WebView, so no IP-protection promise could be kept.
Verified: the Settings app handles `android.settings.WIRELESS_SETTINGS` (platform source). No public intent straight to Private DNS found; the screen is reached by hand, the path differs by phone. Not run on a device.

## 51. Zoom and pull-to-refresh options
Rule: pinch zoom is on by default. Three options in Settings, all off by default and kept on the device (`Prefs.kt`, private storage): pull to refresh, force zoom, wrap text when zoomed.
Pull to refresh: Ardour's own touch handling and indicator (`PullRefresh.kt`), no library; starts only when the page is at its top. Reload only; sends nothing new.
Force zoom: a script run at document start (`addDocumentStartJavaScript`, stable API, feature-checked, origin rule `*`) rewrites the viewport meta tag to allow zoom. A page-content change, not a request rewrite (31). Applies to pages opened after the switch.
Wrap text when zoomed: pinch changes the text size (`setTextZoom`, 50 to 300 percent) so text re-wraps to the screen; page scaling and force zoom are off while it is on. Images do not scale.
Force zoom also runs the script on the open page and after each page load (1.0.5), as a second path next to the document-start script.
`setForceUserScalable` exists in platform source but is hidden, so it is not used.
Verified: `addDocumentStartJavaScript` signature and feature flag (developer.android.com); viewport `user-scalable` behaviour (developer.android.com). Not verified: `*` as an origin rule, `setBuiltInZoomControls` text (from memory and forum answers), `setTextZoom` semantics. Not run on a device.

## 52. Print or save as PDF; reset text size
Rule: the menu's Print or save as PDF hands the open page to Android's print screen (`createPrintDocumentAdapter`, `PrintManager`); printers or Save as PDF are the system's. No permission, no new connection. Reset text size shows only while Wrap text is on and the text size differs from 100 percent.
Verified: print flow and WebView limits on developer.android.com (one print job per WebView at a time; no page-range selection). Run on a device: print passed (owner screenshots, 1.0.6).

## 53. Re-run rule for failed builds (owner decision)
Rule: the push token also has Actions write. A red run whose cause is a library download or other network failure, not code and not a checksum mismatch, is re-run once through the API (failed jobs only). A re-run keeps its run number and version. If it fails again, the next step is a normal fix in a new commit. Runs are never cancelled and no other workflow is started by the assistant. Pages access is granted but unused.
Cause: run 106 failed in the release job on a library download while the compile job had resolved the same library; the owner re-ran it by hand and it released.
Also: a failed job's error summary is read from its check-run annotations when the log host is blocked.

## 54. Links from other apps and the default-browser role
Rule: `MainActivity` is `singleTask` and has a VIEW filter (DEFAULT, BROWSABLE, schemes http and https). A link opens only when its scheme is http or https and it has a host; anything else is ignored. Opening from the recents list does not reload the link. Settings has a Links card: where Android offers the browser role, a button asks the system to make Ardour the default (Android shows its own confirmation); Ardour changes nothing itself.
No new permission, no new connection: an opened link loads like a typed address. The Space choice for opened links waits for Phase 12.
Where the role dialog is not offered, the card shows an Open default apps button (`ACTION_MANAGE_DEFAULT_APPS_SETTINGS`, developer.android.com) and redraws when the owner returns (1.0.8).
Verified: `RoleManager` browser role, `isRoleAvailable`, `isRoleHeld`, `createRequestRoleIntent` and `startActivityForResult` (developer.android.com, API 29). Not verified from a primary page: the exact manifest requirements for the browser role (the reference points to the AndroidX Role library). Not run on a device.

## 55. Reader mode
Rule: menu item "Reader mode" shows the open page's main text on an Ardour-drawn screen (`Reader.kt`): serif text, headings, quotes, lists, code; no images, links or page scripts. Themes: twilight (default, warm dusk, cream text, amber accent) and paper; text size in seven steps; both remembered in app storage. Back and the close button return to the page. A page with too little text shows "No article here".
How: one script run on the open page through `evaluateJavascript`, kept in `EngineAdapter.kt`. It scores paragraphs by container, skips navigation, side, footer, hidden and share/comment/menu blocks, and returns JSON (title, byline, site, blocks). The text is parsed natively and set as plain text, never as markup; block count and length are capped. A page can change what the reader shows (it controls its own text) but cannot add views, scripts or links to it.
No new connection, library or permission. Status and navigation bar icons follow the theme.
Verified: the script on four sample pages in a test DOM (article page, plain blog, app page, nested lists). `evaluateJavascript` callback runs on the UI thread (platform reference). Not verified from a primary page: the result of a string-returning script arrives as a quoted JSON string (parsed defensively; unreadable results show the no-article screen). Not run on a device.
Limits: text only; the heading of a list item that holds a nested list is dropped; tables and figures are skipped.
Update after first device check (1.0.9): headings used a synthetic bold on a medium face, which drew wider than it measured and clipped at the right edge; they now use weight 700 directly. A page counts as an article only with at least 200 characters of running paragraphs or quotes, so front pages of headlines show "No article here". That screen sits outside the scroll view.
Update after second device check (1.1.0): article text was still clipped at the right edge. Side padding was applied after the text was laid out, and the old line breaks stayed. Padding is now set before any text is built (1.1.1).
Update (1.1.2): reader text is justified by default with automatic hyphenation; a top-bar button switches to a ragged right edge, remembered in app storage. The reader shows what the page has loaded: a page that loads the rest of an article while scrolling can give a short result.
Device check (1.1.2): with the page scrolled to its end first, the reader shows the full article, justified, without advertisements; an unscrolled page gave only the first paragraphs, so lazily loaded text is confirmed as the cause. Known gaps: no author or date line on sites that keep them outside the article text; one paragraph showed tight word spacing, cause unknown.
Rule: the reader shows only the text the page itself delivers. A page that cuts an article off behind a registration or subscription wall gives a short result; the reader does not look for or reveal text the site withholds. Seen on a second visit to the same article (1.1.2).
Update (1.1.3): the reader opens at once over the whole window with a "Preparing the page" screen; behind it the page is scrolled to its end (up to four rounds, 450 ms apart, while it keeps growing) so text loaded on scroll is present, then put back where it was. This causes only the page's own loads, the same as scrolling it. Author and date come from the page's structured data, then from standard meta tags; they show as one line under the title. A page that withholds text behind a wall is unchanged.
## 56. Top row replaces the bottom bar
Rule: one top row: the address pill on the left half, back, forward, reload/stop and menu on the right half. No bottom bar. Editing the address widens the pill left to right over the buttons (buttons fade out), puts the cursor right after the site name (before the dot that leads to .com, .org or .co.uk), and shows a semi-transparent floating bar under it with Copy, Paste, Clear and Go. The pill has a slight ember glow, stronger while editing. The menu card opens below its button.
Reader: the extracted text is kept for the same page load; opening the reader again without a reload shows it at once. The scroll-ahead before reading is at most three rounds of 300 ms and stops when the page grows by less than 8%.
Copy and Paste use the phone's clipboard only on the user's tap; nothing is read in the background. Android may show its own "pasted from clipboard" notice.
Open question: gsmarena pages show content but keep loading for a long time. Cause not found; likely the page's own ads and trackers (no content blocking before Phase 8) or third-party requests; not verified.
Update (1.1.5): on a Times of India article the reader showed only a "What Readers Are Saying" box: the article text sits loose in a container with no paragraph tags. The finder now also scores text placed directly in an element and reads such text as paragraphs (a double line break starts a new one); advert-named blocks and "Advertisement" lines are skipped. Tested on seven sample pages, not yet on that site.
Update to the open question: the same gsmarena pages show in Chrome on the same phone within a few seconds (owner recording), so the slow loading is specific to Ardour or the system web view. To test: Desktop site switched on (user agent), and Private DNS set to a public resolver (system name lookup).
## 57. Third-party cookies switch; reader finder ladder; menu motion
Rule: Settings > Browsing has "Third-party cookies" (default off, as the engine's own default for apps like this). It calls the platform cookie setting for the open view (documented, API 21) and applies to pages opened next. Reason: an experiment for the slow "page finished" signal on ad-heavy sites (see below); the choice stays with the user.
Reader finder (1.1.6): strict pass; if it finds under 300 characters of running text, a second pass ignores container names such as "ad" or "menu"; if that is still short, the page is scrolled down in screen-sized steps (up to 24, 160 ms apart) and the blocks seen at each step are merged, then the page is put back. Lines such as "End of Article" or "What Readers Are Saying" end the article. The reader needs 300 characters of paragraphs or quotes. Tested on nine sample pages and a merge test, not yet on the Times of India article that failed in 1.1.5 (cause there unknown: container name, or text kept only near the screen).
Menu: it opens with a quick fade and spring settle, and closes with a fade and shrink; a touch outside it, or on its clear margin, closes it.
Loading timings from owner screenshots (1.1.5): Times of India article still loading 7:10:59 to 7:11:17, loaded by 7:11:26 (at least 18 s after the first screenshot); gsmarena with Desktop site on loaded between 7:12:18 and 7:12:53 (more than 35 s). Desktop site did not help, so the identity string is unlikely to be the cause. Chrome loads gsmarena in seconds on the same phone.
## 58. Reader report; loading timings with cookies on and off
Device check of 1.1.6 (owner screenshots): the Times of India reader showed only a collapsed "About the Author" box. Cause: that box passed the 300-character minimum, so the fallback never ran. 1.1.7: both finder passes always run and the lenient one is used only when it finds clearly more text (over 1.6 times plus 100 characters); the step-by-step scroll runs when the result is under 600 characters on a tall page (over three screens).
Diagnostic: a long press on the reader's title (also on "No article here") copies a report to the clipboard: page structure of the likeliest containers (tags, ids, first class names), why large text blocks were skipped, requests slower than 3 s (host and start of the path, no query), page height. No page text, no network use; the report leaves the app only if the user pastes it.
Third-party cookies did not change the loading time: gsmarena about 35 to 40 s with cookies off (loading 1:24:03, loaded 1:24:42) and with cookies on (1:35:24 to about 1:35:59); Times of India with cookies on loaded between 1:20:26 and 1:21:50. Desktop site did not help either. Chrome shows the same pages in seconds. Open: which requests keep the page "loading"; the report's slow-request list is meant to answer it.
## 59. Third-party cookies on by default; page settles; reader paragraphs
Owner decision: Settings > Browsing > Third-party cookies is on by default (it was off in 1.1.6 and 1.1.7). Data stated: embedded content such as ads and sign-in frames can set cookies, which lets those sites recognise the user across the pages that embed them. Ardour adds no connection and keeps the cookies on the device; this is a weaker setting than the engine's own default for apps like this (amends the privacy default in 2 for this one switch). The owner reports pages load acceptably with it on, though not always at the same speed. My timings read from screenshots did not show a clear difference (about 35 to 40 s on gsmarena both ways); the cause of the slow "finished" signal is still not found. A user who switched it off keeps it off.
Settle rule: when the engine reports 90% or more and no finish follows within 2.5 s, the address row treats the page as ready (progress bar hidden, stop button back to reload). Loading that continues is not stopped. This changes what the interface shows, not how fast the page loads.
Reader (1.1.9): any element that is not a plain inline one (links, bold, spans and similar) now ends a paragraph, so a page that wraps each paragraph in an unusual element no longer comes out as one block of text (seen on a Times of India Plus article). The reader is a rule-based text finder: it works on many articles and can fail on pages built unusually; the long-press report exists to show why. Owner asked for it to be fixed or removed; kept for now, pending the next device check.
Speed: no public claim of speed is made until measured on a device (start-up, frame rate, page loads); none of these has been measured in this phase.
Version 1.1.8 was never released (its run failed to compile); skipped numbers are expected.
## 60. Instant navigation feedback; third-party cookies off by default again
Device check of 1.1.9 (owner recordings): after Go, the address bar fell back to "Search or enter address" with grey buttons for about 1.5 s (duckduckgo) and about 4 s (gsmarena) until the engine reported the page started. Now the address shows the target at once, the stop button appears and the bar sweeps (indeterminate) until real progress arrives. The same happens for tapped links (main-frame navigations the page starts), links from other apps and the error screen's retry. The 2.5 s settle rule worked on the owner's device (owner report).
Owner decision, reverses 59 for the default: Third-party cookies are off by default again; the owner saw no difference in loading with them on or off. The switch stays in Settings; a user who set it on keeps it on. Privacy default in 2 stands.
Reader: the owner asked for a different reader or removal. Candidate: Mozilla Readability 0.6.0 (the extraction Firefox Reader View uses), Apache-2.0 (compatible with MPL 2.0 code in a public repo; licence text must travel with it), run inside the page view on a copy of the page, no network, about 32 KB minified and about 11 KB in the APK. It is a JS asset, not an Android component. Needs owner approval before adoption (dependency rule). Until then the current rule-based finder stays.
## 61. Compact top row; Settings trimmed; update button beside the version
Owner requests after 1.2.0 (owner annotated screenshots): the address row sits as high as possible with no empty band under it, and is neater. Top row now: 2 dp above, pill and buttons 40 dp high, the progress line runs along the bottom edge of the row (no height of its own), icons slightly smaller. Total height about 48 dp instead of about 74 dp.
Settings: the Links label, the Default browser title and text, the Private DNS card (supersedes 50) and the Updates card are removed. The Make Ardour the default button stays alone in its card (hidden once Ardour holds the role; amends 54). The update flow (decision 33, 36) moves next to the Ardour version as one button: Update Check, Checking, Up to date, Download X.Y.Z, Installing X.Y.Z (or Try again later, Check failed, Allow installs, Checksum failed, Update failed). The line between the Ardour and WebView version rows turns into the amber glowing download bar while a download runs. Same connections as before; nothing new is sent. The play build has no button.
Owner reports the 1.2.0 navigation feedback and the 2.5 s settle both work.
## 62. Mozilla Readability as the main reader finder
Owner approved (after the rule-based finder failed on several Times of India pages). Library: Mozilla Readability 0.6.0, Apache License 2.0 (compatible with the MPL 2.0 code in a public repo and a sideloaded release); copyright notice kept in the file header and the full licence text shipped in the app as `assets/Readability.LICENSE.txt`. It is a JavaScript asset (`assets/Readability.min.js`, 33,261 bytes minified with terser from the published source), not an Android component and not an engine. It is handed to the page view only while reading and removed afterwards; it runs on a copy of the page, uses no network and opens no connection of its own.
Size cost, measured: the APK grew from 3,192,944 bytes (1.2.1) to 3,211,987 bytes (1.2.2), 19,043 bytes, including the licence text and the code change.
How it is used: its cleaned article is turned into blocks by the existing block code and wins when it finds at least 300 characters of running text and at least half of what the built-in finder found; otherwise the built-in finder (decisions 55, 57, 58, 59) is used. While a page is scrolled in steps the built-in finder runs alone. The long-press report now says whether Readability was used. Title, author and date fall back to Readability's when the page's structured data has none.
Tested with the real library on ten sample pages in a test DOM (Readability took 6 of them, the built-in finder the rest); not yet on the Times of India pages.
Top row (1.2.2): pill 38 dp, row 42 dp, 2 dp above; the remaining gap above is Android's status bar, which the app does not draw into.

## 63. Progress bar stays until the page finishes
Owner request: hiding the bar after 2.5 s while the page is still loading is untrue. The settle rule of 59 is removed. The bar shows the engine's progress and stays, with the stop button, until the engine reports the load finished or the user stops it. There is no timer. On ad-heavy sites this means the bar can sit near full for a long time; that is the real state. 59's cookie default is unchanged (see 60).

## 64. Browser-like page layout
Cause of pages cut off in mobile mode (Speedometer on browserbench.org; other browsers show it fitted): the engine's defaults lay every page out at the screen width and ignore the page's viewport rules. Fix (1.2.3), from the Chromium WebView layout guide, "Configuring WebView like a browser": wide viewport on, overview mode on, text auto-sizing. Pages without a mobile viewport get a 980 px layout shown fitted to the screen; pages with one keep it. The Desktop site switch now changes only the user-agent string. Not run on a device: it changes how every page is laid out, so mobile sites, the reader and text size need a look.

## 65. Top row geometry
Owner screenshot (gsmarena, loading): the progress line ran under the address pill. Pill and buttons are now 34 dp in a 42 dp row, leaving a strip under them for the progress line, which is also drawn above the pill's shadow. Replaces the sizes in 62 (pill 38 dp).

## 66. Page counts as finished when its document is parsed and the engine is at 90%
Owner recordings (gsmarena): Chrome shows the page finished within about 3 s while the ad slot is still blank; Ardour showed the same content at about 3 s but kept the bar and stop button until the ads arrived, because the engine's own finished signal waits for ads and tracking frames. Cause of Chrome's earlier signal not confirmed. Rule (1.2.4), amending 63: the bar still follows the engine's progress; once the engine reports 90% or more, the page's `document.readyState` is read every 300 ms, and when it is "interactive" or "complete" the bar goes and the stop button returns to reload. The engine's own finished signal still ends it at once when it comes first. No timer decides it: a page whose document is not parsed, or whose progress stays under 90%, keeps the bar. Images, ads and frames still loading carry on quietly. The read is a plain script run by the app on the open page; no object is exposed to pages and no connection is added. Not run on a device.

## 67. Page loading line
Owner request: the line was too thick; make it subtle and smooth. Now 2 dp (was 4 dp), a faint track, an ember-to-amber fill that glides to each reported value, a soft sheen travelling along the fill, and a small glow at the leading edge that fades as the load completes. A load that ends fills to the end and fades out. Before the first real progress a light sweeps the track. Motion only where it carries meaning (40): the sheen and glow show that loading is alive. With system animations off it jumps and does not loop. The downloads line keeps its own style.

## 68. Address actions
Owner request: Copy, Paste, Clear and Go looked plain. Now hand-drawn line icons beside each label in one glass capsule. They are state-aware: Copy, Clear and Go dim while the field is empty; Paste dims when the clipboard holds no text (only its type is read, never its content, so no clipboard notice appears); Go turns ember and pops when there is something to open; Copy shows a green check and "Copied" for a moment. The four rise in one after another, each tap gives a light tick, and the chips never take focus.

## 69. Address pill as one button
Owner request: a tap on the address bar dropped the cursor under the finger, then it jumped to the end of the site name. Cause: the tap focused the text field, which then placed its own cursor at the touch point. While the pill is idle it now takes the touch itself (with a small press response) and opens the editor with the cursor already after the site name. Once open, touches reach the text as usual. Not run on a device.

## 70. Loading line inside the address pill
Owner request: the line sits inside the pill, along its bottom straight edge, and fills from the start to the end of that edge; when the pill widens for editing, the line widens with it. The strip it used outside the pill is returned to the page (top row 36 dp instead of 42 dp). The ember rim and the line never show together: while a page loads, the editor has the plain rim and the line; when the load ends while the editor is open, the ember rim fades in over the plain one. Replaces the placement in 65 and 67; the line's look in 67 is unchanged. Not run on a device.

## 71. Go as an amber arrow
Owner request: no pill for Go. Go is now only an arrow. While there is text to open, the arrow's shaft slowly fills like a tiny progress line in ember-to-amber, the head lights when the fill arrives, then it fades and starts again (2.8 s). Without text it is a dim static arrow. Replaces the Go look in 68. With system animations off it shows the full amber arrow still.

## 72. Reader text size by pinch
Owner request: one A button replaces A- and A+. The text size is continuous (12 to 34 sp, original 18 sp) and set by pinching the article; a percentage pill shows while it moves. The A button is dim and inactive at the original size; once the size differs it turns ember and active, and a tap glides the text back to the original, shrinking or growing it in place with the reading position kept. The size is remembered (new setting; the old step setting is no longer read). Not run on a device.

## 73. Links that open other apps
Cause of the Amazon "Open" banner and Play Store links doing nothing: addresses that are not web pages were dropped. Now a tap behind a link (a user gesture on the main page, never a hidden frame) may open another app: market:, intent:, tel:, mailto: and other app schemes go to the system as a plain view request; the target component, selector, flags and any action other than view are removed; javascript:, file:, content:, data:, blob: and about: are never handed on. When no app takes the link, an intent: link's fallback address (https only) or the app's web store page loads instead; otherwise a short notice says no app can open it. A Play Store web address (play.google.com/store/apps) opens in the Play Store app when it is installed, else loads normally. An address handed to an app no longer starts the loading bar. No manifest change: apps are started without looking them up first. Not run on a device.

## 74. Desktop site sends matching client hints
Owner reports: mobile to desktop did not switch on some sites (gsmarena), desktop sometimes kept loading (google.com), desktop did not return to mobile on some (Gmail). Probable cause, not confirmed on a device: only the user-agent string changed, while the browser's client hints (mobile or not, platform) still said phone, and sites decide on those; and a cached copy of the page for the other kind of device could be shown. Fix (1.2.8), from the androidx.webkit user-agent metadata API (1.9.0 and later, feature checked first): the desktop switch also sets the hints to not mobile, Linux, no device model, and restores the original hints for mobile; the switch stops the running load and loads the address once without the cache, then the cache returns to normal when that page finishes.

## 75. Finish check reads only the new page; reload stops background loads
Cause of the refresh button not responding after the bar ended (and a slow navigation ending early): the finish check of 66 read the page still on screen, which is already complete, before the new page had been committed. Now the check starts only after the engine reports the new page is on screen (onPageCommitVisible, API 23) and progress is 90% or more. Reload now first stops anything still loading in the background, then reloads. Amends 66.

## 76. Desktop site layout and mobile addresses
Owner report (1.2.8): after the switch, Chrome and Opera fit the whole desktop page and Ardour needed pinching; sites with a mobile address (m.gsmarena.com) stayed mobile until the m. was removed by hand. Cause of the first: the engine always obeys a page's own viewport tag, which many sites set to the device width. Fix (1.2.9): while the switch is on, a start-of-page script keeps the viewport at 1024 px wide (replacing or adding the tag and watching it), so overview mode fits the page. When the switch goes on, a mobile address (m., mobile. or .m. in the host) is changed to the normal one before the load. Gmail's "use web version" choice is remembered by Gmail itself in its cookies; the switch cannot undo it (a per-site data clear would). Not run on a device.

## 77. Line, then rim; Go larger; touch response
Owner request: the ember rim must wait until the loading line has completely gone, then fade in slowly (amends 70): the line fills and fades, then after a short pause the rim fades in over 0.76 s. Go arrow drawn 1.6 times larger with a 3.2 dp stroke (amends 71). Back, forward and reload icons turn ember the moment they are touched and ease back to ink shortly after release.

## 78. App links through redirects (amends 73)
Cause of the Amazon "Open" banner still doing nothing: the tap leads to a web address that redirects to the app address, and the redirect was treated as having no tap behind it; the cancelled navigation then left the loading bar running and the address showing the app address. Now a redirect within 8 s of a tapped navigation counts as tapped. Whenever the main page is asked for an address that is not a web page, the view restores the current page's address and ends the loading state. The address bar shows web addresses only. Not run on a device; if the banner still does nothing, the next step is to record which address it asks for.

## 79. Desktop viewport script rewrites every tag and adds none
Owner recordings of 1.2.9: the desktop page still showed zoomed in and cut off. Cause: the script of 76 changed only the first viewport tag, and a page's later tag (usually the device width) won, since the last tag in the page is the one the engine obeys. Fix (1.3.0): every viewport tag is rewritten to 1024 px as it appears and kept that way; no tag is added to a page that has none (such a page already gets a wide layout), which also keeps the script from touching the page structure. Amends 76. Not run on a device.

## 80. Desktop pages fitted by an explicit scale
Owner screenshots of 1.3.0: gsmarena in desktop mode still zoomed in and cut off; Gmail in desktop mode showed its phone page. The viewport tag rewrite of 76 and 79 did not fix the fit, and the cause of that is not known. The platform documentation says an explicit initial scale ignores the screen density, so a 1024 px layout fits a view of N pixels at N*100/1024 percent (720 px: 70%). Fix (1.3.1): while the desktop switch is on, the view's initial scale is set to that percentage before each toggle load, and again when the view's width changes (it applies to the next page loaded); mobile mode returns it to the default. The viewport script stays. Gmail serving its phone page in desktop mode is not addressed: it may follow from the layout width and is to be re-checked. Not run on a device.

## 81. Clear site data
Approved by the owner after Gmail kept its remembered web or phone choice in its own cookies. The menu has "Clear site data" for the open web page. It asks first (a card over the page: the site's name, what is removed, Cancel and Clear; Back or a tap outside cancels). Clearing expires the site's cookies, found by name for the open address and removed for its host, each parent domain (a sign-in kept on google.com for mail.google.com) and each path of the address, and removes its saved data (local storage, databases) for its host. The cache, history and bookmarks are not touched, and nothing is sent anywhere. Not cleared: cookies of other hosts (embedded third-party frames), cookies set for paths the address does not reach, and the shared cache. The page then reloads from the network and a short notice names the site. Sites sharing a parent-domain sign-in are signed out too, and the card says so. Not run on a device.

## 82. Clear site data in three choices (built in 1.3.3)
Owner request: login details, site settings and cache cleared separately. Plan for the next session, replacing the single action of 81: the card shows three checkboxes. Login details: the site's cookies (as in 81). Saved site data: local storage and databases for the host, and the site's saved location permission. Cache: the whole browser cache, since the engine cannot clear one site's cache; the card says it applies to all sites. Login details and Saved site data start ticked, Cache unticked; Clear stays inactive while none is ticked. Camera and microphone choices cannot be cleared per site this way and are not offered. The page reloads fresh and the notice names what was cleared. Before writing any call, read the documentation of the geolocation permission and cache calls (engine rule); no new connection is added. Built (1.3.3) as planned. The documentation was read first: `GeolocationPermissions.clear(origin)` takes an origin of scheme, host and port; `WebView.clearCache(true)` clears the disk cache for every WebView of the app. Ardour has no location prompt, so no location permission is saved today and that clear does nothing yet. The notice names what was cleared. Not run on a device.
