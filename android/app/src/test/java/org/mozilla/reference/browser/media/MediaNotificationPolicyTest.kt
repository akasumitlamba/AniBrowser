/* SPDX-License-Identifier: MPL-2.0 */
package org.mozilla.reference.browser.media

import mozilla.components.concept.engine.mediasession.MediaSession.PlaybackState
import org.junit.Assert.assertEquals
import org.junit.Test

class MediaNotificationPolicyTest {
    @Test fun onlyPlayingSessionsRetainTheirNotification() {
        for (state in PlaybackState.values()) {
            assertEquals(state.name, state == PlaybackState.PLAYING, MediaNotificationPolicy.keepSession(state))
        }
        assertEquals(false, MediaNotificationPolicy.keepSession(null))
    }

    @Test fun pauseEndAndRemovedMediaReleaseButRealResumeRecreatesSession() {
        val states = listOf(PlaybackState.PLAYING, PlaybackState.PAUSED, PlaybackState.PLAYING, PlaybackState.STOPPED, null)
        assertEquals(listOf(true, false, true, false, false), states.map(MediaNotificationPolicy::keepSession))
    }
}
