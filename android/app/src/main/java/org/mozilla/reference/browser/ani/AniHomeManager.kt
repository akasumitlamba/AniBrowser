/* SPDX-License-Identifier: MPL-2.0 */
package org.mozilla.reference.browser.ani

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import java.util.UUID

data class AniHomeTile(
    val id: String,
    val title: String,
    val url: String,
    val iconPath: String? = null,
    val isCustom: Boolean = false,
    val bgHex: String? = null,
)

object AniHomeManager {
    private const val PREFS_FILE = "anihome_tiles.json"
    private const val ICONS_DIR = "anihome_icons"
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    var onTilesChanged: (() -> Unit)? = null

    private val DEFAULT_TILES = listOf(
        AniHomeTile("youtube", "YouTube", "https://www.youtube.com", bgHex = "#FFFFFF"),
        AniHomeTile("crunchyroll", "Crunchyroll", "https://www.crunchyroll.com", bgHex = "#FF6B35"),
    )

    @Synchronized
    fun getTiles(context: Context): List<AniHomeTile> {
        val file = File(context.filesDir, PREFS_FILE)
        if (!file.exists()) {
            // Seed initial defaults and copy pre-bundled real icons
            copyBundledIcon(context, "youtube.png", "youtube")
            copyBundledIcon(context, "crunchyroll.png", "crunchyroll")
            val seeded = DEFAULT_TILES.map { tile ->
                val iconFile = File(getIconsDir(context), "${tile.id}.png")
                if (iconFile.exists()) tile.copy(iconPath = iconFile.absolutePath) else tile
            }
            saveTiles(context, seeded)
            ensureIconsFetched(context)
            return seeded
        }

        return try {
            val jsonStr = file.readText()
            val array = JSONArray(jsonStr)
            val list = mutableListOf<AniHomeTile>()
            var anyUpdated = false
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val id = obj.getString("id")
                val title = obj.getString("title")
                val url = obj.getString("url")
                if (id == "settings" || url.startsWith("anibrowser://settings")) continue

                var iconPath = obj.optString("iconPath", "").ifBlank { null }
                val isCustom = obj.optBoolean("isCustom", true)
                val bgHex = obj.optString("bgHex", "").ifBlank { null }

                if (iconPath == null || !File(iconPath).exists()) {
                    val bundled = findBundledIcon(url, id)
                    if (bundled != null) {
                        iconPath = copyBundledIcon(context, bundled, id)
                        if (iconPath != null) anyUpdated = true
                    }
                }

                list.add(
                    AniHomeTile(
                        id = id,
                        title = title,
                        url = url,
                        iconPath = iconPath,
                        isCustom = isCustom,
                        bgHex = bgHex,
                    )
                )
            }

            if (anyUpdated) {
                saveTiles(context, list)
            }


            list
        } catch (e: Exception) {
            DEFAULT_TILES
        }
    }

    private fun findBundledIcon(url: String, id: String): String? {
        val lowUrl = url.lowercase(Locale.ROOT)
        return when {
            id == "youtube" || lowUrl.contains("youtube") -> "youtube.png"
            id == "crunchyroll" || lowUrl.contains("crunchyroll") -> "crunchyroll.png"
            id == "netflix" || lowUrl.contains("netflix") -> "netflix.png"
            id == "primevideo" || lowUrl.contains("primevideo") || lowUrl.contains("amazon") -> "primevideo.png"
            id == "appletv" || lowUrl.contains("apple.com") || lowUrl.contains("tv.apple") -> "appletv.png"
            id == "twitch" || lowUrl.contains("twitch") -> "twitch.png"
            id == "zoom" || lowUrl.contains("zoom") -> "zoom.png"
            id == "github" || lowUrl.contains("github") -> "github.png"
            id == "accuweather" || lowUrl.contains("accuweather") -> "accuweather.png"
            id == "spotify" || lowUrl.contains("spotify") -> "spotify.png"
            id == "reddit" || lowUrl.contains("reddit") -> "reddit.png"
            id == "wikipedia" || lowUrl.contains("wikipedia") -> "wikipedia.png"
            else -> null
        }
    }

    @Synchronized
    fun saveTiles(context: Context, tiles: List<AniHomeTile>) {
        val array = JSONArray()
        for (tile in tiles) {
            val obj = JSONObject()
            obj.put("id", tile.id)
            obj.put("title", tile.title)
            obj.put("url", tile.url)
            obj.put("iconPath", tile.iconPath ?: "")
            obj.put("isCustom", tile.isCustom)
            obj.put("bgHex", tile.bgHex ?: "")
            array.put(obj)
        }
        val file = File(context.filesDir, PREFS_FILE)
        file.writeText(array.toString(2))
    }

    @Synchronized
    fun addManualTile(context: Context, rawUrl: String, rawTitle: String?, onComplete: ((AniHomeTile) -> Unit)? = null) {
        val url = normalizeUrl(rawUrl)
        val title = if (!rawTitle.isNullOrBlank()) rawTitle.trim() else smartTitle(url)
        val id = UUID.randomUUID().toString().replace("-", "").take(12)

        val tile = AniHomeTile(
            id = id,
            title = title,
            url = url,
            isCustom = true,
            bgHex = generateBrandColor(url),
        )

        val current = getTiles(context).toMutableList()
        current.add(tile)
        saveTiles(context, current)
        onTilesChanged?.invoke()
        onComplete?.invoke(tile)

        scope.launch {
            val localPath = downloadAndSaveLogoOrIcon(context, id, url)
            if (localPath != null) {
                updateTileIcon(context, id, localPath)
            }
        }
    }

    @Synchronized
    fun addFromSession(context: Context, rawUrl: String, rawTitle: String?, sessionIcon: Bitmap?) {
        val url = normalizeUrl(rawUrl)
        val title = if (!rawTitle.isNullOrBlank() && rawTitle != "about:blank" && rawTitle != "about:home") {
            rawTitle.trim()
        } else {
            smartTitle(url)
        }
        val id = UUID.randomUUID().toString().replace("-", "").take(12)

        var localPath: String? = null
        if (sessionIcon != null && !sessionIcon.isRecycled) {
            localPath = saveBitmapLocally(context, id, sessionIcon)
        }

        val tile = AniHomeTile(
            id = id,
            title = title,
            url = url,
            iconPath = localPath,
            isCustom = true,
            bgHex = generateBrandColor(url),
        )

        val current = getTiles(context).toMutableList()
        current.removeAll { it.url.equals(url, ignoreCase = true) }
        current.add(tile)
        saveTiles(context, current)
        onTilesChanged?.invoke()

        run {
            scope.launch {
                val downloaded = downloadAndSaveLogoOrIcon(context, id, url)
                if (downloaded != null) {
                    updateTileIcon(context, id, downloaded)
                }
            }
        }
    }

    @Synchronized
    fun updateTileTitle(context: Context, id: String, newTitle: String, notify: Boolean = true) {
        if (newTitle.isBlank()) return
        val tiles = getTiles(context).toMutableList()
        val index = tiles.indexOfFirst { it.id == id }
        if (index >= 0) {
            tiles[index] = tiles[index].copy(title = newTitle.trim())
            saveTiles(context, tiles)
            if (notify) onTilesChanged?.invoke()
        }
    }

    fun ensureIconsFetched(context: Context) {
        scope.launch {
            val tiles = getTiles(context)
            for (tile in tiles) {
                val iconFile = tile.iconPath?.let { File(it) }
                if (iconFile == null || !iconFile.exists()) {
                    val downloaded = downloadAndSaveLogoOrIcon(context, tile.id, tile.url)
                    if (downloaded != null) {
                        updateTileIcon(context, tile.id, downloaded)
                    }
                }
            }
        }
    }

    fun copyBundledIcon(context: Context, assetName: String, destId: String): String? {
        val destFile = File(getIconsDir(context), "$destId.png")
        if (!destFile.exists()) {
            try {
                context.assets.open("defaults/$assetName").use { input ->
                    FileOutputStream(destFile).use { output ->
                        input.copyTo(output)
                    }
                }
            } catch (e: Exception) {
                return null
            }
        }
        return destFile.absolutePath
    }

    @Synchronized
    fun removeTile(context: Context, id: String, notify: Boolean = true) {
        val current = getTiles(context).toMutableList()
        val removed = current.removeAll { it.id == id }
        if (removed) {
            saveTiles(context, current)
            val iconFile = File(getIconsDir(context), "$id.png")
            if (iconFile.exists()) iconFile.delete()
            if (notify) onTilesChanged?.invoke()
        }
    }

    @Synchronized
    private fun updateTileIcon(context: Context, id: String, localPath: String, notify: Boolean = true) {
        val tiles = getTiles(context).toMutableList()
        val index = tiles.indexOfFirst { it.id == id }
        if (index >= 0) {
            tiles[index] = tiles[index].copy(iconPath = localPath)
            saveTiles(context, tiles)
            if (notify) onTilesChanged?.invoke()
        }
    }

    fun normalizeUrl(raw: String): String {
        val trimmed = raw.trim()
        return when {
            trimmed.startsWith("http://", ignoreCase = true) || trimmed.startsWith("https://", ignoreCase = true) || trimmed.startsWith("anibrowser://", ignoreCase = true) -> trimmed
            else -> "https://$trimmed"
        }
    }

    fun smartTitle(url: String): String {
        return try {
            val uri = Uri.parse(url)
            val host = (uri.host ?: url).lowercase(Locale.ROOT).removePrefix("www.")
            when {
                host.contains("youtube") -> "YouTube"
                host.contains("crunchyroll") -> "Crunchyroll"
                host.contains("spotify") -> "Spotify"
                host.contains("netflix") -> "Netflix"
                host.contains("primevideo") || (host.contains("amazon") && host.contains("video")) -> "Prime Video"
                host.contains("apple") && host.contains("tv") -> "Apple TV"
                host.contains("twitch") -> "Twitch"
                host.contains("zoom") -> "Zoom"
                host.contains("github") -> "GitHub"
                host.contains("reddit") -> "Reddit"
                host.contains("disneyplus") -> "Disney+"
                host.contains("hulu") -> "Hulu"
                host.contains("jiocinema") -> "JioCinema"
                host.contains("sonyliv") -> "Sony LIV"
                host.contains("zee5") -> "ZEE5"
                host.contains("accuweather") -> "AccuWeather"
                host.contains("f1") || host.contains("formula1") -> "Formula 1"
                host.contains("twitter") || host == "x.com" -> "X"
                host.contains("wikipedia") -> "Wikipedia"
                host.contains("hianime") -> "HiAnime"
                host.contains("anilist") -> "AniList"
                else -> {
                    val part = host.split(".").firstOrNull { it != "m" && it != "mobile" } ?: host
                    part.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.ROOT) else it.toString() }
                }
            }
        } catch (e: Exception) {
            "Site"
        }
    }

    private fun getIconsDir(context: Context): File {
        val dir = File(context.filesDir, ICONS_DIR)
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    private fun saveBitmapLocally(context: Context, id: String, bitmap: Bitmap): String? {
        return try {
            val file = File(getIconsDir(context), "$id.png")
            val largest = maxOf(bitmap.width, bitmap.height)
            val outputBitmap = if (largest > MAX_ICON_EDGE) {
                val scale = MAX_ICON_EDGE.toFloat() / largest
                Bitmap.createScaledBitmap(
                    bitmap,
                    (bitmap.width * scale).toInt().coerceAtLeast(1),
                    (bitmap.height * scale).toInt().coerceAtLeast(1),
                    true,
                )
            } else {
                bitmap
            }
            FileOutputStream(file).use { out ->
                outputBitmap.compress(Bitmap.CompressFormat.PNG, 90, out)
            }
            if (outputBitmap !== bitmap) outputBitmap.recycle()
            file.absolutePath
        } catch (e: Exception) {
            null
        }
    }

    private suspend fun downloadAndSaveLogoOrIcon(context: Context, id: String, siteUrl: String): String? = withContext(Dispatchers.IO) {
        val host = try {
            Uri.parse(siteUrl).host?.removePrefix("www.")
        } catch (e: Exception) {
            null
        } ?: return@withContext null

        val candidateUrls = discoverLogoUrls(siteUrl) + listOf(
            "https://t3.gstatic.com/faviconV2?client=SOCIAL&type=FAVICON&fallback_opts=TYPE,SIZE,URL&url=https://" + host + "&size=128",
            "https://" + host + "/favicon.ico",
            "https://icons.duckduckgo.com/ip3/" + host + ".ico"
        )

        for (candidate in candidateUrls) {
            var connection: HttpURLConnection? = null
            try {
                val urlObj = URL(candidate)
                val conn = urlObj.openConnection() as HttpURLConnection
                connection = conn
                conn.connectTimeout = 4000
                conn.readTimeout = 4000
                conn.instanceFollowRedirects = true
                conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Android; Mobile; rv:115.0) Gecko/115.0 Firefox/115.0")
                if (conn.responseCode == 200) {
                    val bytes = conn.inputStream.use { input ->
                        val output = java.io.ByteArrayOutputStream()
                        val buffer = ByteArray(8 * 1024)
                        var total = 0
                        while (true) {
                            val count = input.read(buffer)
                            if (count < 0) break
                            total += count
                            if (total > MAX_DOWNLOAD_BYTES) break
                            output.write(buffer, 0, count)
                        }
                        if (total > MAX_DOWNLOAD_BYTES) ByteArray(0) else output.toByteArray()
                    }
                    if (bytes.size > 200) {
                        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
                        var sample = 1
                        while (maxOf(bounds.outWidth, bounds.outHeight) / sample > MAX_ICON_EDGE * 2) sample *= 2
                        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
                        if (bitmap != null) {
                            val saved = saveBitmapLocally(context, id, bitmap)
                            bitmap.recycle()
                            if (saved != null) return@withContext saved
                        }
                    }
                }
            } catch (_: Exception) {
                // Try the next logo candidate.
            } finally {
                connection?.disconnect()
            }
        }
        null
    }

    /** Prefer a site's own labelled logo image before falling back to its favicon. */
    private fun discoverLogoUrls(siteUrl: String): List<String> {
        val connection = runCatching { URL(siteUrl).openConnection() as HttpURLConnection }.getOrNull() ?: return emptyList()
        return try {
            connection.connectTimeout = 3000
            connection.readTimeout = 3000
            val html = connection.inputStream.bufferedReader().use { reader ->
                val chars = CharArray(192 * 1024)
                var total = 0
                while (total < chars.size) {
                    val n = reader.read(chars, total, chars.size - total)
                    if (n < 0) break
                    total += n
                }
                String(chars, 0, total)
            }
            Regex("""<img\b[^>]*>""", RegexOption.IGNORE_CASE).findAll(html)
                .map { it.value }.filter { it.contains("logo", true) }
                .mapNotNull { tag -> Regex("""\bsrc\s*=\s*["']([^"']+)["']""", RegexOption.IGNORE_CASE).find(tag)?.groupValues?.get(1) }
                .mapNotNull { runCatching { URL(URL(siteUrl), it.replace("&amp;", "&")).toString() }.getOrNull() }
                .filter { it.startsWith("https://") || it.startsWith("http://") }.take(3).toList()
        } catch (_: Exception) {
            emptyList()
        } finally {
            connection.disconnect()
        }
    }

    fun refreshLogos(context: Context, complete: (Int) -> Unit = {}) {
        val app = context.applicationContext
        scope.launch {
            var refreshed = 0
            try {
                getTiles(app).forEach { tile ->
                    val path = downloadAndSaveLogoOrIcon(app, tile.id, tile.url)
                    if (path != null) { updateTileIcon(app, tile.id, path, notify = false); refreshed++ }
                }
            } finally { withContext(Dispatchers.Main) { complete(refreshed) } }
        }
    }

    private fun generateBrandColor(url: String): String {
        val h = Math.abs(url.hashCode()) % 6
        return when (h) {
            0 -> "#1E293B"
            1 -> "#0F172A"
            2 -> "#1F2937"
            3 -> "#18181B"
            4 -> "#1E1B4B"
            else -> "#172554"
        }
    }

    private const val MAX_ICON_EDGE = 384
    private const val MAX_DOWNLOAD_BYTES = 1024 * 1024
}
