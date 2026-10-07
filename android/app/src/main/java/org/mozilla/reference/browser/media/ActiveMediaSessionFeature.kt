/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.reference.browser.media

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.map
import mozilla.components.browser.state.state.SessionState
import mozilla.components.browser.state.store.BrowserStore
import mozilla.components.concept.engine.mediasession.MediaSession.PlaybackState
import mozilla.components.lib.state.ext.flowScoped

/** Own the service binding for exactly as long as a browser session is playing. */
class ActiveMediaSessionFeature(private val context: Context, private val store: BrowserStore) {
    private var binding = false
    private var service: MediaSessionService.ActiveMediaBinder? = null
    private val stoppedTabs = mutableSetOf<String>()
    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, binder: IBinder) {
            if (!binding) return
            service = binder as MediaSessionService.ActiveMediaBinder
            service?.onTaskRemoved = ::stopForTaskRemoval
            update()
        }
        override fun onServiceDisconnected(name: ComponentName) {
            service = null
        }
    }

    fun start() {
        store.flowScoped(dispatcher = Dispatchers.Main) { flow ->
            flow.map { it.tabs + it.customTabs }
                .distinctUntilChangedBy { tabs -> tabs.map { it.id to it.mediaSessionState } }
                .collect { update() }
        }
    }

    private fun playingSession(): SessionState? {
        val sessions = store.state.tabs + store.state.customTabs
        stoppedTabs.removeAll { id -> sessions.none { it.id == id && it.mediaSessionState?.playbackState == PlaybackState.PLAYING } }
        return sessions.filter {
            it.id !in stoppedTabs && MediaNotificationPolicy.keepSession(it.mediaSessionState?.playbackState)
        }.maxByOrNull { it.mediaSessionState!! }
    }

    private fun update() {
        val playing = playingSession()
        if (playing == null) {
            disconnect()
        } else if (!binding) {
            binding = context.bindService(Intent(context, MediaSessionService::class.java), connection, Context.BIND_AUTO_CREATE)
        } else {
            service?.delegate?.handleMediaPlaying(playing)
        }
    }

    private fun stopForTaskRemoval() {
        // Ignore late PLAYING metadata/position callbacks while Gecko processes stop.
        stoppedTabs.addAll((store.state.tabs + store.state.customTabs).map { it.id })
        disconnect()
    }

    private fun disconnect() {
        if (!binding) return
        service?.onTaskRemoved = null
        service?.release()
        context.unbindService(connection)
        binding = false
        service = null
    }
}
