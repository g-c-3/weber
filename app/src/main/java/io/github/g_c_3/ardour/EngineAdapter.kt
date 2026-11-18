package io.github.g_c_3.ardour

import android.annotation.SuppressLint
import android.content.Context
import android.print.PrintDocumentAdapter
import android.view.ScaleGestureDetector
import android.view.View
import android.view.ViewGroup
import android.net.Uri
import android.net.http.SslError
import android.os.SystemClock
import android.webkit.CookieManager
import android.webkit.RenderProcessGoneDetail
import org.json.JSONException
import org.json.JSONObject
import org.json.JSONTokener
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
import androidx.webkit.UserAgentMetadata
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature

enum class PageError { OFFLINE, NOT_FOUND, UNREACHABLE, SECURITY, NOT_SECURE, OTHER }

// What the browser screen needs to hear from the engine.
interface PageListener {
    fun onError(url: String?, kind: PageError)
    fun onPage(url: String?, canGoBack: Boolean, canGoForward: Boolean)
    fun onProgress(percent: Int)
    fun onLoading(loading: Boolean)
    fun onNavigate(url: String)
    fun onFind(active: Int, total: Int)

    // The new page is on screen: nothing left over from the previous page is drawn any more.
    fun onCommitVisible() {}

    // A tapped link that leaves the page: another app's address, or a Play Store page. True when it was handled.
    fun onExternal(url: String): Boolean = false
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
    private var defaultMetadata: UserAgentMetadata? = null
    private var bypassCache = false
    private var userNavAt = 0L
    private var desktopViewportScript: ScriptHandler? = null
    private var forceZoomScript: ScriptHandler? = null
    private var forceZoomOn = false
    private var textScale = 100f

    // Viewport rewrite for "Force zoom": drops user-scalable=no and any maximum-scale, now and when the page changes it.
    // Desktop site: pages are laid out 1024 px wide, as other browsers do, so the whole page fits the screen at first.
    // The engine always obeys a page's own viewport tags (the last one wins), so every one is rewritten as it appears and
    // kept that way; a page without one already gets a wide layout, so none is added (DECISIONS.md 76, 79).
    private const val DESKTOP_VIEWPORT_JS = """
(function () {
  var W = 'width=1024';
  var obs;
  function fix() {
    var list = document.querySelectorAll('meta[name="viewport" i]');
    for (var i = 0; i < list.length; i++) {
      var m = list[i];
      if (m.getAttribute('content') !== W) m.setAttribute('content', W);
      obs.observe(m, { attributes: true, attributeFilter: ['content'] });
    }
  }
  obs = new MutationObserver(function () {
    fix();
    if (document.head) obs.observe(document.head, { childList: true });
  });
  function setup() {
    if (!document.documentElement) return false;
    obs.observe(document.documentElement, { childList: true });
    if (document.head) obs.observe(document.head, { childList: true });
    fix();
    return true;
  }
  if (!setup()) {
    new MutationObserver(function (muts, o) { if (setup()) o.disconnect(); }).observe(document, { childList: true });
  }
})();
"""

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
    var SKIP = /^(NAV|ASIDE|FOOTER|HEADER|FORM|SCRIPT|STYLE|NOSCRIPT|IFRAME|SVG|BUTTON|SELECT|TEXTAREA|FIGURE|TABLE|INS|AMP-AD|VIDEO|AUDIO|CANVAS|OBJECT|EMBED)$/;
    var JUNK = /comment|share|social|related|promo|advert|sidebar|cookie|subscribe|newsletter|popup|modal|breadcrumb|recommend|outbrain|taboola|menu|footer|adsbygoogle|doubleclick|(^|[\s_-])ads?($|[\s_-])/i;
    // Only these stay inside the running paragraph; any other element (including unknown ones) starts a new paragraph.
    var INLINE = /^(A|ABBR|B|BDI|BDO|CITE|CODE|DATA|DFN|EM|FONT|I|IMG|KBD|LABEL|MARK|Q|S|SAMP|SMALL|SPAN|STRONG|SUB|SUP|TIME|U|VAR|WBR)$/;
    var BLOCKISH = 'p,div,ul,ol,li,h1,h2,h3,h4,h5,h6,blockquote,section,article,pre,table';
    // Lines that end the article: what follows is comments, widgets and recommendations.
    var STOP = /^(end of article|related (stories|articles|news|posts)|recommended for you|you may (also )?like|read next|more from .{1,30}|sponsored (links|content)|what readers are saying|join (the )?conversation|all comments.*|comments( \(\d+\))?)$/i;
    function clean(s) { return (s || '').replace(/\s+/g, ' ').trim(); }
    function textOf(el) { return clean(el.innerText || el.textContent); }
    function ownText(el) {
      var t = '';
      for (var n = el.firstChild; n; n = n.nextSibling) if (n.nodeType === 3) t += n.nodeValue;
      return clean(t);
    }
    function meta(sel) { var m = document.querySelector(sel); return m ? clean(m.getAttribute('content')) : ''; }
    // Author and date from the page's structured data when it has any.
    function structured() {
      var out = {}, nodes = document.querySelectorAll('script[type="application/ld+json"]');
      function walkLd(o) {
        if (!o || typeof o !== 'object') return;
        if (Array.isArray(o)) { o.forEach(walkLd); return; }
        if (o['@graph']) walkLd(o['@graph']);
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
      for (var k = 0; k < nodes.length; k++) { try { walkLd(JSON.parse(nodes[k].textContent)); } catch (e) {} }
      return out;
    }
    function fmt(v) {
      var d = new Date(v);
      if (isNaN(d.getTime())) return '';
      try { return d.toLocaleDateString(undefined, { year: 'numeric', month: 'long', day: 'numeric' }); } catch (e) { return d.toDateString(); }
    }
    // One element that never belongs to the article: page furniture, hidden, or (unless lenient) named like an advert or widget.
    var lenient = false;
    var trusted = false; // true for text Readability already cleaned: no page styles to consult
    function bad(n) {
      if (SKIP.test(n.tagName.toUpperCase()) || n.hidden || n.getAttribute('aria-hidden') === 'true') return true;
      var cls = typeof n.className === 'string' ? n.className : '';
      if (trusted) return false;
      if (!lenient && JUNK.test((n.id || '') + ' ' + cls)) return true;
      var st = getComputedStyle(n);
      return st.display === 'none' || st.visibility === 'hidden';
    }
    function skipped(el, root) {
      for (var n = el; n && n !== root; n = n.parentNode) if (n.nodeType === 1 && bad(n)) return true;
      return false;
    }
    // Short description of an element and its ancestors (tag, id, first classes) for the diagnostic report.
    function short(n) {
      var c = typeof n.className === 'string' ? n.className.trim().split(/\s+/).slice(0, 3).join('.') : '';
      return n.tagName.toLowerCase() + (n.id ? '#' + n.id.slice(0, 20) : '') + (c ? '.' + c.slice(0, 40) : '');
    }
    function path(el) {
      var p = [];
      for (var n = el, i = 0; n && n.nodeType === 1 && i < 6; n = n.parentNode, i++) p.push(short(n));
      return p.reverse().join(' > ');
    }
    function chars(list) {
      var c = 0;
      for (var i = 0; i < list.length; i++) if (list[i][0] === 'p' || list[i][0] === 'q') c += list[i][1].length;
      return c;
    }

    // Turns a container into blocks in reading order. Text sitting loose between elements becomes paragraphs too
    // (a double line break starts a new one), so pages that skip paragraph tags still read properly.
    function collect(root, title) {
    var out = [], total = 0, last = '', stopped = false;
    function push(kind, text, marker) {
      if (stopped || out.length >= 600 || total >= 300000) return;
      var t = kind === 'c' ? String(text || '').replace(/\s+$/, '').slice(0, 4000) : clean(text);
      if (!t) return;
      if (t.length <= 40 && STOP.test(t)) { stopped = true; return; }
      if (kind === 'p' && (t.length < 20 || /^advertisement$/i.test(t))) return;
      if (kind === 'q' && t.length < 10) return;
      if ((kind === '1' || kind === '2' || kind === '3') && (t.length < 2 || t.length > 200 || t === title)) return;
      if (kind === 'l' && t.length < 2) return;
      if (t === last) return;
      last = t;
      out.push([kind, t, marker || '']);
      total += t.length;
    }
    function walk(el) {
      var buf = '';
      function flush() {
        if (!buf) return;
        var parts = buf.split(/\u0001\s*\u0001/);
        for (var k = 0; k < parts.length; k++) push('p', parts[k].replace(/\u0001/g, ' '));
        buf = '';
      }
      for (var n = el.firstChild; n && !stopped; n = n.nextSibling) {
        if (n.nodeType === 3) { buf += n.nodeValue; continue; }
        if (n.nodeType !== 1) continue;
        var tag = n.tagName.toUpperCase();
        if (tag === 'BR') { buf += '\u0001'; continue; }
        if (bad(n)) { flush(); continue; }
        if (INLINE.test(tag) && !n.querySelector(BLOCKISH)) { buf += (n.innerText || n.textContent || ''); continue; }
        flush();
        block(n, tag);
      }
      flush();
    }
    function block(n, tag) {
      if (/^H[1-6]$/.test(tag)) push(tag === 'H1' ? '1' : tag === 'H2' ? '2' : '3', textOf(n));
      else if (tag === 'P') push('p', textOf(n));
      else if (tag === 'BLOCKQUOTE') push('q', textOf(n));
      else if (tag === 'PRE') push('c', n.textContent);
      else if (tag === 'UL' || tag === 'OL') {
        var idx = 0;
        for (var li = n.firstElementChild; li; li = li.nextElementSibling) {
          if (li.tagName !== 'LI' || bad(li)) continue;
          idx++;
          if (li.querySelector('li')) walk(li);
          else push('l', textOf(li), tag === 'OL' ? idx + '.' : '\u2022');
        }
      } else if (tag === 'LI') push('l', textOf(n), '\u2022');
      else walk(n);
    }
      walk(root);
      return out;
    }

    function attempt() {
      // Find the container that holds most running text. Text in paragraphs counts for the paragraph's parents;
      // text sitting directly in other elements (pages that do not use paragraph tags) counts for that element.
      var W = [1, 0.7, 0.5], scores = new Map(), best = null, bestScore = 0;
      var cands = document.body.querySelectorAll('p, div, section, article, main');
      for (var i = 0; i < cands.length; i++) {
        var c = cands[i], isP = c.tagName === 'P';
        var t0 = isP ? textOf(c) : ownText(c);
        if (t0.length < 40 || skipped(c, document.body)) continue;
        var s = 1 + Math.min(10, Math.floor(t0.length / 100)) + Math.min(3, t0.split(',').length - 1);
        var n = isP ? c.parentNode : c;
        for (var d = 0; d < 3 && n && n !== document.documentElement; d++, n = n.parentNode) {
          scores.set(n, (scores.get(n) || 0) + s * W[d]);
        }
      }
      var ranked = [];
      scores.forEach(function (v, el) { ranked.push([v, el]); if (v > bestScore) { bestScore = v; best = el; } });
      ranked.sort(function (x, y) { return y[0] - x[0]; });
      var top = ranked.slice(0, 4).map(function (x) { return Math.round(x[0]) + ' ' + path(x[1]); });
      var root = best || document.body;
      var art = root.closest('article, [itemprop=articleBody]');
      if (art) root = art;
      var h1 = root.querySelector('h1') || document.querySelector('h1');
      var title = h1 ? textOf(h1) : '';
      if (!title || title.length > 200) title = meta('meta[property="og:title"]') || clean(document.title);

      var out = collect(root, title);
      return { title: title, out: out, root: path(root), top: top };
    }

    // Diagnostic report (long press on the reader's title): page structure and slow requests, no page text.
    function report(strict, loose, rdInfo) {
      var lines = ['Ardour reader report', 'host ' + location.hostname, 'readability ' + rdInfo + ', used ' + strict.root,
        'strict chars ' + chars(strict.out) + ' root ' + strict.root, 'lenient chars ' + chars(loose.out) + ' root ' + loose.root];
      strict.top.forEach(function (t) { lines.push(' strict top ' + t); });
      loose.top.forEach(function (t) { lines.push(' lenient top ' + t); });
      var big = [], els = document.body.querySelectorAll('p, div, section, article, main');
      for (var i = 0; i < els.length; i++) {
        var t = els[i].tagName === 'P' ? textOf(els[i]) : ownText(els[i]);
        if (t.length >= 150) big.push([t.length, els[i]]);
      }
      big.sort(function (x, y) { return y[0] - x[0]; });
      big.slice(0, 6).forEach(function (b) {
        var why = '';
        for (var n = b[1]; n && n !== document.body; n = n.parentNode) {
          if (n.nodeType === 1 && bad(n)) { why = ' skipped-by ' + short(n); break; }
        }
        lines.push(' text ' + b[0] + ' ' + path(b[1]) + why);
      });
      try {
        var es = performance.getEntriesByType('resource');
        var slow = es.filter(function (e) { return e.duration > 3000; }).sort(function (x, y) { return y.duration - x.duration; });
        lines.push('resources ' + es.length + ', slower than 3 s: ' + slow.length);
        slow.slice(0, 8).forEach(function (e) {
          var h = '';
          try { var u = new URL(e.name); h = u.hostname + u.pathname.slice(0, 30); } catch (x) { h = e.name.slice(0, 40); }
          lines.push(' slow ' + Math.round(e.duration) + ' ms ' + e.initiatorType + ' ' + h);
        });
      } catch (e) { lines.push('resource timing unavailable'); }
      lines.push('page height ' + document.documentElement.scrollHeight + ' screen ' + window.innerHeight);
      return lines.join('\n');
    }

    var info = structured();
    var by = clean(info.author) || meta('meta[name="author"]');
    if (!by) { var a = document.querySelector('[rel=author], [itemprop=author]'); by = a ? textOf(a) : ''; }
    var tm = document.querySelector('time[datetime]');
    var date = fmt(info.date || meta('meta[property="article:published_time"]') || (tm ? tm.getAttribute('datetime') : ''));
    var site = meta('meta[property="og:site_name"]') || location.hostname.replace(/^www\./, '');

    // Both passes always run. The lenient one ignores names like "ad" or "menu" on containers; it is used only when it
    // finds clearly more text (a page whose article sits inside such a container).
    var r = attempt();
    lenient = true;
    var r2 = attempt();
    lenient = false;
    if (chars(r2.out) > chars(r.out) * 1.6 + 100) r = r2;
    // Mozilla Readability, when loaded, reads a copy of the page; its cleaned article is turned into blocks by the same
    // code and wins when it finds a real article. It is skipped while the page is scrolled in steps, because it needs
    // the whole page at once.
    var rdMeta = null, rdInfo = 'absent';
    if (window.__ardourRd && !window.__ardourMerge) {
      rdInfo = 'no article';
      try {
        var art = new window.__ardourRd(document.cloneNode(true)).parse();
        if (art && art.content) {
          var doc = new DOMParser().parseFromString(art.content, 'text/html');
          trusted = true;
          var rdOut = collect(doc.body, clean(art.title));
          trusted = false;
          rdInfo = 'chars ' + chars(rdOut);
          if (chars(rdOut) >= 300 && chars(rdOut) >= chars(r.out) * 0.5) {
            r = { title: clean(art.title) || r.title, out: rdOut, root: 'readability', top: [] };
            rdMeta = art;
          }
        }
      } catch (e) { trusted = false; rdInfo = 'failed'; }
    }
    if (rdMeta) {
      if (!by) by = clean(rdMeta.byline || '');
      if (!date && rdMeta.publishedTime) date = fmt(rdMeta.publishedTime);
    }
    if (window.__ardourReport) return report(r, r2, rdInfo);
    var out = r.out;
    // While the page is being scrolled in steps (pages that only keep the text near the screen), results of every
    // step are gathered here, new blocks added in the order they were first seen.
    if (window.__ardourMerge) {
      var acc = window.__ardourAcc || (window.__ardourAcc = []);
      var seen = window.__ardourSeen || (window.__ardourSeen = {});
      for (var q = 0; q < out.length && acc.length < 600; q++) {
        if (!seen[out[q][1]]) { seen[out[q][1]] = 1; acc.push(out[q]); }
      }
      out = acc;
    }
    return JSON.stringify({ t: r.title, a: by.slice(0, 80), d: date, s: site, e: rdMeta ? 'rd' : 'own', c: chars(out), k: document.documentElement.scrollHeight > 3 * window.innerHeight ? 1 : 0, b: out });
  } catch (e) {
    return null;
  }
})()
    """

    // Before reading, the page is scrolled to its end (up to three short rounds, while it keeps growing by more than 8%) so text
    // it loads on scroll is present. If that still gives too little text, the page is scrolled down in screen-sized steps
    // and what is on screen at each step is gathered (pages that keep only the text near the screen). Afterwards the page
    // is put back where it was. This causes only the page's own loads.
    private const val PRELOAD_JS = """(function () { if (window.__ardourY === undefined) window.__ardourY = window.scrollY; window.scrollTo(0, document.documentElement.scrollHeight); return document.documentElement.scrollHeight; })()"""
    private const val RESTORE_JS = """(function () { if (window.__ardourY !== undefined) { window.scrollTo(0, window.__ardourY); delete window.__ardourY; } delete window.__ardourMerge; delete window.__ardourAcc; delete window.__ardourSeen; delete window.__ardourRd; })();"""
    private const val STEP_START_JS = """(function () { window.__ardourMerge = true; delete window.__ardourAcc; delete window.__ardourSeen; if (window.__ardourY === undefined) window.__ardourY = window.scrollY; window.scrollTo(0, 0); })();"""
    private const val STEP_JS = """(function () { window.scrollBy(0, Math.max(200, window.innerHeight * 0.9)); return Math.round(window.scrollY) + '|' + document.documentElement.scrollHeight + '|' + window.innerHeight; })()"""
    private const val PRELOAD_ROUNDS = 3
    private const val PRELOAD_WAIT_MS = 300L
    private const val STEP_WAIT_MS = 160L
    private const val STEP_MAX = 24
    private const val MIN_CHARS = 600

    // Desktop site (user switch): a desktop user-agent string. Kept across a view rebuild.
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
        appContext = context.applicationContext
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
            // Layout like a full browser (Chromium's WebView layout guide, "Configuring WebView like a browser"): pages
            // without a mobile viewport get a 980 px layout shown fitted to the screen, pages with one keep it, and
            // text is auto-sized. With the engine defaults every page is laid out at the screen width, so pages
            // built for desktop were cut off (DECISIONS.md 64).
            useWideViewPort = true
            loadWithOverviewMode = true
            layoutAlgorithm = WebSettings.LayoutAlgorithm.TEXT_AUTOSIZING
        }
        defaultAgent = view.settings.userAgentString
        if (WebViewFeature.isFeatureSupported(WebViewFeature.USER_AGENT_METADATA)) {
            defaultMetadata = WebSettingsCompat.getUserAgentMetadata(view.settings)
        }
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
                val web = scheme == "http" || scheme == "https"
                val now = SystemClock.uptimeMillis()
                if (web && request.isForMainFrame && request.hasGesture()) userNavAt = now
                // A tap, or a redirect within a few seconds of one (a link that goes through the site's own address
                // before it reaches the app), may open another app. Anything else never does.
                val tapped = request.isForMainFrame && (request.hasGesture() || (request.isRedirect && now - userNavAt < 8000L))
                // A Play Store page opens in the Play Store app when there is one.
                if (web && tapped && ExternalLinks.isStorePage(request.url) && listener.onExternal(request.url.toString())) return true
                // A tapped link: the app shows where it goes before the engine reports the page started.
                if (web && request.isForMainFrame && !request.isRedirect) listener.onNavigate(request.url.toString())
                if (!web && request.isForMainFrame) {
                    // Another app may take it (DECISIONS.md 73). The view never loads it, so the cancelled navigation must
                    // not leave its address or the loading bar behind.
                    if (tapped) listener.onExternal(request.url.toString())
                    listener.onPage(view.url, view.canGoBack(), view.canGoForward())
                    listener.onLoading(false)
                }
                return !web
            }

            override fun onPageStarted(view: WebView, url: String?, favicon: android.graphics.Bitmap?) {
                // Only web pages count as loading; an address handed to another app must not leave the bar running.
                val scheme = url?.substringBefore(':', "")
                if (scheme != null && scheme != "http" && scheme != "https" && scheme != "about") return
                listener.onLoading(true)
                listener.onPage(url, view.canGoBack(), view.canGoForward())
            }

            override fun onPageCommitVisible(view: WebView, url: String?) {
                listener.onCommitVisible()
            }

            override fun onPageFinished(view: WebView, url: String?) {
                if (bypassCache) {
                    bypassCache = false
                    view.settings.cacheMode = WebSettings.LOAD_DEFAULT
                }
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

    // The user-agent string and the client hints sent with every request (mobile or not, platform) must agree: many
    // sites decide on the hints, not the string (DECISIONS.md 74).
    private fun applyDesktop(view: WebView) {
        val base = defaultAgent
        view.settings.apply {
            userAgentString = if (desktopSite && base != null) desktopAgent(base) else base
        }
        desktopViewportScript?.remove()
        desktopViewportScript = null
        if (desktopSite && WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
            try {
                desktopViewportScript = WebViewCompat.addDocumentStartJavaScript(view, DESKTOP_VIEWPORT_JS, setOf("*"))
            } catch (_: IllegalArgumentException) {
                // The origin rule was refused; pages keep their own viewport.
            }
        }
        val original = defaultMetadata ?: return
        try {
            WebSettingsCompat.setUserAgentMetadata(view.settings, if (desktopSite) desktopMetadata(original) else original)
        } catch (e: Exception) {
            // Hints stay as they were; the user-agent string still changed.
        }
    }

    // A page's mobile address (m.example.com, en.m.example.org, mobile.example.com) becomes its normal address when the
    // desktop switch goes on, so the site is not asked for its phone version by name (DECISIONS.md 76).
    private fun desktopAddress(url: String): String {
        val uri = Uri.parse(url)
        val host = uri.host ?: return url
        val plain = when {
            host.startsWith("m.") -> host.removePrefix("m.")
            host.startsWith("mobile.") -> host.removePrefix("mobile.")
            host.contains(".m.") -> host.replaceFirst(".m.", ".")
            else -> return url
        }
        if (!plain.contains('.')) return url
        val authority = if (uri.port != -1) plain + ":" + uri.port else plain
        return uri.buildUpon().authority(authority).build().toString()
    }

    private fun desktopMetadata(o: UserAgentMetadata): UserAgentMetadata {
        val b = UserAgentMetadata.Builder()
            .setBrandVersionList(o.brandVersionList)
            .setPlatform("Linux")
            .setPlatformVersion("6.8.0")
            .setArchitecture("x86")
            .setBitness(64)
            .setModel("")
            .setMobile(false)
            .setWow64(false)
        o.fullVersion?.let { b.setFullVersion(it) }
        return b.build()
    }

    fun setDesktopSite(on: Boolean, reloadPage: Boolean) {
        desktopSite = on
        val view = webView ?: return
        view.stopLoading()
        applyDesktop(view)
        if (!reloadPage) return
        // A reload keeps the page's old zoom; a fresh load of the same address lets overview mode fit the width. The
        // cache is skipped for this one load: a cached copy would be the page made for the other kind of device.
        val address = view.url?.let { if (on) desktopAddress(it) else it }
        view.setInitialScale(0)
        view.settings.cacheMode = WebSettings.LOAD_NO_CACHE
        bypassCache = true
        if (address != null) view.loadUrl(address) else view.reload()
    }

    // True once the open page's document is parsed ("interactive" or "complete"); images, ads and frames may still load
    // (DECISIONS.md 66). False when there is no view.
    fun documentParsed(onResult: (Boolean) -> Unit) {
        val view = webView
        if (view == null) {
            onResult(false)
            return
        }
        view.evaluateJavascript("document.readyState") { raw -> onResult(raw == "\"interactive\"" || raw == "\"complete\"") }
    }

    // Reader mode: loads the rest of the page's text, then runs the extraction script. The callback (UI thread) gets the
    // JSON string literal the engine returns, "null", or null when there is no view.
    fun extractArticle(onResult: (String?) -> Unit) {
        val view = webView
        if (view == null) {
            onResult(null)
            return
        }
        readabilityScript()?.let { view.evaluateJavascript(it, null) }
        val handler = Handler(Looper.getMainLooper())
        var rounds = 0
        var lastHeight = -1
        var steps = 0

        fun done(raw: String?) {
            view.evaluateJavascript(RESTORE_JS, null)
            onResult(raw)
        }

        fun stepwise() {
            view.evaluateJavascript(STEP_START_JS, null)
            fun next() {
                view.evaluateJavascript(STEP_JS) { position ->
                    steps++
                    val parts = position?.trim('"')?.split('|')
                    val atEnd = parts != null && parts.size == 3 &&
                        (parts[0].toDoubleOrNull() ?: 0.0) + (parts[2].toDoubleOrNull() ?: 0.0) >= (parts[1].toDoubleOrNull() ?: 0.0) - 2
                    handler.postDelayed({
                        view.evaluateJavascript(READER_JS) { raw ->
                            if (atEnd || steps >= STEP_MAX) done(raw) else next()
                        }
                    }, STEP_WAIT_MS)
                }
            }
            next()
        }

        fun finish() {
            view.evaluateJavascript(READER_JS) { raw ->
                val peek = peek(raw)
                // Little text on a tall page: the text may be kept only near the screen, so scroll in steps.
                if (peek[0] < MIN_CHARS && peek[1] == 1) stepwise() else done(raw)
            }
        }

        fun step() {
            view.evaluateJavascript(PRELOAD_JS) { value ->
                val height = value?.toIntOrNull() ?: -1
                rounds++
                if ((lastHeight >= 0 && height <= lastHeight + lastHeight / 12) || rounds >= PRELOAD_ROUNDS) {
                    finish()
                } else {
                    lastHeight = height
                    handler.postDelayed({ step() }, PRELOAD_WAIT_MS)
                }
            }
        }
        step()
    }

    private var appContext: Context? = null
    private var readabilityLoader: String? = null

    // Mozilla Readability (Apache-2.0, DECISIONS.md 62) is kept as an app asset and handed to the page view only for
    // reading; it is removed again afterwards. Null when the asset cannot be read; the built-in finder then runs alone.
    private fun readabilityScript(): String? {
        readabilityLoader?.let { return it }
        val context = appContext ?: return null
        return try {
            val source = context.assets.open("Readability.min.js").bufferedReader().use { it.readText() }
            val script = "if (!window.__ardourRd) { (function () {\n" + source + "\n;window.__ardourRd = Readability; })(); }"
            readabilityLoader = script
            script
        } catch (_: java.io.IOException) {
            null
        }
    }

    // From an extraction result: characters of running text (paragraphs and quotes) and whether the page is tall.
    private fun peek(raw: String?): IntArray {
        if (raw == null || raw == "null") return intArrayOf(0, 0)
        return try {
            val json = JSONObject(JSONTokener(raw).nextValue() as? String ?: return intArrayOf(0, 0))
            intArrayOf(json.optInt("c"), json.optInt("k"))
        } catch (_: JSONException) {
            intArrayOf(0, 0)
        }
    }

    // Diagnostic report on the open page for the reader's long press: page structure and requests slower than 3 s.
    // No page text and no network use; the text goes to the clipboard only when the user asks (DECISIONS.md 58).
    fun pageReport(onResult: (String?) -> Unit) {
        val view = webView
        if (view == null) {
            onResult(null)
            return
        }
        readabilityScript()?.let { view.evaluateJavascript(it, null) }
        view.evaluateJavascript("window.__ardourReport = true;\n" + READER_JS) { raw ->
            view.evaluateJavascript("delete window.__ardourReport; delete window.__ardourRd;", null)
            val text = try {
                JSONTokener(raw ?: "null").nextValue() as? String
            } catch (_: JSONException) {
                null
            }
            onResult(text)
        }
    }

    // Third-party cookies (embedded ads, sign-in frames) are refused unless the user allows them (DECISIONS.md 57).
    fun setThirdPartyCookies(allow: Boolean) {
        val view = webView ?: return
        CookieManager.getInstance().setAcceptThirdPartyCookies(view, allow)
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

    // Stops whatever still loads in the background first, so a reload tap always restarts the page at once.
    fun reload() {
        val view = webView ?: return
        view.stopLoading()
        view.reload()
    }

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
