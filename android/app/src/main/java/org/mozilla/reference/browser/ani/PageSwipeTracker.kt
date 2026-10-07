/* SPDX-License-Identifier: MPL-2.0 */
package org.mozilla.reference.browser.ani

import kotlin.math.abs

/** Observe a page drag without consuming it; report each deliberate direction change. */
internal class PageSwipeTracker(private val threshold: Float) {
    private var active = false
    private var anchorX = 0f
    private var anchorY = 0f
    private var previousY = 0f
    private var direction = 0
    private var reported: Boolean? = null

    fun start(x: Float, y: Float) {
        active = true
        anchorX = x
        anchorY = y
        previousY = y
        direction = 0
        reported = null
    }

    fun cancel() { active = false }

    fun move(x: Float, y: Float): Boolean? {
        if (!active) return null
        val step = y - previousY
        val nextDirection = if (step < 0) -1 else if (step > 0) 1 else direction
        if (direction != 0 && nextDirection != direction) {
            anchorX = x
            anchorY = previousY
        }
        direction = nextDirection
        previousY = y
        val dy = y - anchorY
        val dx = x - anchorX
        if (abs(dx) > threshold && abs(dx) > abs(dy)) {
            cancel()
            return null
        }
        if (abs(dy) < threshold || abs(dy) <= abs(dx) * 1.5f) return null
        val down = dy < 0
        if (reported == down) return null
        reported = down
        return down
    }
}
