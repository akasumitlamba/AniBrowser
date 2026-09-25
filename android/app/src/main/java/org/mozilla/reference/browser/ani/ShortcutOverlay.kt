/* SPDX-License-Identifier: MPL-2.0 */
package org.mozilla.reference.browser.ani

import android.content.Context
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import org.mozilla.reference.browser.R

/** Observes activity gestures without consuming the site's touch events. */
class ShortcutOverlay(
    private val context: Context,
    private val container: ViewGroup,
    private val onSpeedSelected: (Double) -> Unit,
    private val onSeek: (Int) -> Unit,
    private val onViewModeSelected: (Int) -> Unit,
    private val getCurrentSpeed: () -> Double,
    private val getCurrentViewMode: () -> Int,
    private val onSiteSettings: () -> Unit,
) {
    private val handler = Handler(Looper.getMainLooper())
    private val dismiss = Runnable { hide() }
    private val bounds = Rect()
    private var downX = 0f
    private var downY = 0f
    private var watchScroll = false
    private var revealed = false
    private var dialog: AlertDialog? = null
    private val modes = arrayOf("Mobile", "Desktop identity, mobile layout", "Desktop")
    private val modeIcons = intArrayOf(R.drawable.ani_phone, R.drawable.ani_hybrid, R.drawable.ani_desktop)
    private val speeds = listOf(1.0, 1.25, 1.5, 1.75, 2.0)
    private fun dp(value: Int) = (value * context.resources.displayMetrics.density + .5f).toInt()
    private val panel = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(dp(6), dp(4), dp(6), dp(4))
        background = ContextCompat.getDrawable(context, R.drawable.ani_floating_glass)
        elevation = dp(12).toFloat()
        visibility = View.GONE
    }
    private val speedButton: TextView
    private val modeButton: ImageButton
    private val layoutListener = View.OnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> position() }

    init {
        fun icon(drawable: Int, label: String, action: () -> Unit): ImageButton = ImageButton(context).apply {
            setImageResource(drawable)
            contentDescription = label
            background = ContextCompat.getDrawable(context, R.drawable.ani_button_ripple)
            setPadding(dp(12), dp(12), dp(12), dp(12))
            setOnClickListener { action(); resetAutoHide() }
            panel.addView(this, LinearLayout.LayoutParams(0, dp(48), 1f))
        }
        icon(R.drawable.ani_rewind, "Back 10 seconds") { onSeek(-10) }
        speedButton = TextView(context).apply {
            gravity = Gravity.CENTER
            setTextColor(android.graphics.Color.WHITE)
            textSize = 15f
            background = ContextCompat.getDrawable(context, R.drawable.ani_button_ripple)
            setOnClickListener {
                choose("Playback speed", speeds.map { "${it}×" }.toTypedArray(), speeds.indexOf(getCurrentSpeed())) {
                    onSpeedSelected(speeds[it]); refreshLabels()
                }
            }
            panel.addView(this, LinearLayout.LayoutParams(0, dp(48), 1f))
        }
        icon(R.drawable.ani_forward, "Forward 10 seconds") { onSeek(10) }
        modeButton = icon(R.drawable.ani_phone, "Viewing mode") {
            choose("Viewing mode", modes, getCurrentViewMode()) { onViewModeSelected(it); refreshLabels() }
        }
        icon(R.drawable.mozac_ic_settings_24, "Site settings") { hide(); onSiteSettings() }
        icon(R.drawable.mozac_ic_cross_24, "Hide site controls") { hide() }
        container.addView(panel, FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL))
        container.addOnLayoutChangeListener(layoutListener)
        position()
    }

    private fun choose(title: String, labels: Array<String>, selected: Int, changed: (Int) -> Unit) {
        handler.removeCallbacks(dismiss)
        dialog?.dismiss()
        dialog = AlertDialog.Builder(context).setTitle(title)
            .setSingleChoiceItems(labels, selected) { chooser, index -> changed(index); chooser.dismiss() }
            .setNegativeButton("Cancel", null)
            .setOnDismissListener { dialog = null; resetAutoHide() }.show()
    }

    private fun position() {
        if (container.width <= 0) return
        val insets = ViewCompat.getRootWindowInsets(container)?.getInsets(
            WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
        val width = minOf(dp(480), container.width - (insets?.left ?: 0) - (insets?.right ?: 0) - dp(24)).coerceAtLeast(0)
        val bottom = dp(12) + (insets?.bottom ?: 0)
        val params = panel.layoutParams as FrameLayout.LayoutParams
        if (params.width != width || params.bottomMargin != bottom) {
            params.width = width
            params.bottomMargin = bottom
            panel.layoutParams = params
        }
    }

    fun onTouchEvent(event: MotionEvent) {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.rawX; downY = event.rawY; revealed = false
                val inside = panel.visibility == View.VISIBLE && panel.getGlobalVisibleRect(bounds) &&
                    bounds.contains(event.rawX.toInt(), event.rawY.toInt())
                val keyboard = ViewCompat.getRootWindowInsets(container)?.isVisible(WindowInsetsCompat.Type.ime()) == true
                watchScroll = !inside && !keyboard && dialog == null
                if (inside) handler.removeCallbacks(dismiss)
            }
            MotionEvent.ACTION_MOVE -> if (watchScroll && !revealed && event.pointerCount == 1) {
                val distance = kotlin.math.abs(event.rawY - downY)
                if (distance >= dp(24) && distance > kotlin.math.abs(event.rawX - downX) * 1.3f) {
                    revealed = true
                    show()
                }
            }
            MotionEvent.ACTION_POINTER_DOWN -> watchScroll = false
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> { watchScroll = false; resetAutoHide() }
        }
    }

    private fun refreshLabels() {
        speedButton.text = "${getCurrentSpeed()}×"
        speedButton.contentDescription = "Playback speed ${getCurrentSpeed()} times"
        val mode = getCurrentViewMode().coerceIn(0, 2)
        modeButton.setImageResource(modeIcons[mode])
        modeButton.contentDescription = "Viewing mode: ${modes[mode]}"
    }

    fun show() {
        refreshLabels()
        position()
        if (panel.visibility != View.VISIBLE) {
            panel.animate().cancel()
            panel.visibility = View.VISIBLE
            panel.alpha = 0f
            panel.translationY = dp(8).toFloat()
            panel.animate().alpha(1f).translationY(0f).setDuration(160).start()
        }
        resetAutoHide()
    }

    fun hide(): Boolean {
        val visible = panel.visibility == View.VISIBLE
        handler.removeCallbacks(dismiss)
        panel.animate().cancel()
        panel.visibility = View.GONE
        return visible
    }

    private fun resetAutoHide() {
        handler.removeCallbacks(dismiss)
        if (panel.visibility == View.VISIBLE && dialog == null) handler.postDelayed(dismiss, 4500L)
    }

    fun dispose() {
        hide()
        dialog?.dismiss()
        container.removeOnLayoutChangeListener(layoutListener)
        container.removeView(panel)
    }
}
