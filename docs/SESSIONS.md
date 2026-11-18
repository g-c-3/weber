# Sessions

Numbered, never dated. Dates live in TRACK.md.

## 39
- Built (release 1.3.1, decision 80): desktop pages fitted by an explicit initial scale (view pixel width over 1024), set before the toggle load and on width changes.
- Owner check of 1.3.0: failed (gsmarena still zoomed in, Gmail did not show its desktop page). The documentation on the scale's density handling gave the new approach; the earlier cause is still not known.
- Engine: `androidx.webkit` 1.18.0. APK size: 3,250,043 bytes at 1.3.1. Not run on a device: 1.3.1 (DP39).
- Next start: owner's DP39 results. Still open, not to be started unasked: per-site data clear (needs approval), Phase 4 close-out checks (DP17, DP20), the Phase 3 checkbox, Phase 5.

## 38
- Built (release 1.3.0, decision 79): desktop viewport script rewrites every viewport tag of the page and adds none, the cause of pages still showing zoomed in.
- Owner check of 1.2.9: line then rim, larger Go arrow and the Amazon Open banner passed. Unresolved: Gmail in desktop mode showed the new address with the old page still on screen; cause not found, needs a recording on 1.3.0.
- Engine: `androidx.webkit` 1.18.0. APK size: 3,249,483 bytes at 1.3.0. Not run on a device: 1.3.0 (DP38).
- Next start: owner's DP38 results. Still open, not to be started unasked: per-site data clear (needs approval), Phase 4 close-out checks (DP17, DP20), the Phase 3 checkbox, Phase 5.

## 37
- Built (release 1.2.9, decisions 76 to 78): rim fades in only after the loading line has completely gone; Go arrow larger and bolder; back, forward and reload turn ember on touch; links to apps work through redirects and a cancelled one no longer leaves the bar running; desktop site keeps a 1024 px viewport so pages fit, and mobile addresses (m.) become normal ones when it goes on.
- Owner check of 1.2.8: refresh fix and reader passed; line, Go, app links and desktop partly. Gmail returning to mobile after "use web version" is Gmail's own saved choice; offered a per-site data clear as a later item.
- Engine: `androidx.webkit` 1.18.0. APK size: 3,249,635 bytes at 1.2.9. Not run on a device: 1.2.9 (DP37).
- Next start: owner's DP37 results. Still open, not to be started unasked: per-site data clear (needs approval), Phase 4 close-out checks (DP17, DP20), the Phase 3 checkbox, Phase 5.

## 36
- Built (releases 1.2.6 and 1.2.8, decisions 70 to 75): loading line inside the address pill with the ember rim never shown with it, and the old strip returned to the page; Go as an amber arrow with a slow fill; finish check reads only the newly committed page, and reload stops background loads; reader with one A button, pinch sizing and a smooth reset; links open other apps and the Play Store app; desktop site sends matching client hints and skips the cache once.
- Owner check of 1.2.5: pill tap, thin line and edit actions passed. Run 126 failed once on a library download and passed on its single re-run (1.2.6); run 127 failed to compile (a wrong reference) and was fixed in run 128 (1.2.8; 1.2.7 was not released).
- Engine: `androidx.webkit` 1.18.0. APK size: 3,244,859 bytes at 1.2.8. Not run on a device: all of 1.2.6 and 1.2.8 (DP36). The cause of the desktop-site problems is a best reading of the evidence, not confirmed.
- Next start: owner's DP36 results. Still open, not to be started unasked: Phase 4 close-out checks (DP17 stop while loading; DP20 next, previous, no-match text), the Phase 3 checkbox, Phase 5.

## 35
- Built (release 1.2.5, decisions 67 to 69): address pill takes the touch itself while idle and opens the editor with the cursor already after the site name; page loading line now 2 dp with glide, sheen, leading glow and a fade-out; Copy, Paste, Clear and Go redrawn with icons, state-aware dimming, entry motion and a Copied confirmation.
- Engine: `androidx.webkit` 1.18.0. APK size: 3,229,235 bytes at 1.2.5 (about 16 KB more than 1.2.4). Not run on a device: all of 1.2.5 (DP35); the build passed but the code was not compiled outside CI before the push.
- Next start: owner's DP35 results. Still open, not to be started unasked: Phase 4 close-out checks (DP17 stop while loading; DP20 next, previous, no-match text), the Phase 3 checkbox, Phase 5.

## 34
- Built (release 1.2.4, decision 66): the bar and stop button end once the page's document is parsed and the engine is at 90%, instead of waiting for late ads and tracking frames.
- Owner check of 1.2.3: all of DP33 passed; recordings showed Chrome finishing at content time and Ardour waiting for ads.
- Engine: `androidx.webkit` 1.18.0. APK size: 3,213,051 bytes at 1.2.4. DP34 passed on 1.2.4 (owner recordings); the slow-page half is not yet seen.
- Next start: owner's direction. Still open, not to be started unasked: Phase 4 close-out checks (DP17 stop while loading; DP20 next, previous, no-match text), the Phase 3 checkbox, Phase 5.

## 33
- Built (release 1.2.3, decisions 63 to 65): progress bar stays until the engine reports the page finished (settle rule removed); pages laid out like a full browser, so desktop-built pages fit the screen without the Desktop site switch; pill and buttons 34 dp with a clear strip for the progress line.
- Owner check of 1.2.2: reader passed on the Times of India articles; top row showed the progress line under the pill.
- Engine: `androidx.webkit` 1.18.0 (newest stable, checked). APK size: 3,211,271 bytes at 1.2.3. Not run on a device: all of 1.2.3 (DP33).
- Next start: owner's DP33 results. Still open, not to be started unasked: Phase 4 close-out checks (DP17 stop while loading; DP20 next, previous, no-match text), the Phase 3 checkbox, Phase 5.

## 32
- Built (releases 1.0.9 to 1.2.2, decisions 55 to 62): reader mode with twilight and paper themes, justified text, author and date, long-press report, and Mozilla Readability as the main finder with the built-in finder as fallback; one top row (address left, buttons right, expanding address with Copy, Paste, Clear, Go) replacing the bottom bar; instant address and sweeping bar on navigation; page treated as ready 2.5 s after the bar fills; Settings trimmed, with Update Check beside the version and the amber download line.
- Fixed or learned: reader clipping, paragraph splitting and several Times of India page structures; slow "finished" signal on ad-heavy sites is not solved (third-party cookies and Desktop site made no difference; default stays off).
- Engine: `androidx.webkit` 1.18.0. APK size: 3,211,987 bytes at 1.2.2. Passed on the device: DP24, DP25, DP31. Not yet run on a device: Readability and the 4 dp tighter top row (1.2.2).
- Next start: DO NOTHING from the roadmap. Wait for the owner's reply on the three DP32 checks: (1) the Times of India articles that failed before (Lakshmi Venu / TVS, Hello Doctor, Sachin Tendulkar) in Reader mode; (2) a pasted long-press report for any that still fail; (3) a screenshot of the top row. Act only on that reply.
- Instructions updated to version 8 (update section: Update Check button beside the version; Readability named as the one approved JavaScript asset); delivered as a file, not in the repo.
- Still open afterwards, not to be started unasked: Phase 4 close-out checks (DP17 stop while loading; DP20 next, previous, no-match text), the Phase 3 checkbox, Phase 5, and the download path of Update Check (needs a newer release).

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
