/* SPDX-License-Identifier: MPL-2.0 */
package org.mozilla.reference.browser.ani

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.util.AttributeSet
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import org.mozilla.reference.browser.settings.SettingsActivity
import org.mozilla.reference.browser.ext.components
import java.io.File
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Retains a paused Home briefly for quick returns, then releases Chromium so
 * website playback has the memory budget on low-end devices.
 */
class AnimeHubView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : FrameLayout(context, attrs, defStyleAttr) {
    private var internalWebView: WebView? = null
    private var wallpaperScope = MainScope()
    private var wallpaperJob: Job? = null
    private var contentDirty = false
    private val releaseIdleHome = Runnable { if (visibility != VISIBLE) releaseContent(destroy = true) }
    @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION") // Android 10 devices still deliver these memory signals.
    private val memoryCallbacks = object : android.content.ComponentCallbacks2 {
        override fun onConfigurationChanged(configuration: android.content.res.Configuration) = Unit
        override fun onLowMemory() { if (visibility != VISIBLE) releaseContent(destroy = true) }
        override fun onTrimMemory(level: Int) {
            if (level >= android.content.ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW && visibility != VISIBLE)
                releaseContent(destroy = true)
        }
    }
    var onOpenUrl: ((String) -> Unit)? = null
    var onContentReady: (() -> Unit)? = null
    var contentReady = false
        private set

    inner class AniHomeBridge {
        @JavascriptInterface fun refreshLogos() = post {
            val original = internalWebView
            AniHomeManager.refreshLogos(context) { count ->
                if (internalWebView === original) original?.evaluateJavascript("window.finishRefresh && finishRefresh($count)", null)
            }
        }
        @JavascriptInterface fun searchUrl(query: String) = BrowserPreferences.searchUrl(context, query)
        @JavascriptInterface fun openUrl(url: String) = post {
            if (url == "anibrowser://settings") openSettings() else onOpenUrl?.invoke(url)
        }

        @JavascriptInterface fun openSettings() = post {
            context.startActivity(Intent(context, SettingsActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            })
        }

        @JavascriptInterface fun toggleAdShield(): Boolean {
            val prefs = context.getSharedPreferences("anibrowser_prefs", Context.MODE_PRIVATE)
            return (!prefs.getBoolean("adshield_enabled", true)).also {
                prefs.edit().putBoolean("adshield_enabled", it).apply()
            }
        }

        @JavascriptInterface fun isAdShieldActive() = context
            .getSharedPreferences("anibrowser_prefs", Context.MODE_PRIVATE)
            .getBoolean("adshield_enabled", true)

        @JavascriptInterface fun addTile(url: String, title: String) = post {
            AniHomeManager.addManualTile(context, url, title)
        }

        @JavascriptInterface fun updateTile(id: String, title: String) = post {
            AniHomeManager.updateTileTitle(context, id, title, notify = false)
        }

        @JavascriptInterface fun deleteTile(id: String) = post {
            AniHomeManager.removeTile(context, id, notify = false)
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun ensureWebView(): WebView {
        internalWebView?.let { return it }
        return WebView(context).apply {
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
            setBackgroundColor(Color.parseColor("#070A12"))
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = false
            settings.allowFileAccess = false
            settings.allowContentAccess = false
            settings.useWideViewPort = true
            settings.loadWithOverviewMode = false
            settings.setSupportZoom(false)
            isVerticalScrollBarEnabled = false
            overScrollMode = OVER_SCROLL_NEVER
            addJavascriptInterface(AniHomeBridge(), "AniHomeBridge")
            webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView?, url: String?) {
                    contentReady = true
                    onContentReady?.invoke()
                    updateWallpaper()
                }

                override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                    val url = request?.url?.toString() ?: return false
                    if (url.startsWith("http://") || url.startsWith("https://")) {
                        onOpenUrl?.invoke(url)
                        return true
                    }
                    return false
                }

                override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest?): WebResourceResponse? {
                    val uri = request?.url ?: return null
                    if (uri.host != LOCAL_HOST) return null
                    return when {
                        uri.path == "/assets/logo.png" -> runCatching {
                            WebResourceResponse("image/png", null, context.assets.open("anibrowser_logo_160.png"))
                        }.getOrNull()
                        uri.path?.startsWith("/icon/") == true -> iconResponse(uri)
                        uri.path?.startsWith("/wallpaper/") == true -> DailyWallpaper.file(context)?.let { file ->
                            runCatching { WebResourceResponse("image/jpeg", null, file.inputStream()) }.getOrNull()
                        }
                        else -> null
                    }
                }
            }
            this@AnimeHubView.addView(this)
            internalWebView = this
        }
    }

    private fun iconResponse(uri: Uri): WebResourceResponse? {
        val name = uri.lastPathSegment ?: return null
        val id = name.removeSuffix(".png")
        if (!id.matches(Regex("[A-Za-z0-9_-]{1,64}"))) return null
        val file = File(context.filesDir, "anihome_icons/$id.png")
        if (!file.isFile) return null
        return runCatching { WebResourceResponse("image/png", null, file.inputStream()) }.getOrNull()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        wallpaperScope.cancel()
        wallpaperScope = MainScope()
        context.applicationContext.registerComponentCallbacks(memoryCallbacks)
        AniHomeManager.onTilesChanged = { post {
            contentDirty = true
            if (visibility == VISIBLE) loadContent(forceReload = true)
        } }
    }

    override fun onDetachedFromWindow() {
        removeCallbacks(wallpaperCheck)
        AniHomeManager.onTilesChanged = null
        context.applicationContext.unregisterComponentCallbacks(memoryCallbacks)
        removeCallbacks(releaseIdleHome)
        wallpaperScope.cancel()
        releaseContent(destroy = true)
        super.onDetachedFromWindow()
    }

    private val wallpaperCheck = object : Runnable {
        override fun run() {
            val state = context.components.core.store.state
            if (internalWebView != null && windowVisibility == VISIBLE && visibility == VISIBLE &&
                state.tabs.none { it.content.loading } && state.customTabs.none { it.content.loading }) {
                if (wallpaperJob?.isActive != true) {
                    wallpaperJob = wallpaperScope.launch {
                        DailyWallpaper.refresh(context.applicationContext) {
                            val current = context.components.core.store.state
                            current.tabs.none { it.content.loading } && current.customTabs.none { it.content.loading }
                        }
                        updateWallpaper()
                    }
                }
            }
            if (internalWebView != null) postDelayed(this, 60_000L)
        }
    }
    override fun onWindowVisibilityChanged(visibility: Int) {
        super.onWindowVisibilityChanged(visibility)
        removeCallbacks(wallpaperCheck)
        if (visibility == VISIBLE && internalWebView != null) postDelayed(wallpaperCheck, 8000L)
    }
    private fun updateWallpaper() {
        internalWebView?.evaluateJavascript("window.setWallpaper && window.setWallpaper(${DailyWallpaper.state(context)})", null)
    }

    fun loadContent(forceReload: Boolean = false) {
        removeCallbacks(releaseIdleHome)
        removeCallbacks(wallpaperCheck)
        postDelayed(wallpaperCheck, 8000L)
        val wv = internalWebView
        if (!forceReload && !contentDirty && wv != null && contentReady) {
            wv.onResume()
            wv.visibility = VISIBLE
            onContentReady?.invoke()
            return
        }
        contentReady = false
        contentDirty = false
        val view = ensureWebView()
        view.visibility = VISIBLE
        view.onResume()
        view.loadDataWithBaseURL("https://$LOCAL_HOST/", AnimeHub.getHtml(context), "text/html", "UTF-8", null)
    }

    fun releaseContent(destroy: Boolean = false) {
        removeCallbacks(releaseIdleHome)
        if (!destroy) {
            removeCallbacks(wallpaperCheck)
            wallpaperJob?.cancel()
            wallpaperJob = null
            internalWebView?.onPause()
            internalWebView?.visibility = GONE
            postDelayed(releaseIdleHome, 30_000L)
            return
        }
        contentReady = false
        removeCallbacks(wallpaperCheck)
        wallpaperJob?.cancel()
        wallpaperJob = null
        internalWebView?.let {
            it.stopLoading()
            it.removeJavascriptInterface("AniHomeBridge")
            it.webViewClient = WebViewClient()
            removeView(it)
            it.destroy()
        }
        internalWebView = null
    }

    private companion object { const val LOCAL_HOST = "anibrowser.local" }
}
