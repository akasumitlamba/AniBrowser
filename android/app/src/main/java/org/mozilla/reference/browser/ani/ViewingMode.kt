/* SPDX-License-Identifier: MPL-2.0 */
package org.mozilla.reference.browser.ani

object ViewingMode {
    fun siteKey(host: String) = host.removePrefix("www.").removePrefix("m.").removePrefix("mobile.")

    fun userAgent(mode: Int, defaultAgent: String, androidVersion: String): String? {
        if (mode == 2) return null
        val version = Regex("rv:([0-9.]+)").find(defaultAgent)?.groupValues?.get(1) ?: return null
        val platform = if (mode == 1) "X11; Linux x86_64" else "Android $androidVersion; Mobile"
        return defaultAgent.replace(Regex("\\([^)]*\\)"), "($platform; rv:$version)")
    }
}
