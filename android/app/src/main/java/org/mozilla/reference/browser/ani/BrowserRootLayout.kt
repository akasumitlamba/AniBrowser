/* SPDX-License-Identifier: MPL-2.0 */
package org.mozilla.reference.browser.ani

import android.content.Context
import android.util.AttributeSet
import android.view.KeyEvent
import android.view.MotionEvent
import androidx.coordinatorlayout.widget.CoordinatorLayout

/** Close the URL editor even when Back is consumed by the keyboard first. */
class BrowserRootLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : CoordinatorLayout(context, attrs) {
    var dismissAddressEditor: (() -> Boolean)? = null
    private var consumedBack = false
    var onVerticalPageSwipe: ((Boolean) -> Unit)? = null
    private val swipe = PageSwipeTracker(32 * resources.displayMetrics.density)
    private val pageBounds = android.graphics.Rect()
    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                swipe.cancel()
                val page = findViewById<android.view.View>(org.mozilla.reference.browser.R.id.engineView)
                if (page?.getGlobalVisibleRect(pageBounds) == true &&
                    pageBounds.contains(event.rawX.toInt(), event.rawY.toInt())) {
                    swipe.start(event.rawX, event.rawY)
                }
            }
            MotionEvent.ACTION_POINTER_DOWN, MotionEvent.ACTION_CANCEL -> swipe.cancel()
            MotionEvent.ACTION_MOVE -> swipe.move(event.rawX, event.rawY)?.let { onVerticalPageSwipe?.invoke(it) }
            MotionEvent.ACTION_UP -> swipe.cancel()
        }
        return super.dispatchTouchEvent(event)
    }

    override fun dispatchKeyEventPreIme(event: KeyEvent): Boolean {
        if (event.keyCode == KeyEvent.KEYCODE_BACK) {
            if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) {
                consumedBack = dismissAddressEditor?.invoke() == true
                if (consumedBack) return true
            }
            if (event.action == KeyEvent.ACTION_UP && consumedBack) {
                consumedBack = false
                return true
            }
        }
        return super.dispatchKeyEventPreIme(event)
    }
}
