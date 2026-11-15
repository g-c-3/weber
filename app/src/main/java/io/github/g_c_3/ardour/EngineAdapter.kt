package io.github.g_c_3.ardour

import android.annotation.SuppressLint
import android.content.Context
import android.print.PrintDocumentAdapter
import android.view.ScaleGestureDetector
import android.view.View
import android.view.ViewGroup
import android.net.http.SslError
import android.webkit.RenderProcessGoneDetail
import android.os.Handler
import android.os.Looper
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

    // Reader mode: finds the main text of the open page and returns it as JSON text (title, byline, site, blocks).
    // It only reads the page; the result is shown as plain text, never as markup (DECISIONS.md 55).
    private const val READER_JS = """
(function () {
  try {
    var SKIP = /^(NAV|ASIDE|FOOTER|HEADER|FORM|SCRIPT|STYLE|NOSCRIPT|IFRAME|SVG|BUTTON|SELECT|TEXTAREA|FIGURE|TABLE)$/;
    var JUNK = /comment|share|social|related|promo|advert|sidebar|cookie|subscribe|newsletter|popup|modal|breadcrumb|recommend|outbrain|taboola|menu|footer/i;
    function clean(s) { return (s || '').replace(/\s+/g, ' ').trim(); }
    function textOf(el) { return clean(el.innerText || el.textContent); }
    function meta(sel) { var m = document.querySelector(sel); return m ? clean(m.getAttribute('content')) : ''; }
    // Author and date from the page's structured data when it has any.
    function structured() {
      var out = {}, nodes = document.querySelectorAll('script[type="application/ld+json"]');
      function walk(o) {
        if (!o || typeof o !== 'object') return;
        if (Array.isArray(o)) { o.forEach(walk); return; }
        if (o['@graph']) walk(o['@graph']);
        var t = o['@type'];
        t = Array.isArray(t) ? t.join(' ') : String(t || '');
        if (/Article|BlogPosting|Report/.test(t)) {
          if (!out.author && o.author) {
            var list = Array.isArray(o.author) ? o.author : [o.author];
            out.author = list.map(function (x) { return typeof x === 'string' ? x : (x && x.name) || ''; }).filter(Boolean).slice(0, 3).join(', ');
          }
          if (!out.date) out.date = o.datePublished || o.dateCreated || '';
        }
      }
      for (var k = 0; k < nodes.length; k++) { try { walk(JSON.parse(nodes[k].textContent)); } catch (e) {} }
      return out;
    }
    function fmt(v) {
      var d = new Date(v);
      if (isNaN(d.getTime())) return '';
      try { return d.toLocaleDateString(undefined, { year: 'numeric', month: 'long', day: 'numeric' }); } catch (e) { return d.toDateString(); }
    }
    function skipped(el, root) {
      for (var n = el; n && n !== root; n = n.parentNode) {
        if (n.nodeType !== 1) continue;
        if (SKIP.test(n.tagName.toUpperCase()) || n.hidden || n.getAttribute('aria-hidden') === 'true') return true;
        var cls = typeof n.className === 'string' ? n.className : '';
        if (JUNK.test((n.id || '') + ' ' + cls)) return true;
        var st = getComputedStyle(n);
        if (st.display === 'none' || st.visibility === 'hidden') return true;
      }
      return false;
    }
    var W = [1, 0.7, 0.5], scores = new Map(), best = null, bestScore = 0;
    var ps = document.body.querySelectorAll('p');
    for (var i = 0; i < ps.length; i++) {
      var t0 = textOf(ps[i]);
      if (t0.length < 40 || skipped(ps[i], document.body)) continue;
      var s = 1 + Math.min(3, Math.floor(t0.length / 100)) + Math.min(3, t0.split(',').length - 1);
      var n = ps[i].parentNode;
      for (var d = 0; d < 3 && n && n !== document.documentElement; d++, n = n.parentNode) {
        scores.set(n, (scores.get(n) || 0) + s * W[d]);
      }
    }
    scores.forEach(function (v, el) { if (v > bestScore) { bestScore = v; best = el; } });
    var root = best || document.body;
    var art = root.closest('article, [itemprop=articleBody]');
    if (art) root = art;
        var h1 = root.querySelector('h1') || document.querySelector('h1');
    var title = h1 ? textOf(h1) : '';
    if (!title || title.length > 200) title = meta('meta[property="og:title"]') || clean(document.title);
    var info = structured();
    var by = clean(info.author) || meta('meta[name="author"]');
    if (!by) { var a = document.querySelector('[rel=author], [itemprop=author]'); by = a ? textOf(a) : ''; }
    var tm = document.querySelector('time[datetime]');
    var date = fmt(info.date || meta('meta[property="article:published_time"]') || (tm ? tm.getAttribute('datetime') : ''));
    var site = meta('meta[property="og:site_name"]') || location.hostname.replace(/^www\./, '');
    var els = root.querySelectorAll('h1,h2,h3,h4,p,blockquote,li,pre');
    var out = [], total = 0, last = '';
    for (var j = 0; j < els.length && out.length < 600 && total < 300000; j++) {
      var el = els[j], tag = el.tagName.toLowerCase();
      if (skipped(el, root)) continue;
      var c = (tag === 'p' || tag === 'li' || tag === 'blockquote') ? el.parentNode.closest('blockquote, li, pre') : null;
      // Text already carried by an enclosing quote, list item or code block is not repeated.
      if (c && c !== root && root.contains(c) && (tag !== 'li' || c.tagName !== 'LI')) continue;
      if (tag === 'li' && el.querySelector('li')) continue;
      var t = tag === 'pre' ? (el.textContent || '').replace(/\s+$/, '').slice(0, 4000) : textOf(el);
      if (!t) continue;
      var kind, marker = '';
      if (tag === 'h1' || tag === 'h2' || tag === 'h3' || tag === 'h4') {
        if (t.length < 2 || t.length > 200 || t === title) continue;
        kind = tag === 'h1' ? '1' : tag === 'h2' ? '2' : '3';
      } else if (tag === 'p') {
        if (t.length < 20) continue;
        kind = 'p';
      } else if (tag === 'blockquote') {
        if (t.length < 10) continue;
        kind = 'q';
      } else if (tag === 'li') {
        if (t.length < 2) continue;
        kind = 'l';
        var par = el.parentNode;
        marker = par && par.tagName === 'OL' ? (Array.prototype.indexOf.call(par.children, el) + 1) + '.' : '\u2022';
      } else {
        kind = 'c';
      }
      if (t === last) continue;
      last = t;
      out.push([kind, t, marker]);
      total += t.length;
    }
    return JSON.stringify({ t: title, a: by.slice(0, 80), d: date, s: site, b: out });
  } catch (e) {
    return null;
  }
})()
    """

    // Before reading, the page is scrolled to its end (up to a few rounds, while it keeps growing) so text it loads
    // on scroll is present, then put back where it was. It causes only the page's own loads.
    private const val PRELOAD_JS = """(function () { if (window.__ardourY === undefined) window.__ardourY = window.scrollY; window.scrollTo(0, document.documentElement.scrollHeight); return document.documentElement.scrollHeight; })()"""
    private const val RESTORE_JS = """(function () { if (window.__ardourY !== undefined) { window.scrollTo(0, window.__ardourY); delete window.__ardourY; } })();"""
    private const val PRELOAD_ROUNDS = 4
    private const val PRELOAD_WAIT_MS = 450L

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

    // Reader mode: loads the rest of the page's text, then runs the extraction script. The callback (UI thread) gets the
    // JSON string literal the engine returns, "null", or null when there is no view.
    fun extractArticle(onResult: (String?) -> Unit) {
        val view = webView
        if (view == null) {
            onResult(null)
            return
        }
        val handler = Handler(Looper.getMainLooper())
        var rounds = 0
        var lastHeight = -1
        fun finish() {
            view.evaluateJavascript(RESTORE_JS + READER_JS) { onResult(it) }
        }
        fun step() {
            view.evaluateJavascript(PRELOAD_JS) { value ->
                val height = value?.toIntOrNull() ?: -1
                rounds++
                if (height <= lastHeight || rounds >= PRELOAD_ROUNDS) {
                    finish()
                } else {
                    lastHeight = height
                    handler.postDelayed({ step() }, PRELOAD_WAIT_MS)
                }
            }
        }
        step()
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
