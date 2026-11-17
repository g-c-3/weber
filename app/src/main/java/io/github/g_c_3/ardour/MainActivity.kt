package io.github.g_c_3.ardour

import android.animation.ValueAnimator
import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
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
import kotlin.math.max

class MainActivity : Activity() {
    private lateinit var address: AddressField
    private val progress: PageProgress get() = address.progress
    private lateinit var startView: View
    private lateinit var errorView: LinearLayout
    private lateinit var errorTitle: TextView
    private lateinit var errorBody: TextView
    private lateinit var errorHost: TextView
    private lateinit var errorRetry: View
    private lateinit var errorSearch: View
    private var failedUrl: String? = null
    private lateinit var buttonGroup: LinearLayout
    private lateinit var floatingBar: EditBar
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

    private fun pollDocument(id: Int) {
        readyPollId = id
        EngineAdapter.documentParsed { parsed ->
            if (!loading || loadId != id) return@documentParsed
            if (parsed) listener.onLoading(false) else progress.postDelayed({ if (loading && loadId == id) pollDocument(id) }, 300L)
        }
    }

    private val listener: PageListener = object : PageListener {
        override fun onPage(url: String?, canGoBack: Boolean, canGoForward: Boolean) {
            if (url != null && url != "about:blank") {
                address.showUrl(url)
                revealPage()
            }
            backButton.isEnabled = canGoBack
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
            maybePollDocument()
        }

        override fun onLoading(loading: Boolean) {
            this@MainActivity.loading = loading
            if (loading) {
                loadId++
                lastPercent = 0
            }
            if (loading) errorView.visibility = View.GONE
            if (loading && finding) closeFind()
            if (!loading) pageArea.finish()
            reloadButton.kind = if (loading) IconButton.Kind.STOP else IconButton.Kind.RELOAD
            // Until the engine reports its first progress the bar sweeps, so a slow connection looks alive.
            if (loading) progress.start() else progress.finish()
            address.setLoading(loading)
        }
    }

    @Suppress("DEPRECATION")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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
            } else {
                v.setPadding(
                    insets.systemWindowInsetLeft,
                    insets.systemWindowInsetTop,
                    insets.systemWindowInsetRight,
                    insets.systemWindowInsetBottom,
                )
            }
            insets
        }
        setContentView(root)
        root.requestFocus()
        if (gateMessage == null && savedInstanceState == null) handleLink(intent)
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

    private fun buildBrowser(root: LinearLayout) {
        rootLayout = root
        backButton = IconButton(this, IconButton.Kind.BACK).apply {
            contentDescription = "Back"
            isEnabled = false
            setOnClickListener { EngineAdapter.goBack() }
        }
        forwardButton = IconButton(this, IconButton.Kind.FORWARD).apply {
            contentDescription = "Forward"
            isEnabled = false
            setOnClickListener { EngineAdapter.goForward() }
        }
        reloadButton = IconButton(this, IconButton.Kind.RELOAD).apply {
            contentDescription = "Reload"
            isEnabled = false
            setOnClickListener { if (loading) EngineAdapter.stop() else EngineAdapter.reload() }
        }
        val menuButton = IconButton(this, IconButton.Kind.MENU).apply {
            contentDescription = "Menu"
            setOnClickListener { showMenu(this) }
        }
        // One top row: the address pill takes the left half, the four buttons the right half. Editing the address
        // widens the pill over the buttons (setExpanded).
        buttonGroup = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            for (b in listOf(backButton, forwardButton, reloadButton, menuButton)) {
                addView(b, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f))
            }
        }
        address = AddressField(this, ::submit, ::onAddressFocus)
        val bar = FrameLayout(this).apply {
            clipChildren = false
            addView(buttonGroup, FrameLayout.LayoutParams(px(150), px(ROW_HEIGHT), Gravity.END or Gravity.TOP))
            addView(address, FrameLayout.LayoutParams(px(150), px(ROW_HEIGHT), Gravity.START or Gravity.TOP))
            addOnLayoutChangeListener { _, l, _, r, _, _, _, _, _ -> sizeTopRow(r - l) }
        }
        val top = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            clipChildren = false
            setPadding(px(10), px(2), px(10), 0)
        }
        // The loading line lives inside the pill, so the row is only as tall as the pill plus room for its glow.
        top.addView(bar, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, px(ROW_HEIGHT + 2)))
        topBar = top
        root.addView(top, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        val content = PullRefreshLayout(
            this,
            { hasPage && errorView.visibility != View.VISIBLE && EngineAdapter.atTop() },
            { EngineAdapter.reload() },
        )
        pageArea = content
        val fill = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        content.addView(EngineAdapter.createView(this, listener) { root.post { recreate() } }, fill)
        startView = startScreen()
        content.addView(startView, fill)
        errorView = errorScreen().apply { visibility = View.GONE }
        content.addView(errorView, fill)
        content.attachIndicator()
        floatingBar = EditBar(
            this,
            onCopy = { copyAddress() },
            onPaste = { pasteIntoAddress() },
            onClear = { address.input.setText("") },
            onGo = { submit(address.input.text.toString()) },
        )
        address.input.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: android.text.Editable?) {
                floatingBar.refresh(!s.isNullOrEmpty(), clipboardHasText())
            }
        })
        content.addView(
            floatingBar,
            FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP or Gravity.CENTER_HORIZONTAL)
                .apply { topMargin = px(8) },
        )
        root.addView(content, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))

        findBar = FindBar(this, { EngineAdapter.findAll(it) }, { EngineAdapter.findNext(it) }, ::closeFind).apply { visibility = View.GONE }
        root.addView(findBar, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        Ui.enter(listOf(top, startView))
    }

    // Row width known: the pill gets half of it, the buttons the other half.
    private fun sizeTopRow(w: Int) {
        if (w <= 0 || w == rowWidth) return
        rowWidth = w
        val gap = px(8)
        idleWidth = (w - gap) / 2
        val buttons = buttonGroup.layoutParams as FrameLayout.LayoutParams
        buttons.width = w - idleWidth - gap
        buttonGroup.layoutParams = buttons
        val pill = address.layoutParams as FrameLayout.LayoutParams
        pill.width = if (expanded) w else idleWidth
        address.layoutParams = pill
    }

    // Copy action of the floating bar over the page while the address is edited.
    private fun copyAddress() {
        val text = address.input.text.toString()
        if (text.isEmpty()) return
        (getSystemService(CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("address", text))
    }

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
                setColor(0xF2FFFFFF.toInt())
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

    // Whether the clipboard holds text; reads only its type, never its content.
    private fun clipboardHasText(): Boolean {
        val cm = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
        return cm.hasPrimaryClip() && cm.primaryClipDescription?.hasMimeType("text/*") == true
    }

    private fun pasteIntoAddress() {
        val clip = (getSystemService(CLIPBOARD_SERVICE) as ClipboardManager).primaryClip ?: return
        if (clip.itemCount == 0) return
        val text = clip.getItemAt(0).coerceToText(this)?.toString().orEmpty()
        if (text.isEmpty()) return
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
    private fun startScreen(): View = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER
        setBackgroundColor(Ui.PAPER)
        addView(TextView(context).apply {
            text = "Ardour"
            textSize = 44f
            typeface = android.graphics.Typeface.create("sans-serif", android.graphics.Typeface.BOLD)
            letterSpacing = -0.03f
            setTextColor(Ui.EMBER)
            paint.shader = LinearGradient(0f, 0f, paint.measureText("Ardour"), 0f, Ui.EMBER, Ui.AMBER, Shader.TileMode.CLAMP)
        }, centered())
        addView(Ui.body(context, "Search or enter an address above").apply {
            setPadding(0, Ui.dp(context, 10), 0, Ui.dp(context, 64))
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

    override fun onResume() {
        super.onResume()
        if (!::pageArea.isInitialized) return
        pageArea.enabledPull = Prefs.get(this, Prefs.PULL)
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
                MenuItem("Desktop site", toggle = EngineAdapter.desktopSite) {
                    EngineAdapter.setDesktopSite(!EngineAdapter.desktopSite, hasPage)
                },
                *resetItems(),
                MenuItem("Settings") { startActivity(Intent(this, SettingsActivity::class.java)) },
            ),
        )
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
        onReaderStyle(true)
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
        startView.animate().alpha(0f).setDuration(220).withEndAction { startView.visibility = View.GONE }.start()
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

    private fun onAddressFocus(focused: Boolean) {
        if (focused && finding) closeFind()
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
        if (EngineAdapter.canGoBack()) {
            EngineAdapter.goBack()
            return true
        }
        return false
    }

    // Android 13+ with target SDK 36 no longer calls onBackPressed: a back callback must be registered, and only
    // while Ardour has something to go back to, so the system still plays its normal exit when it has not.
    private fun refreshBack() {
        if (Build.VERSION.SDK_INT < 33) return
        val needed = EngineAdapter.canGoBack() || address.input.hasFocus() || finding || reader != null
        if (needed && backCallback == null) registerBack() else if (!needed && backCallback != null) unregisterBack()
    }

    @TargetApi(33)
    private fun registerBack() {
        val callback = OnBackInvokedCallback { handleBack() }
        onBackInvokedDispatcher.registerOnBackInvokedCallback(OnBackInvokedDispatcher.PRIORITY_DEFAULT, callback)
        backCallback = callback
    }

    @TargetApi(33)
    private fun unregisterBack() {
        (backCallback as? OnBackInvokedCallback)?.let { onBackInvokedDispatcher.unregisterOnBackInvokedCallback(it) }
        backCallback = null
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

    override fun onDestroy() {
        EngineAdapter.destroy()
        super.onDestroy()
    }

    private companion object {
        const val ROW_HEIGHT = 34
        const val WEBVIEW_PAGE = "https://play.google.com/store/apps/details?id=com.google.android.webview"
    }
}
