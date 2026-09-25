/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.reference.browser.pip

import android.app.Activity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.mapNotNull
import mozilla.components.browser.state.selector.findTabOrCustomTabOrSelectedTab
import mozilla.components.browser.state.store.BrowserStore
import mozilla.components.feature.session.PictureInPictureFeature
import mozilla.components.lib.state.ext.flowScoped
import mozilla.components.support.base.feature.LifecycleAwareFeature

class PictureInPictureIntegration(
    private val store: BrowserStore,
    private val activity: Activity,
    private val customTabId: String?,
    private val whiteList: List<String> = listOf(
        "youtube.com",
        "crunchyroll.com",
        "netflix.com",
        "twitch.tv",
        "vimeo.com",
        "hianime",
        "animepahe",
        "aniwatch",
    ),
) : LifecycleAwareFeature {
    private var scope: CoroutineScope? = null
    private val pictureFeature = PictureInPictureFeature(store, activity)
    private var whiteListed = false

    override fun start() {
        scope =
            store.flowScoped(dispatcher = Dispatchers.Main) { flow ->
                flow
                    .mapNotNull { state -> state.findTabOrCustomTabOrSelectedTab(customTabId) }
                    .distinctUntilChangedBy { it.content.url }
                    .collect { whiteListed = isWhitelisted(it.content.url) }
            }
    }

    override fun stop() {
        scope?.cancel()
    }

    fun onHomePressed(): Boolean {
        val selected = store.state.findTabOrCustomTabOrSelectedTab(customTabId)
        if (!org.mozilla.reference.browser.ani.MediaPreferences.enabled(activity, org.mozilla.reference.browser.ani.MediaPreferences.PIP, selected?.content?.url.orEmpty())) return false
        val isMediaPlaying = selected?.mediaSessionState?.playbackState ==
            mozilla.components.concept.engine.mediasession.MediaSession.PlaybackState.PLAYING
        val isFullScreen = selected?.content?.fullScreen == true

        if (isMediaPlaying || isFullScreen) {
            val entered = pictureFeature.enterPipModeCompat()
            if (entered) return true
        }
        return false
    }

    private fun isWhitelisted(url: String): Boolean {
        val exists = whiteList.firstOrNull { url.contains(it, ignoreCase = true) }
        return exists != null
    }
}
