/* SPDX-License-Identifier: MPL-2.0 */
package org.mozilla.reference.browser.media

import mozilla.components.concept.engine.mediasession.MediaSession.PlaybackState

internal object MediaNotificationPolicy {
    fun keepSession(state: PlaybackState?): Boolean = state == PlaybackState.PLAYING
}
