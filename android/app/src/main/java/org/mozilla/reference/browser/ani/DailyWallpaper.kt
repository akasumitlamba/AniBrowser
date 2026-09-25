/* SPDX-License-Identifier: MPL-2.0 */
package org.mozilla.reference.browser.ani

import android.content.Context
import android.graphics.BitmapFactory
import android.util.AtomicFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDate

/** Home-only download; never replaces a valid cached wallpaper with a failed response. */
object DailyWallpaper {
    private val lock = Mutex()
    private var lastAttempt = 0L
    private fun prefs(context: Context) = context.getSharedPreferences("daily_wallpaper", 0)
    fun file(context: Context): File? = prefs(context).getString("image", null)?.let {
        if (it.matches(Regex("wallpaper_[01]\\.jpg"))) File(context.filesDir, it).takeIf(File::isFile) else null
    }
    fun state(context: Context): String = JSONObject().apply {
        put("url", file(context)?.let { "https://anibrowser.local/wallpaper/" + it.name } ?: "")
        put("credit", prefs(context).getString("credit", ""))
    }.toString()

    suspend fun refresh(context: Context, canDownload: () -> Boolean = { true }) = withContext(Dispatchers.IO) {
        lock.withLock {
            val today = LocalDate.now().toString()
            val preferences = prefs(context)
            if (file(context) != null && preferences.getString("day", "") == today) return@withLock
            val now = System.currentTimeMillis()
            if (now - lastAttempt < 15 * 60 * 1000L) return@withLock
            lastAttempt = now
            runCatching {
                val metadata = JSONObject(String(download("https://www.bing.com/HPImageArchive.aspx?format=js&idx=0&n=1&mkt=en-IN", 128 * 1024, canDownload), Charsets.UTF_8))
                    .getJSONArray("images").getJSONObject(0)
                val path = metadata.getString("url")
                require(path.startsWith("/th?") && !path.contains("\\"))
                val bytes = download("https://www.bing.com$path", 12 * 1024 * 1024, canDownload)
                currentCoroutineContext().ensureActive()
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
                require(bounds.outWidth >= 800 && bounds.outHeight >= 480)
                val name = if (file(context)?.name == "wallpaper_0.jpg") "wallpaper_1.jpg" else "wallpaper_0.jpg"
                val atomic = AtomicFile(File(context.filesDir, name))
                val stream = atomic.startWrite()
                try { stream.write(bytes); atomic.finishWrite(stream) } catch (error: Exception) {
                    atomic.failWrite(stream); throw error
                }
                preferences.edit().putString("image", name).putString("day", today)
                    .putString("credit", metadata.optString("copyright")).commit()
            }.onFailure { error ->
                if (error is CancellationException) {
                    lastAttempt = 0L
                    throw error
                }
            }
            Unit
        }
    }

    private suspend fun download(address: String, limit: Int, canDownload: () -> Boolean): ByteArray {
        currentCoroutineContext().ensureActive()
        if (!canDownload()) throw CancellationException("Website loading takes priority")
        val connection = URL(address).openConnection() as HttpURLConnection
        return try {
            connection.connectTimeout = 8000
            connection.readTimeout = 12000
            connection.instanceFollowRedirects = false
            connection.setRequestProperty("User-Agent", "AniBrowser/1.0")
            require(connection.responseCode == 200)
            connection.inputStream.use { input ->
                val output = java.io.ByteArrayOutputStream()
                val buffer = ByteArray(8192)
                while (true) {
                    currentCoroutineContext().ensureActive()
                    if (!canDownload()) throw CancellationException("Website loading takes priority")
                    val size = input.read(buffer)
                    if (size < 0) break
                    require(output.size() + size <= limit)
                    output.write(buffer, 0, size)
                }
                output.toByteArray()
            }
        } finally { connection.disconnect() }
    }
}
