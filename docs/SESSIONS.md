# Sessions

Numbered, never dated. Dates live in TRACK.md.

## 32
- Checked: docs, INDEX ranges and sizes (all under 60 KB); remote head and run 108 green; `androidx.webkit` 1.18.0 still the newest stable (released October 21, 2026).
- Built (run 109 green, released as 1.0.9, decision 55): reader mode with twilight and paper themes, text size, progress line. Extraction script tested on sample pages in a test DOM; not yet seen on a device.
- Engine: `androidx.webkit` 1.18.0. APK size: 3,147,356 bytes (3.00 MiB) at 1.0.9; 33,248 bytes more than 1.0.8.
- Device check of 1.0.9 (screenshots) found clipped headings, a cut-off no-article screen and a front page read as an article; fixed in run 110, released as 1.1.0 (3,147,808 bytes).
- Second device check: article and no-article screens work; text still clipped at the right edge; padding order fixed in run 111, released as 1.1.1 (3,147,680 bytes).
- Third device check: clipping fixed. Added justified text with a toggle (run 112, released as 1.1.2, 3,149,000 bytes). A lazily loaded article can show short; not yet understood.
- DP24 passed on 1.1.2 (lazy loading confirmed as the cause of short results).
- Built (run 113, red once on a library download, re-run green, released as 1.1.3): reader loads lazily loaded text first, shows author and date, opens at once over the whole window. Not run on a device.
- Built (run 114, released as 1.1.4, decision 56): top row replaces the bottom bar, expanding address with floating Copy/Paste/Clear/Go bar, cursor after site name, address glow; reader text cached per page load, preparing time shortened. Not run on a device. gsmarena slow loading: open question, cause not found.
- 1.2.2 (run 122 green, decision 62): Mozilla Readability adopted with the built-in finder as fallback (+19,043 bytes), top row 4 dp tighter. Owner confirmed 1.2.1 top row and Settings. Not run on a device.
- 1.2.1 (run 121 green, decision 61): compact top row, Settings trimmed, update button beside the version with the amber download line. Owner confirmed 1.2.0 navigation feedback works. Reader decision (Readability or remove) still open. Not run on a device.
- 1.2.0 (run 120 green, decision 60): instant address and sweeping bar on navigation, cookies off by default again. Reader: owner asked for a different reader or removal; Mozilla Readability proposed, awaiting approval. Not run on a device.
- 1.1.9 (run 119 green; run 118 failed to compile on a type loop, fixed): reader paragraphs split on unusual elements, cookies on by default (owner decision), page treated as ready when its bar is full. Loading cause still open. Not run on a device.
- 1.1.7 (run 117, green): reader fix after the Times of India author-box result, plus a long-press report (page structure, slow requests). Cookies on/off timings read from screenshots: no difference (about 35 to 40 s).
- 1.1.5 reader still failed on the Times of India article; 1.1.6 (run 116, green) adds a fallback ladder, stepwise harvest, animated menu close, and a third-party cookies switch to test the slow loading. Timings read from screenshots: at least 18 s (Times of India), more than 35 s (gsmarena, Desktop site on). Not run on a device.
- DP26 (1.1.4) looks right in screenshots; reader failed on a Times of India article (text outside paragraph tags): fixed in run 115, released as 1.1.5 (3,172,896 bytes). Chrome loads gsmarena quickly, so the slow loading is specific to Ardour or the system web view; two tests proposed.
- DP25 passed (screenshots and a recording): preparing screen seen, about 2.1 s first open and 0.7 s second; page position kept after closing.
- Next start: Phase 4 close-out: owner checks the unseen items (DP17 stop while loading, keyboard hiding the bottom bar; DP20 next and previous, no-match text, close and back), then Phase 5; or: load the rest of an article before reading, or author/date line, or the Phase 4 close-out check; then the Phase 4 close-out check against the roadmap.

## 31
- Checked: docs, INDEX ranges and sizes (all under 60 KB); remote head and run 99 green; `androidx.webkit` 1.18.0 still the newest stable seen (search snapshots differ in age).
- Built (run 100 green, released as 1.0.0): browser menu with share, desktop site switch and Settings (decision 49). Not yet seen on a device.
- Engine: `androidx.webkit` 1.18.0. APK size: 3,087,260 bytes (2.94 MiB) at 1.0.0.
- Owner screenshots of 1.0.0: menu look and Share pass; desktop switch works, but the desktop page appeared cut off at the right edge (fit after load unconfirmed).
- Fixed (run 101, released as 1.0.1): desktop page stayed cut off after loading. Cause (from the engine's documented zoom behaviour, not seen directly): a reload keeps the old zoom. Fix: reset initial scale and load the address afresh so overview mode fits the width. Not yet seen on a device. APK size: 3,087,320 bytes (2.94 MiB) at 1.0.1.
- Owner screenshot of 1.0.1: desktop page fits; DP18 passed. Owner's notes on proxy, Tor and Quad9 read; recorded as proposals in ROADMAP, none approved.
- Owner decision: drop Tor and Quad9; Settings gets a Private DNS shortcut (decision 50). Built (run 102 green, released as 1.0.2): Private DNS card with an "Open network settings" button, shared outlined button in `Ui.kt`. Not yet seen on a device. APK size: 3,088,660 bytes (2.95 MiB) at 1.0.2.
- Built (run 103 green, released as 1.0.3): find in page (`FindBar.kt`; engine calls `findAllAsync`, `findNext`, `clearMatches`, find listener, read from platform sources and mirrors). Not yet seen on a device. APK size: 3,096,904 bytes (2.95 MiB) at 1.0.3.
- Owner screenshots of 1.0.3: DP19 passed; DP20 partly passed (see ROADMAP).
- Owner request: pull to refresh as an option, pinch zoom, force zoom, wrap text when zoomed. Built (run 104 green, released as 1.0.4): decision 51. Not yet seen on a device. APK size: 3,108,644 bytes (2.96 MiB) at 1.0.4.
- Owner result on 1.0.4: pull to refresh works; pinch zoom and force zoom did not (recording shows a pinch with no change). Fixed (run 105 green, released as 1.0.5): force zoom also runs on the open page and after each load. Root cause of the missing pinch zoom not confirmed. APK size: 3,109,032 bytes (2.97 MiB) at 1.0.5.
- Owner result on 1.0.5: zoom, force zoom and wrap text work. Built (decision 52): Print or save as PDF and Reset text size. Run 106 failed once in the release job on a library download (not code); re-run by the owner succeeded; released as 1.0.6. Print passed on device. APK size: 3,110,904 bytes (2.97 MiB) at 1.0.6.
- Owner widened the push token to include Actions read and write; the chat in use still held the old token, so re-running a failed run needs a new chat.
- Owner approved instructions version 7: re-run rule (decision 53).
- Built (run 107 green, released as 1.0.7, decision 54): web links from other apps and the default-browser option. Not yet seen on a device. APK size: 3,113,564 bytes (2.97 MiB) at 1.0.7. Re-run permission tested on a succeeded run: refused as nothing to retry; not proven yet.
- Owner results on 1.0.7: link opens from another app and loads in the open window; Reset text size works; the in-app default-browser button showed no dialog (default set in phone settings instead). Fixed (run 108 green, released as 1.0.8): the card now offers an Open default apps button when the dialog is not offered, and redraws on return. Not yet seen on a device. APK size: 3,114,108 bytes (2.97 MiB) at 1.0.8.
- Owner screenshots of 1.0.8: the default-browser dialog appears from the button; DP23 passed.
- Next start: reader mode with a twilight theme (last open Phase 4 item); then a Phase 4 close-out check against the roadmap.

## 30
- Owner screenshots of 0.9.9: airplane mode shows "No internet connection"; data on shows "This site can't be found" with "Search instead". Decisions 47 and 48 confirmed on device.
- No code change. Engine: `androidx.webkit` 1.18.0. APK size: 3,078,196 bytes (2.94 MiB) at 0.9.9.
- Next start: Phase 4 step 2: the Settings button becomes a menu (share, desktop site, find in page, Settings), then pull-to-refresh, text size and zoom.

## 29
- Owner screenshots of 0.9.8: typo domain correct; airplane mode showed the same "can't be found" text (fault).
- Built (run 99 green, released as 0.9.9): `ACCESS_NETWORK_STATE` and an offline check from the phone's network (decision 48). Not yet seen on a device.
- Engine: `androidx.webkit` 1.18.0. APK size: 3,078,196 bytes (2.94 MiB) at 0.9.9.
- Next start: owner re-tests airplane mode on 0.9.9; then Phase 4 step 2.

## 28
- Owner device pass 0.9.7 (screenshots): back walks history, second back leaves the app; search loads; error screen looks as intended. Fault found: a non-existent domain said "Check your connection".
- Built (run 98 green, released as 0.9.8): error kinds by cause and "Search instead" (decision 47). Not yet seen on a device.
- Engine: `androidx.webkit` 1.18.0. APK size: 3,077,668 bytes (2.94 MiB) at 0.9.8.
- Next start: owner checks 0.9.8 (typo domain, airplane mode); then Phase 4 step 2.

## 27
- Owner screenshots of 0.9.6: cricinfo renders fully with images, flags and local times; start screen centred. Decision 45 confirmed on device.
- Built (run 97 green, released as 0.9.7): Ardour error screen (decision 46); reload arrowhead redrawn as a filled head. Not yet seen on a device.
- Engine: `androidx.webkit` 1.18.0. APK size: 3,076,164 bytes (2.93 MiB) at 0.9.7.
- Next start: DP17 rest (back gesture, stop, keyboard) and the error screen (airplane mode, an http address); then Phase 4 step 2.

## 26
- Owner screenshots of 0.9.5: start screen text flush left (default width, no centring); cricinfo missing images and showing UTC times (page scripts not finishing); reload arrowhead too small.
- Fixed in run 96, released as 0.9.6: start screen centred; DOM storage on (decision 45); larger arrowhead. Not yet seen on a device.
- Engine: `androidx.webkit` 1.18.0. APK size: 3,070,972 bytes (2.93 MiB) at 0.9.6.
- Next start: DP17 on 0.9.6, including cricinfo; if images or times are still wrong, test the user-agent marker as the next cause; error pages in the Ardour look.

## 25
- Built (run 95 green, released as 0.9.5): Phase 4 step 1 per decision 43: address bar, back, forward, reload and stop, progress line, start screen, back callback. Download button now reads "Downloading…" while it runs.
- Run 94 (same code) failed in the release build with no readable cause; run 95 passed without a code change. Cause unknown. Annotations extended to the release build (decision 44).
- DP16 on a 0.9.2 screenshot: bar glow and greyed button look finished; pulse and sweep unseen.
- Engine: `androidx.webkit` 1.18.0. APK size: 3,070,792 bytes (2.93 MiB) at 0.9.5.
- Next start: DP17 (owner, device); plain system error pages are still shown for failed loads and need the same polish; then Phase 4 step 2.

## 24
- Checked: docs, INDEX ranges and sizes (all under 60 KB); `androidx.webkit` 1.18.0 still the newest stable (developer.android.com).
- Built, run 93 green and released as 0.9.3: `version.properties` deleted; build errors written as annotations (`.github/ci/annotate-errors.sh`, decision 42). Not yet seen on a real failure.
- Decisions: 42.
- Engine: `androidx.webkit` 1.18.0. APK size: 3,051,824 bytes (2.91 MiB) at 0.9.3, from the release asset.
- Next start: DP16 (owner, device); read the annotations on the first red run; Phase 4 approval, or a decision on the local crash log.

## 23
- Checked: repo and delivered files identical; run 92 (0.9.2) green after the pulse fix. Token verified: push permission and Actions read work; run logs cannot be downloaded (host blocked in the sandbox), the API gives the failing step only.
- Built: nothing in code. Direct push access (decision 41); instructions version 5.
- Decisions: 41.
- Engine: `androidx.webkit` 1.18.0. APK size: not read.
- Next start: delete `version.properties` with the next code commit (a push of that alone would release again); DP16; owner allows the log host or approves an error-annotation step.

## 22
- Checked on device: 0.0.9 found 0.1.0 (the old raw release notes showed with code fences; fixed in 0.1.0), downloaded, verified, Android confirmed the update, 0.1.0 installed. DP3 passed. 0.1.0 look passed on a screenshot.
- Built, not run through CI or on a device: glow, rim light, pulse, sweep and gradient progress per decision 40 (`Ui.kt`, updater rows). Instructions version 4.
- Fixed: run 91 (0.9.1) failed to compile: `View.ELEVATION` does not exist; the pulse now animates the `elevation` property by name. Version step worked: run 91 gave 0.9.1.
- Decisions: 40.
- Engine: `androidx.webkit` 1.18.0. APK size: not read.
- Next start: owner commits in one upload; judge DP16; the release number must equal the run number (decision 39).

## 21
- Built: version from the build run number (decision 39), not run through CI: workflow computes `X.Y.Z` from `github.run_number`, passes `-PardourVersion` to Gradle; `version.properties` no longer read; docs-only pushes skip the build. Instructions version 3.
- Risk: workflow and Gradle changed together; a red first run is likely a syntax or version-parse error. Failed runs use numbers, so versions will skip.
- Decisions: 39.
- Engine: `androidx.webkit` 1.18.0. APK size: not read.
- Next start: owner commits in one upload and deletes `version.properties`; check the release number equals the run number; then DP15 and DP3.

## 20
- Checked: repo files match the delivered files (all 23 identical; INDEX ranges agree; no secrets, no forbidden words). 0.0.9 on device: Settings shows "up to date (0.0.9)"; the screen looked plain.
- Built: polish rule (decision 38) and a restyle for 0.1.0, not run through CI or on a device: `Ui.kt` helpers, Settings cards with back button, polished gate screen and Settings button, updater rows with progress bar and shorter release summary. 0.1.0 is also the first real test of the install path.
- Decisions: 38.
- Engine: `androidx.webkit` 1.18.0. APK size: 0.0.9 and 0.1.0 not read.
- Next start: commit; read CI; DP15 on a screenshot; then DP3 install from 0.0.9 to 0.1.0.

## 19
- Built: Phase 3 step 2, not run through CI or on a device: sideload updater (check on tap, download, SHA-256 against release notes, `PackageInstaller` session), install permission in the sideload manifest only, play stub, version 0.0.9 (decision 36). Version scheme amended: Y and Z each 0 to 9 (decision 37); the build rejects PATCH above 9. Instructions now carry a version number (1).
- Verified: session flow, mutable `PendingIntent` requirement, install-permission check (platform source and sample code, not the reference page). Not verified: Android developer-verification effect; the `play` flavor is not compiled in CI.
- Decisions: 36, 37.
- Engine: `androidx.webkit` 1.18.0. APK size: 0.0.9 not read.
- Next start: commit, read CI result, then DP3; the install path is tested when 0.1.0 exists.

## 18
- Checked: 0.0.8 released and installed. DP2 passed from a recording: Settings shows Ardour 0.0.8 and WebView 157.0.8103.38. Scroll with the Settings button: median gap 16.4 ms, no fast-motion gap over 34 ms, about 3 flicks only.
- Decisions: none.
- Engine: `androidx.webkit` 1.18.0 (newest stable not rechecked this turn). APK size: 3,021,332 bytes (2.88 MiB) at 0.0.8, from the download header.
- Not measured: start-up with the button, 10-flick scroll, Settings text contrast (value lines are light grey).
- Next start: Phase 3 step 2, the updater (check, download, SHA-256, system installer). Needs API checks for the installer first.

## 17
- Built: Phase 3 step 1, not run through CI or on a device: `sideload` and `play` flavors (CI builds `sideload`), `SettingsActivity` with About, WebView version from the adapter, temporary Settings button. Ships with the gate screen as 0.0.8 (0.0.8 was never committed on its own).
- Decisions: 35 (targets approved, Phase 3 approved, two steps).
- Risk: flavor DSL and renamed APK paths are untested in CI; a red build is likely to be a path or flavor error.
- Engine: `androidx.webkit` 1.18.0. APK size: not read.
- Next start: owner commits and confirms DP2; then updater (step 2).

## 16
- Built: gate screen now says how to update WebView and has an Open Play Store button (decision 34); version 0.0.8. Not run through CI or on a device; the screen cannot be shown on a current phone. Phase 2 marked complete; gate message and Android 10 start moved to the Phase 5 launch test.
- Verified: on Android 10 and higher WebView is the separate app `com.google.android.webview` and Chrome cannot provide it (Chromium WebView documentation). Not verified: how the Play Store handles the link on every phone.
- Decisions: 34.
- Engine: `androidx.webkit` 1.18.0. APK size: 0.0.8 not read.
- Next start: owner approves the revised targets and Phase 3.

## 15
- Measured: 0.0.7 on moto g34 5G (Android 15) from one screen recording: three cold starts, 0.51 to 0.60 s from first launch frame to page; scroll compared with a reference browser on the same page: both median 16.8 ms, no gap over 34 ms during fast motion in either. Method and limits in ROADMAP Quality targets.
- Decisions: none. Targets proposed, not set.
- Engine: `androidx.webkit` 1.18.0. APK size: 2.88 MB at 0.0.6; 0.0.7 not read.
- Not measured: WebView version, memory, a second page, the gate message.
- Next start: owner approves the targets and Phase 3; then Phase 3.

## 14
- Built: DP14 fix, not run through CI or on a device: `themes.xml` with light status and navigation bar icons, manifest uses it, version 0.0.7. No new dependency.
- Verified: `windowLightStatusBar` needs API 23, `windowLightNavigationBar` API 27 (platform reference mirrors); both below minSdk 29. Not verified: behaviour under edge-to-edge on device.
- Decisions: none.
- Engine: `androidx.webkit` 1.18.0 (unchanged; newest stable not rechecked this turn). APK size: 2.88 MB at 0.0.6.
- Next start: owner confirms DP14; then start-up and scroll measurement; Phase 3 needs approval. Icons stay dark on dark pages until Phase 9 theming.

## 13
- Built: nothing in code. Owner checked 0.0.6: build #47 green after bootstrap, release 0.0.6 published, APK 2.88 MB, page and a followed link load on device (DP13 in part). Repo renamed to `g-c-3/weber`; old name redirects.
- Found: status-bar icons are light on a light page (DP14). ARCHITECTURE.md still named the old repo in the update URL; fixed.
- Decisions: 33.
- Engine: `androidx.webkit` 1.18.0. APK size 2.88 MB.
- Next start: DP14 fix (verify API form), then start-up and scroll measurement; Phase 3 needs approval. Instructions text still names the old repo in its read URLs.

## 12
- Built: Phase 2 build files, not run through CI or on a device: `androidx.webkit` 1.18.0 pinned, GeckoView and its workarounds removed, minSdk 29, manifest opt-outs, launch gate, one adapter (Default profile, settings, renderer-gone handling), one APK `Ardour-X.Y.Z.apk` (versionCode D = 1), release notes line, version 0.0.6.
- Verified: 1.18.0 newest stable (release notes, 21 October 2026); Apache 2.0; mixed-content and file-URL defaults. Docs, tags v0.0.1 to v0.0.5 and INDEX ranges agree; doc sizes ROADMAP 12.3 KB, TRACK 1.7 KB, SESSIONS 7.4 KB, DECISIONS 16.3 KB. Not verified: size cost, compileSdk, profile-name rules.
- Decisions: 32.
- Engine: `androidx.webkit` 1.18.0 (in the delivered files); GeckoView stays in releases 0.0.4 and 0.0.5. APK size: no new build; 0.0.5 arm64 91.2 MB, universal 237 MB.
- Next start: owner commits the files, runs `verification-bootstrap`, commits the metadata; read the first build's size and any compileSdk error. Then DP13, and on-device start-up and scroll measurement. Open: DP0 on 0.0.2, DP9.

## 11
- Built: no code. Docs read; file tree and INDEX ranges agree; tags v0.0.1 to v0.0.5 match `version.properties` (0.0.5). Doc sizes: ROADMAP 11.1 KB, TRACK 1.5 KB, SESSIONS 6.5 KB, DECISIONS 16.3 KB; all under 60 KB.
- Verified: `androidx.webkit` 1.18.0 still newest stable (release notes page, last updated 4 November); 1.19.0-alpha01 not used. `getCurrentWebViewPackage(context)` form, manifest meta-data names, `setAllowFileAccess` default recorded in ROADMAP 2. Not verified: mixed-content and file-URL settings, license, size cost, compileSdk.
- Decisions: none.
- Engine: none pinned in the repo (GeckoView `159.0.20261102144711` still in 0.0.5); target `androidx.webkit` 1.18.0. APK size: no new build; 0.0.5 arm64 91.2 MB, universal 237 MB.
- Next start: Phase 2 per ROADMAP 2: close the remaining verification items (search per method name, not whole pages), then deliver the build files in one commit order. Open: DP0 on 0.0.2, DP9, DP13.

## 10
- Built: no code. Docs read; file tree and INDEX ranges agree; tags v0.0.1 to v0.0.5 match `version.properties` (0.0.5). Doc sizes: ROADMAP 10.5 KB, TRACK 1.4 KB, SESSIONS 5.6 KB, DECISIONS 16.3 KB; all under 60 KB.
- Verified: `androidx.webkit` 1.18.0 still newest stable (release notes page); 1.19.0-alpha01 not used. Renderer-gone handling recorded in ROADMAP 2. Not verified: `WebSettings` calls, `getCurrentWebViewPackage` signature, license, size cost, compileSdk.
- Decisions: none.
- Engine: none pinned in the repo (GeckoView `159.0.20261102144711` still in 0.0.5); target `androidx.webkit` 1.18.0. APK size: no new build; 0.0.5 arm64 91.2 MB, universal 237 MB.
- Next start: Phase 2 per ROADMAP 2: close the four open verification items, then deliver the build files in one commit order. Open: DP0 on 0.0.2, DP9, DP13.

## 9
- Built: no code. Docs read; file tree and INDEX ranges agree; tags v0.0.1 to v0.0.5 match `version.properties` (0.0.5). Doc sizes all under 60 KB.
- Verified: newest stable `androidx.webkit` is 1.18.0 (developer.android.com release notes); 1.19.0-alpha01 not used. Profile API forms, `setProfile` ordering rule and `MULTI_PROFILE` flag recorded in ROADMAP 2; the gate uses the run-time flag, no WebView version claimed. Not verified: `WebSettings` calls, renderer-gone callback, license, size cost.
- Decisions: none.
- Engine: none pinned in the repo (GeckoView `159.0.20261102144711` still in 0.0.5); target `androidx.webkit` 1.18.0. APK size: no new build; 0.0.5 arm64 91.2 MB, universal 237 MB.
- Next start: Phase 2 per ROADMAP 2: close the remaining unverified items, then deliver the build files in one commit order. Open: DP0 on 0.0.2, DP9, DP13.

## 8
- Built: Phase 2 on GeckoView: engine pinned `159.0.20261102144711`, adapter, one page loads on device (DP1 passed, releases 0.0.4 and 0.0.5). Measured: arm64 188 MB uncompressed, 91.2 MB with compressed libraries; universal 507 MB, 237 MB. Then the engine changed to system WebView (decision 26); nothing of it built yet.
- Fixed: missing checksums (bootstrap rerun); compileSdk 36 to 37.2 and Kotlin 2.2 to 2.4.20 override, both GeckoView requirements; release notes engine line read "none".
- Decisions: 26 to 31. Only Phase 2 (WebView) approved.
- Engine: none pinned for WebView. `androidx.webkit` version not yet chosen. GeckoView `159.0.20261102144711` is in 0.0.4 and 0.0.5 only.
- Next start: Phase 2 per ROADMAP 2: verify and pin `androidx.webkit`; strip GeckoView; minSdk 29; manifest opt-outs; launch gate; adapter; one APK in CI; regenerate checksums; measure size and start-up. Open: DP0 on 0.0.2; DP9 (icon); DP13; approval of the second connection wording is in the instructions update; optional repo rename (the name `Gecko` stays in the updater URL until decided).

## 7
- Built: no code. Product discussion concluded: Spaces (six, up to ten), second approved connection (filter-list updates), feature plan with phases 11 to 13, Basic and Advanced settings. Docs updated.
- Fixed: engine candidate corrected. Stable is 159.0.x; 160.0 is beta, so session 5 and 6 entries naming `160.0.20261104181552` are superseded (decision 24).
- Decisions: 20 to 25. Only Phase 2 approved; rest planned.
- Engine: none pinned. Candidates `geckoview:159.0.20261102144711`, fallback `159.0.20261022150525`; verified through search only (Maven mirror listing, third-party release), Mozilla's Maven not read.
- Next start: Phase 2 (ROADMAP 2). Read `app/build.gradle.kts`, `libs.versions.toml`, manifest first. Open: release 0.0.3 status (GitHub API was rate-limited); DP0 on 0.0.2; DP9; approval of the project-instructions update (second connection, plus the filter-list schedule; owner pastes the current text).

## 6
- Built: adaptive launcher icon from the owner's logo (decision 19); manifest icon reference; version 0.0.3. Docs brought up to date.
- Fixed: nothing.
- Decisions: 19.
- Engine: none pinned. Candidate `geckoview:160.0.20261104181552` (ROADMAP 2).
- Next start: owner-led product discussion on what makes the browser distinct (Phase 11 inputs); no build until it concludes. Then Phase 2. Open: release 0.0.3 result; DP9 (icon) and DP0 on 0.0.2; approval of the project-instructions update.

## 5
- Built: Phase 1 finished. Release key and secrets; `build-android.yml` with `build`, `plan`, `release` jobs; MPL 2.0 `LICENSE`; application id `io.github.gc3.ardour`; version 0.0.2.
- Fixed: first release run tagged the release `main` (tag taken from the branch name). Tag now `v<version>`. Stray tag and release removed.
- Decisions: 15 to 18.
- Engine: none pinned. Stable is 160.0; candidate `geckoview:160.0.20261104181552` (see ROADMAP 2).
- Next start: confirm DP0 on 0.0.2; Phase 2 (pin, regenerate checksums, adapter, privacy check).

## 4
- Verified: `build-android` run 3 green (debug APKs, arm64 and universal assembled). Runs 1 and 2 red: workflow committed before the app files; commit-order cause, no code fault.
- Engine check: current stable GeckoView not verified (search returned outdated pages). Phase 2 verifies it first, from Mozilla's Maven listing.
- Decisions: none.
- Engine: none pinned yet.
- Next start: owner runs `verification-bootstrap`, commits the checksum file; owner answers app id, keystore method, license. Then the release workflow.

## 3
- Built: `app` module (no engine, plain activity), version wiring with ABI splits, `build-android.yml` (debug build), `verification-bootstrap.yml`.
- Fixed: nothing. API forms checked against AGP documentation: `compileSdk { version = release(..) }`, `output.versionCode.set`.
- Decisions: 14.
- Engine: none pinned yet.
- Next start: owner runs both workflows; paste any failing step. Then keystore method, license, app id approval.

## 2
- Built: toolchain pinned (decision 12): Gradle wrapper 9.9.1, `gradle/libs.versions.toml`, root Gradle files.
- Fixed: nothing. Found: docs were uploaded to the repo root; they belong in `docs/`.
- Decisions: 12, 13.
- Engine: none pinned yet.
- Next start: Phase 1 remainder: app module, `build-android.yml`, verification metadata, keystore method (see ROADMAP).

## 1
- Built: Phase 0. Docs (INDEX, ROADMAP, DECISIONS, SESSIONS, TRACK, ARCHITECTURE), `version.properties` (0.0.1), `.gitignore`.
- Fixed: nothing.
- Decisions: 1 to 11.
- Engine: none pinned yet.
- Next start: Phase 1. Verify current Android Gradle Plugin, Kotlin, Gradle and JDK versions first.
