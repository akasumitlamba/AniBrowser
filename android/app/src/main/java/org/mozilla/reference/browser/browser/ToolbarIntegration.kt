/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.reference.browser.browser

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import mozilla.components.browser.domains.autocomplete.ShippedDomainsProvider
import mozilla.components.browser.state.selector.selectedTab
import mozilla.components.browser.state.state.SessionState
import mozilla.components.browser.state.store.BrowserStore
import mozilla.components.browser.storage.sync.PlacesHistoryStorage
import mozilla.components.browser.toolbar.BrowserToolbar
import mozilla.components.browser.toolbar.display.DisplayToolbar
import mozilla.components.concept.menu.MenuController
import mozilla.components.concept.menu.candidate.ContainerStyle
import mozilla.components.concept.menu.candidate.DrawableMenuIcon
import mozilla.components.concept.menu.candidate.MenuCandidate
import mozilla.components.concept.menu.candidate.RowMenuCandidate
import mozilla.components.concept.menu.candidate.SmallMenuCandidate
import mozilla.components.concept.menu.candidate.TextMenuCandidate
import mozilla.components.feature.pwa.WebAppUseCases
import mozilla.components.feature.session.SessionUseCases
import mozilla.components.feature.tabs.TabsUseCases
import mozilla.components.feature.toolbar.ToolbarAutocompleteFeature
import mozilla.components.feature.toolbar.ToolbarFeature
import mozilla.components.lib.state.ext.flow
import mozilla.components.support.base.feature.LifecycleAwareFeature
import mozilla.components.support.base.feature.UserInteractionHandler
import org.mozilla.reference.browser.R
import org.mozilla.reference.browser.addons.AddonsActivity
import org.mozilla.reference.browser.ext.components
import org.mozilla.reference.browser.ext.share
import org.mozilla.reference.browser.settings.SettingsActivity

@Suppress("LongParameterList")
class ToolbarIntegration(
    private val context: Context,
    private val toolbar: BrowserToolbar,
    historyStorage: PlacesHistoryStorage,
    private val store: BrowserStore,
    private val sessionUseCases: SessionUseCases,
    private val tabsUseCases: TabsUseCases,
    private val webAppUseCases: WebAppUseCases,
    sessionId: String? = null,
) : LifecycleAwareFeature, UserInteractionHandler {
    private val shippedDomainsProvider =
        ShippedDomainsProvider().also {
            it.initialize(context)
        }

    private val scope = MainScope()
    private var menuJob: Job? = null

    private fun revealHome() {
        toolbar.rootView.findViewById<org.mozilla.reference.browser.ani.AnimeHubView>(R.id.animeHubView)?.apply {
            visibility = android.view.View.VISIBLE
            loadContent()
        }
    }

    private fun menuToolbar(session: SessionState?): RowMenuCandidate {
        val tint = ContextCompat.getColor(context, R.color.icons)

        val forward =
            SmallMenuCandidate(
                contentDescription = "Forward",
                icon =
                    DrawableMenuIcon(
                        context,
                        mozilla.components.ui.icons.R.drawable.mozac_ic_forward_24,
                        tint = tint,
                    ),
                containerStyle = ContainerStyle(isEnabled = session?.content?.canGoForward == true),
            ) {
                sessionUseCases.goForward.invoke()
            }

        val refresh =
            SmallMenuCandidate(
                contentDescription = "Refresh",
                icon =
                    DrawableMenuIcon(
                        context,
                        mozilla.components.ui.icons.R.drawable.mozac_ic_arrow_clockwise_24,
                        tint = tint,
                    ),
            ) {
                sessionUseCases.reload.invoke()
            }

        val stop =
            SmallMenuCandidate(
                contentDescription = "Stop",
                icon =
                    DrawableMenuIcon(
                        context,
                        mozilla.components.ui.icons.R.drawable.mozac_ic_cross_24,
                        tint = tint,
                    ),
            ) {
                sessionUseCases.stopLoading.invoke()
            }

        val home =
            SmallMenuCandidate(
                contentDescription = "Home",
                icon =
                    DrawableMenuIcon(
                        context,
                        mozilla.components.ui.icons.R.drawable.mozac_ic_home_24,
                        tint = tint,
                    ),
            ) {
                if (session != null) {
                    sessionUseCases.loadUrl.invoke("about:home", sessionId = session.id)
                } else {
                    sessionUseCases.loadUrl.invoke("about:home")
                }
                revealHome()
            }

        return RowMenuCandidate(listOf(home, forward, refresh, stop))
    }

    private fun sessionMenuItems(sessionState: SessionState): List<MenuCandidate> =
        listOfNotNull(
            menuToolbar(sessionState),
            TextMenuCandidate("New tab") {
                tabsUseCases.addTab.invoke("about:home", selectTab = true)
            },
            TextMenuCandidate("New private tab") {
                tabsUseCases.addTab.invoke("about:privatebrowsing", selectTab = true, private = true)
            },
            TextMenuCandidate("Site settings") {
                org.mozilla.reference.browser.ani.SiteSettings.show(context, sessionState.content.url)
            },
            TextMenuCandidate("Playback speed") {
                org.mozilla.reference.browser.ani.PlaybackController.show(context)
            },
            TextMenuCandidate("Share") {
                val url = sessionState.content.url
                context.share(url)
            },
            TextMenuCandidate("Copy link") {
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                clipboard.setPrimaryClip(android.content.ClipData.newPlainText("Link", sessionState.content.url))
                android.widget.Toast.makeText(context, "Link copied", android.widget.Toast.LENGTH_SHORT).show()
            },
            if (webAppUseCases.isPinningSupported()) {
                TextMenuCandidate(
                    text = "Add to homescreen",
                    containerStyle = ContainerStyle(isVisible = webAppUseCases.isPinningSupported()),
                ) {
                    org.mozilla.reference.browser.ani.WebsiteShortcut.pin(
                        context, sessionState.content.url, sessionState.content.title)
                }
            } else {
                null
            },
            if (sessionState.content.url.startsWith("http")) TextMenuCandidate("Add to AniHome") {
                val url = sessionState.content.url
                if (!url.isNullOrBlank() && url != "about:blank" && url != "about:home") {
                    org.mozilla.reference.browser.ani.AniHomeManager.addFromSession(
                        context,
                        url,
                        sessionState.content.title,
                        sessionState.content.icon,
                    )
                    android.widget.Toast.makeText(context, "Added to AniHome", android.widget.Toast.LENGTH_SHORT).show()
                }
            } else null,
            TextMenuCandidate(text = "Find in Page") {
                FindInPageIntegration.launch?.invoke()
            },
            TextMenuCandidate("Close tab") {
                tabsUseCases.removeTab.invoke(sessionState.id)
            },
        )

    private fun menuItems(sessionState: SessionState?): List<MenuCandidate> {
        val sessionMenuItems =
            if (sessionState != null) {
                sessionMenuItems(sessionState)
            } else {
                emptyList()
            }

        return sessionMenuItems +
            listOf(
                TextMenuCandidate(text = "Add-ons") {
                    val intent = Intent(context, AddonsActivity::class.java)
                    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    context.startActivity(intent)
                },
                TextMenuCandidate(text = "About AniBrowser") {
                    org.mozilla.reference.browser.ani.BrowserPreferences.about(context)
                },
                TextMenuCandidate(text = "Settings") {
                    val intent = Intent(context, SettingsActivity::class.java)
                    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    context.startActivity(intent)
                },
            )
    }

    private val browserMenuController: MenuController = org.mozilla.reference.browser.ani.GlassMenuController()

    init {
        toolbar.display.apply {
            indicators =
                listOf(
                    DisplayToolbar.Indicators.SECURITY,
                )
            displayIndicatorSeparator = false
            menuController = browserMenuController
            hint = context.getString(R.string.toolbar_hint)
            urlFormatter = { url ->
                val s = url.toString()
                if (s == "about:home" || s == "about:blank") "" else url
            }

            setUrlBackground(ResourcesCompat.getDrawable(context.resources, R.drawable.url_background, context.theme))
        }

        ContextCompat.getDrawable(context, mozilla.components.ui.icons.R.drawable.mozac_ic_home_24)?.let { icon ->
            toolbar.addNavigationAction(
                mozilla.components.concept.toolbar.Toolbar.ActionButton(
                    imageDrawable = icon,
                    contentDescription = "Home",
                    iconTintColorResource = R.color.icons,
                    listener = {
                        if (store.state.tabs.isEmpty()) {
                            tabsUseCases.addTab("about:home", selectTab = true)
                        } else {
                            sessionUseCases.loadUrl.invoke("about:home")
                        }
                        revealHome()
                    },
                )
            )
        }

        toolbar.edit.apply {
            hint = context.getString(R.string.toolbar_hint)
        }

        ToolbarAutocompleteFeature(toolbar).apply {
            updateAutocompleteProviders(listOf(historyStorage, shippedDomainsProvider))
        }

        val editUrlId = context.resources.getIdentifier("mozac_browser_toolbar_edit_url_view", "id", context.packageName)
        if (editUrlId != 0) {
            toolbar.findViewById<android.widget.EditText>(editUrlId)?.let { editView ->
                editView.setOnFocusChangeListener { _, hasFocus ->
                    if (hasFocus) {
                        val text = editView.text?.toString()?.trim().orEmpty()
                        if (text == "about:home" || text == "about:blank") {
                            editView.setText("")
                        }
                    }
                }
            }
        }
    }

    private fun observeMenu() {
        menuJob?.cancel()
        menuJob = scope.launch {
            store
                .flow()
                .map { state -> state.selectedTab }
                .distinctUntilChangedBy { tab ->
                    // Playback position updates do not change menu contents.
                    listOf(tab?.id, tab?.content?.url, tab?.content?.title,
                        tab?.content?.canGoForward, tab?.content?.icon)
                }
                .collect { tab ->
                    browserMenuController.submitList(menuItems(tab).map { item ->
                        if (item is TextMenuCandidate) item.copy(start = DrawableMenuIcon(context, when (item.text) {
                            "New private tab" -> R.drawable.mozac_ic_private_mode_24
                            "New tab" -> R.drawable.ani_plus
                            "Add-ons" -> R.drawable.ani_extensions
                            "Close tab" -> R.drawable.mozac_ic_cross_24
                            "Share" -> R.drawable.ani_share
                            "Copy link" -> R.drawable.ani_copy
                            "Playback speed" -> R.drawable.ani_speed
                            "Find in Page" -> R.drawable.ani_search
                            "About AniBrowser" -> R.drawable.ani_info
                            "Site settings" -> R.drawable.mozac_ic_shield_24
                            "Add to AniHome" -> R.drawable.ani_home_add
                            "Add to homescreen" -> R.drawable.ani_shortcut
                            else -> R.drawable.mozac_ic_settings_24
                        }, tint = android.graphics.Color.parseColor("#9AC5FF"))) else item
                    })
                }
        }
    }

    private val toolbarFeature: ToolbarFeature =
        ToolbarFeature(
            toolbar,
            context.components.core.store,
            context.components.useCases.sessionUseCases.loadUrl,
            { searchTerms ->
                context.components.useCases.sessionUseCases.loadUrl(
                    org.mozilla.reference.browser.ani.BrowserPreferences.searchUrl(context, searchTerms)
                )
            },
            sessionId,
        )

    private var toolbarStateJob: Job? = null

    private fun observeToolbarState() {
        toolbarStateJob?.cancel()
        toolbarStateJob = scope.launch {
            store
                .flow()
                .map { state ->
                    val tab = state.selectedTab
                    Pair(tab?.content?.url.orEmpty(), tab?.content?.loading == true)
                }
                .distinctUntilChanged()
                .collect { (url, _) ->
                    val isHome = url.isBlank() || url == "about:home" || url == "about:blank"
                    toolbar.display.indicators = if (isHome) emptyList() else listOf(DisplayToolbar.Indicators.SECURITY)
                    if (isHome) {
                        toolbar.post {
                            toolbar.url = ""
                        }
                    }
                }
        }
    }

    override fun start() {
        toolbarFeature.start()
        observeMenu()
        observeToolbarState()
    }

    override fun stop() {
        toolbarStateJob?.cancel()
        toolbarStateJob = null
        menuJob?.cancel()
        menuJob = null
        toolbarFeature.stop()
    }

    override fun onBackPressed(): Boolean = toolbarFeature.onBackPressed()
}
