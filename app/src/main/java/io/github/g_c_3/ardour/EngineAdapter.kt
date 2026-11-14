package io.github.g_c_3.ardour

import android.annotation.SuppressLint
import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.webkit.Profile
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature

// What the browser screen needs to hear from the engine.
interface PageListener {
    fun onPage(url: String?, canGoBack: Boolean, canGoForward: Boolean)
    fun onProgress(percent: Int)
    fun onLoading(loading: Boolean)
}

// The only file that calls android.webkit or androidx.webkit (DECISIONS.md 26). An engine API change is fixed here.
object EngineAdapter {
    private var webView: WebView? = null

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
        view.setBackgroundColor(Ui.PAPER)
        view.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView, newProgress: Int) = listener.onProgress(newProgress)
        }
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
                listener.onLoading(false)
                listener.onPage(url, view.canGoBack(), view.canGoForward())
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
