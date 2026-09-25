/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.reference.browser.settings

import android.os.Bundle
import android.view.MenuItem
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import mozilla.components.support.base.feature.UserInteractionHandler
import mozilla.components.support.ktx.android.view.setupPersistentInsets
import org.mozilla.reference.browser.R

class SettingsActivity : AppCompatActivity(), SettingsFragment.ActionBarUpdater {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        window.setupPersistentInsets(true)
        supportActionBar?.apply {
            setBackgroundDrawable(androidx.core.content.ContextCompat.getDrawable(this@SettingsActivity, R.drawable.ani_glass_backdrop))
            elevation = 0f
        }
        supportFragmentManager.registerFragmentLifecycleCallbacks(object : androidx.fragment.app.FragmentManager.FragmentLifecycleCallbacks() {
            override fun onFragmentViewCreated(fm: androidx.fragment.app.FragmentManager, fragment: androidx.fragment.app.Fragment, view: android.view.View, state: Bundle?) {
                if (fragment is androidx.preference.PreferenceFragmentCompat) {
                    val list = fragment.listView
                    val density = resources.displayMetrics.density
                    val side = ((resources.configuration.screenWidthDp - 680).coerceAtLeast(32) / 2 * density).toInt()
                    list.setPadding(side, (16 * density).toInt(), side, (28 * density).toInt())
                    list.clipToPadding = false
                    fragment.setDivider(null)
                    fun compact(group: androidx.preference.PreferenceGroup) {
                        for (index in 0 until group.preferenceCount) {
                            val preference = group.getPreference(index)
                            preference.isIconSpaceReserved = true
                            preference.isSingleLineTitle = false
                            val name = (preference.key.orEmpty() + " " + preference.title.toString()).lowercase()
                            val icon = when {
                                "background" in name || "playback" in name -> R.drawable.ani_audio
                                "picture" in name -> R.drawable.ani_pip
                                "search" in name -> R.drawable.ani_search
                                "privacy" in name || "shield" in name || "tracking" in name -> R.drawable.ani_privacy
                                "debug" in name -> R.drawable.ani_debug
                                "collection" in name -> R.drawable.ani_extensions
                                "external" in name || "links" in name -> R.drawable.ani_external
                                "default" in name -> R.drawable.ani_default
                                "autofill" in name -> R.drawable.ani_autofill
                                "developer" in name -> R.drawable.ani_tools
                                "about" in name || "anibrowser" in name -> R.drawable.ani_info
                                else -> R.drawable.ani_browser
                            }
                            preference.setIcon(icon)
                            if (preference is androidx.preference.PreferenceGroup) compact(preference)
                        }
                    }
                    compact(fragment.preferenceScreen)
                    val rowBackgrounds = java.util.WeakHashMap<android.view.View, android.graphics.drawable.Drawable>()
                    list.addItemDecoration(object : androidx.recyclerview.widget.RecyclerView.ItemDecoration() {
                        override fun getItemOffsets(rect: android.graphics.Rect, row: android.view.View, parent: androidx.recyclerview.widget.RecyclerView, state: androidx.recyclerview.widget.RecyclerView.State) {
                            rect.bottom = (8 * density).toInt()
                            val position = parent.getChildAdapterPosition(row)
                            val pref = (parent.adapter as? androidx.preference.PreferenceGroupAdapter)?.getItem(position)
                            val minimum = ((if(pref is androidx.preference.PreferenceCategory) 52 else 88) * density).toInt()
                            if (row.minimumHeight != minimum) row.minimumHeight = minimum
                        }
                        override fun onDraw(canvas: android.graphics.Canvas, parent: androidx.recyclerview.widget.RecyclerView, state: androidx.recyclerview.widget.RecyclerView.State) {
                            for (index in 0 until parent.childCount) {
                                val row = parent.getChildAt(index)
                                org.mozilla.reference.browser.ani.GlassControls.tint(row)
                                val position = parent.getChildAdapterPosition(row)
                                if (position == androidx.recyclerview.widget.RecyclerView.NO_POSITION) continue
                                val preference = (parent.adapter as? androidx.preference.PreferenceGroupAdapter)?.getItem(position)
                                val heading = preference is androidx.preference.PreferenceCategory
                                val title = row.findViewById<android.widget.TextView>(android.R.id.title)
                                title?.setTextColor(android.graphics.Color.parseColor(if (heading) "#9FE8DE" else "#F5F9FF"))
                                title?.textSize = if (heading) 13f else 16f
                                title?.setTypeface(null, if (heading) android.graphics.Typeface.BOLD else android.graphics.Typeface.NORMAL)
                                if (heading) {
                                    row.background = null
                                    continue
                                }
                                val background = rowBackgrounds.getOrPut(row) {
                                    androidx.core.content.ContextCompat.getDrawable(this@SettingsActivity, R.drawable.ani_glass_row)!!
                                }
                                if (row.background !== background) row.background = background
                                row.clipToOutline = true
                            }
                        }
                    })
                }
            }
        }, true)

        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    val handled =
                        supportFragmentManager.fragments.any {
                            it is UserInteractionHandler && it.onBackPressed()
                        }
                    if (!handled) {
                        remove()
                        onBackPressedDispatcher.onBackPressed()
                    }
                }
            },
        )

        if (savedInstanceState == null) {
            with(supportFragmentManager.beginTransaction()) {
                replace(R.id.container, SettingsFragment())
                commit()
            }
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean =
        when (item.itemId) {
            android.R.id.home -> {
                onBackPressedDispatcher.onBackPressed()
                true
            }

            else -> {
                super.onOptionsItemSelected(item)
            }
        }

    override fun updateTitle(titleResId: Int) {
        setTitle(titleResId)
    }
}
