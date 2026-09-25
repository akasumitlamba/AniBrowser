/* SPDX-License-Identifier: MPL-2.0 */
package org.mozilla.reference.browser.ani

import android.content.Context
import android.net.Uri
import androidx.appcompat.app.AlertDialog
import mozilla.components.concept.engine.EngineSession
import org.mozilla.reference.browser.ext.components

object SiteSettings {
    fun host(url: String) = Uri.parse(url).host?.lowercase().orEmpty()
    private fun prefs(context: Context) = context.getSharedPreferences("ani_sites", Context.MODE_PRIVATE)
    fun redirects(context: Context, url: String): Boolean? {
        val key = "redirects:${host(url)}"
        return if (prefs(context).contains(key)) prefs(context).getBoolean(key, false) else null
    }
    fun choose(context: Context, url: String, result: (Boolean) -> Unit) {
        val domain = host(url)
        if (domain.isEmpty()) { result(false); return }
        var answered = false
        val dialog = AlertDialog.Builder(context).setTitle("Browsing rules for $domain")
            .setMessage("Choose once for this domain. Block stops pop-ups and automatic cross-site page changes. Ordinary links and server redirects still work.")
            .setPositiveButton("Block") { _, _ ->
                answered = true; prefs(context).edit().putBoolean("redirects:$domain", false).apply(); result(false)
            }.setNegativeButton("Allow") { _, _ ->
                answered = true; prefs(context).edit().putBoolean("redirects:$domain", true).apply(); result(true)
            }.setOnDismissListener { if (!answered) result(false) }.create()
        dialog.show()
        dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.setTextColor(android.graphics.Color.parseColor("#3B82F6"))
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE)?.setTextColor(android.graphics.Color.parseColor("#10B981"))
    }
    fun viewingMode(context: Context, url: String): Int {
        val preferences = prefs(context)
        return preferences.getInt("view:${ViewingMode.siteKey(host(url))}",
            preferences.getInt("view:${host(url)}", 0)).coerceIn(0, 2)
    }
    fun setViewingMode(context: Context, url: String, mode: Int) {
        prefs(context).edit().putInt("view:${ViewingMode.siteKey(host(url))}", mode.coerceIn(0, 2)).apply()
    }
    fun apply(context: Context, session: EngineSession, url: String) {
        val mode = viewingMode(context, url)
        session.toggleDesktopMode(mode == 2, false)
        val agent = ViewingMode.userAgent(mode, org.mozilla.geckoview.GeckoSession.getDefaultUserAgent(), android.os.Build.VERSION.RELEASE)
        if (session.settings.userAgentString != agent) session.settings.userAgentString = agent
    }
    fun show(context: Context, url: String) {
        val domain = host(url)
        if (domain.isEmpty()) return
        val density = context.resources.displayMetrics.density
        fun dp(value: Int) = (value * density).toInt()
        val content = android.widget.LinearLayout(context).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(dp(20), dp(8), dp(20), dp(12))
        }
        fun heading(text: String) {
            content.addView(android.widget.TextView(context).apply {
                this.text = text
                textSize = 12f
                setTypeface(null, android.graphics.Typeface.BOLD)
                setTextColor(android.graphics.Color.parseColor("#9FE8DE"))
                setPadding(0, dp(16), 0, dp(8))
            })
        }
        fun toggle(label: String, icon: Int, checked: Boolean, enabled: Boolean = true, changed: (Boolean) -> Unit) {
            val row = android.widget.LinearLayout(context).apply {
                gravity = android.view.Gravity.CENTER_VERTICAL
                setPadding(dp(12), dp(6), dp(12), dp(6))
                background = androidx.core.content.ContextCompat.getDrawable(context, org.mozilla.reference.browser.R.drawable.ani_glass_row)
            }
            row.addView(android.widget.ImageView(context).apply { setImageResource(icon) }, android.widget.LinearLayout.LayoutParams(dp(24),dp(24)).apply { marginEnd=dp(12) })
            row.addView(androidx.appcompat.widget.SwitchCompat(context).apply {
                text = label
                textSize = 15f
                minHeight = dp(48)
                setTextColor(android.graphics.Color.WHITE)
                isChecked = checked
                isEnabled = enabled
                GlassControls.tint(this)
                setOnCheckedChangeListener { _, value -> changed(value) }
            }, android.widget.LinearLayout.LayoutParams(0, -2, 1f))
            content.addView(row, android.widget.LinearLayout.LayoutParams(-1, -2).apply { bottomMargin=dp(8) })
            if (!enabled) content.addView(android.widget.TextView(context).apply {
                text = "Off in Settings. Enable globally to use this site option."
                textSize = 12f
                setTextColor(android.graphics.Color.parseColor("#CCDBEC"))
                setPadding(dp(4),0,dp(4),dp(8))
            })
        }
        heading("BROWSING")
        toggle("Allow pop-ups and automatic navigation", org.mozilla.reference.browser.R.drawable.ani_privacy, redirects(context,url)==true) {
            prefs(context).edit().putBoolean("redirects:$domain", it).apply()
        }
        heading("VIEWING MODE")
        val mode = viewingMode(context, url)
        val modes = arrayOf("Mobile", "Desktop identity, mobile layout", "Desktop")
        var selectedMode = mode
        val modeRow = android.widget.LinearLayout(context).apply {
            gravity = android.view.Gravity.CENTER_VERTICAL
            setPadding(dp(12), 0, dp(12), 0)
            background = androidx.core.content.ContextCompat.getDrawable(context, org.mozilla.reference.browser.R.drawable.ani_glass_row)
        }
        val modeIcons = intArrayOf(org.mozilla.reference.browser.R.drawable.ani_phone, org.mozilla.reference.browser.R.drawable.ani_hybrid, org.mozilla.reference.browser.R.drawable.ani_desktop)
        val modeIcon = android.widget.ImageView(context).apply { setImageResource(modeIcons[mode]) }
        modeRow.addView(modeIcon, android.widget.LinearLayout.LayoutParams(dp(24),dp(24)).apply { marginEnd=dp(12) })
        val modePicker = androidx.appcompat.widget.AppCompatSpinner(context).apply {
            adapter = object : android.widget.ArrayAdapter<String>(context, android.R.layout.simple_spinner_dropdown_item, modes) {
                override fun getDropDownView(position: Int, convertView: android.view.View?, parent: android.view.ViewGroup): android.view.View {
                    return (super.getDropDownView(position, convertView, parent) as android.widget.TextView).apply {
                        setCompoundDrawablesRelativeWithIntrinsicBounds(modeIcons[position], 0, 0, 0)
                        compoundDrawablePadding = dp(12)
                        setPadding(dp(16), dp(12), dp(16), dp(12))
                        minHeight = dp(52)
                    }
                }
            }
            minimumHeight = dp(52)
            setPopupBackgroundResource(org.mozilla.reference.browser.R.drawable.ani_glass_surface)
            contentDescription = "Viewing mode"
            setSelection(mode)
            onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
                override fun onNothingSelected(parent: android.widget.AdapterView<*>?) = Unit
                override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: android.view.View?, position: Int, id: Long) {
                    modeIcon.setImageResource(modeIcons[position])
                    if (selectedMode == position) return
                    selectedMode = position
                    setViewingMode(context, url, position)
                    val state = context.components.core.store.state
                    (state.tabs + state.customTabs).filter {
                        ViewingMode.siteKey(host(it.content.url)) == ViewingMode.siteKey(domain)
                    }.forEach { tab ->
                        tab.engineState.engineSession?.let { session ->
                            apply(context, session, tab.content.url)
                            session.reload()
                        }
                    }
                }
            }
        }
        modeRow.addView(modePicker,android.widget.LinearLayout.LayoutParams(0,-2,1f))
        modeRow.setOnClickListener { modePicker.performClick() }
        modeIcon.setOnClickListener { modePicker.performClick() }
        content.addView(modeRow, android.widget.LinearLayout.LayoutParams(-1,-2))
        heading("PLAYBACK")
        listOf(MediaPreferences.BACKGROUND to "Background play", MediaPreferences.PIP to "Picture-in-Picture").forEach { (key,label) ->
            val enabled = MediaPreferences.global(context,key)
            toggle(label, if(key==MediaPreferences.BACKGROUND) org.mozilla.reference.browser.R.drawable.ani_audio else org.mozilla.reference.browser.R.drawable.ani_pip,
                enabled && MediaPreferences.site(context,key,url), enabled) { MediaPreferences.setSite(context,key,url,it) }
        }
        val scroll = android.widget.ScrollView(context).apply { addView(content) }
        AlertDialog.Builder(context).setTitle(domain).setView(scroll).setPositiveButton("Done", null).show()
    }
}
