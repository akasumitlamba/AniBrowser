/* SPDX-License-Identifier: MPL-2.0 */
package org.mozilla.reference.browser.ani

import java.net.URI

/** Stable IDs keep saved selections independent of the display order. */
object DnsProvider {
    const val SYSTEM = "system"
    const val CUSTOM = "custom"
    data class Provider(val id: String, val name: String, val endpoint: String)

    val providers = listOf(
        Provider(SYSTEM, "System default", ""),
        Provider("cloudflare", "Cloudflare (1.1.1.1)", "https://cloudflare-dns.com/dns-query"),
        Provider("google", "Google Public DNS", "https://dns.google/dns-query"),
        Provider("quad9", "Quad9 (blocks malicious domains)", "https://dns.quad9.net/dns-query"),
        Provider(CUSTOM, "Custom provider…", ""),
    )

    fun find(id: String?) = providers.firstOrNull { it.id == id } ?: providers.first()

    /** Accept an HTTPS endpoint, never credentials, fragments, or URI templates. */
    fun normalizeEndpoint(value: String): String? {
        val trimmed = value.trim()
        return try {
            val uri = URI(trimmed)
            if (!uri.scheme.equals("https", ignoreCase = true) || uri.host.isNullOrBlank() ||
                uri.rawUserInfo != null || uri.rawFragment != null ||
                (uri.port != -1 && uri.port !in 1..65535)
            ) null else trimmed
        } catch (_: java.net.URISyntaxException) {
            null
        }
    }

    fun endpoint(id: String?, custom: String): String {
        val provider = find(id)
        return if (provider.id == CUSTOM) normalizeEndpoint(custom).orEmpty() else provider.endpoint
    }
}
