/* SPDX-License-Identifier: MPL-2.0 */
package org.mozilla.reference.browser.ani

/** A placeholder engine snapshot must not replace a saved website on restart. */
object SessionRestorePolicy {
    fun needsUrlReload(savedUrl: String, activeEntryUrl: String?): Boolean =
        (savedUrl.startsWith("https://") || savedUrl.startsWith("http://")) &&
            (activeEntryUrl.isNullOrBlank() || activeEntryUrl == "about:blank" || activeEntryUrl == "about:home")
}
