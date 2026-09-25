/* SPDX-License-Identifier: MPL-2.0 */
package org.mozilla.reference.browser.ani

import android.content.res.ColorStateList
import android.graphics.Color
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.widget.SwitchCompat

object GlassControls {
    private val states = arrayOf(intArrayOf(-android.R.attr.state_enabled), intArrayOf(android.R.attr.state_checked), intArrayOf())
    private val thumb = ColorStateList(states, intArrayOf(Color.parseColor("#778A99"), Color.WHITE, Color.parseColor("#DAE5ED")))
    private val track = ColorStateList(states, intArrayOf(Color.parseColor("#364858"), Color.parseColor("#32B99A"), Color.parseColor("#64778A")))
    fun tint(view: View) {
        if (view is SwitchCompat) {
            view.thumbTintList = thumb
            view.trackTintList = track
        }
        if (view is ViewGroup) for(index in 0 until view.childCount) tint(view.getChildAt(index))
    }
}
