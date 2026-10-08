/* SPDX-License-Identifier: MPL-2.0 */
package org.mozilla.reference.browser.ani

import android.util.JsonWriter
import mozilla.components.browser.state.state.recover.RecoverableTab
import org.json.JSONObject
import java.io.StringWriter

object SessionRestore {
    /** Run on IO alongside session deserialization. Valid engine history is retained. */
    fun repair(tab: RecoverableTab): RecoverableTab {
        val snapshot = tab.engineSessionState ?: return tab
        if (!tab.state.url.startsWith("https://") && !tab.state.url.startsWith("http://")) return tab
        val needsReload = try {
            val serialized = StringWriter()
            JsonWriter(serialized).use { snapshot.writeTo(it) }
            val wrapper = JSONObject(serialized.toString())
            if (!wrapper.has("GECKO_STATE")) return tab
            val history = JSONObject(wrapper.getString("GECKO_STATE")).optJSONObject("history")
            val entries = history?.optJSONArray("entries")
            val index = (history?.optInt("index", 0) ?: 0) - 1
            val activeUrl = entries?.optJSONObject(index)?.optString("url")
            SessionRestorePolicy.needsUrlReload(tab.state.url, activeUrl)
        } catch (_: org.json.JSONException) {
            true
        }
        // A null snapshot tells EngineMiddleware to load the saved URL on selection.
        return if (needsReload) tab.copy(engineSessionState = null) else tab
    }
}
