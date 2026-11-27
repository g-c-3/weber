package io.github.g_c_3.ardour

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.os.SystemClock
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.LinearGradient
import android.graphics.drawable.GradientDrawable
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.graphics.Shader
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.print.PrintAttributes
import android.print.PrintManager
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.ViewGroup
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.animation.PathInterpolator
import android.view.inputmethod.InputMethodManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.window.OnBackInvokedCallback
import android.window.OnBackInvokedDispatcher
import android.annotation.TargetApi

class MainActivity : Activity() {
    private lateinit var address: AddressField
    private val progress: PageProgress get() = address.progress
    private lateinit var startView: View
    private lateinit var searchBlock: StartSearch
    private lateinit var errorView: LinearLayout
    private lateinit var errorTitle: TextView
    private lateinit var errorBody: TextView
    private lateinit var errorHost: TextView
    private lateinit var errorRetry: View
    private lateinit var errorSearch: View
    private var failedUrl: String? = null
    private lateinit var buttonGroup: LinearLayout
    private var builtDark = false
    // Shield Mode of the active tab (DECISIONS.md 134, 163); the Shield button shows it.
    private val privateOn: Boolean get() = EngineAdapter.activeTab()?.shield == true
    private lateinit var privateButton: IconButton
    private var startBack = false // the start screen was reached by Back through the page history (DECISIONS.md 138)
    private var lastExitBack = 0L
    private var homeOver = false // the start screen is shown over a page the user left with Home (DECISIONS.md 127)
    private lateinit var floatingBar: EditBar
    private lateinit var suggestList: SuggestionList
    private lateinit var suggest: Suggestions.Session
    private var expanded = false
    private var rowWidth = 0
    private var idleWidth = 0
    private var widthAnim: ValueAnimator? = null
    private var loadId = 0
    private var cachedArticle: Article? = null
    private var cachedLoadId = -1
    private lateinit var topBar: View
    private lateinit var rootLayout: LinearLayout
    private var reader: ReaderView? = null
    private lateinit var findBar: FindBar
    private lateinit var pageArea: PullRefreshLayout
    private var finding = false
    private lateinit var backButton: IconButton
    private lateinit var tabsButton: IconButton
    private lateinit var tabSwitcher: TabSwitcher
    private lateinit var tabStrip: TabStrip
    private var pageView: View? = null // the active tab's page view in the page area (DECISIONS.md 161)
    private lateinit var forwardButton: IconButton
    private lateinit var reloadButton: IconButton
    private var loading = false
    private var hasPage = false
    private var backCallback: Any? = null

    // Ad-heavy pages show all their content long before the engine reports the load finished, because ads and
    // tracking frames keep it loading. At 90% or more the page's document state is checked until it is parsed; no timer
    // decides it (DECISIONS.md 66). Whatever still loads carries on quietly.
    private var readyPollId = -1
    private var lastPercent = 0
    private var committedId = -1

    // Only once the new page is on screen (never the page being left, whose document is long complete), the engine
    // is at 90% or more, and this load has not been polled yet.
    private fun maybePollDocument() {
        if (loading && lastPercent >= 90 && committedId == loadId && readyPollId != loadId) pollDocument(loadId)
    }

    // Trace only (DECISIONS.md 84): a line every 2 s while a page loads, at most 90 per load.
    private var beats = 0
    private val heartbeat = object : Runnable {
        override fun run() {
            if (!loading) return
            Trace.log("HEARTBEAT", "load=$loadId ${EngineAdapter.heartbeat()}")
            val beat = beats
            var answered = false
            EngineAdapter.probe { state ->
                answered = true
                Trace.log("PROBE", "load=$loadId beat=$beat state=$state")
            }
            progress.postDelayed({ if (!answered) Trace.log("PROBE", "load=$loadId beat=$beat no answer after 1.5 s") }, 1500L)
            if (++beats < 90) progress.postDelayed(this, 2000L)
        }
    }

    private fun pollDocument(id: Int) {
        readyPollId = id
        EngineAdapter.documentParsed { parsed ->
            Trace.log("POLL", "load=$id parsed=$parsed current=${loadId == id && loading}")
            if (!loading || loadId != id) return@documentParsed
            if (parsed) listener.onLoading(false) else progress.postDelayed({ if (loading && loadId == id) pollDocument(id) }, 300L)
        }
    }

    private val listener: PageListener = object : PageListener {
        override fun onPage(url: String?, canGoBack: Boolean, canGoForward: Boolean) {
            if (url != null && (url.startsWith("http://") || url.startsWith("https://"))) {
                // A page that is still reporting after Home was pressed must not bring the page back over the start screen
                // (DECISIONS.md 157); only a new load of the owner's does (beginNavigation).
                if (!homeOver) {
                    address.showUrl(url)
                    revealPage()
                }
            }
            backButton.isEnabled = if (homeOver) !startBack else canGoBack || hasPage
            forwardButton.isEnabled = canGoForward || (homeOver && startBack)
            reloadButton.isEnabled = hasPage
            refreshBack()
            refreshStrip()
        }

        override fun onError(url: String?, kind: PageError) = showError(url, kind)

        override fun onNavigate(url: String) = beginNavigation(url)

        override fun onFind(active: Int, total: Int) {
            if (finding) findBar.showCount(active, total)
        }

        override fun onProgress(percent: Int) {
            // The bar shows the engine's own progress. It leaves when the engine reports the load finished, or earlier
            // when the engine is at 90% or more and the page's document is parsed (DECISIONS.md 66).
            lastPercent = percent
            progress.update(percent / 100f)
            maybePollDocument()
        }

        override fun onExternal(url: String): Boolean {
            val uri = android.net.Uri.parse(url)
            if (ExternalLinks.isStorePage(uri)) return ExternalLinks.openInStore(this@MainActivity, url)
            val outcome = ExternalLinks.open(this@MainActivity, url)
            if (!outcome.opened) {
                val next = outcome.loadInstead
                if (next != null) pageArea.post { submit(next) } else pageArea.post { showNotice("No app can open this link") }
            }
            return outcome.opened
        }

        override fun onTabOpened(index: Int) {
            saveTabState()
            activate(index)
        }

        override fun onTabCloseRequest(index: Int) = closeTabAt(index)

        override fun onTabLimit() = showNotice("Tab limit reached (${EngineAdapter.maxTabs})")

        override fun onCommitVisible() {
            committedId = loadId
            Trace.log("UI", "commit load=$loadId")
            maybePollDocument()
        }

        override fun onLoading(loading: Boolean) {
            this@MainActivity.loading = loading
            if (loading) {
                loadId++
                lastPercent = 0
            }
            Trace.log("UI", "loading=$loading load=$loadId")
            progress.removeCallbacks(heartbeat)
            if (loading) {
                beats = 0
                progress.postDelayed(heartbeat, 2000L)
            }
            if (loading) errorView.visibility = View.GONE
            if (loading && finding) closeFind()
            if (!loading) pageArea.finish()
            if (!loading) refreshStrip()
            reloadButton.kind = if (loading) IconButton.Kind.STOP else IconButton.Kind.RELOAD
            // Until the engine reports its first progress the bar sweeps, so a slow connection looks alive.
            address.setLoading(loading)
        }
    }

    @Suppress("DEPRECATION")
    override fun onCreate(savedInstanceState: Bundle?) {
        Ui.applyTheme(this)
        setTheme(if (Ui.dark) R.style.Theme_Ardour_Dark else R.style.Theme_Ardour)
        super.onCreate(savedInstanceState)
        builtDark = Ui.dark
        CrashLog.install(this)
        SearchEngines.load(this)
        val gateMessage = buildScreen()
        if (gateMessage == null) restoreTabState()
        if (gateMessage == null && savedInstanceState == null) handleLink(intent)
    }

    // The whole browser screen: the gate message when the engine is too old, else the browser with the tab list beside it.
    // Built at launch and again, in place, when the theme changes (DECISIONS.md 164).
    @Suppress("DEPRECATION")
    private fun buildScreen(): String? {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Ui.PAPER)
            isFocusableInTouchMode = true
        }
        val match = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        val gateMessage = EngineAdapter.launchGateMessage(this)
        if (gateMessage != null) {
            // Gate unmet: message and Play Store button only, nothing is loaded (DECISIONS.md 28, 34).
            root.addView(gateView(gateMessage), match)
        } else {
            buildBrowser(root)
        }
        // Target SDK 36 draws edge to edge; keep the page clear of the system bars and the keyboard.
        root.setOnApplyWindowInsetsListener { v, insets ->
            if (Build.VERSION.SDK_INT >= 30) {
                val bars = insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.ime())
                v.setPadding(bars.left, bars.top, bars.right, bars.bottom)
                spaceTopBar()
            } else {
                v.setPadding(
                    insets.systemWindowInsetLeft,
                    insets.systemWindowInsetTop,
                    insets.systemWindowInsetRight,
                    insets.systemWindowInsetBottom,
                )
                spaceTopBar()
            }
            insets
        }
        // The tab list covers the whole screen, so it sits beside the browser, not inside it (DECISIONS.md 162).
        val screen = FrameLayout(this)
        screen.addView(root, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        if (gateMessage == null) screen.addView(tabSwitcher, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        setContentView(screen)
        Ui.styleWindow(window)
        applySecure()
        root.requestFocus()
        return gateMessage
    }

    // The screen was built with the other theme (Settings choice, or the phone's setting while the choice is System).
    private fun restyleIfNeeded(): Boolean {
        Ui.applyTheme(this)
        if (Ui.dark == builtDark || !::pageArea.isInitialized) return false
        // Built again in place: the page views are taken out of the old screen and put into the new one, so every tab
        // keeps its page, history and scroll (DECISIONS.md 164).
        saveTabState()
        if (finding) closeFind()
        if (reader != null) closeReader()
        sheet.dismiss()
        widthAnim?.cancel()
        noticeView = null
        topNoticeView = null
        // Sizes measured for the old top bar: the new one is sized again when its row width is known (DECISIONS.md 165).
        rowWidth = 0
        idleWidth = 0
        expanded = false
        pageView?.let { (it.parent as? ViewGroup)?.removeView(it) }
        pageView = null
        setTheme(if (Ui.dark) R.style.Theme_Ardour_Dark else R.style.Theme_Ardour)
        builtDark = Ui.dark
        if (buildScreen() == null) {
            showTabState()
            refreshShield()
            EngineAdapter.themeChanged()
        }
        return true
    }

    // singleTask: a link from another app arrives here while Ardour is already open.
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (::pageArea.isInitialized) handleLink(intent)
    }

    // Links from other apps: only http and https with a host are opened (DECISIONS.md 54).
    private fun handleLink(intent: Intent) {
        if (intent.action != Intent.ACTION_VIEW) return
        if ((intent.flags and Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY) != 0) return
        val uri = intent.data ?: return
        val scheme = uri.scheme?.lowercase() ?: return
        if ((scheme != "http" && scheme != "https") || uri.host.isNullOrEmpty()) return
        if (finding) closeFind()
        if (reader != null) closeReader()
        if (tabSwitcher.showing) tabSwitcher.hide()
        EngineAdapter.load(uri.toString())
        beginNavigation(uri.toString())
        leaveAddress()
    }

    private fun px(n: Int) = Ui.dp(this, n)

    // The top row sits 1 dp above the top of the page area, between the old lift of 7 dp and the gap of 5 dp (DECISIONS.md 105, 171, 172).
    private fun spaceTopBar() {
        if (!::topBar.isInitialized) return
        (topBar.layoutParams as? LinearLayout.LayoutParams)?.let {
            if (it.topMargin != -px(1)) {
                it.topMargin = -px(1)
                topBar.layoutParams = it
            }
        }
    }

    // Builds the browser screen: top row of address and buttons, page area, start and error views, find bar; wires the engine.
    private fun buildBrowser(root: LinearLayout) {
        rootLayout = root
        backButton = IconButton(this, IconButton.Kind.BACK).apply {
            contentDescription = "Back"
            isEnabled = false
            setOnClickListener { stepBack() }
            onSwipeDown = { stepBack() }
        }
        forwardButton = IconButton(this, IconButton.Kind.FORWARD).apply {
            contentDescription = "Forward"
            isEnabled = false
            setOnClickListener { stepForward() }
            onSwipeDown = { stepForward() }
        }
        reloadButton = IconButton(this, IconButton.Kind.RELOAD).apply {
            contentDescription = "Reload"
            isEnabled = false
            setOnClickListener { if (loading) EngineAdapter.stop() else EngineAdapter.reload() }
            onSwipeDown = { if (loading) EngineAdapter.stop() else EngineAdapter.reload() }
        }
        val homeButton = IconButton(this, IconButton.Kind.HOME).apply {
            contentDescription = "Home"
            setOnClickListener { showHome() }
            onSwipeDown = { showHome() }
        }
        tabsButton = IconButton(this, IconButton.Kind.TABS).apply {
            contentDescription = "Tabs"
            setOnClickListener { openTabs() }
            onSwipeDown = { openTabs() }
            // A long press opens a new tab (DECISIONS.md 165).
            setOnLongClickListener {
                newTabFromBar()
                true
            }
        }
        privateButton = IconButton(this, IconButton.Kind.PRIVATE).apply {
            contentDescription = if (privateOn) "Shield Mode, on" else "Shield Mode, off"
            if (privateOn) setOn(true)
            setOnClickListener { togglePrivate(this) }
            setOnLongClickListener {
                quickClean(this)
                true
            }
            onSwipeDown = { togglePrivate(this) }
        }
        val menuButton = IconButton(this, IconButton.Kind.MENU).apply {
            contentDescription = "Menu"
            setOnClickListener { showMenu(this) }
            onSwipeDown = { showMenu(this) }
        }
        // One top row: the address pill at the left, seven equal buttons at the right edge. Editing the address
        // widens the pill over the buttons (setExpanded).
        buttonGroup = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            for (b in listOf(backButton, forwardButton, reloadButton, homeButton, tabsButton, privateButton, menuButton)) {
                // The menu button is narrower; the other six share its room (DECISIONS.md 169).
                addView(b, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, if (b === menuButton) 0.7f else 1f))
            }
        }
        address = AddressField(this, ::submit, ::onAddressFocus)
        val bar = FrameLayout(this).apply {
            clipChildren = false
            addView(buttonGroup, FrameLayout.LayoutParams(px(150), Ui.sdp(this@MainActivity, ROW_HEIGHT), Gravity.END or Gravity.TOP))
            addView(address, FrameLayout.LayoutParams(px(150), Ui.sdp(this@MainActivity, ROW_HEIGHT), Gravity.START or Gravity.TOP))
            addOnLayoutChangeListener { _, l, _, r, _, _, _, _, _ -> sizeTopRow(r - l) }
        }
        val top = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            clipChildren = false
            setPadding(px(10), 0, px(10), 0)
        }
        // The loading line lives inside the pill, so the row is only as tall as the pill plus room for its glow.
        top.addView(bar, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Ui.sdp(this, ROW_HEIGHT) + px(2)))
        // The tab strip above the row, when the Settings switch is on (DECISIONS.md 173).
        tabStrip = TabStrip(this, ::switchTo, ::closeTabAt, ::moveTab)
        tabStrip.visibility = if (Prefs.get(this, Prefs.TOP_TABS)) View.VISIBLE else View.GONE
        top.addView(tabStrip, 0, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, TabSizes.height(this)).apply { bottomMargin = px(4) })
        topBar = top
        root.clipToPadding = false
        root.addView(top, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        val content = PullRefreshLayout(
            this,
            { hasPage && errorView.visibility != View.VISIBLE && !(::tabSwitcher.isInitialized && tabSwitcher.showing) && EngineAdapter.atTop() },
            { EngineAdapter.reload() },
        )
        pageArea = content
        val fill = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        val first = EngineAdapter.createView(this, listener) { root.post { recreate() } }
        pageView = first
        content.addView(first, fill)
        EngineAdapter.cleanLeftover(this)
        startView = startScreen()
        content.addView(startView, fill)
        errorView = errorScreen().apply { visibility = View.GONE }
        content.addView(errorView, fill)
        content.attachIndicator()
        floatingBar = newFloatingBar()
        address.input.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: android.text.Editable?) {
                floatingBar.refresh(!s.isNullOrEmpty(), clipboardHasText())
                if (address.input.hasFocus() && !address.filling) {
                    suggest.query(s?.toString().orEmpty())
                } else {
                    closeSuggestions()
                }
            }
        })
        content.addView(floatingBar, floatingBarParams())
        suggestList = SuggestionList(this) { pick ->
            closeSuggestions()
            submit(pick)
        }
        suggest = Suggestions.Session { suggestList.showItems(it) }
        content.addView(
            suggestList,
            FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP).apply {
                marginStart = px(10)
                marginEnd = px(10)
                topMargin = px(8) + Ui.sdp(this@MainActivity, 36) + px(10) + px(8)
            },
        )
        tabSwitcher = TabSwitcher(this, ::switchTo, ::closeTabAt, ::openNewTab) { applySecure() }
        root.addView(content, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))

        findBar = FindBar(this, { EngineAdapter.findAll(it) }, { EngineAdapter.findNext(it) }, ::closeFind).apply { visibility = View.GONE }
        root.addView(findBar, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        Ui.enter(listOf(top, startView))
        refreshTabCount()
        refreshBack()
    }

    private fun newFloatingBar() = EditBar(
        this,
        onCopy = { copyAddress() },
        onPaste = { pasteIntoAddress() },
        onClear = { address.input.setText("") },
        onGo = { submit(address.input.text.toString()) },
    )

    private fun floatingBarParams() =
        FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP or Gravity.CENTER_HORIZONTAL)
            .apply { topMargin = px(8) }

    // The system font size changed: the top row and the edit bar follow it in place; the page and its history stay
    // (DECISIONS.md 112).
    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        if (!::floatingBar.isInitialized) return
        if (restyleIfNeeded()) return
        val row = Ui.sdp(this, ROW_HEIGHT)
        buttonGroup.layoutParams.height = row
        address.layoutParams.height = row
        val bar = (topBar as ViewGroup).getChildAt(0)
        bar.layoutParams.height = row + px(2)
        address.applyScale()
        val old = floatingBar
        floatingBar = newFloatingBar().also {
            it.visibility = old.visibility
            it.alpha = old.alpha
            it.translationY = old.translationY
        }
        pageArea.removeView(old)
        pageArea.addView(floatingBar, floatingBarParams())
        floatingBar.refresh(address.input.text.isNotEmpty(), clipboardHasText())
        val width = rowWidth
        rowWidth = 0
        sizeTopRow(width)
        buttonGroup.requestLayout()
        address.requestLayout()
        bar.requestLayout()
    }

    // Row width known: the pill takes half of it as before, seven equal buttons share the other half; on a narrow
    // screen the buttons keep 30 dp and the pill gives way (DECISIONS.md 127, 128).
    private fun sizeTopRow(w: Int) {
        if (w <= 0 || w == rowWidth) return
        rowWidth = w
        val gap = px(8)
        val each = maxOf((w - gap - (w - gap) / 2) / 7, px(30))
        idleWidth = w - gap - each * 7
        val buttons = buttonGroup.layoutParams as FrameLayout.LayoutParams
        buttons.width = each * 7
        buttonGroup.layoutParams = buttons
        val pill = address.layoutParams as FrameLayout.LayoutParams
        pill.width = if (expanded) w else idleWidth
        address.layoutParams = pill
    }

    // Copy action of the floating bar over the page while the address is edited.
    private fun copyAddress() = Clipboard.copy(this, address.input.text.toString())

    private var noticeView: TextView? = null

    // A short pill at the bottom of the page, for example when a link has no app to open it.
    private fun showNotice(text: String) {
        val v = noticeView ?: TextView(this).apply {
            textSize = 14f
            typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL)
            setTextColor(Ui.INK)
            gravity = Gravity.CENTER
            setPadding(px(18), px(10), px(18), px(10))
            background = android.graphics.drawable.GradientDrawable().apply {
                setColor(Ui.cardAlpha(0xF2))
                cornerRadius = px(22).toFloat()
                setStroke(px(1), 0x40E8451D)
            }
            elevation = px(8).toFloat()
            outlineSpotShadowColor = 0x70E8451D
            visibility = View.GONE
            pageArea.addView(this, FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL).apply { bottomMargin = px(56) })
        }.also { noticeView = it }
        v.text = text
        v.animate().cancel()
        v.removeCallbacks(hideNotice)
        v.visibility = View.VISIBLE
        v.alpha = 0f
        v.translationY = px(12).toFloat()
        v.animate().alpha(1f).translationY(0f).setDuration(320).setInterpolator(SpringInterpolator()).withEndAction {
            v.postDelayed(hideNotice, 1800)
        }.start()
    }

    private var topNoticeView: TextView? = null
    private var bubbleDrop = 0f
    private var bubbleGrow = 0f
    private var bubbleFold = 0f
    private var bubbleAnims = ArrayList<ValueAnimator>()

    // A bubble drops from the top edge as a small ember-rimmed circle, bubbles out into a pill with the text, holds, then
    // folds back to a circle and rises away (DECISIONS.md 166, 171). For a new tab made by the Tabs button's long press.
    private fun showTopNotice(text: String) {
        val v = topNoticeView ?: TextView(this).apply {
            textSize = 14f
            typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL)
            setTextColor(Ui.INK)
            gravity = Gravity.CENTER
            setPadding(px(18), px(9), px(18), px(9))
            background = android.graphics.drawable.GradientDrawable().apply {
                setColor(Ui.cardAlpha(0xF2))
                cornerRadius = px(22).toFloat()
                setStroke(Ui.dp(this@MainActivity, 1.5f).toInt(), Ui.EMBER)
            }
            elevation = px(8).toFloat()
            outlineSpotShadowColor = 0x70E8451D
            visibility = View.INVISIBLE
            pageArea.addView(this, FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP or Gravity.CENTER_HORIZONTAL).apply { topMargin = px(12) })
        }.also { topNoticeView = it }
        v.text = text
        endBubble()
        v.removeCallbacks(hideTopNotice)
        bubbleDrop = 0f
        bubbleGrow = 0f
        bubbleFold = 0f
        v.alpha = 0f
        v.visibility = View.INVISIBLE
        // Started once the pill has its size: the circle it starts from is worked out from it.
        v.post {
            applyBubble()
            v.visibility = View.VISIBLE
            val drop = bubbleAnimator(0f, 1f, 380L, 0L, SpringInterpolator()) { bubbleDrop = it }
            val grow = bubbleAnimator(0f, 1f, 520L, 230L, SpringInterpolator()) { bubbleGrow = it }
            grow.addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    if (bubbleAnims.contains(grow) && bubbleGrow >= 1f) v.postDelayed(hideTopNotice, 1300)
                }
            })
            bubbleAnims.addAll(listOf(drop, grow))
            drop.start()
            grow.start()
        }
    }

    private fun bubbleAnimator(from: Float, to: Float, ms: Long, delay: Long, ease: android.animation.TimeInterpolator, set: (Float) -> Unit) =
        ValueAnimator.ofFloat(from, to).apply {
            duration = ms
            startDelay = delay
            interpolator = ease
            addUpdateListener {
                set(it.animatedValue as Float)
                applyBubble()
            }
        }

    private fun endBubble() {
        for (a in bubbleAnims) a.cancel()
        bubbleAnims.clear()
    }

    // The three stages decide everything the bubble shows: dropping in (position, fade), growing from a circle into the
    // pill (size, then the text), folding back (the same in reverse, then up and out).
    private fun applyBubble() {
        val v = topNoticeView ?: return
        if (v.width == 0 || v.height == 0) return
        val round = px(26).toFloat()
        val smallX = (round / v.width).coerceAtMost(1f)
        val smallY = (round / v.height).coerceAtMost(1f)
        val fold = bubbleFold * bubbleFold * (3f - 2f * bubbleFold)
        val open = bubbleGrow * (1f - fold)
        v.scaleX = smallX + (1f - smallX) * open
        v.scaleY = smallY + (1f - smallY) * open
        v.translationY = -px(36) * (1f - bubbleDrop) - px(24) * bubbleFold
        v.alpha = minOf(bubbleDrop * 3f, 1f) * (1f - ((bubbleFold - 0.7f) / 0.3f).coerceIn(0f, 1f))
        val ink = (255 * ((open - 0.5f) / 0.4f).coerceIn(0f, 1f)).toInt()
        v.setTextColor((Ui.INK and 0x00FFFFFF) or (ink shl 24))
    }

    private val hideTopNotice = Runnable {
        val v = topNoticeView ?: return@Runnable
        val fold = bubbleAnimator(0f, 1f, 480L, 0L, android.view.animation.AccelerateDecelerateInterpolator()) { bubbleFold = it }
        fold.addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator) {
                if (bubbleFold >= 1f) v.visibility = View.INVISIBLE
            }
        })
        bubbleAnims.add(fold)
        fold.start()
    }

    private val hideNotice = Runnable {
        noticeView?.animate()?.alpha(0f)?.setDuration(260)?.withEndAction { noticeView?.visibility = View.GONE }?.start()
    }

    // Whether the clipboard holds text; the content is read once per copied clip (DECISIONS.md 124).
    private fun clipboardHasText(): Boolean = Clipboard.hasText(this)

    private fun pasteIntoAddress() {
        val text = Clipboard.text(this)
        if (text.isEmpty()) {
            floatingBar.refresh(address.input.text.isNotEmpty(), clipboardHasText())
            return
        }
        val field = address.input
        val a = field.selectionStart.coerceAtLeast(0)
        val b = field.selectionEnd.coerceAtLeast(0)
        field.text.replace(minOf(a, b), maxOf(a, b), text)
        floatingBar.refresh(field.text.isNotEmpty(), clipboardHasText())
    }

    // The pill widens from half the row to the full row, left to right, over the buttons; they fade away.
    private fun setExpanded(on: Boolean) {
        if (expanded == on) return
        expanded = on
        widthAnim?.cancel()
        if (rowWidth > 0) {
            val from = (address.layoutParams as FrameLayout.LayoutParams).width
            widthAnim = ValueAnimator.ofInt(from, if (on) rowWidth else idleWidth).apply {
                duration = 340
                interpolator = PathInterpolator(0.2f, 0f, 0f, 1f)
                addUpdateListener {
                    val pill = address.layoutParams as FrameLayout.LayoutParams
                    pill.width = it.animatedValue as Int
                    address.layoutParams = pill
                }
                start()
            }
        }
        buttonGroup.animate().cancel()
        buttonGroup.animate().alpha(if (on) 0f else 1f).translationX(if (on) px(24).toFloat() else 0f)
            .setDuration(if (on) 200L else 280L).start()
        floatingBar.animate().cancel()
        if (on) {
            floatingBar.refresh(address.input.text.isNotEmpty(), clipboardHasText())
            floatingBar.visibility = View.VISIBLE
            floatingBar.playIn()
            floatingBar.translationY = -px(10).toFloat()
            floatingBar.animate().alpha(1f).translationY(0f).setStartDelay(120).setDuration(380)
                .setInterpolator(SpringInterpolator()).start()
        } else {
            floatingBar.animate().alpha(0f).translationY(-px(6).toFloat()).setStartDelay(0).setDuration(160)
                .setInterpolator(android.view.animation.LinearInterpolator())
                .withEndAction { if (!expanded) floatingBar.visibility = View.GONE }.start()
        }
    }

    // Wordmark in the ember gradient over a quiet hint; shown until the first page starts.
    // Fresh page: the search block (box, voice language, mic, search engine) above the name (DECISIONS.md 117).
    private fun startScreen(): View = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER
        setBackgroundColor(Ui.PAPER)
        clipChildren = false
        searchBlock = StartSearch(this@MainActivity) { submit(it) }.also {
            it.onEngineChanged = { address.refreshHint() }
            it.onBoxFocus = { focused -> fadeField(address, !focused) }
        }
        addView(searchBlock, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            marginStart = px(24)
            marginEnd = px(24)
        })
        val mark = TextView(context).apply {
            text = "Ardour"
            textSize = 44f
            typeface = android.graphics.Typeface.create("sans-serif", android.graphics.Typeface.BOLD)
            letterSpacing = -0.03f
            setTextColor(Ui.EMBER)
            paint.shader = LinearGradient(0f, 0f, paint.measureText("Ardour"), 0f, Ui.EMBER, Ui.AMBER, Shader.TileMode.CLAMP)
            setPadding(0, Ui.dp(context, 28), 0, Ui.dp(context, 96))
        }
        addView(mark, centered())
        // A short page area (landscape, or the keyboard up) has no room for the generous padding, which cut the name off.
        addOnLayoutChangeListener { _, _, top, _, bottom, _, oldTop, _, oldBottom ->
            if (bottom - top == oldBottom - oldTop) return@addOnLayoutChangeListener
            val tight = bottom - top < Ui.dp(context, 520)
            mark.setPadding(0, Ui.dp(context, if (tight) 10 else 28), 0, Ui.dp(context, if (tight) 10 else 96))
        }
    }

    private fun centered() = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        .apply { gravity = Gravity.CENTER_HORIZONTAL }

    // Shown over the engine's own error page: ring, plain words, one action.
    private fun errorScreen(): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER
        setBackgroundColor(Ui.PAPER)
        isClickable = true
        val pad = Ui.dp(context, 32)
        setPadding(pad, pad, pad, Ui.dp(context, 64))
        addView(TextView(context).apply {
            text = "!"
            textSize = 32f
            typeface = android.graphics.Typeface.create("sans-serif", android.graphics.Typeface.BOLD)
            gravity = Gravity.CENTER
            setTextColor(Ui.EMBER)
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Ui.CARD)
                setStroke(Ui.dp(context, 3), Ui.EMBER)
            }
            elevation = Ui.dp(context, 6).toFloat()
            outlineSpotShadowColor = 0x80E8451D.toInt()
        }, LinearLayout.LayoutParams(Ui.dp(context, 72), Ui.dp(context, 72)).apply { gravity = Gravity.CENTER_HORIZONTAL })
        errorTitle = Ui.title(context, "").apply {
            textSize = 24f
            gravity = Gravity.CENTER
            setPadding(0, Ui.dp(context, 28), 0, Ui.dp(context, 8))
        }
        addView(errorTitle)
        errorBody = Ui.body(context, "").apply {
            textSize = 16f
            gravity = Gravity.CENTER
        }
        addView(errorBody)
        errorHost = Ui.body(context, "").apply {
            gravity = Gravity.CENTER
            setPadding(0, Ui.dp(context, 12), 0, Ui.dp(context, 28))
        }
        addView(errorHost)
        errorRetry = Ui.pillButton(context, "Try again").apply {
            setOnClickListener { failedUrl?.let { EngineAdapter.load(it); beginNavigation(it) } }
        }
        addView(errorRetry, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { gravity = Gravity.CENTER_HORIZONTAL })
        errorSearch = TextView(context).apply {
            text = "Search instead"
            textSize = 15f
            typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL)
            setTextColor(Ui.INK)
            gravity = Gravity.CENTER
            setPadding(Ui.dp(context, 24), Ui.dp(context, 13), Ui.dp(context, 24), Ui.dp(context, 13))
            background = GradientDrawable().apply {
                setColor(Ui.CARD)
                cornerRadius = Ui.dp(context, 28).toFloat()
                setStroke(Ui.dp(context, 1), Ui.LINE)
            }
            isClickable = true
            isFocusable = true
            Ui.springPress(this)
            setOnClickListener {
                failedUrl?.let {
                    val search = UrlInput.searchUrl(UrlInput.display(it))
                    EngineAdapter.load(search)
                    beginNavigation(search)
                }
            }
        }
        addView(errorSearch, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            gravity = Gravity.CENTER_HORIZONTAL
            topMargin = Ui.dp(context, 12)
        })
    }

    // True when the phone has a network that reaches the internet (a validated one). Reads local state only.
    private fun isOnline(): Boolean {
        val cm = getSystemService(CONNECTIVITY_SERVICE) as ConnectivityManager
        val caps = cm.activeNetwork?.let { cm.getNetworkCapabilities(it) } ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }

    private fun showError(url: String?, found: PageError) {
        failedUrl = url
        // A lookup or connection failure with no working network is "offline", whatever the engine says.
        val kind = if ((found == PageError.NOT_FOUND || found == PageError.UNREACHABLE) && !isOnline()) PageError.OFFLINE else found
        val (title, body) = when (kind) {
            PageError.OFFLINE -> "No internet connection" to "Check your connection, then try again."
            PageError.NOT_FOUND -> "This site can't be found" to "Check the address for typing mistakes."
            PageError.UNREACHABLE -> "Can't reach this page" to "The site may be down, or your connection may be weak."
            PageError.SECURITY -> "This connection isn't secure" to "Ardour stopped the page because its security certificate can't be trusted."
            PageError.NOT_SECURE -> "Ardour won't open this page" to "The address uses an unencrypted connection (http)."
            PageError.OTHER -> "This page didn't load" to "Something went wrong while loading it."
        }
        errorTitle.text = title
        errorBody.text = body
        errorHost.text = url?.let { UrlInput.display(it) } ?: ""
        errorRetry.visibility = if (kind == PageError.NOT_SECURE || url == null) View.GONE else View.VISIBLE
        errorSearch.visibility = if (kind == PageError.NOT_FOUND && url != null) View.VISIBLE else View.GONE
        errorView.alpha = 0f
        errorView.visibility = View.VISIBLE
        errorView.animate().alpha(1f).setDuration(220).start()
    }

    // Android hides the clipboard from an app without window focus, so Paste is read again when focus returns.
    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) refreshClipboardButtons()
    }

    override fun onResume() {
        super.onResume()
        if (restyleIfNeeded()) return
        applySecure()
        Clipboard.watch(this, clipListener)
        refreshClipboardButtons()
        if (!::pageArea.isInitialized) return
        pageArea.enabledPull = Prefs.get(this, Prefs.PULL)
        EngineAdapter.maxTabs = Prefs.getInt(this, Prefs.TAB_MAX, 12).coerceIn(1, 26)
        if (::tabStrip.isInitialized) {
            tabStrip.visibility = if (Prefs.get(this, Prefs.TOP_TABS)) View.VISIBLE else View.GONE
            (tabStrip.layoutParams as? LinearLayout.LayoutParams)?.let {
                if (it.height != TabSizes.height(this)) {
                    it.height = TabSizes.height(this)
                    tabStrip.layoutParams = it
                }
            }
            tabStrip.applySize()
            refreshStrip()
        }
        if (::address.isInitialized) address.refreshAddressText()
        applyEnginePrefs()
    }

    // The Settings choices that live on the page view; applied to the active tab each time the screen resumes or the
    // active tab changes.
    private fun applyEnginePrefs() {
        EngineAdapter.setZoomMode(Prefs.get(this, Prefs.FORCE_ZOOM), Prefs.get(this, Prefs.WRAP))
        EngineAdapter.setThirdPartyCookies(Prefs.get(this, Prefs.THIRD_PARTY, false))
    }

    // Tabs (DECISIONS.md 161). Each tab keeps its own page, history and start-screen state; the screen shows one.
    // The Shield button and the secure window follow the active tab.
    private fun refreshShield() {
        privateButton.setOn(privateOn)
        privateButton.contentDescription = if (privateOn) "Shield Mode, on" else "Shield Mode, off"
        applySecure()
    }

    private fun refreshTabCount() {
        tabsButton.count = EngineAdapter.tabs.size
        refreshStrip()
    }

    // A tab dragged to another place in the strip (DECISIONS.md 176).
    private fun moveTab(from: Int, to: Int) {
        EngineAdapter.moveTab(from, to)
        refreshTabCount()
        if (::tabSwitcher.isInitialized) tabSwitcher.update(tabItems(), EngineAdapter.activeIndex())
    }

    // The tab strip follows the tabs, their titles and the open tab.
    private fun refreshStrip() {
        if (!::tabStrip.isInitialized || tabStrip.visibility != View.VISIBLE) return
        tabStrip.set(tabItems(), EngineAdapter.activeIndex())
    }

    // The active tab's start-screen state is held by the screen while it is shown; copied to its tab before the screen
    // leaves it (a switch, or the screen ending).
    private fun saveTabState() {
        val tab = EngineAdapter.activeTab() ?: return
        tab.blank = !hasPage && !homeOver
        tab.home = homeOver
        tab.startBack = startBack
    }

    // A tab that was never navigated shows the start screen; one with a page shows it.
    private fun showTabState() {
        val tab = EngineAdapter.activeTab() ?: return
        if (reader != null) closeReader()
        if (finding) closeFind()
        if (address.input.hasFocus()) leaveAddress()
        closeSuggestions()
        errorView.visibility = View.GONE
        homeOver = tab.home
        startBack = tab.startBack
        hasPage = !tab.blank && !tab.home
        val start = tab.blank || tab.home
        searchBlock.reset()
        startView.animate().cancel()
        // A cancelled slide-in leaves the view part way: put it in place (DECISIONS.md 168, NOTES 23).
        startView.alpha = 1f
        startView.translationY = 0f
        startView.visibility = if (start) View.VISIBLE else View.GONE
        address.showUrl(if (start) "" else tab.url.orEmpty())
        listener.onLoading(hasPage && EngineAdapter.activeProgress() < 100)
        backButton.isEnabled = if (homeOver) !startBack else EngineAdapter.canGoBack() || hasPage
        forwardButton.isEnabled = EngineAdapter.canGoForward() || (homeOver && startBack)
        reloadButton.isEnabled = hasPage
        refreshBack()
    }

    // Puts the tab at `index` on screen: its page view replaces the current one. A tab whose view was ended with an
    // earlier screen loads its last address now.
    private fun activate(index: Int) {
        val wakeUrl = if (EngineAdapter.sleeping(index)) EngineAdapter.tabs[index].url else null
        pageView?.let { pageArea.removeView(it) }
        val view = EngineAdapter.select(index, this)
        pageArea.addView(view, 0, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        pageView = view
        applyEnginePrefs()
        showTabState()
        refreshShield()
        if (wakeUrl != null) {
            EngineAdapter.load(wakeUrl)
            if (hasPage) beginNavigation(wakeUrl)
        }
        refreshTabCount()
    }

    private fun tabItems(): List<TabSwitcher.Item> {
        val active = EngineAdapter.activeIndex()
        val pictureForShield = Prefs.get(this, Prefs.SHIELD_PREVIEW)
        return EngineAdapter.tabs.mapIndexed { i, tab ->
            // A tab whose start screen is up is listed as the start page, whatever page lies under it.
            val start = if (i == active) !hasPage else tab.blank || tab.home
            val url = tab.url
            val host = url?.let { android.net.Uri.parse(it).host }?.removePrefix("www.").orEmpty()
            val title = tab.title?.trim().orEmpty().ifEmpty { host }.ifEmpty { "New tab" }
            TabSwitcher.Item(
                if (start) "Start page" else title,
                if (start) "Ardour" else host,
                start,
                tab.shield,
                if (!tab.shield || pictureForShield) tab.preview else null,
                tab.id,
            )
        }
    }

    // A small picture of what the active tab shows, for its card. Taken only when it will be shown: not for a Shield tab
    // unless Settings allows its preview. Memory only (DECISIONS.md 162).
    private fun capturePreview() {
        val tab = EngineAdapter.activeTab() ?: return
        if (tab.shield && !Prefs.get(this, Prefs.SHIELD_PREVIEW)) {
            tab.preview = null
            return
        }
        val source: View = (if (!hasPage) startView else pageView) ?: return
        if (source.width <= 0 || source.height <= 0) return
        try {
            val scale = 360f / source.width
            val bmp = android.graphics.Bitmap.createBitmap(360, (source.height * scale).toInt().coerceAtLeast(1), android.graphics.Bitmap.Config.ARGB_8888)
            val canvas = android.graphics.Canvas(bmp)
            canvas.scale(scale, scale)
            canvas.drawColor(Ui.PAPER)
            source.draw(canvas)
            tab.preview = bmp
        } catch (e: Exception) {
            tab.preview = null
        }
    }

    private fun openTabs() {
        if (tabSwitcher.showing) {
            tabSwitcher.hide()
            return
        }
        if (finding) closeFind()
        if (address.input.hasFocus()) leaveAddress()
        closeSuggestions()
        saveTabState()
        capturePreview()
        tabSwitcher.show(tabItems(), EngineAdapter.activeIndex())
        applySecure()
    }

    private fun switchTo(index: Int) {
        tabSwitcher.hide()
        if (index == EngineAdapter.activeIndex()) return
        saveTabState()
        activate(index)
    }

    // The Tabs button's long press: a new tab, shown at once.
    private fun newTabFromBar() {
        if (EngineAdapter.tabs.size >= EngineAdapter.maxTabs) {
            showNotice("Tab limit reached (${EngineAdapter.maxTabs})")
            return
        }
        tabsButton.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)
        saveTabState()
        activate(EngineAdapter.newTab())
        showTopNotice("New Tab")
    }

    // A tap on + opens a normal tab, a long press a Shield tab (DECISIONS.md 163).
    private fun openNewTab(shield: Boolean) {
        if (EngineAdapter.tabs.size >= EngineAdapter.maxTabs) {
            tabSwitcher.limitNote()
            return
        }
        saveTabState()
        tabSwitcher.hide()
        val index = EngineAdapter.newTab()
        if (shield) EngineAdapter.startShield(EngineAdapter.tabs[index])
        activate(index)
    }

    // Closes a tab. A Shield tab first follows the Settings choice for leaving Shield Mode: delete what it added, keep, or ask.
    private fun closeTabAt(index: Int) {
        val tab = EngineAdapter.tabs.getOrNull(index) ?: return
        if (!tab.shield) {
            removeTab(tab)
            return
        }
        when (Prefs.getString(this, Prefs.PRIVATE_LEAVE, Prefs.LEAVE_KEEP)) {
            Prefs.LEAVE_DELETE -> {
                EngineAdapter.endShield(tab, true)
                removeTab(tab)
            }
            Prefs.LEAVE_KEEP -> {
                EngineAdapter.endShield(tab, false)
                removeTab(tab)
            }
            else -> sheet.show(
                title = "Close Shield tab?",
                body = "Delete all removes the cookies, site data and cache this tab's Shield Mode session added. Sign-ins you already had stay.",
                confirm = "Delete all",
                cancelLabel = "Continue as is",
                onCancelTap = {
                    EngineAdapter.endShield(tab, false)
                    removeTab(tab)
                },
                dangerConfirm = true,
            ) {
                EngineAdapter.endShield(tab, true)
                removeTab(tab)
            }
        }
    }

    // The next tab (or, for the last, a new empty one) takes its place when the closed one was the active one.
    private fun removeTab(tab: Tab) {
        val index = EngineAdapter.tabs.indexOf(tab)
        if (index < 0) return
        val wasActive = index == EngineAdapter.activeIndex()
        if (wasActive) {
            pageView?.let { pageArea.removeView(it) }
            pageView = null
        } else {
            saveTabState()
        }
        EngineAdapter.closeTab(index)
        if (EngineAdapter.tabs.isEmpty()) EngineAdapter.newTab()
        if (wasActive) activate(minOf(index, EngineAdapter.tabs.lastIndex)) else refreshTabCount()
        tabSwitcher.update(tabItems(), EngineAdapter.activeIndex())
        applySecure()
    }

    // A screen built again (theme change, ended renderer) shows the active tab's page again.
    private fun restoreTabState() {
        val tab = EngineAdapter.activeTab() ?: return
        val url = tab.url ?: return
        if (tab.blank) return
        EngineAdapter.load(url)
        beginNavigation(url)
    }

    private fun showMenu(anchor: View) {
        MenuPopup.show(
            anchor,
            listOf(
                MenuItem("Share", enabled = hasPage) { share() },
                MenuItem("Find in page", enabled = hasPage) { openFind() },
                MenuItem("Reader mode", enabled = hasPage && errorView.visibility != View.VISIBLE) { openReader() },
                MenuItem("Print or save as PDF", enabled = hasPage) { printPage() },
                MenuItem("Desktop site", toggle = EngineAdapter.desktopSite, dividerBefore = true) {
                    EngineAdapter.setDesktopSite(!EngineAdapter.desktopSite, hasPage)
                },
                *resetItems(),
                MenuItem("Clear site data", enabled = hasPage && siteHost() != null, dividerBefore = true) { confirmClearSite() },
                MenuItem("Settings") { startActivity(Intent(this, SettingsActivity::class.java)) },
            ),
        )
    }

    private val sheet = ConfirmSheet(this) { refreshBack() }

    // The open page's host when it is a web address, else null.
    private fun siteHost(): String? {
        val uri = android.net.Uri.parse(EngineAdapter.currentUrl() ?: return null)
        return if (uri.scheme == "http" || uri.scheme == "https") uri.host else null
    }

    // Asks first, with three choices (DECISIONS.md 81, 82). Login details and saved site data start ticked, cache not.
    private fun confirmClearSite() {
        val url = EngineAdapter.currentUrl() ?: return
        val host = siteHost() ?: return
        sheet.show(
            "Clear data for $host?",
            "Choose what to remove. The page reloads afterwards. History and bookmarks stay.",
            "Clear",
            listOf(
                Choice("Login details", "Cookies. You are signed out; sites sharing its sign-in, such as other Google services, too.", true),
                Choice("Saved site data", "Local storage, databases and the saved location permission.", true),
                Choice("Cache", "Saved page files. Applies to all sites, not only this one.", false),
            ),
        ) { ticked ->
            val (logins, siteData, cache) = ticked
            EngineAdapter.clearSiteData(url, logins, siteData, cache)
            EngineAdapter.reloadFresh()
            val site = listOfNotNull("login details".takeIf { logins }, "saved site data".takeIf { siteData }).joinToString(" and ")
            showNotice(
                when {
                    site.isEmpty() -> "Cleared the cache"
                    cache -> "Cleared $site for $host, and the cache"
                    else -> "Cleared $site for $host"
                },
            )
        }
    }

    // "Reset text size" appears only when Wrap text is on and the text size was changed by pinching.
    private fun resetItems(): Array<MenuItem> =
        if (Prefs.get(this, Prefs.WRAP) && EngineAdapter.textSizeChanged()) {
            arrayOf(MenuItem("Reset text size") { EngineAdapter.resetTextSize() })
        } else {
            emptyArray()
        }

    // Reader mode: the reader covers the whole window at once, the engine gathers the page's main text behind it,
    // and Ardour draws that text itself (DECISIONS.md 55).
    private fun openReader() {
        if (reader != null) return
        val view = ReaderView(this, ::closeReader, ::onReaderStyle, ::reportPage)
        reader = view
        view.setPadding(rootLayout.paddingLeft, rootLayout.paddingTop, rootLayout.paddingRight, rootLayout.paddingBottom)
        findViewById<ViewGroup>(android.R.id.content).addView(
            view,
            ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT),
        )
        view.start()
        refreshBack()
        // The same page, not loaded again since: its text is already known, so no preparing screen.
        val known = cachedArticle?.takeIf { cachedLoadId == loadId }
        if (known != null) {
            view.setArticle(known)
            return
        }
        EngineAdapter.extractArticle { raw ->
            if (reader === view && !isDestroyed && !isFinishing) {
                val article = Article.parse(raw)
                if (article != null && article.readable && !loading) {
                    cachedArticle = article
                    cachedLoadId = loadId
                }
                view.setArticle(article)
            }
        }
    }

    // Long press on the reader's title: copies a report about the page's structure and slow requests (no page text).
    private fun reportPage() {
        EngineAdapter.pageReport { text ->
            if (text.isNullOrEmpty()) {
                reader?.notice("No report")
            } else {
                (getSystemService(CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("Ardour report", text))
                reader?.notice("Report copied")
            }
        }
    }

    private fun closeReader() {
        val view = reader ?: return
        reader = null
        (view.parent as? ViewGroup)?.removeView(view)
        onReaderStyle(!Ui.dark)
        refreshBack()
    }

    // Whether the system bar icons are dark (on a light background) while the reader is shown.
    @Suppress("DEPRECATION")
    private fun onReaderStyle(lightBackground: Boolean) {
        if (Build.VERSION.SDK_INT >= 30) {
            val mask = WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS or WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS
            window.insetsController?.setSystemBarsAppearance(if (lightBackground) mask else 0, mask)
        } else {
            val mask = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
            val flags = window.decorView.systemUiVisibility
            window.decorView.systemUiVisibility = if (lightBackground) flags or mask else flags and mask.inv()
        }
    }

    // The system print screen: printers, or Save as PDF. Ardour sends nothing itself.
    private fun printPage() {
        val name = EngineAdapter.pageTitle()?.takeIf { it.isNotBlank() } ?: "Page"
        val adapter = EngineAdapter.printAdapter(name) ?: return
        val manager = getSystemService(Context.PRINT_SERVICE) as PrintManager
        manager.print(name, adapter, PrintAttributes.Builder().build())
    }

    // The system share sheet; Ardour sends nothing itself.
    private fun share() {
        val url = EngineAdapter.currentUrl() ?: return
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, url)
        }
        try {
            startActivity(Intent.createChooser(send, null))
        } catch (_: ActivityNotFoundException) {
            // No app can take the text.
        }
    }

    private fun submit(text: String) {
        val target = UrlInput.resolve(text) ?: return
        Trace.log("ACTION", "submit ${text.length} characters typed")
        EngineAdapter.load(target)
        leaveAddress()
        beginNavigation(target)
    }

    // A navigation the user started (Go, a tapped link, a link from another app): the address shows where it goes and
    // the bar sweeps at once, instead of staying blank until the engine reports the page started, which can take
    // seconds on a slow connection.
    private fun beginNavigation(url: String) {
        address.showUrl(url)
        revealPage()
        reloadButton.isEnabled = true
        listener.onLoading(true)
    }

    private fun revealPage() {
        if (hasPage) return
        hasPage = true
        homeOver = false
        startBack = false
        searchBlock.reset()
        startView.animate().alpha(0f).setDuration(220).withEndAction { startView.visibility = View.GONE }.start()
    }

    // The Shield Mode button (DECISIONS.md 134, 136, 139): on keeps every sign-in and starts recording what this session adds;
    // off asks, or follows the Settings choice, whether to delete those additions. History is not recorded while on
    // (the history list is not built yet).
    // Screenshots in Shield Mode (DECISIONS.md 156): while it is on the window is secure, so screenshots, screen recordings
    // and casting show nothing, unless the Settings switch allows them. Shield Mode off: never secure.
    private fun applySecure() {
        // Secure while the active tab is shielded, and while the tab list is up and any tab is.
        val listUp = ::tabSwitcher.isInitialized && tabSwitcher.showing && EngineAdapter.privateActive
        val secure = (privateOn || listUp) && !Prefs.get(this, Prefs.SHIELD_SHOTS)
        if (secure) window.addFlags(WindowManager.LayoutParams.FLAG_SECURE) else window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
    }

    private fun togglePrivate(button: IconButton) {
        if (!privateOn) {
            EngineAdapter.activeTab()?.let { EngineAdapter.startShield(it) }
            applySecure()
            button.setOn(true)
            button.contentDescription = "Shield Mode, on"
            showNotice("Shield Mode on. Your sign-ins stay.")
            return
        }
        when (Prefs.getString(this, Prefs.PRIVATE_LEAVE, Prefs.LEAVE_KEEP)) {
            Prefs.LEAVE_DELETE -> leavePrivate(button, true)
            Prefs.LEAVE_KEEP -> leavePrivate(button, false)
            else -> sheet.show(
                title = "Leave Shield Mode?",
                body = "Delete all removes the cookies, site data and cache this tab's Shield Mode session added. Sign-ins you already had stay.",
                confirm = "Delete all",
                cancelLabel = "Continue as is",
                onCancelTap = { leavePrivate(button, false) },
                dangerConfirm = true,
            ) { leavePrivate(button, true) }
        }
    }

    // The shredder animation over the page area, started by the quick clean (DECISIONS.md 157); skipped when the phone's
    // animations are off.
    private fun shred() {
        if (!android.animation.ValueAnimator.areAnimatorsEnabled()) return
        val at = IntArray(2)
        pageArea.getLocationInWindow(at)
        val content = findViewById<ViewGroup>(android.R.id.content)
        content.addView(ShredView(this, at[1]) { content.removeView(it) }, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
    }

    // Long press on the shield (DECISIONS.md 151): cleans the open page's site at once, Shield Mode on or off, with no
    // question and no notice; the rings on the shield show it is done. Nothing happens in a tab that never held a web page.
    private fun quickClean(button: IconButton) {
        // Any tab that was used: also one now showing the start page over its page (DECISIONS.md 180). A tab never used has no
        // web page and nothing to clean.
        if (EngineAdapter.quickClean()) {
            button.pulseRing()
            shred()
        }
    }

    private fun leavePrivate(button: IconButton, delete: Boolean) {
        EngineAdapter.activeTab()?.let { EngineAdapter.endShield(it, delete) }
        applySecure()
        button.setOn(false)
        button.contentDescription = "Shield Mode, off"
        showNotice(if (delete) "Shield Mode off. This session's additions are deleted." else "Shield Mode off. Nothing deleted.")
    }

    // Home: the start screen over the page; the page and its history stay. Back, or any new load, brings the page back.
    // Reached by Back with the page history used up (`fromBack`, DECISIONS.md 138), it is the first step of the history:
    // Back there does not bring the page back, it asks to be pressed again to exit.
    private fun showHome(fromBack: Boolean = false) {
        if (finding) closeFind()
        if (address.input.hasFocus()) leaveAddress()
        if (!hasPage || homeOver) return
        EngineAdapter.stop()
        homeOver = true
        startBack = fromBack
        hasPage = false
        errorView.visibility = View.GONE
        address.showUrl("")
        reloadButton.isEnabled = false
        backButton.isEnabled = !fromBack
        // The page is kept under the start screen reached by Back, so Forward returns to it.
        forwardButton.isEnabled = fromBack
        startView.animate().cancel()
        startView.alpha = 0f
        startView.visibility = View.VISIBLE
        startView.animate().alpha(1f).setDuration(220).start()
        refreshBack()
    }

    private fun closeHome() {
        if (!homeOver) return
        homeOver = false
        startBack = false
        hasPage = true
        searchBlock.reset()
        startView.animate().cancel()
        startView.animate().alpha(0f).setDuration(220).withEndAction { startView.visibility = View.GONE }.start()
        EngineAdapter.currentUrl()?.let { address.showUrl(it) }
        reloadButton.isEnabled = true
        backButton.isEnabled = true
        forwardButton.isEnabled = EngineAdapter.canGoForward()
        refreshBack()
    }

    // Forward from the start screen that Back brought returns to the kept page; otherwise the page's own history.
    private fun stepForward() {
        if (homeOver && startBack) closeHome() else EngineAdapter.goForward()
    }

    // The Back button: leaves the Home screen first, walks the page history, then returns to the start screen.
    private fun stepBack() {
        when {
            homeOver && !startBack -> closeHome()
            homeOver -> Unit
            EngineAdapter.canGoBack() -> EngineAdapter.goBack()
            hasPage -> showHome(fromBack = true)
        }
    }

    // On the start screen a single Back only warns; a second one within two seconds exits (DECISIONS.md 138).
    private fun exitOnSecondBack(): Boolean {
        val now = SystemClock.uptimeMillis()
        if (now - lastExitBack <= 2000L) {
            finish()
            return true
        }
        lastExitBack = now
        showNotice("Press again to exit")
        return true
    }

    private fun openFind() {
        if (finding) return
        finding = true
        findBar.visibility = View.VISIBLE
        findBar.open()
        refreshBack()
    }

    private fun closeFind() {
        if (!finding) return
        finding = false
        EngineAdapter.clearFind()
        findBar.close()
        findBar.visibility = View.GONE
        refreshBack()
    }

    private fun closeSuggestions() {
        if (!::suggest.isInitialized) return
        suggest.stop()
        suggestList.hideList()
    }

    private fun onAddressFocus(focused: Boolean) {
        if (focused && finding) closeFind()
        if (!focused) closeSuggestions()
        if (::searchBlock.isInitialized) fadeField(searchBlock, !focused)
        setExpanded(focused)
        refreshBack()
    }

    // On the start page only one of the two search fields shows while the other is in use: the top address bar and the box
    // in the middle do the same thing (DECISIONS.md 175). The faded one keeps its place, so nothing moves.
    private fun fadeField(v: View, show: Boolean) {
        v.animate().cancel()
        if (show) {
            v.visibility = View.VISIBLE
            v.animate().alpha(1f).setDuration(240).start()
        } else {
            v.animate().alpha(0f).setDuration(200).withEndAction { v.visibility = View.INVISIBLE }.start()
        }
    }

    private fun leaveAddress() {
        (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager).hideSoftInputFromWindow(address.input.windowToken, 0)
        address.input.clearFocus()
        address.rootView.requestFocus()
    }

    // True when back was used: closes the editor first, then walks the page history.
    private fun handleBack(): Boolean {
        if (tabSwitcher.showing) {
            tabSwitcher.hide()
            return true
        }
        if (sheet.showing) {
            sheet.dismiss()
            return true
        }
        if (reader != null) {
            closeReader()
            return true
        }
        if (finding) {
            closeFind()
            return true
        }
        if (address.input.hasFocus()) {
            leaveAddress()
            return true
        }
        if (::searchBlock.isInitialized && searchBlock.boxFocused()) {
            searchBlock.leaveBox()
            return true
        }
        if (homeOver && !startBack) {
            closeHome()
            return true
        }
        if (!homeOver && EngineAdapter.canGoBack()) {
            EngineAdapter.goBack()
            return true
        }
        if (hasPage && !homeOver) {
            showHome(fromBack = true)
            return true
        }
        // With other tabs open, Back from the start screen closes this tab; the last one asks to be pressed twice to exit.
        if (EngineAdapter.tabs.size > 1) {
            closeTabAt(EngineAdapter.activeIndex())
            return true
        }
        return exitOnSecondBack()
    }

    // Android 13+ with target SDK 36 no longer calls onBackPressed: a back callback must be registered.
    private fun refreshBack() {
        if (Build.VERSION.SDK_INT < 33) return
        // Back always has a step now (start screen, then the exit warning), so the callback stays registered.
        if (backCallback == null) registerBack()
    }

    @TargetApi(33)
    private fun registerBack() {
        val callback = OnBackInvokedCallback { handleBack() }
        onBackInvokedDispatcher.registerOnBackInvokedCallback(OnBackInvokedDispatcher.PRIORITY_DEFAULT, callback)
        backCallback = callback
    }

    @Suppress("DEPRECATION")
    @Deprecated("Used below API 33; newer versions use the back callback above.")
    override fun onBackPressed() {
        if (!handleBack()) super.onBackPressed()
    }

    private fun gateView(message: String): View {
        val pad = Ui.dp(this, 32)
        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(Ui.PAPER)
            setPadding(pad, pad, pad, pad)
        }
        column.addView(Ui.title(this, "Update WebView").apply { textSize = 28f })
        column.addView(Ui.body(this, message).apply {
            textSize = 16f
            setPadding(0, Ui.dp(this@MainActivity, 16), 0, Ui.dp(this@MainActivity, 28))
        })
        column.addView(Ui.pillButton(this, "Open Play Store").apply { setOnClickListener { openWebViewPage() } })
        return column
    }

    // Hands off to the Play Store or browser on the user's tap; Ardour sends nothing itself (DECISIONS.md 34).
    private fun openWebViewPage() {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(WEBVIEW_PAGE)))
        } catch (_: ActivityNotFoundException) {
            // No app can open the link; the message above still tells the user what to do.
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == VoiceInput.REQUEST && ::searchBlock.isInitialized) {
            searchBlock.onPermissionResult(grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED)
        }
    }

    // The clipboard can change while the app is away or while a bar is open: Paste follows it.
    private val clipListener = ClipboardManager.OnPrimaryClipChangedListener {
        refreshClipboardButtons()
    }

    private fun refreshClipboardButtons() {
        if (::floatingBar.isInitialized && floatingBar.visibility == View.VISIBLE) {
            floatingBar.refresh(address.input.text.isNotEmpty(), clipboardHasText())
        }
        if (::searchBlock.isInitialized) searchBlock.refreshClipboard()
    }

    // The mic never keeps listening once the app leaves the screen.
    override fun onPause() {
        Clipboard.unwatch(this, clipListener)
        if (::searchBlock.isInitialized) searchBlock.stopVoice()
        super.onPause()
    }

    override fun onDestroy() {
        if (isFinishing && EngineAdapter.privateActive && Prefs.get(this, Prefs.PRIVATE_CLOSE)) EngineAdapter.deletePrivateAdditions()
        saveTabState()
        EngineAdapter.destroy(keepTabs = !isFinishing)
        if (isFinishing) AppIcon.applyPending(this)
        super.onDestroy()
    }

    private companion object {
        const val ROW_HEIGHT = 34
        const val WEBVIEW_PAGE = "https://play.google.com/store/apps/details?id=com.google.android.webview"
    }
}
