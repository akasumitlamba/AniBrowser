/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.reference.browser.browser

import android.os.Bundle
import android.view.View
import android.widget.FrameLayout
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.lifecycle.lifecycleScope
import com.google.android.material.floatingactionbutton.FloatingActionButton
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import mozilla.components.browser.state.selector.findTabOrCustomTab
import mozilla.components.browser.state.selector.selectedTab
import mozilla.components.browser.thumbnails.BrowserThumbnails
import mozilla.components.browser.toolbar.BrowserToolbar
import mozilla.components.concept.awesomebar.AwesomeBar.Suggestion
import mozilla.components.concept.engine.EngineView
import mozilla.components.lib.state.ext.flow
import mozilla.components.feature.awesomebar.AwesomeBarFeature
import mozilla.components.feature.awesomebar.provider.SearchSuggestionProvider
import mozilla.components.feature.readerview.view.ReaderViewControlsBar
import mozilla.components.feature.toolbar.WebExtensionToolbarFeature
import mozilla.components.support.base.feature.UserInteractionHandler
import mozilla.components.support.base.feature.ViewBoundFeatureWrapper
import org.mozilla.reference.browser.R
import org.mozilla.reference.browser.ani.ShortcutOverlay
import org.mozilla.reference.browser.ani.SiteSettings
import org.mozilla.reference.browser.ani.PlaybackController
import org.mozilla.reference.browser.ext.components
import org.mozilla.reference.browser.ext.requireComponents
import org.mozilla.reference.browser.search.AwesomeBarWrapper
import org.mozilla.reference.browser.tabs.TabsTrayFragment

/** Fragment used for browsing the web within the main app. */
class BrowserFragment : BaseBrowserFragment(), UserInteractionHandler {
    private val thumbnailsFeature = ViewBoundFeatureWrapper<BrowserThumbnails>()
    private val readerViewFeature = ViewBoundFeatureWrapper<ReaderViewIntegration>()
    private val webExtToolbarFeature = ViewBoundFeatureWrapper<WebExtensionToolbarFeature>()

    private val awesomeBar: AwesomeBarWrapper
        get() = requireView().findViewById(R.id.awesomeBar)

    private val toolbar: BrowserToolbar
        get() = requireView().findViewById(R.id.toolbar)

    private val engineView: EngineView
        get() = requireView().findViewById<View>(R.id.engineView) as EngineView

    private val readerViewBar: ReaderViewControlsBar
        get() = requireView().findViewById(R.id.readerViewBar)

    private val readerViewAppearanceButton: FloatingActionButton
        get() = requireView().findViewById(R.id.readerViewAppearanceButton)

    private val animeHubView: org.mozilla.reference.browser.ani.AnimeHubView?
        get() = view?.findViewById(R.id.animeHubView)

    override val shouldUseComposeUI: Boolean get() = false

    private var shortcutOverlay: ShortcutOverlay? = null
    private var addressEditCancelled = false

    @Suppress("LongMethod")
    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?,
    ) {
        super.onViewCreated(view, savedInstanceState)

        bindLoadingScreen(view)
        view.isFocusableInTouchMode = true
        (view as? org.mozilla.reference.browser.ani.BrowserRootLayout)?.dismissAddressEditor = {
            dismissAddressEditing().also { dismissed ->
                if (dismissed) (activity as? org.mozilla.reference.browser.BrowserActivity)?.apply {
                    suppressAddressBackKeyUp = true
                    addressBackHandledAt = android.os.SystemClock.uptimeMillis()
                }
            }
        }


        if (activity !is org.mozilla.reference.browser.ani.WebsiteActivity) {
        AwesomeBarFeature(awesomeBar, toolbar)
            .addSearchProvider(
                requireComponents.core.store,
                requireComponents.useCases.searchUseCases.defaultSearch,
                fetchClient = requireComponents.core.client,
                mode = SearchSuggestionProvider.Mode.MULTIPLE_SUGGESTIONS,
                engine = requireComponents.core.engine,
                limit = 5,
                filterExactMatch = true,
            )
            .addSessionProvider(
                resources,
                requireComponents.core.store,
                requireComponents.useCases.tabsUseCases.selectTab,
            )
            .addHistoryProvider(
                requireComponents.core.historyStorage,
                requireComponents.useCases.sessionUseCases.loadUrl,
            )
            .addClipboardProvider(requireContext(), requireComponents.useCases.sessionUseCases.loadUrl)

        // Account UI is disabled in this build. Do not initialize its sync
        // databases and network services for the address bar.
        awesomeBar.setOnRemoveSuggestionButtonClicked {
            awesomeBar.addHiddenSuggestion(it)
            (it.suggestion as? Suggestion)?.let { s -> deleteHistorySuggestion(s) }
        }

        val tabCount = org.mozilla.reference.browser.ani.TabCountAction(::showTabs)
        toolbar.addBrowserAction(tabCount)
        viewLifecycleOwner.lifecycleScope.launch {
            requireComponents.core.store.flow().map { it.tabs.size }.distinctUntilChanged().collect { tabCount.update(it) }
        }

        thumbnailsFeature.set(
            feature =
                BrowserThumbnails(
                    requireContext(),
                    engineView,
                    requireComponents.core.store,
                ),
            owner = this,
            view = view,
        )

        readerViewFeature.set(
            feature =
                ReaderViewIntegration(
                    requireContext(),
                    requireComponents.core.engine,
                    requireComponents.core.store,
                    toolbar,
                    readerViewBar,
                    readerViewAppearanceButton,
                ),
            owner = this,
            view = view,
        )

        webExtToolbarFeature.set(
            feature =
                WebExtensionToolbarFeature(
                    toolbar,
                    requireContext().components.core.store,
                ),
            owner = this,
            view = view,
        )

        }
        if (activity !is org.mozilla.reference.browser.ani.WebsiteActivity) {
            androidx.core.content.ContextCompat.getDrawable(requireContext(), R.drawable.mozac_ic_cross_24)?.let { icon ->
                toolbar.addEditActionStart(mozilla.components.concept.toolbar.Toolbar.ActionButton(
                    imageDrawable = icon,
                    contentDescription = "Cancel address editing",
                    iconTintColorResource = R.color.icons,
                    listener = { dismissAddressEditing() },
                ))
            }
        }
        engineView.setDynamicToolbarMaxHeight(0)
        (toolbar.layoutParams as? CoordinatorLayout.LayoutParams)?.behavior = null
        (view.findViewById<View>(R.id.swipeRefresh).layoutParams as? CoordinatorLayout.LayoutParams)?.apply {
            behavior = null
            topMargin = 0
        }
        updateBrowserViewport()
        view.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
            updateBrowserViewport()
            val focused = toolbar.findFocus()
            if (focused is android.widget.EditText && !focused.isShown) {
                addressEditCancelled = true
                clearAddressFocus()
            }
            val density = resources.displayMetrics.density
            val margin = maxOf((12 * density).toInt(), (view.width - (720 * density).toInt()) / 2)
            (awesomeBar.layoutParams as? CoordinatorLayout.LayoutParams)?.let { params ->
                val height = minOf((168*density).toInt(), (view.height - 88*density).toInt().coerceAtLeast(0))
                if (params.height != height || params.leftMargin != margin || params.rightMargin != margin) {
                    params.height = height
                    params.leftMargin = margin
                    params.rightMargin = margin
                    awesomeBar.layoutParams = params
                }
            }
            (toolbar.layoutParams as? CoordinatorLayout.LayoutParams)?.let { params ->
                if (params.leftMargin != margin || params.rightMargin != margin) {
                    params.leftMargin = margin
                    params.rightMargin = margin
                    toolbar.layoutParams = params
                }
            }
        }

        // Shortcut / WebsiteActivity mode — toolbar hidden, fullscreen, gesture overlay
        if (activity is org.mozilla.reference.browser.ani.WebsiteActivity) {
            toolbar.visibility = View.GONE
            engineView.setDynamicToolbarMaxHeight(0)

            // Remove coordinator behaviour so engine fills screen top-to-bottom
            val refresh = view.findViewById<View>(R.id.swipeRefresh)
            (refresh.layoutParams as? CoordinatorLayout.LayoutParams)?.apply {
                behavior = null
                topMargin = 0
                bottomMargin = 0
            }

            // Immersive mode — bars visible transiently on swipe from screen edge
            androidx.core.view.WindowInsetsControllerCompat(requireActivity().window, view).apply {
                systemBarsBehavior = androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                hide(androidx.core.view.WindowInsetsCompat.Type.systemBars())
            }

            // Attach gesture-triggered overlay on the root FrameLayout of the activity
            val rootContainer = requireActivity().window.decorView
                .findViewById<FrameLayout>(android.R.id.content)
            if (rootContainer != null) {
                val ctx = requireContext()
                val prefs = ctx.getSharedPreferences("anibrowser", android.content.Context.MODE_PRIVATE)
                fun currentSession() = requireComponents.core.store.state.tabs
                    .firstOrNull { it.id == sessionId }

                shortcutOverlay = ShortcutOverlay(
                    context = ctx,
                    container = rootContainer,
                    onSpeedSelected = { speed ->
                        PlaybackController.setSpeed(ctx, speed)
                    },
                    onSeek = { seconds ->
                        PlaybackController.mediaCommand(if (seconds < 0) "back" else "forward")
                    },
                    onViewModeSelected = { mode ->
                        currentSession()?.let { tab ->
                            SiteSettings.setViewingMode(ctx, tab.content.url, mode)
                            tab.engineState.engineSession?.let { engine ->
                                SiteSettings.apply(ctx, engine, tab.content.url)
                                engine.reload()
                            }
                        }
                    },
                    getCurrentSpeed = { prefs.getFloat("speed", 1f).toDouble() },
                    getCurrentViewMode = { SiteSettings.viewingMode(ctx, currentSession()?.content?.url.orEmpty()) },
                    onSiteSettings = { SiteSettings.show(ctx, currentSession()?.content?.url.orEmpty()) },
                )
                (activity as? org.mozilla.reference.browser.ani.WebsiteActivity)?.shortcutControls = shortcutOverlay
            }
        }
        run {
            if (activity is org.mozilla.reference.browser.ani.WebsiteActivity) {
                (animeHubView?.layoutParams as? android.view.ViewGroup.MarginLayoutParams)?.bottomMargin = 0
            }
            animeHubView?.apply {
                onOpenUrl = { targetUrl ->
                    visibility = View.GONE
                    releaseContent()
                    val tabId = sessionId ?: requireComponents.core.store.state.selectedTabId
                    if (tabId != null) {
                        requireComponents.useCases.sessionUseCases.loadUrl.invoke(targetUrl, sessionId = tabId)
                    } else {
                        requireComponents.useCases.tabsUseCases.addTab(targetUrl, selectTab = true)
                    }
                }
            }

            viewLifecycleOwner.lifecycleScope.launch {
                requireComponents.core.store
                    .flow()
                    .map { state ->
                        val tab = state.findTabOrCustomTab(sessionId ?: state.selectedTabId.orEmpty()) ?: state.selectedTab
                        tab?.content?.url
                    }
                    .distinctUntilChanged()
                    .collect { url ->
                        val isHome = url.isNullOrBlank() || url == "about:home" || url == "about:blank"
                        animeHubView?.visibility = if (isHome) View.VISIBLE else View.GONE
                        if (isHome) {
                            animeHubView?.loadContent()
                        } else {
                            animeHubView?.releaseContent(destroy = false)
                        }
                    }
            }
        }
    }

    private data class LoadingState(val id: String?, val url: String, val loading: Boolean, val progress: Int, val painted: Boolean)

    private fun bindLoadingScreen(root: View) {
        val screen = root.findViewById<View>(R.id.siteLoadingScreen)
        screen.visibility = View.GONE
        val logo = root.findViewById<android.widget.ImageView>(R.id.siteLoadingLogo)
        val label = root.findViewById<android.widget.TextView>(R.id.siteLoadingLabel)
        val progress = root.findViewById<android.widget.ProgressBar>(R.id.siteLoadingProgress)
        val pulseAnim = runCatching {
            android.view.animation.AnimationUtils.loadAnimation(root.context, R.anim.ani_logo_pulse)
        }.getOrNull()
        var key = ""
        var sawLoading = false
        var showJob: kotlinx.coroutines.Job? = null

        fun hideScreen() {
            showJob?.cancel()
            showJob = null
            logo?.clearAnimation()
            screen.isClickable = false
            if (screen.visibility == View.VISIBLE) {
                screen.animate().alpha(0f).setDuration(200L).withEndAction {
                    screen.visibility = View.GONE
                    screen.alpha = 1f
                }.start()
            } else {
                screen.visibility = View.GONE
            }
        }

        fun showScreen() {
            screen.animate().cancel()
            screen.alpha = 1f
            screen.visibility = View.VISIBLE
            screen.isClickable = true
            if (pulseAnim != null && logo?.animation == null) {
                logo?.startAnimation(pulseAnim)
            }
        }

        animeHubView?.onContentReady = {
            if (animeHubView?.visibility == View.VISIBLE) hideScreen()
        }

        viewLifecycleOwner.lifecycleScope.launch {
            requireComponents.core.store.flow().map { state ->
                val tab = state.findTabOrCustomTab(sessionId ?: state.selectedTabId.orEmpty()) ?: state.selectedTab
                LoadingState(tab?.id, tab?.content?.url.orEmpty(), tab?.content?.loading == true,
                    tab?.content?.progress ?: 0, tab?.content?.firstContentfulPaint == true)
            }.distinctUntilChanged().collect { state ->
                val nextKey = "${state.id}:${state.url}"
                if (key != nextKey) { key = nextKey; sawLoading = false }
                if (state.loading) sawLoading = true
                val home = state.url.isBlank() || state.url == "about:home" || state.url == "about:blank"
                val shouldShow = home && animeHubView?.contentReady != true

                label.setText(R.string.ani_opening_app)
                progress.isIndeterminate = true

                if (!shouldShow || activity?.isInPictureInPictureMode == true) {
                    hideScreen()
                } else if (screen.visibility != View.VISIBLE && showJob?.isActive != true) {
                    showJob = viewLifecycleOwner.lifecycleScope.launch {
                        kotlinx.coroutines.delay(200L)
                        showScreen()
                    }
                }
            }
        }
    }

    private fun showTabs() {
        dismissAddressEditing()
        activity?.supportFragmentManager?.beginTransaction()?.apply {
            replace(R.id.container, TabsTrayFragment())
            commit()
        }
    }

    @OptIn(DelicateCoroutinesApi::class)
    private fun deleteHistorySuggestion(suggestion: Suggestion) {
        lifecycleScope.launch(Dispatchers.IO) {
            suggestion.description?.let {
                requireComponents.core.historyStorage.deleteHistoryMetadataForUrl(it)
            }
        }
    }

    override fun onBackPressed(): Boolean {
        if (dismissAddressEditing()) return true
        if (shortcutOverlay?.hide() == true) return true
        if (animeHubView?.visibility == View.VISIBLE) {
            val tab = requireComponents.core.store.state.findTabOrCustomTab(sessionId ?: requireComponents.core.store.state.selectedTabId.orEmpty())
            val curUrl = tab?.content?.url
            if (curUrl != null && curUrl != "about:home" && curUrl != "about:blank" && curUrl.isNotBlank()) {
                animeHubView?.visibility = View.GONE
                return true
            }
        }
        return readerViewFeature.onBackPressed() || super.onBackPressed()
    }

    fun dismissAddressEditing(hideKeyboard: Boolean = true): Boolean {
        if (view == null || activity is org.mozilla.reference.browser.ani.WebsiteActivity) return false
        if (!toolbar.onBackPressed()) return false
        (activity as? org.mozilla.reference.browser.BrowserActivity)?.addressBackHandledAt = android.os.SystemClock.uptimeMillis()
        addressEditCancelled = true
        awesomeBar.visibility = View.GONE
        clearAddressFocus(hideKeyboard)
        return true
    }

    private fun clearAddressFocus(hideKeyboard: Boolean = true) {
        toolbar.findFocus()?.clearFocus()
        toolbar.clearFocus()
        // Hidden edit fields must not remain the window's focus target: a later
        // inset/layout change can otherwise summon the keyboard again.
        val root = requireView() as android.view.ViewGroup
        val previous = root.descendantFocusability
        root.descendantFocusability = android.view.ViewGroup.FOCUS_BLOCK_DESCENDANTS
        root.requestFocus()
        root.descendantFocusability = previous
        if (hideKeyboard) {
            val input = requireContext().getSystemService(android.content.Context.INPUT_METHOD_SERVICE)
                as android.view.inputmethod.InputMethodManager
            input.hideSoftInputFromWindow(toolbar.windowToken, 0)
        }
    }

    fun onAddressKeyboardShown() {
        // The engine toolbar posts its keyboard request. A quick Back/cancel can
        // arrive first; reject that late request after the editor has closed.
        if (view != null && addressEditCancelled) clearAddressFocus()
    }

    fun dismissAddressEditingOutside(x: Int, y: Int): Boolean {
        if (view == null || activity is org.mozilla.reference.browser.ani.WebsiteActivity) return false
        val bounds = android.graphics.Rect()
        addressEditCancelled = false
        if (toolbar.getGlobalVisibleRect(bounds) && bounds.contains(x, y)) return false
        if (awesomeBar.isShown && awesomeBar.getGlobalVisibleRect(bounds) && bounds.contains(x, y)) return false
        return dismissAddressEditing()
    }

    override fun onDestroyView() {
        (view as? org.mozilla.reference.browser.ani.BrowserRootLayout)?.dismissAddressEditor = null
        (activity as? org.mozilla.reference.browser.ani.WebsiteActivity)?.shortcutControls = null
        shortcutOverlay?.dispose()
        shortcutOverlay = null
        animeHubView?.onContentReady = null
        animeHubView?.releaseContent(destroy = true)
        super.onDestroyView()
    }

    companion object {
        fun create(sessionId: String? = null) =
            BrowserFragment().apply {
                arguments =
                    Bundle().apply {
                        putSessionId(sessionId)
                    }
            }
    }
}
