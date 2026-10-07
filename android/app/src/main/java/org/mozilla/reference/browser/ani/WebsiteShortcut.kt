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
import java.security.MessageDigest
import org.mozilla.reference.browser.BrowserActivity
import org.mozilla.reference.browser.R
import org.mozilla.reference.browser.browser.BrowserFragment
import org.mozilla.reference.browser.ext.components

object WebsiteShortcut {
    private fun launcherIcon(context: Context, bitmap: Bitmap?): IconCompat {
        if (bitmap == null) return IconCompat.createWithResource(context, R.mipmap.ic_launcher)
        val icon = Bitmap.createBitmap(192, 192, Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(icon)
        canvas.drawColor(android.graphics.Color.rgb(3, 5, 30))
        val scale = 128f / maxOf(bitmap.width, bitmap.height)
        val width = bitmap.width * scale
        val height = bitmap.height * scale
        canvas.drawBitmap(bitmap, null, android.graphics.RectF(
            (192 - width) / 2, (192 - height) / 2, (192 + width) / 2, (192 + height) / 2,
        ), android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG or android.graphics.Paint.FILTER_BITMAP_FLAG))
        return IconCompat.createWithAdaptiveBitmap(icon)
    }

    /** Replace old generated-letter icons in place without duplicating or re-pinning shortcuts. */
    suspend fun refreshPinned(context: Context): Boolean {
        val manager = context.getSystemService(android.content.pm.ShortcutManager::class.java)
        val shortcuts = manager.pinnedShortcuts
        android.util.Log.i("AniShortcutIcons", "Refreshing ${shortcuts.size} pinned shortcuts")
        val preferences = context.getSharedPreferences("website_shortcuts", Context.MODE_PRIVATE)
        var complete = true
        for (shortcut in shortcuts) {
            val registeredUrl = preferences.getString(shortcut.id, null)
            // Launcher entries can survive app-data restoration without our URL registry.
            // Recognize artwork only; never invent or overwrite their launch destination.
            val label = shortcut.shortLabel?.toString().orEmpty()
            val url = registeredUrl ?: when {
                label.startsWith("Crunchyroll:") -> "https://www.crunchyroll.com"
                label.startsWith("Home - Anikoto -") -> "https://anikototv.to/home"
                else -> null
            }
            if (url == null) {
                android.util.Log.w("AniShortcutIcons", "Pinned shortcut has no registered destination")
                complete = false
                continue
            }
            val isHome = url == "about:home" || url == "about:blank"
            val bitmap = if (isHome) null else AniHomeManager.shortcutBitmap(context, url)
            // A blocked/offline icon host must not leave the stale generated letter.
            // Show the browser artwork now and retry the site's artwork later.
            if (!isHome && bitmap == null) complete = false
            val updated = android.content.pm.ShortcutInfo.Builder(context, shortcut.id)
                .setShortLabel(shortcut.shortLabel ?: "AniBrowser")
                .setIcon(launcherIcon(context, bitmap).toIcon(context))
                .build()
            val updatedSuccessfully = manager.updateShortcuts(listOf(updated))
            android.util.Log.i("AniShortcutIcons", "Icon update accepted: $updatedSuccessfully")
            if (!updatedSuccessfully) complete = false
            bitmap?.recycle()
        }
        return complete
    }

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
                AniHomeManager.shortcutBitmap(context, url)
            } catch (_: Exception) { null }

            withContext(Dispatchers.Main) {
                val icon = launcherIcon(context, bitmap)
                bitmap?.recycle()

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
