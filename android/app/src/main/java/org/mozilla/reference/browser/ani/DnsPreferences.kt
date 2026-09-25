/* SPDX-License-Identifier: MPL-2.0 */
package org.mozilla.reference.browser.ani

import android.content.Context
import android.text.InputType
import android.widget.EditText
import android.widget.FrameLayout
import androidx.appcompat.app.AlertDialog
import org.mozilla.geckoview.GeckoRuntimeSettings
import org.mozilla.reference.browser.EngineProvider
import org.mozilla.reference.browser.R

object DnsPreferences {
    private fun prefs(context: Context) = context.getSharedPreferences("anibrowser", Context.MODE_PRIVATE)
    private fun selected(context: Context) = DnsProvider.find(prefs(context).getString("dns_provider", DnsProvider.SYSTEM))
    private fun custom(context: Context) = prefs(context).getString("dns_custom_endpoint", "").orEmpty()
    private fun endpoint(context: Context) = DnsProvider.endpoint(selected(context).id, custom(context))

    fun summary(context: Context): String = if (endpoint(context).isEmpty()) {
        context.getString(R.string.dns_system_summary)
    } else {
        val name = if (selected(context).id == DnsProvider.CUSTOM) context.getString(R.string.dns_custom_name) else selected(context).name
        context.getString(R.string.dns_provider_summary, name)
    }

    fun configure(context: Context, builder: GeckoRuntimeSettings.Builder) {
        val uri = endpoint(context)
        builder.trustedRecursiveResolverUri(uri)
        builder.trustedRecursiveResolverMode(mode(uri))
    }

    fun apply(context: Context, settings: GeckoRuntimeSettings) {
        val uri = endpoint(context)
        // Disable first so a provider change cannot temporarily use the previous endpoint.
        settings.setTrustedRecursiveResolverMode(GeckoRuntimeSettings.TRR_MODE_DISABLED)
        settings.setTrustedRecursiveResolverUri(uri)
        settings.setTrustedRecursiveResolverMode(mode(uri))
    }

    private fun mode(uri: String) = if (uri.isEmpty()) GeckoRuntimeSettings.TRR_MODE_DISABLED else GeckoRuntimeSettings.TRR_MODE_FIRST

    private fun save(context: Context, id: String, changed: () -> Unit, uri: String? = null) {
        prefs(context).edit().apply {
            putString("dns_provider", id)
            if (uri != null) putString("dns_custom_endpoint", uri)
        }.apply()
        EngineProvider.updateDnsSettings(context)
        changed()
    }

    fun choose(context: Context, changed: () -> Unit) {
        val names = DnsProvider.providers.map { it.name }.toTypedArray()
        AlertDialog.Builder(context)
            .setTitle(R.string.dns_provider_title)
            .setSingleChoiceItems(names, DnsProvider.providers.indexOf(selected(context))) { dialog, index ->
                val provider = DnsProvider.providers[index]
                dialog.dismiss()
                if (provider.id == DnsProvider.CUSTOM) editCustom(context, changed) else save(context, provider.id, changed)
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun editCustom(context: Context, changed: () -> Unit) {
        val input = EditText(context).apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
            setSingleLine(true)
            hint = "https://dns.example.com/dns-query"
            setText(custom(context))
            contentDescription = context.getString(R.string.dns_endpoint_label)
        }
        val padding = (24 * context.resources.displayMetrics.density).toInt()
        val container = FrameLayout(context).apply {
            setPadding(padding, 0, padding, 0)
            addView(input)
        }
        val dialog = AlertDialog.Builder(context)
            .setTitle(R.string.dns_custom_name)
            .setMessage(R.string.dns_custom_description)
            .setView(container)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.dns_save, null)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val uri = DnsProvider.normalizeEndpoint(input.text.toString())
                if (uri == null) {
                    input.error = context.getString(R.string.dns_invalid_endpoint)
                } else {
                    save(context, DnsProvider.CUSTOM, changed, uri)
                    dialog.dismiss()
                }
            }
        }
        dialog.show()
    }
}
