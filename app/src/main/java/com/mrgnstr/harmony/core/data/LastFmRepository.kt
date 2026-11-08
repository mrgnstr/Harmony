package com.mrgnstr.harmony.core.data

import android.content.Context
import com.mrgnstr.harmony.core.datastore.SettingsStore
import com.mrgnstr.harmony.core.network.LastFmClient
import com.mrgnstr.harmony.core.network.LastFmInfo
import com.mrgnstr.harmony.core.network.MiniJson
import com.mrgnstr.harmony.core.network.buildJsonObj
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext

/**
 * Artist biographies and album texts from Last.fm, for music the server has nothing on (local files). Off unless the user turned it
 * on in Settings and gave their API key. Answers are kept on disk (a month; "nothing there" for a day), so a page opens offline
 * and Last.fm is asked once.
 */
@Singleton
class LastFmRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settings: SettingsStore,
    private val client: LastFmClient,
) {
    private val dir get() = File(context.filesDir, "lastfm").apply { mkdirs() }

    /** Emits null while the option is off or has no key, then the artist's text once it is known (null when Last.fm has none). */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun artist(name: String): Flow<LastFmInfo?> = enabledKey().flatMapLatest { key ->
        if (key == null) flowOf<LastFmInfo?>(null) else flow { emit(cached("artist", name, null) { client.artistInfo(key, name) }) }
    }.flowOn(Dispatchers.IO)

    @OptIn(ExperimentalCoroutinesApi::class)
    fun album(artist: String, title: String): Flow<LastFmInfo?> = enabledKey().flatMapLatest { key ->
        if (key == null) flowOf<LastFmInfo?>(null) else flow { emit(cached("album", artist, title) { client.albumInfo(key, artist, title) }) }
    }.flowOn(Dispatchers.IO)

    private fun enabledKey(): Flow<String?> =
        combine(settings.lastFmInfo, settings.lastFmApiKey) { on, key -> key.takeIf { on && it.isNotBlank() } }.distinctUntilChanged()

    private suspend fun cached(kind: String, name: String, title: String?, fetch: () -> LastFmInfo?): LastFmInfo? = withContext(Dispatchers.IO) {
        val file = File(dir, "$kind-${sha("${name.trim().lowercase()}|${title?.trim()?.lowercase().orEmpty()}")}.json")
        val now = System.currentTimeMillis()
        runCatching {
            val o = MiniJson.parseObject(file.readText())
            val age = now - o.long("at")
            val none = o.bool("none")
            if (age < (if (none) DAY else MONTH)) return@withContext if (none) null else LastFmInfo(o.strOrNull("text"), o.strOrNull("url"))
        }
        val fresh = fetch()
        // A failed request (offline) is not remembered as "nothing there": only a reachable Last.fm with no entry would be, and the
        // client cannot tell the two apart, so a miss is kept for just a day.
        runCatching {
            file.writeText(buildJsonObj {
                put("at", now)
                put("none", fresh == null)
                put("text", fresh?.text)
                put("url", fresh?.url)
            }.toJsonString())
        }
        fresh
    }

    private fun sha(s: String) = MessageDigest.getInstance("SHA-1").digest(s.toByteArray()).joinToString("") { "%02x".format(it) }.take(20)

    private companion object {
        const val DAY = 24L * 60 * 60 * 1000
        const val MONTH = 30 * DAY
    }
}
