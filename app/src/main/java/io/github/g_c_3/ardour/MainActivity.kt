package io.github.g_c_3.ardour

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
    private var privateOn = EngineAdapter.privateActive // the Private button's state (DECISIONS.md 134)
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
                address.showUrl(url)
                revealPage()
            }
            backButton.isEnabled = if (homeOver) !startBack else canGoBack || hasPage
            forwardButton.isEnabled = canGoForward
            reloadButton.isEnabled = hasPage
            refreshBack()
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
                liftTopBar(bars.top)
            } else {
                v.setPadding(
                    insets.systemWindowInsetLeft,
                    insets.systemWindowInsetTop,
                    insets.systemWindowInsetRight,
                    insets.systemWindowInsetBottom,
                )
                liftTopBar(insets.systemWindowInsetTop)
            }
            insets
        }
        setContentView(root)
        Ui.styleWindow(window)
        root.requestFocus()
        if (gateMessage == null && savedInstanceState == null) handleLink(intent)
        carryUrl?.let {
            carryUrl = null
            if (gateMessage == null) {
                EngineAdapter.load(it)
                beginNavigation(it)
            }
        }
    }

    // The screen was built with the other theme (Settings choice, or the phone's setting while the choice is System):
    // build it again. The open page is loaded again afterwards; its back history is not kept.
    private fun restyleIfNeeded(): Boolean {
        Ui.applyTheme(this)
        if (Ui.dark == builtDark || !::pageArea.isInitialized) return false
        carryUrl = if (hasPage) EngineAdapter.currentUrl() else null
        recreate()
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
        EngineAdapter.load(uri.toString())
        beginNavigation(uri.toString())
        leaveAddress()
    }

    private fun px(n: Int) = Ui.dp(this, n)

    // The status bar's icons sit in the upper part of its height: lift the top row into the free strip below them
    // (DECISIONS.md 105), never on a short status bar.
    private fun liftTopBar(statusBarHeight: Int) {
        if (!::topBar.isInitialized) return
        val lift = if (statusBarHeight >= px(30)) px(7) else 0
        (topBar.layoutParams as? LinearLayout.LayoutParams)?.let {
            if (it.topMargin != -lift) {
                it.topMargin = -lift
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
            setOnClickListener { EngineAdapter.goForward() }
            onSwipeDown = { EngineAdapter.goForward() }
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
        val tabsButton = IconButton(this, IconButton.Kind.TABS).apply {
            contentDescription = "Tabs"
            setOnClickListener { showNotice("Tabs come in a later update") }
            onSwipeDown = { showNotice("Tabs come in a later update") }
        }
        val privateButton = IconButton(this, IconButton.Kind.PRIVATE).apply {
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
                addView(b, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f))
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
        topBar = top
        root.clipToPadding = false
        root.addView(top, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        val content = PullRefreshLayout(
            this,
            { hasPage && errorView.visibility != View.VISIBLE && EngineAdapter.atTop() },
            { EngineAdapter.reload() },
        )
        pageArea = content
        val fill = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        content.addView(EngineAdapter.createView(this, listener) { root.post { recreate() } }, fill)
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
        root.addView(content, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))

        findBar = FindBar(this, { EngineAdapter.findAll(it) }, { EngineAdapter.findNext(it) }, ::closeFind).apply { visibility = View.GONE }
        root.addView(findBar, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        Ui.enter(listOf(top, startView))
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
        searchBlock = StartSearch(this@MainActivity) { submit(it) }.also { it.onEngineChanged = { address.refreshHint() } }
        addView(searchBlock, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            marginStart = px(24)
            marginEnd = px(24)
        })
        addView(TextView(context).apply {
            text = "Ardour"
            textSize = 44f
            typeface = android.graphics.Typeface.create("sans-serif", android.graphics.Typeface.BOLD)
            letterSpacing = -0.03f
            setTextColor(Ui.EMBER)
            paint.shader = LinearGradient(0f, 0f, paint.measureText("Ardour"), 0f, Ui.EMBER, Ui.AMBER, Shader.TileMode.CLAMP)
            setPadding(0, Ui.dp(context, 28), 0, Ui.dp(context, 96))
        }, centered())
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
        Clipboard.watch(this, clipListener)
        refreshClipboardButtons()
        if (!::pageArea.isInitialized) return
        pageArea.enabledPull = Prefs.get(this, Prefs.PULL)
        if (::address.isInitialized) address.refreshAddressText()
        EngineAdapter.setZoomMode(Prefs.get(this, Prefs.FORCE_ZOOM), Prefs.get(this, Prefs.WRAP))
        EngineAdapter.setThirdPartyCookies(Prefs.get(this, Prefs.THIRD_PARTY, false))
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
    private fun togglePrivate(button: IconButton) {
        if (!privateOn) {
            privateOn = true
            EngineAdapter.setPrivate(true)
            button.setOn(true)
            button.contentDescription = "Shield Mode, on"
            showNotice("Shield Mode on. Your sign-ins stay.")
            return
        }
        when (Prefs.getString(this, Prefs.PRIVATE_LEAVE, Prefs.LEAVE_ASK)) {
            Prefs.LEAVE_DELETE -> leavePrivate(button, true)
            Prefs.LEAVE_KEEP -> leavePrivate(button, false)
            else -> sheet.show(
                title = "Leave Shield Mode?",
                body = "Delete all removes the cookies, site data and cache this Shield Mode session added. Sign-ins you already had stay.",
                confirm = "Delete all",
                cancelLabel = "Continue as is",
                onCancelTap = { leavePrivate(button, false) },
                dangerConfirm = true,
            ) { leavePrivate(button, true) }
        }
    }

    // Long press on the shield (DECISIONS.md 151): cleans the open page's site at once, Shield Mode on or off, with no
    // question and no notice; the rings on the shield show it is done. Nothing happens without a web page open.
    private fun quickClean(button: IconButton) {
        if (!hasPage || homeOver) return
        if (EngineAdapter.quickClean()) button.pulseRing()
    }

    private fun leavePrivate(button: IconButton, delete: Boolean) {
        if (delete) EngineAdapter.deletePrivateAdditions()
        EngineAdapter.setPrivate(false)
        privateOn = false
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
        refreshBack()
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
        setExpanded(focused)
        refreshBack()
    }

    private fun leaveAddress() {
        (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager).hideSoftInputFromWindow(address.input.windowToken, 0)
        address.input.clearFocus()
        address.rootView.requestFocus()
    }

    // True when back was used: closes the editor first, then walks the page history.
    private fun handleBack(): Boolean {
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
        if (isFinishing && privateOn && Prefs.get(this, Prefs.PRIVATE_CLOSE)) EngineAdapter.deletePrivateAdditions()
        EngineAdapter.destroy()
        super.onDestroy()
    }

    private companion object {
        var carryUrl: String? = null
        const val ROW_HEIGHT = 34
        const val WEBVIEW_PAGE = "https://play.google.com/store/apps/details?id=com.google.android.webview"
    }
}
