/* SPDX-License-Identifier: MPL-2.0 */
package org.mozilla.reference.browser.ani

/** Classifies schemes without cancelling web redirects or replaying POST requests. */
object NavigationPolicy {
    enum class Decision { ALLOW, DENY, CONFIRM_EXTERNAL }

    fun classify(url: String, direct: Boolean, subframe: Boolean): Decision {
        val scheme = url.substringBefore(':', "").lowercase(java.util.Locale.ROOT)
        return when {
            scheme in setOf("https", "http", "blob") -> Decision.ALLOW
            url.equals("about:blank", ignoreCase = true) -> Decision.ALLOW
            direct && !subframe && scheme == "about" -> Decision.ALLOW
            scheme in setOf("javascript", "data", "file", "content", "resource", "chrome", "about", "") -> Decision.DENY
            subframe -> Decision.DENY
            else -> Decision.CONFIRM_EXTERNAL
        }
    }
}
