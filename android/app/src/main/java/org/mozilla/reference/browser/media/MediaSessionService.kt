/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */



package org.mozilla.reference.browser.media

import android.content.Intent
import android.os.Binder
import android.os.IBinder

import mozilla.components.browser.state.store.BrowserStore
import mozilla.components.concept.base.crash.CrashReporting
import mozilla.components.feature.media.service.AbstractMediaSessionService
import mozilla.components.feature.media.service.MediaSessionDelegate
import mozilla.components.support.base.ids.SharedIdsHelper
import mozilla.components.support.base.android.NotificationsDelegate
import org.mozilla.reference.browser.ext.components

/** [AbstractMediaSessionService] implementation for injecting [BrowserStore] singleton. */
class MediaSessionService : AbstractMediaSessionService() {
    override val store: BrowserStore by lazy { components.core.store }
    override val crashReporter: CrashReporting by lazy { components.analytics.crashReporter }
    override val notificationsDelegate: NotificationsDelegate by lazy { components.notificationsDelegate }

    class ActiveMediaBinder(val delegate: MediaSessionDelegate) : Binder() {
        var onTaskRemoved: (() -> Unit)? = null
        private var released = false

        fun release() {
            if (released) return
            released = true
            delegate.handleNoMedia()
        }
    }

    private var activeBinder: ActiveMediaBinder? = null

    override fun onBind(intent: Intent?): IBinder? {
        // The pinned Mozilla 157.0 API exposes MediaSessionDelegate publicly but
        // marks its binder internal. Keep this single adapter version-specific;
        // never reflect into the delegate or duplicate its audio-focus handling.
        val delegate = MediaBinderAdapter.delegate(super.onBind(intent)) ?: return null
        return ActiveMediaBinder(delegate).also { activeBinder = it }
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        // The upstream implementation stops normal tabs only. Include custom
        // tabs and unbind the feature so no released media session is reused.
        (store.state.tabs + store.state.customTabs).forEach {
            it.mediaSessionState?.controller?.stop()
        }
        val callback = activeBinder?.onTaskRemoved
        if (callback != null) callback() else super.onTaskRemoved(rootIntent)
        removeNotification()
        stopSelf()
    }

    override fun onDestroy() {
        activeBinder?.onTaskRemoved = null
        activeBinder?.release()
        activeBinder = null
        super.onDestroy() // Cancels pending artwork/notification coroutines first.
        removeNotification()
    }

    private fun removeNotification() {
        stopForeground(STOP_FOREGROUND_REMOVE)
        notificationsDelegate.notificationManagerCompat.cancel(
            SharedIdsHelper.getIdForTag(this, NOTIFICATION_TAG),
        )
    }
}
