package io.github.g_c_3.ardour

import android.app.Activity
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
    private lateinit var progress: ThinProgress
    private lateinit var startView: View
    private lateinit var errorView: LinearLayout
    private lateinit var errorTitle: TextView
    private lateinit var errorBody: TextView
    private lateinit var errorHost: TextView
    private lateinit var errorRetry: View
    private lateinit var errorSearch: View
    private var failedUrl: String? = null
    private lateinit var bottomBar: View
    private lateinit var findBar: FindBar
    private lateinit var pageArea: PullRefreshLayout
    private var finding = false
    private lateinit var backButton: IconButton
    private lateinit var forwardButton: IconButton
    private lateinit var reloadButton: IconButton
    private var loading = false
    private var hasPage = false
    private var backCallback: Any? = null

    private val listener = object : PageListener {
        override fun onPage(url: String?, canGoBack: Boolean, canGoForward: Boolean) {
            if (url != null && url != "about:blank") {
                address.showUrl(url)
                if (!hasPage) {
                    hasPage = true
                    startView.animate().alpha(0f).setDuration(220).withEndAction { startView.visibility = View.GONE }.start()
                }
            }
            backButton.isEnabled = canGoBack
            forwardButton.isEnabled = canGoForward
            reloadButton.isEnabled = hasPage
            refreshBack()
        }

        override fun onError(url: String?, kind: PageError) = showError(url, kind)

        override fun onFind(active: Int, total: Int) {
            if (finding) findBar.showCount(active, total)
        }

        override fun onProgress(percent: Int) {
            progress.value = max(percent, 6) / 100f
        }

        override fun onLoading(loading: Boolean) {
            this@MainActivity.loading = loading
            if (loading) errorView.visibility = View.GONE
            if (loading && finding) closeFind()
            if (!loading) pageArea.finish()
            reloadButton.kind = if (loading) IconButton.Kind.STOP else IconButton.Kind.RELOAD
            progress.visibility = if (loading) View.VISIBLE else View.INVISIBLE
            if (loading) progress.value = 0.06f
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
    }

    private fun buildBrowser(root: LinearLayout) {
        val top = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(Ui.dp(this@MainActivity, 12), Ui.dp(this@MainActivity, 8), Ui.dp(this@MainActivity, 12), Ui.dp(this@MainActivity, 4))
        }
        address = AddressField(this, ::submit, ::onAddressFocus)
        top.addView(address, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, 48)))
        progress = ThinProgress(this).apply { visibility = View.INVISIBLE }
        top.addView(progress, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, 8)).apply { topMargin = Ui.dp(this@MainActivity, 4) })
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
        root.addView(content, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))

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
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(Ui.CARD)
            for (b in listOf(backButton, forwardButton, reloadButton, menuButton)) {
                addView(b, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f))
            }
        }
        bottomBar = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(Ui.divider(this@MainActivity))
            addView(row, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this@MainActivity, 56)))
        }
        root.addView(bottomBar, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        findBar = FindBar(this, { EngineAdapter.findAll(it) }, { EngineAdapter.findNext(it) }, ::closeFind).apply { visibility = View.GONE }
        root.addView(findBar, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        Ui.enter(listOf(top, startView, bottomBar))
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
            setOnClickListener { failedUrl?.let { EngineAdapter.load(it) } }
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
            setOnClickListener { failedUrl?.let { EngineAdapter.load(UrlInput.searchUrl(UrlInput.display(it))) } }
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
    }

    private fun showMenu(anchor: View) {
        MenuPopup.show(
            anchor,
            listOf(
                MenuItem("Share", enabled = hasPage) { share() },
                MenuItem("Find in page", enabled = hasPage) { openFind() },
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
    }

    private fun openFind() {
        if (finding) return
        finding = true
        bottomBar.visibility = View.GONE
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
        bottomBar.visibility = View.VISIBLE
        refreshBack()
    }

    private fun onAddressFocus(focused: Boolean) {
        if (focused && finding) closeFind()
        bottomBar.visibility = if (focused) View.GONE else View.VISIBLE
        refreshBack()
    }

    private fun leaveAddress() {
        (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager).hideSoftInputFromWindow(address.input.windowToken, 0)
        address.input.clearFocus()
        address.rootView.requestFocus()
    }

    // True when back was used: closes the editor first, then walks the page history.
    private fun handleBack(): Boolean {
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
        val needed = EngineAdapter.canGoBack() || address.input.hasFocus() || finding
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
        const val WEBVIEW_PAGE = "https://play.google.com/store/apps/details?id=com.google.android.webview"
    }
}
