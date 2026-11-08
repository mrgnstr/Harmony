package com.mrgnstr.harmony.core.network

import com.mrgnstr.harmony.core.model.AlbumExtraInfo
import com.mrgnstr.harmony.core.model.ArtistExtraInfo
import com.mrgnstr.harmony.core.model.AudioBookmark
import com.mrgnstr.harmony.core.model.AudioFormat
import com.mrgnstr.harmony.core.model.DiscTitleInfo
import com.mrgnstr.harmony.core.model.LyricAgent
import com.mrgnstr.harmony.core.model.LyricLine
import com.mrgnstr.harmony.core.model.LyricWordCue
import com.mrgnstr.harmony.core.model.Lyrics
import com.mrgnstr.harmony.core.model.MusicFolder
import com.mrgnstr.harmony.core.model.NowPlayingSession
import com.mrgnstr.harmony.core.model.OpenSubsonicExtension
import com.mrgnstr.harmony.core.model.ParticipantCredit
import com.mrgnstr.harmony.core.model.RadioStation
import com.mrgnstr.harmony.core.model.RemoteIds
import com.mrgnstr.harmony.core.model.ReplayGainInfo
import com.mrgnstr.harmony.core.model.ScanStatusInfo
import com.mrgnstr.harmony.core.model.ServerAccount
import com.mrgnstr.harmony.core.model.ShareLink
import com.mrgnstr.harmony.core.model.SimilarArtistRef
import com.mrgnstr.harmony.core.model.Song
import com.mrgnstr.harmony.core.model.SonicMatchItem
import com.mrgnstr.harmony.core.model.StreamDetailsModel
import com.mrgnstr.harmony.core.model.TranscodeDecisionModel
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

data class SubsonicPingResult(
    val ok: Boolean,
    val version: String,
    val type: String,
    val serverVersion: String,
    val openSubsonic: Boolean,
)

data class SubsonicUserPermissions(
    val username: String,
    val email: String?,
    val isAdmin: Boolean,
    val downloadRole: Boolean,
    val shareRole: Boolean,
    val jukeboxRole: Boolean,
    val playlistRole: Boolean,
    val scrobblingEnabled: Boolean,
    val maxBitRate: Int,
    val folders: List<Int>,
)

data class RemotePlaylistDto(
    val remoteId: String,
    val name: String,
    val comment: String?,
    val owner: String?,
    val isPublic: Boolean,
    val songCount: Int,
    val durationMs: Long,
    val createdMs: Long,
    val changedMs: Long,
    val coverArtId: String?,
    val readonly: Boolean,
    val validUntilMs: Long?,
    val songs: List<Song>,
)

data class RemoteStarredResult(
    val songs: List<Song>,
    val albums: List<AlbumExtraInfo>,
    val artists: List<ArtistExtraInfo>,
)

data class RemoteSearchResult(
    val songs: List<Song>,
    val albums: List<AlbumExtraInfo>,
    val artists: List<ArtistExtraInfo>,
)

data class RemoteAlbumWithSongs(
    val extra: AlbumExtraInfo,
    val title: String,
    val artist: String,
    val year: Int,
    val genre: String?,
    val starred: Boolean,
    val createdSec: Long,
    val songs: List<Song>,
)

/**
 * Full OpenSubsonic / Subsonic v1.16.1 API client (`/rest/`).
 * Uses `formPost` (POST urlencoded) when advertised by the server, falling back to GET query params.
 */
@Singleton
class SubsonicApiClient @Inject constructor(
    private val http: NavidromeHttpClient,
) {
    suspend fun call(
        account: ServerAccount,
        endpoint: String,
        params: List<Pair<String, String>> = emptyList(),
        usePost: Boolean = account.supportsExtension("formPost"),
    ): JsonObj {
        val base = account.normalizedBaseUrl
        val clean = endpoint.removePrefix("/").removePrefix("rest/").removeSuffix(".view")
        val allParams = SubsonicAuth.authParams(account, jsonFormat = true) + params
        val resp = if (usePost) {
            val encoded = SubsonicAuth.encodeQuery(allParams).toByteArray(Charsets.UTF_8)
            http.request(
                account = account,
                url = "$base/rest/$clean",
                method = "POST",
                bodyBytes = encoded,
                contentType = "application/x-www-form-urlencoded",
            )
        } else {
            http.request(
                account = account,
                url = "$base/rest/$clean?${SubsonicAuth.encodeQuery(allParams)}",
                method = "GET",
            )
        }
        if (resp.statusCode == 429) {
            val retryAfter = resp.header("Retry-After")?.toIntOrNull()
            throw NavidromeApiException(429, "Too many concurrent transcodes", retryAfterSeconds = retryAfter)
        }
        if (resp.statusCode !in 200..299 && resp.body.isBlank()) {
            throw NavidromeApiException(resp.statusCode, "HTTP ${resp.statusCode} on $clean")
        }
        val root = MiniJson.parseObject(resp.body)
        val sub = root.obj("subsonic-response")
            ?: throw NavidromeApiException(resp.statusCode, "Invalid Subsonic response on $clean")
        if (sub.str("status") != "ok") {
            val err = sub.obj("error")
            val code = err?.int("code", 0) ?: 0
            val msg = err?.str("message", "Subsonic error $code") ?: "Subsonic error"
            throw NavidromeApiException(code, msg, isSubsonicError = true)
        }
        return sub
    }

    // --- System & Auth ---

    suspend fun ping(account: ServerAccount): SubsonicPingResult {
        val sub = call(account, "ping", usePost = false)
        return SubsonicPingResult(
            ok = sub.str("status") == "ok",
            version = sub.str("version", SubsonicAuth.SUBSONIC_VERSION),
            type = sub.str("type", "navidrome"),
            serverVersion = sub.str("serverVersion"),
            openSubsonic = sub.bool("openSubsonic", false),
        )
    }

    suspend fun getOpenSubsonicExtensions(account: ServerAccount): List<OpenSubsonicExtension> {
        val sub = call(account, "getOpenSubsonicExtensions", usePost = false)
        return sub.arr("openSubsonicExtensions").objects().map { ext ->
            OpenSubsonicExtension(
                name = ext.str("name"),
                versions = ext.arr("versions").ints(),
            )
        }
    }

    suspend fun tokenInfo(account: ServerAccount): String? {
        val sub = call(account, "tokenInfo")
        return sub.obj("tokenInfo")?.strOrNull("username")
    }

    suspend fun getUser(account: ServerAccount, username: String = account.username): SubsonicUserPermissions {
        val sub = call(account, "getUser", listOf("username" to username))
        val u = sub.obj("user") ?: JsonObj()
        return SubsonicUserPermissions(
            username = u.str("username", username),
            email = u.strOrNull("email"),
            isAdmin = u.bool("adminRole", false),
            downloadRole = u.bool("downloadRole", true),
            shareRole = u.bool("shareRole", false),
            jukeboxRole = u.bool("jukeboxRole", false),
            playlistRole = u.bool("playlistRole", true),
            scrobblingEnabled = u.bool("scrobblingEnabled", true),
            maxBitRate = u.int("maxBitRate", 0),
            folders = u.arr("folder").ints(),
        )
    }

    // --- Multi-Library ---

    suspend fun getMusicFolders(account: ServerAccount): List<MusicFolder> {
        val sub = call(account, "getMusicFolders")
        val folders = sub.obj("musicFolders")?.arr("musicFolder")?.objects().orEmpty()
        return folders.map { f ->
            val id = f.int("id")
            MusicFolder(
                id = id,
                name = f.str("name", "Library $id"),
                serverId = account.id.coerceAtLeast(1L),
                selected = account.selectedMusicFolders.isEmpty() || id in account.selectedMusicFolders,
            )
        }
    }

    private fun folderParams(account: ServerAccount, folderIds: Collection<Int> = account.selectedMusicFolders): List<Pair<String, String>> =
        folderIds.map { "musicFolderId" to it.toString() }

    // --- Browsing & Catalog ---

    suspend fun getArtists(account: ServerAccount): List<ArtistExtraInfo> {
        val sub = call(account, "getArtists", folderParams(account))
        val indexes = sub.obj("artists")?.arr("index")?.objects().orEmpty()
        val serverId = account.id.coerceAtLeast(1L)
        return indexes.flatMap { idx ->
            idx.arr("artist").objects().map { a -> parseArtistID3(serverId, a) }
        }
    }

    suspend fun getArtist(account: ServerAccount, artistRemoteId: String): Pair<ArtistExtraInfo, List<AlbumExtraInfo>> {
        val sub = call(account, "getArtist", listOf("id" to artistRemoteId))
        val serverId = account.id.coerceAtLeast(1L)
        val a = sub.obj("artist") ?: JsonObj()
        val artist = parseArtistID3(serverId, a)
        val albums = a.arr("album").objects().map { parseAlbumID3(serverId, it).first }
        return artist to albums
    }

    suspend fun getArtistInfo2(account: ServerAccount, artistRemoteId: String, count: Int = 20): ArtistExtraInfo {
        val sub = call(account, "getArtistInfo2", listOf("id" to artistRemoteId, "count" to count.toString()))
        val info = sub.obj("artistInfo2") ?: JsonObj()
        val similar = info.arr("similarArtist").objects().mapNotNull { s ->
            val id = s.str("id")
            val name = s.str("name")
            if (name.isBlank()) null else SimilarArtistRef(
                id = id,
                name = name,
                coverArtId = s.strOrNull("coverArt"),
                albumCount = s.int("albumCount"),
            )
        }
        return ArtistExtraInfo(
            name = "",
            serverId = account.id.coerceAtLeast(1L),
            remoteId = artistRemoteId,
            biography = info.strOrNull("biography")?.let(::stripHtmlTags),
            mbzArtistId = info.strOrNull("musicBrainzId"),
            externalUrl = info.strOrNull("lastFmUrl"),
            similarArtists = similar,
        )
    }

    suspend fun getAlbumList2(
        account: ServerAccount,
        type: String = "newest",
        size: Int = 500,
        offset: Int = 0,
        genre: String? = null,
        fromYear: Int? = null,
        toYear: Int? = null,
    ): List<RemoteAlbumWithSongs> {
        val params = buildList {
            add("type" to type)
            add("size" to size.coerceIn(1, 500).toString())
            add("offset" to offset.coerceAtLeast(0).toString())
            if (genre != null) add("genre" to genre)
            if (fromYear != null) add("fromYear" to fromYear.toString())
            if (toYear != null) add("toYear" to toYear.toString())
            addAll(folderParams(account))
        }
        val sub = call(account, "getAlbumList2", params)
        val serverId = account.id.coerceAtLeast(1L)
        return sub.obj("albumList2")?.arr("album")?.objects().orEmpty().map { a ->
            val (extra, summary) = parseAlbumID3(serverId, a)
            summary.copy(extra = extra)
        }
    }

    suspend fun getAlbum(account: ServerAccount, albumRemoteId: String): RemoteAlbumWithSongs {
        val sub = call(account, "getAlbum", listOf("id" to albumRemoteId))
        val serverId = account.id.coerceAtLeast(1L)
        val a = sub.obj("album") ?: JsonObj()
        val (extra, summary) = parseAlbumID3(serverId, a)
        val songs = a.arr("song").objects().map { parseSongChild(account, it) }
        return summary.copy(extra = extra, songs = songs)
    }

    suspend fun getAlbumInfo2(account: ServerAccount, albumRemoteId: String): Triple<String?, String?, String?> {
        val sub = call(account, "getAlbumInfo2", listOf("id" to albumRemoteId))
        val info = sub.obj("albumInfo") ?: JsonObj()
        return Triple(
            info.strOrNull("notes")?.let(::stripHtmlTags),
            info.strOrNull("musicBrainzId"),
            info.strOrNull("lastFmUrl"),
        )
    }

    suspend fun getSong(account: ServerAccount, songRemoteId: String): Song? {
        val sub = call(account, "getSong", listOf("id" to songRemoteId))
        return sub.obj("song")?.let { parseSongChild(account, it) }
    }

    suspend fun getTopSongs(
        account: ServerAccount,
        artistName: String? = null,
        artistRemoteId: String? = null,
        count: Int = 50,
    ): List<Song> {
        val params = buildList {
            if (!artistName.isNullOrBlank()) add("artist" to artistName)
            if (!artistRemoteId.isNullOrBlank() && account.supportsExtension("topSongsByArtistId")) {
                add("id" to artistRemoteId)
            }
            add("count" to count.toString())
        }
        val sub = call(account, "getTopSongs", params)
        return sub.obj("topSongs")?.arr("song")?.objects().orEmpty().map { parseSongChild(account, it) }
    }

    suspend fun getSimilarSongs2(account: ServerAccount, id: String, count: Int = 50): List<Song> {
        val sub = call(account, "getSimilarSongs2", listOf("id" to id, "count" to count.toString()))
        return sub.obj("similarSongs2")?.arr("song")?.objects().orEmpty().map { parseSongChild(account, it) }
    }

    suspend fun getRandomSongs(
        account: ServerAccount,
        size: Int = 50,
        genre: String? = null,
        fromYear: Int? = null,
        toYear: Int? = null,
    ): List<Song> {
        val params = buildList {
            add("size" to size.coerceIn(1, 500).toString())
            if (!genre.isNullOrBlank()) add("genre" to genre)
            if (fromYear != null && fromYear > 0) add("fromYear" to fromYear.toString())
            if (toYear != null && toYear > 0) add("toYear" to toYear.toString())
            addAll(folderParams(account))
        }
        val sub = call(account, "getRandomSongs", params)
        return sub.obj("randomSongs")?.arr("song")?.objects().orEmpty().map { parseSongChild(account, it) }
    }

    suspend fun getSongsByGenre(account: ServerAccount, genre: String, count: Int = 500, offset: Int = 0): List<Song> {
        val params = listOf("genre" to genre, "count" to count.toString(), "offset" to offset.toString()) + folderParams(account)
        val sub = call(account, "getSongsByGenre", params)
        return sub.obj("songsByGenre")?.arr("song")?.objects().orEmpty().map { parseSongChild(account, it) }
    }

    // --- Sonic Similarity (OpenSubsonic sonicSimilarity extension) ---

    suspend fun getSonicSimilarTracks(account: ServerAccount, songRemoteId: String, count: Int = 20): List<SonicMatchItem> {
        val sub = call(account, "getSonicSimilarTracks", listOf("id" to songRemoteId, "count" to count.toString()))
        return sub.arr("sonicMatch").objects().mapNotNull { m ->
            val entry = m.obj("entry") ?: return@mapNotNull null
            SonicMatchItem(parseSongChild(account, entry), m.double("similarity", 0.0))
        }
    }

    // --- Starred (Favourites) & Ratings ---

    suspend fun getStarred2(account: ServerAccount): RemoteStarredResult {
        val sub = call(account, "getStarred2", folderParams(account))
        val st = sub.obj("starred2") ?: JsonObj()
        val serverId = account.id.coerceAtLeast(1L)
        return RemoteStarredResult(
            songs = st.arr("song").objects().map { parseSongChild(account, it) },
            albums = st.arr("album").objects().map { parseAlbumID3(serverId, it).first },
            artists = st.arr("artist").objects().map { parseArtistID3(serverId, it) },
        )
    }

    suspend fun setStar(
        account: ServerAccount,
        starred: Boolean,
        ids: List<String> = emptyList(),
        albumIds: List<String> = emptyList(),
        artistIds: List<String> = emptyList(),
    ) {
        val params = buildList {
            ids.forEach { add("id" to it) }
            albumIds.forEach { add("albumId" to it) }
            artistIds.forEach { add("artistId" to it) }
        }
        if (params.isEmpty()) return
        call(account, if (starred) "star" else "unstar", params)
    }

    suspend fun setRating(account: ServerAccount, remoteId: String, rating: Int) {
        call(account, "setRating", listOf("id" to remoteId, "rating" to rating.coerceIn(0, 5).toString()))
    }

    // --- Scrobbling & Playback Reporting ---

    suspend fun scrobble(
        account: ServerAccount,
        remoteIds: List<String>,
        timestampsMs: List<Long> = emptyList(),
        submission: Boolean = true,
        positionSec: Int = 0,
    ) {
        if (remoteIds.isEmpty()) return
        val params = buildList {
            remoteIds.forEach { add("id" to it) }
            timestampsMs.forEach { add("time" to it.toString()) }
            add("submission" to submission.toString())
            if (!submission && positionSec > 0) add("position" to positionSec.toString())
        }
        call(account, "scrobble", params)
    }

    suspend fun reportPlayback(
        account: ServerAccount,
        mediaRemoteId: String,
        positionMs: Long,
        state: String,
        playbackRate: Double = 1.0,
        ignoreScrobble: Boolean = false,
    ) {
        val params = listOf(
            "mediaId" to mediaRemoteId,
            "mediaType" to "song",
            "positionMs" to positionMs.coerceAtLeast(0L).toString(),
            "state" to state,
            "playbackRate" to playbackRate.toString(),
            "ignoreScrobble" to ignoreScrobble.toString(),
        )
        call(account, "reportPlayback", params)
    }

    suspend fun getNowPlaying(account: ServerAccount): List<NowPlayingSession> {
        val sub = call(account, "getNowPlaying")
        val entries = sub.obj("nowPlaying")?.arr("entry")?.objects().orEmpty()
        return entries.map { e ->
            NowPlayingSession(
                song = parseSongChild(account, e),
                username = e.str("username"),
                minutesAgo = e.int("minutesAgo"),
                playerId = e.int("playerId"),
                playerName = e.str("playerName"),
                state = e.str("state", "playing"),
                positionMs = e.long("positionMs"),
                playbackRate = e.double("playbackRate", 1.0),
            )
        }
    }

    // --- Bookmarks ---

    suspend fun getBookmarks(account: ServerAccount): List<AudioBookmark> {
        val sub = call(account, "getBookmarks")
        val list = sub.obj("bookmarks")?.arr("bookmark")?.objects().orEmpty()
        return list.mapNotNull { b ->
            val entry = b.obj("entry") ?: return@mapNotNull null
            val song = parseSongChild(account, entry)
            val remoteId = song.remoteId ?: return@mapNotNull null
            AudioBookmark(
                songId = song.id,
                remoteSongId = remoteId,
                songTitle = song.title,
                artist = song.artist,
                albumId = song.albumId,
                positionMs = b.long("position", 0L),
                comment = b.str("comment"),
                createdMs = parseIsoTimeMs(b.strOrNull("created")),
                changedMs = parseIsoTimeMs(b.strOrNull("changed")),
            )
        }
    }

    suspend fun createBookmark(account: ServerAccount, remoteSongId: String, positionMs: Long, comment: String = "") {
        val params = buildList {
            add("id" to remoteSongId)
            add("position" to positionMs.coerceAtLeast(0L).toString())
            if (comment.isNotBlank()) add("comment" to comment)
        }
        call(account, "createBookmark", params)
    }

    suspend fun deleteBookmark(account: ServerAccount, remoteSongId: String) {
        call(account, "deleteBookmark", listOf("id" to remoteSongId))
    }

    // --- Playlists ---

    suspend fun getPlaylists(account: ServerAccount): List<RemotePlaylistDto> {
        val sub = call(account, "getPlaylists")
        return sub.obj("playlists")?.arr("playlist")?.objects().orEmpty().map { parsePlaylist(account, it) }
    }

    suspend fun getPlaylist(account: ServerAccount, playlistRemoteId: String): RemotePlaylistDto {
        val sub = call(account, "getPlaylist", listOf("id" to playlistRemoteId))
        val p = sub.obj("playlist") ?: JsonObj()
        return parsePlaylist(account, p)
    }

    suspend fun createPlaylist(
        account: ServerAccount,
        name: String,
        songRemoteIds: List<String> = emptyList(),
        playlistRemoteId: String? = null,
    ): RemotePlaylistDto {
        val params = buildList {
            if (!playlistRemoteId.isNullOrBlank()) add("playlistId" to playlistRemoteId)
            if (name.isNotBlank()) add("name" to name)
            songRemoteIds.forEach { add("songId" to it) }
        }
        val sub = call(account, "createPlaylist", params)
        return parsePlaylist(account, sub.obj("playlist") ?: JsonObj())
    }

    suspend fun updatePlaylist(
        account: ServerAccount,
        playlistRemoteId: String,
        name: String? = null,
        comment: String? = null,
        isPublic: Boolean? = null,
        songIdsToAdd: List<String> = emptyList(),
        songIndexesToRemove: List<Int> = emptyList(),
    ) {
        val params = buildList {
            add("playlistId" to playlistRemoteId)
            if (name != null) add("name" to name)
            if (comment != null) add("comment" to comment)
            if (isPublic != null) add("public" to isPublic.toString())
            songIdsToAdd.forEach { add("songIdToAdd" to it) }
            songIndexesToRemove.forEach { add("songIndexToRemove" to it.toString()) }
        }
        call(account, "updatePlaylist", params)
    }

    suspend fun deletePlaylist(account: ServerAccount, playlistRemoteId: String) {
        call(account, "deletePlaylist", listOf("id" to playlistRemoteId))
    }

    // --- Search ---

    suspend fun search3(
        account: ServerAccount,
        query: String,
        artistCount: Int = 20,
        albumCount: Int = 20,
        songCount: Int = 50,
    ): RemoteSearchResult {
        val params = listOf(
            "query" to query,
            "artistCount" to artistCount.toString(),
            "albumCount" to albumCount.toString(),
            "songCount" to songCount.toString(),
        ) + folderParams(account)
        val sub = call(account, "search3", params)
        val sr = sub.obj("searchResult3") ?: JsonObj()
        val serverId = account.id.coerceAtLeast(1L)
        return RemoteSearchResult(
            songs = sr.arr("song").objects().map { parseSongChild(account, it) },
            albums = sr.arr("album").objects().map { parseAlbumID3(serverId, it).first },
            artists = sr.arr("artist").objects().map { parseArtistID3(serverId, it) },
        )
    }

    // --- Lyrics (OpenSubsonic songLyrics v1 & v2) ---

    suspend fun getLyricsBySongId(account: ServerAccount, songRemoteId: String, enhanced: Boolean = true): Lyrics? {
        val sub = call(
            account,
            "getLyricsBySongId",
            listOf("id" to songRemoteId, "enhanced" to enhanced.toString()),
        )
        val structured = sub.obj("lyricsList")?.arr("structuredLyrics")?.objects().orEmpty()
        if (structured.isEmpty()) return null
        return parseStructuredLyrics(structured)
    }

    suspend fun getLegacyLyrics(account: ServerAccount, artist: String, title: String): Lyrics? {
        val sub = call(account, "getLyrics", listOf("artist" to artist, "title" to title))
        val value = sub.obj("lyrics")?.strOrNull("value") ?: return null
        return com.mrgnstr.harmony.core.model.LrcParser.parse(value)
    }

    internal fun parseStructuredLyrics(entries: List<JsonObj>): Lyrics? {
        if (entries.isEmpty()) return null
        val mainEntry = entries.firstOrNull {
            val k = it.str("kind", "main")
            k.isBlank() || k.equals("main", ignoreCase = true)
        } ?: entries.first()
        val translationEntry = entries.firstOrNull { it.str("kind").equals("translation", ignoreCase = true) }
        val pronunciationEntry = entries.firstOrNull { it.str("kind").equals("pronunciation", ignoreCase = true) }

        val offsetMs = mainEntry.long("offset", 0L)
        val synced = mainEntry.bool("synced", false)
        val lang = mainEntry.str("lang", "und")
        val kind = mainEntry.str("kind", "main").ifBlank { "main" }

        val agents = mainEntry.arr("agent").objects().ifEmpty { mainEntry.arr("agents").objects() }.map { a ->
            LyricAgent(
                id = a.str("id"),
                role = a.str("role", "main"),
                name = a.strOrNull("name"),
            )
        }

        // Translation and pronunciation lines map by line index when they line up with the main lines, else by start time.
        val rawLines = mainEntry.arr("line").objects()
        val transLines = translationEntry?.arr("line")?.objects().orEmpty()
        val pronLines = pronunciationEntry?.arr("line")?.objects().orEmpty()
        val transByIndex = transLines.size == rawLines.size
        val pronByIndex = pronLines.size == rawLines.size
        fun byStart(lines: List<JsonObj>, start: Long): String? =
            lines.firstOrNull { l -> l["start"]?.asLongOrNull?.let { (it - offsetMs).coerceAtLeast(0L) } == start }?.str("value")
        val pronCueLines = pronunciationEntry?.arr("cueLine")?.objects().orEmpty().groupBy { it.int("index", -1) }

        // Layers of one line[index]: cueLines sharing an index are parallel (main voice, backing, other voices).
        val roles = agents.associate { it.id to it.role.lowercase() }
        val mainVoice = agents.firstOrNull { it.role.equals("main", ignoreCase = true) }?.id ?: agents.firstOrNull()?.id
        val cueLinesByIndex = mainEntry.arr("cueLine").objects().groupBy { it.int("index", -1) }
        var anyWordCues = false

        val builtLines = ArrayList<LyricLine>(rawLines.size)
        rawLines.forEachIndexed { idx, lineObj ->
            val startRaw = lineObj["start"]?.asLongOrNull
            val startMs = if (synced && startRaw != null) (startRaw - offsetMs).coerceAtLeast(0L) else -1L
            val text = lineObj.str("value")
            val translation = if (transByIndex) transLines.getOrNull(idx)?.str("value") else if (synced && startMs >= 0) byStart(transLines, startMs) else null
            val isBgCl = { cl: JsonObj -> cl.strOrNull("agentId")?.let { roles[it] == "bg" || it.contains("bg", ignoreCase = true) } == true }
            val pronCl = pronCueLines[idx]?.firstOrNull { !isBgCl(it) }
            val pronBgCl = pronCueLines[idx]?.firstOrNull(isBgCl)
            val pronunciation = (if (pronByIndex) pronLines.getOrNull(idx)?.str("value") else if (synced && startMs >= 0) byStart(pronLines, startMs) else null)
                ?: pronCl?.strOrNull("value")
            val pronunciationCues = pronCl?.let { cuesOf(it, offsetMs, null) }.orEmpty()
            if (pronunciationCues.isNotEmpty()) anyWordCues = true

            val layers = cueLinesByIndex[idx].orEmpty()
            val bgLayer = layers.firstOrNull { roles[it.strOrNull("agentId")] == "bg" }
            val isMain = { cl: JsonObj -> cl.strOrNull("agentId").let { it == null || it == mainVoice } }
            val mainLayer = layers.firstOrNull { it !== bgLayer && isMain(it) }
            val others = layers.filter { it !== bgLayer && it !== mainLayer && !isMain(it) }
            val primary = mainLayer ?: others.firstOrNull()
            if (primary == null) {
                if (bgLayer == null) {
                    builtLines += LyricLine(startMs, text, translation = translation, pronunciation = pronunciation, pronunciationCues = pronunciationCues)
                } else {
                    // Only a backing vocal at this index: it stands as a line.
                    builtLines += cueLineToLine(bgLayer, offsetMs, startMs, text, synced, bgLayer.strOrNull("agentId"), false).also { anyWordCues = anyWordCues || it.cues.isNotEmpty() }
                        .copy(translation = translation, pronunciation = pronunciation, pronunciationCues = pronunciationCues)
                }
                return@forEachIndexed
            }
            val agentId = primary.strOrNull("agentId")
            val duet = primary !== mainLayer
            val background = bgLayer?.let { stripParens(cueLineToLine(it, offsetMs, startMs, "", synced, agentId, duet)) }?.takeIf { it.text.isNotEmpty() }
                ?.let { b ->
                    // The backing vocal's pronunciation comes as a parallel cueLine of its own.
                    val pb = pronBgCl?.let { stripParens(cueLineToLine(it, offsetMs, startMs, "", synced, null, duet)) }?.takeIf { it.text.isNotEmpty() }
                    if (pb == null) b else b.copy(pronunciation = pb.text, pronunciationCues = pb.cues)
                }
            val line = cueLineToLine(primary, offsetMs, startMs, text, synced, agentId, duet)
            anyWordCues = anyWordCues || line.cues.isNotEmpty() || background?.cues?.isNotEmpty() == true
            builtLines += line.copy(
                translation = translation, pronunciation = pronunciation, pronunciationCues = pronunciationCues, background = background,
            )
            // A second voice singing alongside the main one gets its own duet line right after.
            if (mainLayer != null) for (o in others) {
                val oid = o.strOrNull("agentId")
                val l = cueLineToLine(o, offsetMs, startMs, text, synced, oid, true)
                anyWordCues = anyWordCues || l.cues.isNotEmpty()
                builtLines += l
            }
        }
        if (builtLines.isEmpty()) return null
        return Lyrics(
            lines = if (synced) builtLines.sortedBy { it.startMs } else builtLines,
            synced = synced,
            lang = lang,
            kind = kind,
            agents = agents,
            hasTranslations = transLines.isNotEmpty(),
            hasPronunciations = pronLines.isNotEmpty() || pronCueLines.isNotEmpty(),
            hasWordCues = anyWordCues,
        ).withoutRedundantPronunciations()
    }

    private fun cueLineToLine(cl: JsonObj, offsetMs: Long, fallbackStart: Long, fallbackText: String, synced: Boolean, agentId: String?, duet: Boolean): LyricLine {
        val start = if (synced) cl["start"]?.asLongOrNull?.let { (it - offsetMs).coerceAtLeast(0L) } ?: fallbackStart else -1L
        val end = if (synced) cl["end"]?.asLongOrNull?.let { (it - offsetMs).coerceAtLeast(0L) } else null
        val value = cl.str("value").ifEmpty { fallbackText }
        return LyricLine(start, value, end, agentId, cuesOf(cl, offsetMs, agentId), duet = duet)
    }

    /** Cue text is sliced from the line's UTF-8 bytes (`byteEnd` inclusive) when the offsets fit, else it is the cue's own value. */
    private fun cuesOf(cl: JsonObj, offsetMs: Long, agentId: String?): List<LyricWordCue> {
        val bytes = cl.str("value").toByteArray(Charsets.UTF_8)
        return cl.arr("cue").objects().map { c ->
            val bs = c["byteStart"]?.asIntOrNull
            val be = c["byteEnd"]?.asIntOrNull
            val sliced = if (bs != null && be != null && bs >= 0 && be >= bs && be < bytes.size) {
                String(bytes, bs, be - bs + 1, Charsets.UTF_8).takeIf { '\uFFFD' !in it }
            } else null
            LyricWordCue(
                startMs = (c.long("start") - offsetMs).coerceAtLeast(0L),
                endMs = c["end"]?.asLongOrNull?.let { (it - offsetMs).coerceAtLeast(0L) },
                byteStart = c.int("byteStart"),
                byteEnd = c.int("byteEnd"),
                text = sliced ?: c.str("value"),
                agentId = agentId,
            )
        }
    }

    /** Backing vocals are usually sent in parentheses; the UI draws them without. */
    private fun stripParens(l: LyricLine): LyricLine {
        if (l.cues.isEmpty()) return l.copy(text = l.text.trim().removeSurrounding("(", ")").trim())
        val cues = l.cues.toMutableList()
        cues[0] = cues[0].copy(text = cues[0].text.removePrefix("("))
        cues[cues.lastIndex] = cues.last().let { it.copy(text = it.text.trimEnd().removeSuffix(")")) }
        cues.removeAll { it.text.isEmpty() }
        val joined = cues.joinToString("") { it.text }.trim()
        // The cue slices can leave out the spaces between words ("(Hey" "uh)"); the line's own value has them.
        val own = l.text.trim().removePrefix("(").removeSuffix(")").trim()
        val text = if (own.filterNot { it.isWhitespace() } == joined.filterNot { it.isWhitespace() }) own else joined
        return l.copy(text = text, cues = cues)
    }

    // --- Transcoding & Streaming ---

    suspend fun getTranscodeDecision(
        account: ServerAccount,
        mediaRemoteId: String,
        clientInfoJson: JsonObj,
    ): TranscodeDecisionModel {
        val url = SubsonicAuth.buildRestUrl(
            account = account,
            endpoint = "getTranscodeDecision",
            extraParams = listOf("mediaId" to mediaRemoteId, "mediaType" to "song"),
        )
        val resp = http.request(
            account = account,
            url = url,
            method = "POST",
            bodyBytes = clientInfoJson.toJsonString().toByteArray(Charsets.UTF_8),
            contentType = "application/json",
        )
        val root = MiniJson.parseObject(resp.body)
        val sub = root.obj("subsonic-response")
            ?: throw NavidromeApiException(resp.statusCode, "Invalid getTranscodeDecision response")
        if (sub.str("status") != "ok") {
            val err = sub.obj("error")
            throw NavidromeApiException(err?.int("code") ?: 0, err?.str("message") ?: "Transcode decision failed", isSubsonicError = true)
        }
        val td = sub.obj("transcodeDecision") ?: JsonObj()
        return TranscodeDecisionModel(
            canDirectPlay = td.bool("canDirectPlay", true),
            canTranscode = td.bool("canTranscode", false),
            transcodeReasons = td.arr("transcodeReason").strings(),
            errorReason = td.strOrNull("errorReason"),
            transcodeParams = td.strOrNull("transcodeParams"),
            sourceStream = td.obj("sourceStream")?.let(::parseStreamDetails),
            transcodeStream = td.obj("transcodeStream")?.let(::parseStreamDetails),
        )
    }

    private fun parseStreamDetails(s: JsonObj): StreamDetailsModel = StreamDetailsModel(
        protocol = s.str("protocol", "http"),
        container = s.str("container"),
        codec = s.str("codec"),
        audioChannels = s.int("audioChannels", 2),
        audioBitrateBps = s.int("audioBitrate", 0),
        audioProfile = s.str("audioProfile"),
        audioSamplerate = s.int("audioSamplerate", 0),
        audioBitdepth = s.int("audioBitdepth", 0),
    )

    fun buildTranscodeStreamUrl(
        account: ServerAccount,
        mediaRemoteId: String,
        transcodeParams: String,
        offsetSec: Int = 0,
    ): String {
        val params = buildList {
            add("mediaId" to mediaRemoteId)
            add("mediaType" to "song")
            add("transcodeParams" to transcodeParams)
            if (offsetSec > 0) add("offset" to offsetSec.toString())
        }
        return SubsonicAuth.buildRestUrl(account, "getTranscodeStream", params, jsonFormat = false)
    }

    fun buildStreamUrl(
        account: ServerAccount,
        songRemoteId: String,
        maxBitRateKbps: Int = 0,
        format: String? = null,
        timeOffsetSec: Int = 0,
    ): String {
        val params = buildList {
            add("id" to songRemoteId)
            if (maxBitRateKbps > 0) add("maxBitRate" to maxBitRateKbps.toString())
            if (!format.isNullOrBlank()) add("format" to format)
            if (timeOffsetSec > 0) add("timeOffset" to timeOffsetSec.toString())
        }
        return SubsonicAuth.buildRestUrl(account, "stream", params, jsonFormat = false)
    }

    fun buildDownloadUrl(
        account: ServerAccount,
        remoteId: String,
        format: String = "raw",
        bitrateKbps: Int = 0,
    ): String {
        val params = buildList {
            add("id" to remoteId)
            if (format.isNotBlank()) add("format" to format)
            if (bitrateKbps > 0) add("bitrate" to bitrateKbps.toString())
        }
        return SubsonicAuth.buildRestUrl(account, "download", params, jsonFormat = false)
    }

    fun buildCoverArtUrl(
        account: ServerAccount,
        coverArtId: String,
        sizePx: Int = 512,
        square: Boolean = false,
    ): String {
        val params = buildList {
            add("id" to coverArtId)
            if (sizePx > 0) add("size" to sizePx.toString())
            if (square) add("square" to "true")
        }
        return SubsonicAuth.buildRestUrl(account, "getCoverArt", params, jsonFormat = false)
    }

    // --- Sharing ---

    suspend fun getShares(account: ServerAccount): List<ShareLink> {
        val sub = call(account, "getShares")
        return sub.obj("shares")?.arr("share")?.objects().orEmpty().map { parseShare(it) }
    }

    suspend fun createShare(
        account: ServerAccount,
        remoteIds: List<String>,
        description: String = "",
        expiresMs: Long? = null,
        downloadable: Boolean = true,
    ): ShareLink? {
        val params = buildList {
            remoteIds.forEach { add("id" to it) }
            if (description.isNotBlank()) add("description" to description)
            if (expiresMs != null && expiresMs > 0L) add("expires" to expiresMs.toString())
            add("downloadable" to downloadable.toString())
        }
        val sub = call(account, "createShare", params)
        return sub.obj("shares")?.arr("share")?.objects()?.firstOrNull()?.let(::parseShare)
    }

    suspend fun updateShare(
        account: ServerAccount,
        shareId: String,
        description: String? = null,
        expiresMs: Long? = null,
        downloadable: Boolean? = null,
    ) {
        val params = buildList {
            add("id" to shareId)
            if (description != null) add("description" to description)
            if (expiresMs != null) add("expires" to expiresMs.toString())
            if (downloadable != null) add("downloadable" to downloadable.toString())
        }
        call(account, "updateShare", params)
    }

    suspend fun deleteShare(account: ServerAccount, shareId: String) {
        call(account, "deleteShare", listOf("id" to shareId))
    }

    private fun parseShare(s: JsonObj): ShareLink = ShareLink(
        id = s.str("id"),
        url = s.str("url"),
        description = s.str("description"),
        username = s.str("username"),
        createdMs = parseIsoTimeMs(s.strOrNull("created")),
        expiresMs = s.strOrNull("expires")?.let(::parseIsoTimeMs)?.takeIf { it > 0L },
        lastVisitedMs = s.strOrNull("lastVisited")?.let(::parseIsoTimeMs)?.takeIf { it > 0L },
        visitCount = s.int("visitCount"),
        downloadable = s.bool("downloadable", true),
        itemCount = s.arr("entry").size,
    )

    // --- Internet Radio ---

    suspend fun getInternetRadioStations(account: ServerAccount): List<RadioStation> {
        val sub = call(account, "getInternetRadioStations")
        val serverId = account.id.coerceAtLeast(1L)
        val stations = sub.obj("internetRadioStations")?.arr("internetRadioStation")?.objects().orEmpty()
        return stations.map { r ->
            val remoteId = r.str("id")
            RadioStation(
                id = RemoteIds.radioId(serverId, remoteId),
                serverId = serverId,
                remoteId = remoteId,
                name = r.str("name"),
                streamUrl = r.str("streamUrl"),
                homePageUrl = r.strOrNull("homePageUrl"),
                coverArtId = r.strOrNull("coverArt"),
            )
        }
    }

    suspend fun createInternetRadioStation(
        account: ServerAccount,
        name: String,
        streamUrl: String,
        homepageUrl: String? = null,
    ) {
        val params = buildList {
            add("name" to name)
            add("streamUrl" to streamUrl)
            if (!homepageUrl.isNullOrBlank()) add("homepageUrl" to homepageUrl)
        }
        call(account, "createInternetRadioStation", params)
    }

    suspend fun updateInternetRadioStation(
        account: ServerAccount,
        remoteId: String,
        name: String,
        streamUrl: String,
        homepageUrl: String? = null,
    ) {
        val params = buildList {
            add("id" to remoteId)
            add("name" to name)
            add("streamUrl" to streamUrl)
            if (!homepageUrl.isNullOrBlank()) add("homepageUrl" to homepageUrl)
        }
        call(account, "updateInternetRadioStation", params)
    }

    suspend fun deleteInternetRadioStation(account: ServerAccount, remoteId: String) {
        call(account, "deleteInternetRadioStation", listOf("id" to remoteId))
    }

    // --- Library Scanning ---

    suspend fun getScanStatus(account: ServerAccount): ScanStatusInfo {
        val sub = call(account, "getScanStatus")
        val st = sub.obj("scanStatus") ?: JsonObj()
        return ScanStatusInfo(
            scanning = st.bool("scanning", false),
            count = st.long("count", 0L),
            folderCount = st.long("folderCount", 0L),
            lastScanMs = st.strOrNull("lastScan")?.let(::parseIsoTimeMs),
            error = st.strOrNull("error"),
            scanType = st.strOrNull("scanType"),
            elapsedTimeMs = st.long("elapsedTime", 0L) / 1_000_000L,
        )
    }

    suspend fun startScan(
        account: ServerAccount,
        fullScan: Boolean = false,
        targets: List<String> = emptyList(),
    ): ScanStatusInfo {
        val params = buildList {
            add("fullScan" to fullScan.toString())
            targets.forEach { add("target" to it) }
        }
        val sub = call(account, "startScan", params)
        val st = sub.obj("scanStatus") ?: JsonObj()
        return ScanStatusInfo(
            scanning = st.bool("scanning", false),
            count = st.long("count", 0L),
            folderCount = st.long("folderCount", 0L),
            lastScanMs = st.strOrNull("lastScan")?.let(::parseIsoTimeMs),
            error = st.strOrNull("error"),
            scanType = st.strOrNull("scanType"),
            elapsedTimeMs = st.long("elapsedTime", 0L) / 1_000_000L,
        )
    }

    // --- Entity Parsers ---

    internal fun parseSongChild(account: ServerAccount, c: JsonObj): Song {
        val serverId = account.id.coerceAtLeast(1L)
        val remoteId = c.str("id")
        val remoteAlbumId = c.str("albumId").ifEmpty { c.str("parent") }.ifEmpty { "unknown:${c.str("album")}" }
        val localId = RemoteIds.songId(serverId, remoteId)
        val localAlbumId = RemoteIds.albumId(serverId, remoteAlbumId)
        val suffix = c.str("suffix").lowercase()
        val contentType = c.str("contentType")
        val bitDepth = c.int("bitDepth", 0)
        val sampleRate = c.int("samplingRate", 0)
        val channels = c.int("channelCount", 2).coerceAtLeast(1)
        val lossless = AudioFormat.isLossless(contentType) || suffix in setOf("flac", "wav", "aif", "aiff", "alac", "wv", "ape", "dsf") ||
            (suffix == "m4a" && bitDepth > 0)
        val codec = when {
            suffix == "flac" -> "FLAC"
            suffix == "m4a" && bitDepth > 0 -> "ALAC"
            suffix == "wav" -> "WAV"
            suffix == "aif" || suffix == "aiff" -> "AIFF"
            suffix == "opus" -> "Opus"
            suffix == "ogg" || suffix == "oga" -> "Vorbis"
            suffix == "mp3" -> "MP3"
            suffix == "m4a" || suffix == "aac" -> "AAC"
            contentType.isNotBlank() -> AudioFormat.codecName(contentType)
            suffix.isNotBlank() -> suffix.uppercase()
            else -> null
        }
        val rgObj = c.obj("replayGain")
        val replayGain = rgObj?.let {
            ReplayGainInfo(
                trackGainDb = it.doubleOrNull("trackGain"),
                albumGainDb = it.doubleOrNull("albumGain"),
                trackPeak = it.doubleOrNull("trackPeak"),
                albumPeak = it.doubleOrNull("albumPeak"),
                baseGainDb = it.doubleOrNull("baseGain"),
                fallbackGainDb = it.doubleOrNull("fallbackGain"),
            ).takeUnless(ReplayGainInfo::isEmpty)
        }
        val createdSec = parseIsoTimeMs(c.strOrNull("created")) / 1000L
        val artist = c.strOrNull("displayArtist") ?: c.str("artist", "[Unknown Artist]")
        val albumArtist = c.strOrNull("displayAlbumArtist")
            ?: c.arr("albumArtists").objects().firstOrNull()?.strOrNull("name")
            ?: artist
        val coverArtId = c.strOrNull("coverArt")

        return Song(
            id = localId,
            title = c.str("title", "Untitled"),
            artist = artist,
            albumArtist = albumArtist,
            album = c.str("album", "[Unknown Album]"),
            albumId = localAlbumId,
            durationMs = c.long("duration", 0L) * 1000L,
            trackNumber = c.int("track", 0),
            discNumber = c.int("discNumber", 1).coerceAtLeast(1),
            dateAddedSec = if (createdSec > 0L) createdSec else System.currentTimeMillis() / 1000L,
            dateModifiedSec = if (createdSec > 0L) createdSec else 1L,
            genre = c.strOrNull("genre") ?: c.arr("genres").objects().firstOrNull()?.strOrNull("name"),
            year = c.int("year", 0),
            lossless = lossless,
            serverId = serverId,
            remoteId = remoteId,
            coverArtId = coverArtId,
            streamUri = buildStreamUrl(account, remoteId),
            codec = codec,
            bitRateKbps = c.int("bitRate", 0),
            sampleRateHz = sampleRate,
            bitDepth = bitDepth,
            channels = channels,
            userRating = c.int("userRating", 0),
            replayGain = replayGain,
            bookmarkPositionMs = c.long("bookmarkPosition", 0L),
            explicitStatus = c.strOrNull("explicitStatus"),
            bpm = c.int("bpm", 0),
            comment = c.strOrNull("comment"),
            discSubtitle = null,
        )
    }

    internal fun parseAlbumID3(serverId: Long, a: JsonObj): Pair<AlbumExtraInfo, RemoteAlbumWithSongs> {
        val remoteId = a.str("id")
        val localAlbumId = RemoteIds.albumId(serverId, remoteId)
        val title = a.str("name").ifEmpty { a.str("title", "[Unknown Album]") }
        val artist = a.strOrNull("displayArtist") ?: a.str("artist", "[Unknown Artist]")
        val discTitles = a.arr("discTitles").objects().map { d ->
            DiscTitleInfo(
                disc = d.int("disc", 1),
                title = d.str("title"),
                coverArtId = d.strOrNull("coverArt"),
            )
        }
        val labels = a.arr("recordLabels").objects().mapNotNull { it.strOrNull("name") }
        val releaseTypes = a.arr("releaseTypes").strings()
        val participants = buildList {
            a.arr("artists").objects().forEach { ar ->
                add(ParticipantCredit(id = ar.str("id"), name = ar.str("name"), role = "albumartist"))
            }
            a.arr("contributors").objects().forEach { c ->
                val ar = c.obj("artist")
                if (ar != null) {
                    add(ParticipantCredit(id = ar.str("id"), name = ar.str("name"), role = c.str("role"), subRole = c.strOrNull("subRole")))
                }
            }
        }
        val extra = AlbumExtraInfo(
            albumId = localAlbumId,
            serverId = serverId,
            remoteId = remoteId,
            coverArtId = a.strOrNull("coverArt"),
            thumbHash = a.strOrNull("thumbHash"),
            dominantColor = a.strOrNull("dominantColor"),
            releaseTypes = releaseTypes,
            version = a.strOrNull("version"),
            recordLabels = labels,
            catalogNum = a.strOrNull("catalogNum"),
            originalReleaseDate = formatItemDate(a.obj("originalReleaseDate")),
            releaseDate = formatItemDate(a.obj("releaseDate")),
            isCompilation = a.bool("isCompilation", false),
            description = a.strOrNull("description"),
            mbzAlbumId = a.strOrNull("musicBrainzId"),
            userRating = a.int("userRating", 0),
            averageRating = a.double("averageRating", 0.0),
            discTitles = discTitles,
            participants = participants,
        )
        val summary = RemoteAlbumWithSongs(
            extra = extra,
            title = title,
            artist = artist,
            year = a.int("year", 0),
            genre = a.strOrNull("genre") ?: a.arr("genres").objects().firstOrNull()?.strOrNull("name"),
            starred = a.strOrNull("starred") != null,
            createdSec = parseIsoTimeMs(a.strOrNull("created")) / 1000L,
            songs = emptyList(),
        )
        return extra to summary
    }

    internal fun parseArtistID3(serverId: Long, a: JsonObj): ArtistExtraInfo = ArtistExtraInfo(
        name = a.str("name"),
        serverId = serverId,
        remoteId = a.str("id"),
        sortName = a.strOrNull("sortName"),
        coverArtId = a.strOrNull("coverArt"),
        thumbHash = a.strOrNull("thumbHash"),
        dominantColor = a.strOrNull("dominantColor"),
        mbzArtistId = a.strOrNull("musicBrainzId"),
        roles = a.arr("roles").strings(),
        userRating = a.int("userRating", 0),
        averageRating = a.double("averageRating", 0.0),
    )

    private fun parsePlaylist(account: ServerAccount, p: JsonObj): RemotePlaylistDto = RemotePlaylistDto(
        remoteId = p.str("id"),
        name = p.str("name", "Playlist"),
        comment = p.strOrNull("comment"),
        owner = p.strOrNull("owner"),
        isPublic = p.bool("public", false),
        songCount = p.int("songCount", 0),
        durationMs = p.long("duration", 0L) * 1000L,
        createdMs = parseIsoTimeMs(p.strOrNull("created")),
        changedMs = parseIsoTimeMs(p.strOrNull("changed")),
        coverArtId = p.strOrNull("coverArt"),
        readonly = p.bool("readonly", false),
        validUntilMs = p.strOrNull("validUntil")?.let(::parseIsoTimeMs)?.takeIf { it > 0L },
        songs = p.arr("entry").objects().map { parseSongChild(account, it) },
    )

    companion object {
        fun parseIsoTimeMs(iso: String?): Long {
            if (iso.isNullOrBlank()) return 0L
            // Navidrome writes the server's local offset ("2026-11-07T19:56:21.535236098+02:00"), which Instant.parse rejects:
            // every date then fell back to 0 and "added" became the time of the sync.
            return runCatching { java.time.OffsetDateTime.parse(iso).toInstant().toEpochMilli() }
                .recoverCatching { Instant.parse(iso).toEpochMilli() }
                .getOrElse { iso.toLongOrNull() ?: 0L }
        }

        private fun formatItemDate(dateObj: JsonObj?): String? {
            if (dateObj == null) return null
            val y = dateObj.int("year", 0)
            if (y <= 0) return null
            val m = dateObj.int("month", 0)
            val d = dateObj.int("day", 0)
            return when {
                m > 0 && d > 0 -> "%04d-%02d-%02d".format(y, m, d)
                m > 0 -> "%04d-%02d".format(y, m)
                else -> "%04d".format(y)
            }
        }

        fun stripHtmlTags(raw: String): String =
            raw.replace(Regex("<a\\s+[^>]*>.*?</a>", RegexOption.IGNORE_CASE), "")
                .replace(Regex("<[^>]+>"), "")
                .replace("&quot;", "\"")
                .replace("&amp;", "&")
                .replace("&#39;", "'")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .trim()
    }
}
