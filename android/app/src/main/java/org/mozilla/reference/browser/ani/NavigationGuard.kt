/* SPDX-License-Identifier: MPL-2.0 */
package org.mozilla.reference.browser.ani

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.appcompat.app.AlertDialog
import java.lang.ref.WeakReference

/** Fail closed when no foreground activity is available. Never grant a domain-wide exemption. */
object NavigationGuard {
    private var activity = WeakReference<Activity>(null)
    private var dialog: AlertDialog? = null
    private val handler = Handler(Looper.getMainLooper())
    private val siteWaiters = mutableMapOf<String, MutableList<(Boolean) -> Unit>>()

    fun bind(value: Activity) { activity = WeakReference(value) }
    fun siteChoice(url: String, result: (Boolean) -> Unit) {
        handler.post {
            val context = activity.get()
            if (context == null || context.isFinishing) { result(false); return@post }
            val domain = SiteSettings.host(url)
            if (domain.isEmpty() || domain == "anibrowser.local" || domain == "about:blank" || domain == "about:home") {
                result(true)
                return@post
            }
            val saved = SiteSettings.redirects(context, url)
            if (saved != null) { result(saved); return@post }
            val waiting = siteWaiters[domain]
            if (waiting != null) { waiting.add(result); return@post }
            if (siteWaiters.isNotEmpty()) { result(false); return@post }
            siteWaiters[domain] = mutableListOf(result)
            SiteSettings.choose(context, url) { allowed ->
                siteWaiters.remove(domain)?.forEach { it(allowed) }
            }
        }
    }
    fun allowWindow(url: String, allow: () -> Unit) {
        siteChoice(url) { if (it) allow() }
    }
    fun unbind(value: Activity) {
        if (activity.get() === value) {
            dialog?.dismiss()
            dialog = null
            activity.clear()
        }
    }

    fun confirm(source: String?, destination: String, deny: () -> Unit = {}, allow: () -> Unit) {
        handler.post {
            val host = activity.get()
            if (host == null || host.isFinishing || host.isDestroyed || dialog != null) {
                deny()
                return@post
            }
            var decided = false
            dialog = AlertDialog.Builder(host)
                .setTitle("Allow navigation?")
                .setMessage("From: ${display(source)}\n\nTo: ${display(destination)}")
                .setPositiveButton("Allow once") { _, _ -> decided = true; allow() }
                .setNegativeButton("Block", null)
                .create().also { prompt ->
                    val timeout = Runnable { prompt.dismiss() }
                    prompt.setOnDismissListener {
                        handler.removeCallbacks(timeout)
                        dialog = null
                        if (!decided) deny()
                    }
                    prompt.show()
                    prompt.getButton(AlertDialog.BUTTON_NEGATIVE)?.setTextColor(android.graphics.Color.parseColor("#3B82F6"))
                    prompt.getButton(AlertDialog.BUTTON_POSITIVE)?.setTextColor(android.graphics.Color.parseColor("#10B981"))
                    handler.postDelayed(timeout, 10000)
                }
        }
    }

    private fun display(value: String?): String {
        if (value.isNullOrBlank()) return "New window (destination not yet supplied)"
        val uri = Uri.parse(value)
        // Exclude query strings, fragments and embedded credentials from prompts.
        return if (uri.host != null) "${uri.scheme}://${uri.host}${uri.path.orEmpty()}" else "${uri.scheme ?: "unknown"}: link"
    }

    fun intercept(url: String, previous: String?, direct: Boolean, subframe: Boolean): Boolean {
        // The messaging bridge is not a network-readiness signal. Never cancel
        // normal page requests (or replay a pending POST as GET) while it reconnects.
        return when (NavigationPolicy.classify(url, direct, subframe)) {
            NavigationPolicy.Decision.ALLOW -> false
            NavigationPolicy.Decision.DENY -> true
            NavigationPolicy.Decision.CONFIRM_EXTERNAL -> {
                confirm(previous, url) { openExternal(url) }
                true
            }
        }
    }

    private fun openExternal(url: String) {
        val host = activity.get() ?: return
        try {
            val intent = if (url.startsWith("intent:")) Intent.parseUri(url, Intent.URI_INTENT_SCHEME)
                else Intent(Intent.ACTION_VIEW, Uri.parse(url))
            intent.component = null
            intent.selector = null
            intent.action = Intent.ACTION_VIEW
            intent.replaceExtras(android.os.Bundle())
            intent.addCategory(Intent.CATEGORY_BROWSABLE)
            intent.flags = 0
            host.startActivity(intent)
        } catch (_: Exception) {
            android.widget.Toast.makeText(host, "No application can open this link", android.widget.Toast.LENGTH_SHORT).show()
        }
    }
}
