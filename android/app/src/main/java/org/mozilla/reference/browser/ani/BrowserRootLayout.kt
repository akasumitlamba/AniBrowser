/* SPDX-License-Identifier: MPL-2.0 */
package org.mozilla.reference.browser.ani

import android.content.Context
import android.util.AttributeSet
import android.view.KeyEvent
import androidx.coordinatorlayout.widget.CoordinatorLayout

/** Close the URL editor even when Back is consumed by the keyboard first. */
class BrowserRootLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : CoordinatorLayout(context, attrs) {
    var dismissAddressEditor: (() -> Boolean)? = null
    private var consumedBack = false

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
