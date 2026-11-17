package io.github.g_c_3.ardour

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri

// Links that leave the page for another app (DECISIONS.md 73). Only a tap behind the link starts one, and only as a
// plain "view this address" request: the target component, selector, flags and any action other than view are
// dropped, so a page cannot aim an intent at an app's private screens.
object ExternalLinks {
    // opened: an app took the link. loadInstead: an address to load in the page when no app could take it.
    class Outcome(val opened: Boolean, val loadInstead: String? = null)

    private val NEVER = setOf("javascript", "file", "content", "data", "blob", "about", "filesystem")

    fun open(context: Context, url: String): Outcome {
        val scheme = Uri.parse(url).scheme?.lowercase() ?: return Outcome(false)
        if (scheme in NEVER) return Outcome(false)
        val intent = try {
            if (scheme == "intent") Intent.parseUri(url, Intent.URI_INTENT_SCHEME) else Intent(Intent.ACTION_VIEW, Uri.parse(url))
        } catch (e: Exception) {
            return Outcome(false)
        }
        val fallback = intent.getStringExtra("browser_fallback_url")?.takeIf { it.startsWith("https://") }
        if (intent.action != null && intent.action != Intent.ACTION_VIEW) return Outcome(false, fallback)
        intent.action = Intent.ACTION_VIEW
        intent.addCategory(Intent.CATEGORY_BROWSABLE)
        intent.component = null
        intent.selector = null
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
        return try {
            context.startActivity(intent)
            Outcome(true)
        } catch (e: ActivityNotFoundException) {
            Outcome(false, fallback ?: storePage(intent))
        } catch (e: SecurityException) {
            Outcome(false, fallback)
        }
    }

    // An app is not installed: its page in the web store, when the link names one.
    private fun storePage(intent: Intent): String? {
        val data = intent.data
        val id = if (data != null && data.scheme == "market") data.getQueryParameter("id") else intent.`package`
        return id?.takeIf { it.isNotEmpty() }?.let { "https://play.google.com/store/apps/details?id=" + Uri.encode(it) }
    }

    fun isStorePage(uri: Uri): Boolean =
        uri.scheme == "https" && uri.host == "play.google.com" && (uri.path ?: "").startsWith("/store/apps")

    // A Play Store web address opens in the Play Store app, when it is installed.
    fun openInStore(context: Context, url: String): Boolean {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
            addCategory(Intent.CATEGORY_BROWSABLE)
            setPackage("com.android.vending")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        return try {
            context.startActivity(intent)
            true
        } catch (e: ActivityNotFoundException) {
            false
        } catch (e: SecurityException) {
            false
        }
    }
}
