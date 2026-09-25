/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.reference.browser.tabs

import android.content.Context
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import mozilla.components.browser.state.state.TabSessionState
import mozilla.components.browser.tabstray.DefaultTabViewHolder
import mozilla.components.browser.tabstray.TabsAdapter
import mozilla.components.browser.tabstray.TabsTray
import mozilla.components.browser.tabstray.TabsTrayStyling
import mozilla.components.browser.tabstray.ViewHolderProvider
import mozilla.components.browser.thumbnails.loader.ThumbnailLoader
import mozilla.components.feature.tabs.tabstray.TabsFeature
import mozilla.components.support.base.feature.UserInteractionHandler
import org.mozilla.reference.browser.R
import org.mozilla.reference.browser.browser.BrowserFragment
import org.mozilla.reference.browser.ext.components
import org.mozilla.reference.browser.ext.requireComponents

/** A fragment for displaying the tabs tray. */
class TabsTrayFragment : Fragment(), UserInteractionHandler {
    private var tabsFeature: TabsFeature? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View = inflater.inflate(R.layout.fragment_tabstray, container, false)

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?,
    ) {
        super.onViewCreated(view, savedInstanceState)

        val trayAdapter = createAndSetupTabsTray(requireContext())

        tabsFeature =
            TabsFeature(
                trayAdapter,
                requireComponents.core.store,
                ::closeTabsTray,
            ) {
                !it.content.private
            }

        val tabsPanel: TabsPanel = view.findViewById(R.id.tabsPanel)
        val tabsToolbar: TabsToolbar = view.findViewById(R.id.tabsToolbar)

        tabsPanel.initialize(tabsFeature, updateTabsToolbar = ::updateTabsToolbar)
        tabsToolbar.initialize(tabsFeature) { closeTabsTray() }
    }

    override fun onStart() {
        super.onStart()

        tabsFeature?.start()
    }

    override fun onStop() {
        super.onStop()

        tabsFeature?.stop()
    }

    override fun onBackPressed(): Boolean {
        closeTabsTray()
        return true
    }

    private fun closeTabsTray() {
        activity?.supportFragmentManager?.beginTransaction()?.apply {
            replace(R.id.container, BrowserFragment.create())
            commit()
        }
    }

    private fun updateTabsToolbar(isPrivate: Boolean) {
        val tabsToolbar = requireView().findViewById<TabsToolbar>(R.id.tabsToolbar)
        tabsToolbar.updateToolbar(isPrivate)
    }

    private fun createAndSetupTabsTray(context: Context): TabsTray {
        val layoutManager = GridLayoutManager(context, if (resources.configuration.screenWidthDp >= 600) 3 else 2)
        val thumbnailLoader = ThumbnailLoader(context.components.core.thumbnailStorage)
        val trayStyling = TabsTrayStyling(itemBackgroundColor = Color.TRANSPARENT, selectedItemBackgroundColor = Color.parseColor("#21334F"), itemTextColor = Color.WHITE, selectedItemTextColor = Color.WHITE, itemUrlTextColor = Color.parseColor("#9BA8BD"), selectedItemUrlTextColor = Color.parseColor("#A8CFFF"))
        val viewHolderProvider: ViewHolderProvider = { viewGroup ->
            val view = LayoutInflater.from(context).inflate(R.layout.browser_tabstray_item, viewGroup, false)

            object : mozilla.components.browser.tabstray.TabViewHolder(view) {
                private val standard = DefaultTabViewHolder(view, thumbnailLoader)
                private val home = DefaultTabViewHolder(view, null)
                private var active = standard
                override var tab: TabSessionState? = null

                override fun bind(tab: TabSessionState, isSelected: Boolean, styling: TabsTrayStyling, delegate: TabsTray.Delegate) {
                    this.tab = tab
                    val isHome = tab.content.url in listOf("about:home", "about:blank", "")
                    active = if (isHome) home else standard
                    active.bind(tab, isSelected, styling, delegate)
                    view.findViewById<View>(R.id.ani_home_thumbnail).visibility = if (isHome) View.VISIBLE else View.GONE
                    view.findViewById<View>(R.id.mozac_browser_tabstray_thumbnail).visibility = if (isHome) View.INVISIBLE else View.VISIBLE
                    if (isHome) {
                        view.findViewById<android.widget.TextView>(R.id.mozac_browser_tabstray_title).text = "AniHome"
                        view.findViewById<android.widget.TextView>(R.id.mozac_browser_tabstray_url).text = "Home"
                    }
                }

                override fun updateSelectedTabIndicator(showAsSelected: Boolean) = active.updateSelectedTabIndicator(showAsSelected)
            }
        }
        val tabsAdapter =
            TabsAdapter(
                thumbnailLoader = thumbnailLoader,
                viewHolderProvider = viewHolderProvider,
                styling = trayStyling,
                delegate =
                    object : TabsTray.Delegate {
                        override fun onTabSelected(
                            tab: TabSessionState,
                            source: String?,
                        ) {
                            requireComponents.useCases.tabsUseCases.selectTab(tab.id)
                            closeTabsTray()
                        }

                        override fun onTabClosed(
                            tab: TabSessionState,
                            source: String?,
                        ) {
                            requireComponents.useCases.tabsUseCases.removeTab(tab.id)
                        }
                    },
            )

        val tabsTray = requireView().findViewById<RecyclerView>(R.id.tabsTray)
        tabsTray.layoutManager = layoutManager
        tabsTray.adapter = tabsAdapter

        TabsTouchHelper {
                requireComponents.useCases.tabsUseCases.removeTab(it.id)
            }
            .attachToRecyclerView(tabsTray)

        return tabsAdapter
    }
}
