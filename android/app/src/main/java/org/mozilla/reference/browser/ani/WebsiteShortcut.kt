/* SPDX-License-Identifier: MPL-2.0 */
package org.mozilla.reference.browser.ani

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import androidx.fragment.app.Fragment
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import mozilla.components.browser.icons.BrowserIcons
import mozilla.components.browser.icons.IconRequest
import java.security.MessageDigest
import org.mozilla.reference.browser.BrowserActivity
import org.mozilla.reference.browser.R
import org.mozilla.reference.browser.browser.BrowserFragment
import org.mozilla.reference.browser.ext.components

object WebsiteShortcut {
    /**
     * Pin a shortcut for [url] to the launcher.
     * Attempts to fetch the site favicon first; falls back to the app icon if unavailable.
     */
    fun pin(context: Context, url: String, title: String) {
        val uri = Uri.parse(url)
        val isHome = url == "about:home" || url == "about:blank"
        if (!isHome && (uri.scheme !in listOf("http", "https") || uri.host == null)) return

        val id = MessageDigest.getInstance("SHA-256")
            .digest(url.toByteArray())
            .joinToString("") { "%02x".format(it) }

        context.getSharedPreferences("website_shortcuts", Context.MODE_PRIVATE)
            .edit().putString(id, url).apply()

        val launchIntent = Intent(context, WebsiteActivity::class.java)
            .setAction(Intent.ACTION_VIEW)
            .setData(Uri.parse("anibrowser://shortcut/$id"))
            .putExtra("shortcut_id", id)

        // Try to fetch the favicon asynchronously and create the shortcut with it.
        CoroutineScope(Dispatchers.IO).launch {
            val bitmap: Bitmap? = if (isHome) null else try {
                val icons = context.components.core.icons
                val result = icons.loadIcon(IconRequest(url = url, size = IconRequest.Size.LAUNCHER))
                    .await()
                result.bitmap
            } catch (_: Exception) { null }

            withContext(Dispatchers.Main) {
                val icon = if (bitmap != null) {
                    // Use the fetched favicon as the shortcut icon
                    IconCompat.createWithBitmap(bitmap)
                } else {
                    IconCompat.createWithResource(context, R.mipmap.ic_launcher)
                }

                val shortcut = ShortcutInfoCompat.Builder(context, id)
                    .setShortLabel((if (isHome) "AniHome" else title.ifBlank { uri.host.orEmpty() }).take(40))
                    .setIcon(icon)
                    .setIntent(launchIntent)
                    .build()

                if (!ShortcutManagerCompat.requestPinShortcut(context, shortcut, null)) {
                    android.widget.Toast.makeText(
                        context,
                        "Your launcher does not support pinned shortcuts",
                        android.widget.Toast.LENGTH_LONG,
                    ).show()
                }
            }
        }
    }
}

/** Only launch locally registered shortcuts; arbitrary external extras cannot select a URL. */
class WebsiteActivity : BrowserActivity() {
    private var websiteSession: String? = null
    var shortcutControls: ShortcutOverlay? = null

    override fun dispatchTouchEvent(event: android.view.MotionEvent): Boolean {
        if (!isInPictureInPictureMode) shortcutControls?.onTouchEvent(event)
        return super.dispatchTouchEvent(event)
    }

    override fun onPause() {
        shortcutControls?.hide()
        super.onPause()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        val id = intent.getStringExtra("shortcut_id").orEmpty()
        val url = getSharedPreferences("website_shortcuts", Context.MODE_PRIVATE).getString(id, null)
        if (url == null) { super.onCreate(savedInstanceState); finish(); return }

        websiteSession = savedInstanceState?.getString("website_session")
            ?.takeIf { candidate -> components.core.store.state.tabs.any { it.id == candidate } }
            ?: components.useCases.tabsUseCases.addTab(url = url, selectTab = true)

        val canRestore = savedInstanceState?.getString("website_session") == websiteSession
        super.onCreate(if (canRestore) savedInstanceState else null)
    }

    override fun createBrowserFragment(sessionId: String?): Fragment =
        BrowserFragment.create(websiteSession)

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString("website_session", websiteSession)
        super.onSaveInstanceState(outState)
    }
}
