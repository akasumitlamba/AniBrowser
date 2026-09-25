/* SPDX-License-Identifier: MPL-2.0 */
package org.mozilla.reference.browser.ani

import android.graphics.Rect
import android.view.View
import android.widget.PopupWindow
import mozilla.components.browser.menu2.BrowserMenuController
import mozilla.components.concept.menu.MenuController
import mozilla.components.concept.menu.MenuStyle
import mozilla.components.concept.menu.Orientation
import org.mozilla.reference.browser.R

/** Keeps the scrolling menu above the floating dock, including on narrow displays. */
class GlassMenuController(
    private val delegate: BrowserMenuController = BrowserMenuController(style = MenuStyle(backgroundColor = android.graphics.Color.TRANSPARENT)),
) : MenuController by delegate {
    override fun show(anchor: View, orientation: Orientation?, autoDismiss: Boolean): PopupWindow {
        val popup = delegate.show(anchor, orientation, autoDismiss)
        popup.animationStyle = 0
        popup.contentView.alpha = 0f
        popup.contentView.post {
            if (popup.isShowing) {
                val dock = anchor.rootView.findViewById<View>(R.id.toolbar) ?: anchor
                val position = IntArray(2)
                dock.getLocationOnScreen(position)
                val frame = Rect()
                anchor.getWindowVisibleDisplayFrame(frame)
                val gap = (8 * anchor.resources.displayMetrics.density).toInt()
                val maxHeight = (position[1] - frame.top - gap * 2).coerceAtLeast(1)
                val width = popup.contentView.measuredWidth.coerceIn(1, (frame.width() - gap * 2).coerceAtLeast(1))
                // Remeasure the scrolling content itself, not just the popup window.
                // Otherwise the list retains its tall portrait viewport and its
                // final rows are clipped without being reachable by scrolling.
                popup.contentView.measure(
                    View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(maxHeight, View.MeasureSpec.AT_MOST),
                )
                val height = popup.contentView.measuredHeight.coerceIn(1, maxHeight)
                popup.contentView.layoutParams?.let { params ->
                    params.height = height
                    popup.contentView.layoutParams = params
                }
                val x = (position[0] + dock.width - width).coerceIn(frame.left + gap, (frame.right - width - gap).coerceAtLeast(frame.left + gap))
                popup.update(x, position[1] - height - gap, width, height)
                popup.contentView.animate().alpha(1f).setDuration(140).start()
            }
        }
        return popup
    }
}
