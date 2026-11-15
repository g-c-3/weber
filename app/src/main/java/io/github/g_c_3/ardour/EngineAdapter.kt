package io.github.g_c_3.ardour

import android.annotation.SuppressLint
import android.content.Context
import android.print.PrintDocumentAdapter
import android.view.ScaleGestureDetector
import android.view.View
import android.view.ViewGroup
import android.net.http.SslError
import android.webkit.RenderProcessGoneDetail
import android.webkit.SslErrorHandler
import android.webkit.WebResourceError
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.webkit.Profile
import androidx.webkit.ScriptHandler
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature

enum class PageError { OFFLINE, NOT_FOUND, UNREACHABLE, SECURITY, NOT_SECURE, OTHER }

// What the browser screen needs to hear from the engine.
interface PageListener {
    fun onError(url: String?, kind: PageError)
    fun onPage(url: String?, canGoBack: Boolean, canGoForward: Boolean)
    fun onProgress(percent: Int)
    fun onLoading(loading: Boolean)
    fun onFind(active: Int, total: Int)
}

// The only file that calls android.webkit or androidx.webkit (DECISIONS.md 26). An engine API change is fixed here.
object EngineAdapter {
    private val OFFLINE_CODES = setOf(
        WebViewClient.ERROR_HOST_LOOKUP,
        WebViewClient.ERROR_CONNECT,
        WebViewClient.ERROR_TIMEOUT,
        WebViewClient.ERROR_IO,
    )
    private const val ERROR_FAILED_SSL_HANDSHAKE = WebViewClient.ERROR_FAILED_SSL_HANDSHAKE
    private var webView: WebView? = null
    private var defaultAgent: String? = null
    private var forceZoomScript: ScriptHandler? = null
    private var forceZoomOn = false
    private var textScale = 100f

    // Viewport rewrite for "Force zoom": drops user-scalable=no and any maximum-scale, now and when the page changes it.
    private const val FORCE_ZOOM_JS = """
        (function () {
          if (window.__ardourZoomFix) { window.__ardourZoomFix(); return; }
          function fix() {
            var m = document.querySelector('meta[name=viewport]');
            if (!m) return;
            var c = m.getAttribute('content') || '';
            var n = c.replace(/user-scalable\s*=\s*[^,;\s]+/gi, 'user-scalable=yes')
                     .replace(/maximum-scale\s*=\s*[^,;\s]+/gi, 'maximum-scale=10');
            if (n.indexOf('user-scalable') < 0) n += (n ? ', ' : '') + 'user-scalable=yes';
            if (n !== c) m.setAttribute('content', n);
          }
          Object.defineProperty(window, '__ardourZoomFix', { value: fix });
          fix();
          document.addEventListener('DOMContentLoaded', fix);
          new MutationObserver(fix).observe(document, {childList: true, subtree: true, attributes: true, attributeFilter: ['content']});
        })();
    """

    // Desktop site (user switch): a desktop user-agent string and a wide viewport. Kept across a view rebuild.
    var desktopSite = false
        private set

    // Launch gate (DECISIONS.md 28): null when the engine meets the app's needs, else the text to show.
    // No WebView version number is claimed; the check is the package and the profile feature at run time.
    fun launchGateMessage(context: Context): String? {
        val pkg = WebViewCompat.getCurrentWebViewPackage(context)
        val ok = pkg != null && WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE)
        return if (ok) null else
            "Ardour needs a newer Android System WebView.\n\nOpen it in the Play Store, tap Update, then open Ardour again.\n\nNo Play Store? Update WebView through your phone maker's app store or system update."
    }

    // For Settings: the WebView package and version in use, or null when none is found.
    fun webViewVersion(context: Context): String? =
        WebViewCompat.getCurrentWebViewPackage(context)?.let { "${it.versionName} (${it.packageName})" }

    @SuppressLint("SetJavaScriptEnabled")
    @Suppress("DEPRECATION")
    fun createView(context: Context, listener: PageListener, onEngineLost: () -> Unit): View {
        val view = WebView(context)
        // Profile first: no other call on the view before it (ROADMAP 2). Named Space profiles arrive in Phase 12.
        WebViewCompat.setProfile(view, Profile.DEFAULT_PROFILE_NAME)
        view.settings.apply {
            javaScriptEnabled = true
            // Off by default in WebView; most sites need it to run (DECISIONS.md 45).
            domStorageEnabled = true
            allowFileAccess = false
            allowContentAccess = false
            allowFileAccessFromFileURLs = false
            allowUniversalAccessFromFileURLs = false
            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
        }
        defaultAgent = view.settings.userAgentString
        applyDesktop(view)
        view.setBackgroundColor(Ui.PAPER)
        view.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView, newProgress: Int) = listener.onProgress(newProgress)
        }
        // Find in page results: active match is 0-based, total is the match count.
        view.setFindListener { active, total, _ -> listener.onFind(active, total) }
        view.webViewClient = object : WebViewClient() {
            // Only web addresses load in the view; other schemes are dropped until Ardour handles them itself.
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                val scheme = request.url.scheme
                return scheme != "http" && scheme != "https"
            }

            override fun onPageStarted(view: WebView, url: String?, favicon: android.graphics.Bitmap?) {
                listener.onLoading(true)
                listener.onPage(url, view.canGoBack(), view.canGoForward())
            }

            override fun onPageFinished(view: WebView, url: String?) {
                if (forceZoomOn) view.evaluateJavascript(FORCE_ZOOM_JS, null)
                listener.onLoading(false)
                listener.onPage(url, view.canGoBack(), view.canGoForward())
            }

            // Main-frame failures only; the screen shows its own page instead of the engine's (DECISIONS.md 46).
            override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
                if (!request.isForMainFrame) return
                val text = error.description?.toString().orEmpty()
                val kind = when {
                    "CLEARTEXT" in text -> PageError.NOT_SECURE
                    "NAME_NOT_RESOLVED" in text -> PageError.NOT_FOUND
                    "INTERNET_DISCONNECTED" in text -> PageError.OFFLINE
                    error.errorCode in OFFLINE_CODES -> PageError.UNREACHABLE
                    error.errorCode == ERROR_FAILED_SSL_HANDSHAKE -> PageError.SECURITY
                    else -> PageError.OTHER
                }
                listener.onError(request.url.toString(), kind)
            }

            // Certificate problems are never bypassed: the load is cancelled.
            override fun onReceivedSslError(view: WebView, handler: SslErrorHandler, error: SslError) {
                handler.cancel()
                listener.onError(error.url, PageError.SECURITY)
            }

            override fun doUpdateVisitedHistory(view: WebView, url: String?, isReload: Boolean) =
                listener.onPage(url, view.canGoBack(), view.canGoForward())

            // Renderer ended: drop the view and let the app build a new one; returning false would end the app.
            override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
                (view.parent as? ViewGroup)?.removeView(view)
                view.destroy()
                if (webView === view) webView = null
                onEngineLost()
                return true
            }
        }
        webView = view
        return view
    }

    // Desktop agent: the phone's own string with the Android token and "Mobile" removed; the engine version is kept.
    private fun desktopAgent(mobile: String): String =
        mobile.replace(Regex("\\(Linux; Android[^)]*\\)"), "(X11; Linux x86_64)").replace(" Mobile", "")

    private fun applyDesktop(view: WebView) {
        val base = defaultAgent
        view.settings.apply {
            userAgentString = if (desktopSite && base != null) desktopAgent(base) else base
            useWideViewPort = desktopSite
            loadWithOverviewMode = desktopSite
        }
    }

    fun setDesktopSite(on: Boolean, reloadPage: Boolean) {
        desktopSite = on
        webView?.let {
            applyDesktop(it)
            if (reloadPage) {
                // A reload keeps the page's old zoom; a fresh load of the same address lets overview mode fit the width.
                val address = it.url
                it.setInitialScale(0)
                if (address != null) it.loadUrl(address) else it.reload()
            }
        }
    }

    fun currentUrl(): String? = webView?.url

    fun pageTitle(): String? = webView?.title

    // Print adapter for the open page; the system print screen offers Save as PDF.
    fun printAdapter(jobName: String): PrintDocumentAdapter? = webView?.createPrintDocumentAdapter(jobName)

    fun textSizeChanged(): Boolean = textScale.toInt() != 100

    fun resetTextSize() {
        textScale = 100f
        webView?.settings?.textZoom = 100
    }

    // True when the page is scrolled to its top (pull to refresh may start).
    fun atTop(): Boolean = webView?.canScrollVertically(-1) == false

    // Pinch zoom is on by default. `force` ignores a site's zoom block for pages opened next. `wrap` replaces page
    // scaling with text size changes, so the text re-wraps to the screen width; it needs no viewport rewrite.
    @SuppressLint("ClickableViewAccessibility")
    fun setZoomMode(force: Boolean, wrap: Boolean) {
        val view = webView ?: return
        view.settings.apply {
            setSupportZoom(true)
            builtInZoomControls = !wrap
            displayZoomControls = false
        }
        forceZoomScript?.remove()
        forceZoomScript = null
        forceZoomOn = force && !wrap
        // Also run it on the page already open and after every page load, in case the start-of-page script is not used.
        if (forceZoomOn) view.evaluateJavascript(FORCE_ZOOM_JS, null)
        if (force && !wrap && WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
            try {
                forceZoomScript = WebViewCompat.addDocumentStartJavaScript(view, FORCE_ZOOM_JS, setOf("*"))
            } catch (_: IllegalArgumentException) {
                // The origin rule was refused; force zoom stays off.
            }
        }
        if (wrap) {
            val detector = ScaleGestureDetector(view.context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
                override fun onScale(d: ScaleGestureDetector): Boolean {
                    textScale = (textScale * d.scaleFactor).coerceIn(50f, 300f)
                    view.settings.textZoom = textScale.toInt()
                    return true
                }
            })
            view.setOnTouchListener { _, e ->
                detector.onTouchEvent(e)
                detector.isInProgress
            }
        } else {
            view.setOnTouchListener(null)
            textScale = 100f
            view.settings.textZoom = 100
        }
    }

    // Find in page: an empty query clears the highlights.
    fun findAll(query: String) {
        val view = webView ?: return
        if (query.isEmpty()) view.clearMatches() else view.findAllAsync(query)
    }

    fun findNext(forward: Boolean) = webView?.findNext(forward)

    fun clearFind() = webView?.clearMatches()

    fun load(url: String) = webView?.loadUrl(url)

    fun reload() = webView?.reload()

    fun stop() = webView?.stopLoading()

    fun goBack() = webView?.goBack()

    fun goForward() = webView?.goForward()

    fun canGoBack(): Boolean = webView?.canGoBack() == true

    fun destroy() {
        webView?.let {
            (it.parent as? ViewGroup)?.removeView(it)
            it.destroy()
        }
        webView = null
    }
}
