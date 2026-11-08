package com.mrgnstr.harmony.core.network

import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import javax.inject.Inject
import javax.inject.Singleton

/** What Last.fm says about an artist or an album: the text (HTML removed) and the page it comes from. */
data class LastFmInfo(val text: String?, val url: String?)

/** Last.fm's public read API (artist.getInfo, album.getInfo); every call needs the user's own API key. */
@Singleton
class LastFmClient @Inject constructor() {
    /** @return null when Last.fm has nothing (unknown name, bad key, offline). */
    fun artistInfo(apiKey: String, artist: String): LastFmInfo? {
        val root = get("artist.getinfo", apiKey, "artist" to artist) ?: return null
        val a = root.obj("artist") ?: return null
        val bio = a.obj("bio")
        return info(bio?.strOrNull("content") ?: bio?.strOrNull("summary"), a.strOrNull("url"))
    }

    fun albumInfo(apiKey: String, artist: String, album: String): LastFmInfo? {
        val root = get("album.getinfo", apiKey, "artist" to artist, "album" to album) ?: return null
        val a = root.obj("album") ?: return null
        val wiki = a.obj("wiki")
        return info(wiki?.strOrNull("content") ?: wiki?.strOrNull("summary"), a.strOrNull("url"))
    }

    private fun info(text: String?, url: String?): LastFmInfo? {
        val clean = text?.let(::cleanText)?.takeIf { it.isNotBlank() }
        return if (clean == null && url == null) null else LastFmInfo(clean, url)
    }

    private fun get(method: String, apiKey: String, vararg params: Pair<String, String>): JsonObj? {
        val query = buildString {
            append("method=$method&api_key=${enc(apiKey)}&format=json&autocorrect=1")
            params.forEach { (k, v) -> append("&$k=${enc(v)}") }
        }
        val conn = URL("https://ws.audioscrobbler.com/2.0/?$query").openConnection() as HttpURLConnection
        return try {
            conn.connectTimeout = 8_000
            conn.readTimeout = 8_000
            conn.setRequestProperty("User-Agent", "Harmony")
            val body = (if (conn.responseCode in 200..299) conn.inputStream else conn.errorStream)?.bufferedReader()?.use { it.readText() }
            val obj = body?.let { runCatching { MiniJson.parse(it).asObjectOrNull }.getOrNull() }
            // {"error": 6, "message": "Artist not found"} also comes with a 200.
            obj?.takeUnless { it.fields.containsKey("error") }
        } catch (e: Exception) {
            null
        } finally {
            conn.disconnect()
        }
    }

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")

    internal companion object {
        /** Last.fm's text carries a "Read more" link and HTML breaks; the licence line after it is kept. */
        fun cleanText(raw: String): String =
            SubsonicApiClient.stripHtmlTags(raw.replace(Regex("<br\\s*/?>", RegexOption.IGNORE_CASE), "\n"))
                .replace(Regex("[ \\t]+\n"), "\n").replace(Regex("\n{3,}"), "\n\n").trim()
    }
}
