/* SPDX-License-Identifier: MPL-2.0 */
package org.mozilla.reference.browser.ani

import org.junit.Assert.*
import org.junit.Test

class PageSwipeTrackerTest {
    @Test fun hidesDuringDragAndRevealsAfterReversingWithoutLiftingFinger() {
        val tracker = PageSwipeTracker(32f)
        tracker.start(100f, 200f)
        assertNull(tracker.move(100f, 185f))
        assertEquals(true, tracker.move(100f, 160f))
        assertNull(tracker.move(100f, 110f))
        assertNull(tracker.move(100f, 125f))
        assertEquals(false, tracker.move(100f, 150f))
    }

    @Test fun horizontalGesturesStayIgnoredEvenIfTheyLaterTurnVertical() {
        val tracker = PageSwipeTracker(32f)
        tracker.start(0f, 100f)
        assertNull(tracker.move(60f, 110f))
        assertNull(tracker.move(60f, 200f))
    }

    @Test fun cancelledOrMultiTouchGesturesCannotChangeToolbar() {
        val tracker = PageSwipeTracker(32f)
        tracker.start(0f, 200f)
        tracker.cancel()
        assertNull(tracker.move(0f, 0f))
        tracker.start(0f, 200f)
        assertEquals(true, tracker.move(0f, 150f))
    }
}
