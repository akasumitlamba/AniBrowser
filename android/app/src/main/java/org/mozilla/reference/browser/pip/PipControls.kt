/* SPDX-License-Identifier: MPL-2.0 */
package org.mozilla.reference.browser.pip

import android.app.Activity
import android.app.PendingIntent
import android.app.PictureInPictureParams
import android.app.RemoteAction
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.drawable.Icon
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import mozilla.components.lib.state.ext.flowScoped
import mozilla.components.concept.engine.mediasession.MediaSession
import org.mozilla.reference.browser.R
import org.mozilla.reference.browser.ext.components
import mozilla.components.browser.state.selector.selectedTab

/** Three media actions; Android supplies the expand-to-fullscreen and close buttons. */
class PipControls(private val activity: Activity) {
    private val action = activity.packageName + ".PIP_CONTROL"
    private var scope: CoroutineScope? = null
    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val command = intent.getStringExtra("command") ?: return
            if (command !in listOf("back", "toggle", "forward")) return
            org.mozilla.reference.browser.ani.PlaybackController.mediaCommand(command)
        }
    }
    fun start() {
        ContextCompat.registerReceiver(activity, receiver, IntentFilter(action), ContextCompat.RECEIVER_NOT_EXPORTED)
        scope = activity.components.core.store.flowScoped(dispatcher = Dispatchers.Main) { flow ->
            flow.map { it.selectedTab?.mediaSessionState?.playbackState == MediaSession.PlaybackState.PLAYING }
                .distinctUntilChanged().collect { update(it) }
        }
    }
    private fun update(playing: Boolean) {
        fun control(command: String, title: String, icon: Int, request: Int): RemoteAction {
            val intent = Intent(action).setPackage(activity.packageName).putExtra("command",command)
            val pending = PendingIntent.getBroadcast(activity, request, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            return RemoteAction(Icon.createWithResource(activity,icon),title,title,pending)
        }
        activity.setPictureInPictureParams(PictureInPictureParams.Builder().setActions(listOf(
            control("back","Back 10 seconds",R.drawable.ani_rewind,201),
            control("toggle",if(playing) "Pause" else "Play",if(playing) R.drawable.ani_pause else R.drawable.ani_play,202),
            control("forward","Forward 10 seconds",R.drawable.ani_forward,203)
        )).build())
    }
    fun stop() { scope?.cancel(); activity.unregisterReceiver(receiver) }
}
