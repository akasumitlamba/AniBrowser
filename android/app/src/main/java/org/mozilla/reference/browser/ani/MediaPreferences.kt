/* SPDX-License-Identifier: MPL-2.0 */
package org.mozilla.reference.browser.ani

import android.content.Context
import android.net.Uri
import org.json.JSONObject

object MediaPreferences {
    const val BACKGROUND = "background_play"
    const val PIP = "picture_in_picture"
    private fun prefs(context: Context) = context.getSharedPreferences("ani_media", 0)
    fun global(context: Context, key: String) = prefs(context).getBoolean(key, true)
    fun site(context: Context, key: String, url: String) = prefs(context).getBoolean("$key:${Uri.parse(url).host.orEmpty().lowercase()}", true)
    fun enabled(context: Context, key: String, url: String) = global(context, key) && site(context, key, url)
    fun setGlobal(context: Context, key: String, value: Boolean) {
        prefs(context).edit().putBoolean(key, value).apply()
        PlaybackController.publishMediaPreferences(context)
    }
    fun setSite(context: Context, key: String, url: String, value: Boolean) {
        prefs(context).edit().putBoolean("$key:${Uri.parse(url).host.orEmpty().lowercase()}", value).apply()
        PlaybackController.publishMediaPreferences(context)
    }
    fun state(context: Context) = JSONObject().apply {
        put("type", "mediaPreferences")
        put("background", global(context, BACKGROUND))
        val sites = JSONObject()
        prefs(context).all.forEach { (key, value) ->
            if (key.startsWith("$BACKGROUND:")) sites.put(key.substringAfter(":"), value)
        }
        put("sites", sites)
    }
}
