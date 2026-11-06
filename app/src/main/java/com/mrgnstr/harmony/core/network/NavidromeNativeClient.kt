package com.mrgnstr.harmony.core.network

import com.mrgnstr.harmony.core.model.AlbumExtraInfo
import com.mrgnstr.harmony.core.model.ArtistExtraInfo
import com.mrgnstr.harmony.core.model.AudioFormat
import com.mrgnstr.harmony.core.model.DiscTitleInfo
import com.mrgnstr.harmony.core.model.MissingFileItem
import com.mrgnstr.harmony.core.model.ParticipantCredit
import com.mrgnstr.harmony.core.model.QuickConnectChallenge
import com.mrgnstr.harmony.core.model.RemoteIds
import com.mrgnstr.harmony.core.model.ReplayGainInfo
import com.mrgnstr.harmony.core.model.ServerAccount
import com.mrgnstr.harmony.core.model.SmartPlaylistCriteria
import com.mrgnstr.harmony.core.model.Song
import com.mrgnstr.harmony.core.model.normalizeServerUrl
import java.net.URLEncoder
import javax.inject.Inject
import javax.inject.Singleton

data class NativeLoginResponse(
    val userId: String,
    val username: String,
    val name: String,
    val isAdmin: Boolean,
    val jwtToken: String,
    val subsonicSalt: String,
    val subsonicToken: String,
    val avatarUrl: String?,
)

data class NativeServerInfo(
    val name: String,
    val serverVersion: String,
    val specVersion: String,
    val setupRequired: Boolean,
)

data class NativePlaylistSummary(
    val remoteId: String,
    val name: String,
    val comment: String?,
    val ownerName: String?,
    val isPublic: Boolean,
    val songCount: Int,
    val durationSec: Float,
    val isSmart: Boolean,
    val rulesJson: String?,
    val evaluatedAtMs: Long?,
    val coverArtId: String?,
    val thumbHash: String?,
    val dominantColor: String?,
    val userRating: Int,
    val starred: Boolean,
    val updatedAtMs: Long,
)

/**
 * Client for Navidrome's Native REST API (`/auth/`, `/api/`, `/api/v1/`, `/jellyfin/QuickConnect/`).
 * Unlocks features beyond standard Subsonic: JWT auth, ThumbHash/DominantColor hydration, Smart Playlists (`.nsp`),
 * custom artwork uploads, playlist track reordering, M3U import/export, Quick Connect, and admin tools.
 */
@Singleton
class NavidromeNativeClient @Inject constructor(
    private val http: NavidromeHttpClient,
    private val subsonic: SubsonicApiClient,
) {
    suspend fun getServerInfo(baseUrl: String, allowSelfSignedTls: Boolean = false): NativeServerInfo? {
        val temp = ServerAccount(name = "temp", baseUrl = baseUrl, username = "", allowSelfSignedTls = allowSelfSignedTls)
        val resp = runCatching {
            http.request(temp, "${temp.normalizedBaseUrl}/api/v1/server", method = "GET")
        }.getOrNull() ?: return null
        if (resp.statusCode !in 200..299) return null
        val obj = MiniJson.parseObject(resp.body)
        return NativeServerInfo(
            name = obj.str("name", "Navidrome"),
            serverVersion = obj.str("serverVersion"),
            specVersion = obj.str("specVersion"),
            setupRequired = obj.bool("setupRequired", false),
        )
    }

    suspend fun login(
        baseUrl: String,
        username: String,
        password: String,
        allowSelfSignedTls: Boolean = false,
    ): NativeLoginResponse {
        val temp = ServerAccount(name = "login", baseUrl = baseUrl, username = username, allowSelfSignedTls = allowSelfSignedTls)
        val payload = buildJsonObj {
            put("username", username)
            put("password", password)
        }.toJsonString().toByteArray(Charsets.UTF_8)
        val resp = http.request(
            account = temp,
            url = "${temp.normalizedBaseUrl}/auth/login",
            method = "POST",
            bodyBytes = payload,
            contentType = "application/json",
        )
        if (resp.statusCode !in 200..299) {
            val errObj = runCatching { MiniJson.parseObject(resp.body) }.getOrNull()
            val msg = errObj?.strOrNull("error") ?: errObj?.strOrNull("message") ?: "Login failed (HTTP ${resp.statusCode})"
            throw NavidromeApiException(resp.statusCode, msg)
        }
        val obj = MiniJson.parseObject(resp.body)
        return NativeLoginResponse(
            userId = obj.str("id"),
            username = obj.str("username", username),
            name = obj.str("name", username),
            isAdmin = obj.bool("isAdmin", false),
            jwtToken = obj.str("token"),
            subsonicSalt = obj.str("subsonicSalt"),
            subsonicToken = obj.str("subsonicToken"),
            avatarUrl = obj.strOrNull("avatar"),
        )
    }

    // --- Quick Connect ---

    private fun mediaBrowserHeader(deviceName: String, deviceId: String): String =
        "MediaBrowser Client=\"Harmony\", Device=\"$deviceName\", DeviceId=\"$deviceId\", Version=\"0.2.0\""

    suspend fun initiateQuickConnect(
        baseUrl: String,
        deviceName: String,
        allowSelfSignedTls: Boolean = false,
    ): QuickConnectChallenge {
        val temp = ServerAccount(name = "qc", baseUrl = baseUrl, username = "", allowSelfSignedTls = allowSelfSignedTls)
        val resp = http.request(
            account = temp,
            url = "${temp.normalizedBaseUrl}/jellyfin/QuickConnect/Initiate",
            method = "POST",
            headers = mapOf("X-Emby-Authorization" to mediaBrowserHeader(deviceName, http.clientUniqueId)),
            bodyBytes = ByteArray(0),
        )
        if (resp.statusCode !in 200..299) {
            throw NavidromeApiException(resp.statusCode, "Quick Connect is not enabled on this server")
        }
        val obj = MiniJson.parseObject(resp.body)
        return QuickConnectChallenge(
            code = obj.str("Code"),
            secret = obj.str("Secret"),
            authenticated = obj.bool("Authenticated", false),
            deviceName = obj.str("DeviceName", deviceName),
            appName = obj.str("AppName", "Harmony"),
        )
    }

    suspend fun checkQuickConnectStatus(
        baseUrl: String,
        secret: String,
        allowSelfSignedTls: Boolean = false,
    ): QuickConnectChallenge {
        val temp = ServerAccount(name = "qc", baseUrl = normalizeServerUrl(baseUrl), username = "", allowSelfSignedTls = allowSelfSignedTls)
        val encoded = URLEncoder.encode(secret, "UTF-8")
        val resp = http.request(
            account = temp,
            url = "${temp.normalizedBaseUrl}/jellyfin/QuickConnect/Connect?secret=$encoded",
            method = "GET",
            headers = mapOf("X-Emby-Authorization" to mediaBrowserHeader("Android", http.clientUniqueId)),
        )
        if (resp.statusCode !in 200..299) {
            throw NavidromeApiException(resp.statusCode, "Quick Connect session expired")
        }
        val obj = MiniJson.parseObject(resp.body)
        return QuickConnectChallenge(
            code = obj.str("Code"),
            secret = obj.str("Secret", secret),
            authenticated = obj.bool("Authenticated", false),
            deviceName = obj.str("DeviceName"),
            appName = obj.str("AppName"),
        )
    }

    suspend fun lookupQuickConnectCode(account: ServerAccount, code: String): QuickConnectChallenge {
        val cleanCode = code.filterNot { it.isWhitespace() }
        val resp = http.request(
            account = account,
            url = "${account.normalizedBaseUrl}/api/quickconnect?code=${URLEncoder.encode(cleanCode, "UTF-8")}",
            method = "GET",
        )
        if (resp.statusCode !in 200..299) {
            throw NavidromeApiException(resp.statusCode, "Invalid or expired Quick Connect code")
        }
        val obj = MiniJson.parseObject(resp.body)
        return QuickConnectChallenge(
            code = cleanCode,
            secret = "",
            authenticated = false,
            deviceName = obj.str("deviceName"),
            appName = obj.str("appName"),
        )
    }

    suspend fun authorizeQuickConnectCode(account: ServerAccount, code: String): QuickConnectChallenge {
        val cleanCode = code.filterNot { it.isWhitespace() }
        val body = buildJsonObj { put("code", cleanCode) }.toJsonString().toByteArray(Charsets.UTF_8)
        val resp = http.request(
            account = account,
            url = "${account.normalizedBaseUrl}/api/quickconnect/authorize",
            method = "POST",
            bodyBytes = body,
            contentType = "application/json",
        )
        if (resp.statusCode !in 200..299) {
            throw NavidromeApiException(resp.statusCode, "Could not authorize Quick Connect code")
        }
        val obj = MiniJson.parseObject(resp.body)
        return QuickConnectChallenge(
            code = cleanCode,
            secret = "",
            authenticated = true,
            deviceName = obj.str("deviceName"),
            appName = obj.str("appName"),
        )
    }

    // --- Fast Paginated Native Catalog Sync (with ThumbHash & DominantColor) ---

    suspend fun fetchAlbumsPage(
        account: ServerAccount,
        start: Int = 0,
        end: Int = 500,
    ): Pair<List<AlbumExtraInfo>, Int> {
        val libraryParam = if (account.selectedMusicFolders.size == 1) {
            "&library_id=${account.selectedMusicFolders.first()}"
        } else ""
        val url = "${account.normalizedBaseUrl}/api/album?_start=$start&_end=$end&_sort=id&_order=ASC$libraryParam"
        val resp = http.request(account, url, method = "GET")
        if (resp.statusCode !in 200..299) {
            throw NavidromeApiException(resp.statusCode, "Native /api/album failed (${resp.statusCode})")
        }
        val total = resp.header("X-Total-Count")?.toIntOrNull() ?: 0
        val serverId = account.id.coerceAtLeast(1L)
        val items = MiniJson.parseArray(resp.body).objects().map { a ->
            val remoteId = a.str("id")
            val imageHash = a.strOrNull("imageHash")
            val imageAbsent = a.bool("imageAbsent", false)
            val coverArtId = if (imageAbsent || remoteId.isBlank()) null else {
                if (!imageHash.isNullOrBlank()) "al-${remoteId}_$imageHash" else "al-$remoteId"
            }
            val discsObj = a.obj("discs")
            val discTitles = discsObj?.fields?.entries?.mapNotNull { (k, v) ->
                val discNum = k.toIntOrNull() ?: return@mapNotNull null
                val title = v.asStringOrNull.orEmpty()
                DiscTitleInfo(disc = discNum, title = title, coverArtId = "dc-$remoteId:$discNum")
            }?.sortedBy { it.disc }.orEmpty()

            val tagsObj = a.obj("tags")
            val releaseTypes = tagsObj?.arr("releasetype")?.strings()
                ?.ifEmpty { listOfNotNull(a.strOrNull("mbzAlbumType")) }.orEmpty()
            val recordLabels = tagsObj?.arr("recordlabel")?.strings().orEmpty()
            val version = tagsObj?.arr("albumversion")?.strings()?.firstOrNull()

            val participantsObj = a.obj("participants")
            val participants = ArrayList<ParticipantCredit>()
            participantsObj?.fields?.forEach { (role, arrVal) ->
                arrVal.asArrayOrNull?.objects()?.forEach { p ->
                    participants += ParticipantCredit(
                        id = p.str("id"),
                        name = p.str("name"),
                        role = role,
                        subRole = p.strOrNull("subRole"),
                    )
                }
            }

            AlbumExtraInfo(
                albumId = RemoteIds.albumId(serverId, remoteId),
                serverId = serverId,
                remoteId = remoteId,
                coverArtId = coverArtId,
                thumbHash = a.strOrNull("thumbHash"),
                dominantColor = a.strOrNull("dominantColor"),
                releaseTypes = releaseTypes,
                version = version,
                recordLabels = recordLabels,
                catalogNum = a.strOrNull("catalogNum"),
                originalReleaseDate = a.strOrNull("originalDate"),
                releaseDate = a.strOrNull("releaseDate") ?: a.strOrNull("date"),
                isCompilation = a.bool("compilation", false),
                description = a.strOrNull("description")?.let(SubsonicApiClient::stripHtmlTags),
                mbzAlbumId = a.strOrNull("mbzAlbumId"),
                externalUrl = a.strOrNull("externalUrl"),
                userRating = a.int("rating", 0),
                averageRating = a.double("averageRating", 0.0),
                discTitles = discTitles,
                participants = participants,
            )
        }
        return items to total
    }

    suspend fun fetchSongsPage(
        account: ServerAccount,
        start: Int = 0,
        end: Int = 500,
    ): Pair<List<Song>, Int> {
        val libraryParam = if (account.selectedMusicFolders.size == 1) {
            "&library_id=${account.selectedMusicFolders.first()}"
        } else ""
        val url = "${account.normalizedBaseUrl}/api/song?_start=$start&_end=$end&_sort=id&_order=ASC$libraryParam"
        val resp = http.request(account, url, method = "GET")
        if (resp.statusCode !in 200..299) {
            throw NavidromeApiException(resp.statusCode, "Native /api/song failed (${resp.statusCode})")
        }
        val total = resp.header("X-Total-Count")?.toIntOrNull() ?: 0
        val serverId = account.id.coerceAtLeast(1L)
        val songs = MiniJson.parseArray(resp.body).objects().map { mf ->
            val remoteId = mf.str("id")
            val remoteAlbumId = mf.str("albumId").ifEmpty { "unknown:${mf.str("album")}" }
            val suffix = mf.str("suffix").lowercase()
            val codecRaw = mf.str("codec").lowercase().ifEmpty { suffix }
            val bitDepth = mf.int("bitDepth", 0)
            val sampleRate = mf.int("sampleRate", 0)
            val channels = mf.int("channels", 2).coerceAtLeast(1)
            val lossless = codecRaw in setOf("flac", "alac", "pcm", "wav", "aiff", "wv", "ape", "dsd") ||
                (suffix == "m4a" && bitDepth > 0)
            val codecDisplay = when (codecRaw) {
                "flac" -> "FLAC"
                "alac" -> "ALAC"
                "pcm", "wav" -> "WAV"
                "aiff", "aif" -> "AIFF"
                "opus" -> "Opus"
                "vorbis", "ogg" -> "Vorbis"
                "mp3" -> "MP3"
                "aac", "m4a" -> "AAC"
                "eac3" -> "E-AC-3"
                "ac3" -> "AC-3"
                else -> codecRaw.uppercase().ifEmpty { "Audio" }
            }
            val rg = ReplayGainInfo(
                trackGainDb = mf.doubleOrNull("rgTrackGain"),
                albumGainDb = mf.doubleOrNull("rgAlbumGain"),
                trackPeak = mf.doubleOrNull("rgTrackPeak"),
                albumPeak = mf.doubleOrNull("rgAlbumPeak"),
            ).takeUnless(ReplayGainInfo::isEmpty)
            val createdSec = SubsonicApiClient.parseIsoTimeMs(mf.strOrNull("createdAt")) / 1000L
            val updatedSec = SubsonicApiClient.parseIsoTimeMs(mf.strOrNull("updatedAt")) / 1000L
            val imageHash = mf.strOrNull("imageHash")
            val imageAbsent = mf.bool("imageAbsent", false)
            val coverArtId = if (imageAbsent) null else {
                if (!imageHash.isNullOrBlank()) "al-${remoteAlbumId}_$imageHash" else "al-$remoteAlbumId"
            }

            Song(
                id = RemoteIds.songId(serverId, remoteId),
                title = mf.str("title", "Untitled"),
                artist = mf.str("artist", "[Unknown Artist]"),
                albumArtist = mf.str("albumArtist").ifEmpty { mf.str("artist", "[Unknown Artist]") },
                album = mf.str("album", "[Unknown Album]"),
                albumId = RemoteIds.albumId(serverId, remoteAlbumId),
                durationMs = (mf.double("duration", 0.0) * 1000.0).toLong(),
                trackNumber = mf.int("trackNumber", 0),
                discNumber = mf.int("discNumber", 1).coerceAtLeast(1),
                dateAddedSec = if (createdSec > 0L) createdSec else System.currentTimeMillis() / 1000L,
                dateModifiedSec = if (updatedSec > 0L) updatedSec else 1L,
                genre = mf.strOrNull("genre"),
                year = mf.int("year", 0).takeIf { it > 0 } ?: mf.int("originalYear", 0),
                lossless = lossless,
                serverId = serverId,
                remoteId = remoteId,
                coverArtId = coverArtId,
                streamUri = subsonic.buildStreamUrl(account, remoteId),
                codec = codecDisplay,
                bitRateKbps = mf.int("bitRate", 0),
                sampleRateHz = sampleRate,
                bitDepth = bitDepth,
                channels = channels,
                userRating = mf.int("rating", 0),
                replayGain = rg,
                bookmarkPositionMs = mf.long("bookmarkPosition", 0L),
                explicitStatus = when (mf.strOrNull("explicitStatus")) {
                    "e", "explicit" -> "explicit"
                    "c", "clean" -> "clean"
                    else -> null
                },
                bpm = mf.int("bpm", 0),
                comment = mf.strOrNull("comment"),
                discSubtitle = mf.strOrNull("discSubtitle"),
            )
        }
        return songs to total
    }

    suspend fun fetchArtistsPage(
        account: ServerAccount,
        start: Int = 0,
        end: Int = 500,
    ): Pair<List<ArtistExtraInfo>, Int> {
        val libraryParam = if (account.selectedMusicFolders.size == 1) {
            "&library_id=${account.selectedMusicFolders.first()}"
        } else ""
        val url = "${account.normalizedBaseUrl}/api/artist?_start=$start&_end=$end&_sort=name&_order=ASC$libraryParam"
        val resp = http.request(account, url, method = "GET")
        if (resp.statusCode !in 200..299) {
            throw NavidromeApiException(resp.statusCode, "Native /api/artist failed (${resp.statusCode})")
        }
        val total = resp.header("X-Total-Count")?.toIntOrNull() ?: 0
        val serverId = account.id.coerceAtLeast(1L)
        val artists = MiniJson.parseArray(resp.body).objects().map { ar ->
            val remoteId = ar.str("id")
            val imageHash = ar.strOrNull("imageHash")
            val imageAbsent = ar.bool("imageAbsent", false)
            val coverArtId = if (imageAbsent || remoteId.isBlank()) null else {
                if (!imageHash.isNullOrBlank()) "ar-${remoteId}_$imageHash" else "ar-$remoteId"
            }
            val roles = ar.obj("stats")?.fields?.keys?.toList().orEmpty()
            ArtistExtraInfo(
                name = ar.str("name"),
                serverId = serverId,
                remoteId = remoteId,
                sortName = ar.strOrNull("sortArtistName") ?: ar.strOrNull("orderArtistName"),
                coverArtId = coverArtId,
                thumbHash = ar.strOrNull("thumbHash"),
                dominantColor = ar.strOrNull("dominantColor"),
                biography = ar.strOrNull("biography")?.let(SubsonicApiClient::stripHtmlTags),
                mbzArtistId = ar.strOrNull("mbzArtistId"),
                externalUrl = ar.strOrNull("externalUrl"),
                roles = roles,
                userRating = ar.int("rating", 0),
                averageRating = ar.double("averageRating", 0.0),
            )
        }
        return artists to total
    }

    // --- Native Playlists & Smart Playlists (.nsp) ---

    suspend fun getNativePlaylists(account: ServerAccount): List<NativePlaylistSummary> {
        val url = "${account.normalizedBaseUrl}/api/playlist?_start=0&_end=500&_sort=name&_order=ASC"
        val resp = http.request(account, url, method = "GET")
        if (resp.statusCode !in 200..299) return emptyList()
        return MiniJson.parseArray(resp.body).objects().map(::parseNativePlaylist)
    }

    suspend fun createSmartPlaylist(
        account: ServerAccount,
        name: String,
        comment: String = "",
        isPublic: Boolean = false,
        criteria: SmartPlaylistCriteria,
    ): NativePlaylistSummary {
        val rulesObj = MiniJson.parseObject(criteria.toJsonString())
        val payload = buildJsonObj {
            put("name", name)
            put("comment", comment)
            put("public", isPublic)
            put("rules", rulesObj)
        }.toJsonString().toByteArray(Charsets.UTF_8)
        val resp = http.request(
            account = account,
            url = "${account.normalizedBaseUrl}/api/playlist",
            method = "POST",
            bodyBytes = payload,
            contentType = "application/json",
        )
        if (resp.statusCode !in 200..299) {
            throw NavidromeApiException(resp.statusCode, "Could not create Smart Playlist (${resp.statusCode})")
        }
        return parseNativePlaylist(MiniJson.parseObject(resp.body))
    }

    suspend fun updateSmartPlaylist(
        account: ServerAccount,
        remoteId: String,
        name: String,
        comment: String = "",
        isPublic: Boolean = false,
        criteria: SmartPlaylistCriteria?,
    ): NativePlaylistSummary {
        val payload = buildJsonObj {
            put("id", remoteId)
            put("name", name)
            put("comment", comment)
            put("public", isPublic)
            if (criteria != null) {
                put("rules", MiniJson.parseObject(criteria.toJsonString()))
            }
        }.toJsonString().toByteArray(Charsets.UTF_8)
        val resp = http.request(
            account = account,
            url = "${account.normalizedBaseUrl}/api/playlist/$remoteId",
            method = "PUT",
            bodyBytes = payload,
            contentType = "application/json",
        )
        if (resp.statusCode !in 200..299) {
            throw NavidromeApiException(resp.statusCode, "Could not update playlist (${resp.statusCode})")
        }
        return parseNativePlaylist(MiniJson.parseObject(resp.body))
    }

    suspend fun reorderPlaylistTrack(
        account: ServerAccount,
        playlistRemoteId: String,
        trackOneBasedId: Int,
        insertBeforeOneBasedPos: Int,
    ) {
        val payload = buildJsonObj {
            put("insert_before", insertBeforeOneBasedPos.toString())
        }.toJsonString().toByteArray(Charsets.UTF_8)
        val resp = http.request(
            account = account,
            url = "${account.normalizedBaseUrl}/api/playlist/$playlistRemoteId/tracks/$trackOneBasedId",
            method = "PUT",
            bodyBytes = payload,
            contentType = "application/json",
        )
        if (resp.statusCode !in 200..299) {
            throw NavidromeApiException(resp.statusCode, "Failed to reorder track (${resp.statusCode})")
        }
    }

    suspend fun addToPlaylistBulk(
        account: ServerAccount,
        playlistRemoteId: String,
        songRemoteIds: List<String> = emptyList(),
        albumRemoteIds: List<String> = emptyList(),
        artistRemoteIds: List<String> = emptyList(),
    ): Int {
        val payload = buildJsonObj {
            if (songRemoteIds.isNotEmpty()) putStrings("ids", songRemoteIds)
            if (albumRemoteIds.isNotEmpty()) putStrings("albumIds", albumRemoteIds)
            if (artistRemoteIds.isNotEmpty()) putStrings("artistIds", artistRemoteIds)
        }.toJsonString().toByteArray(Charsets.UTF_8)
        val resp = http.request(
            account = account,
            url = "${account.normalizedBaseUrl}/api/playlist/$playlistRemoteId/tracks",
            method = "POST",
            bodyBytes = payload,
            contentType = "application/json",
        )
        if (resp.statusCode !in 200..299) {
            throw NavidromeApiException(resp.statusCode, "Failed to add items to playlist")
        }
        return MiniJson.parseObject(resp.body).int("added", 0)
    }

    suspend fun getSongPlaylists(account: ServerAccount, songRemoteId: String): List<NativePlaylistSummary> {
        val resp = http.request(
            account = account,
            url = "${account.normalizedBaseUrl}/api/song/$songRemoteId/playlists",
            method = "GET",
        )
        if (resp.statusCode !in 200..299) return emptyList()
        return MiniJson.parseArray(resp.body).objects().map(::parseNativePlaylist)
    }

    suspend fun importM3uPlaylist(account: ServerAccount, m3uContent: String): String {
        val resp = http.request(
            account = account,
            url = "${account.normalizedBaseUrl}/api/playlist",
            method = "POST",
            bodyBytes = m3uContent.toByteArray(Charsets.UTF_8),
            contentType = "audio/x-mpegurl",
        )
        if (resp.statusCode !in 200..299) {
            throw NavidromeApiException(resp.statusCode, "Failed to import M3U playlist")
        }
        return resp.body
    }

    suspend fun exportM3uPlaylist(account: ServerAccount, playlistRemoteId: String): String {
        val resp = http.request(
            account = account,
            url = "${account.normalizedBaseUrl}/api/playlist/$playlistRemoteId/tracks",
            method = "GET",
            headers = mapOf("Accept" to "audio/x-mpegurl"),
        )
        if (resp.statusCode !in 200..299) {
            throw NavidromeApiException(resp.statusCode, "Failed to export M3U playlist")
        }
        return resp.body
    }

    // --- Custom Image Upload / Delete & Metadata Refresh ---

    suspend fun uploadEntityImage(
        account: ServerAccount,
        entityType: String, // "artist", "playlist", or "radio"
        remoteId: String,
        imageBytes: ByteArray,
        filename: String = "cover.png",
        mimeType: String = "image/png",
    ): Boolean {
        val resp = http.uploadMultipartImage(
            account = account,
            url = "${account.normalizedBaseUrl}/api/$entityType/$remoteId/image",
            imageBytes = imageBytes,
            filename = filename,
            mimeType = mimeType,
        )
        return resp.statusCode in 200..299
    }

    suspend fun deleteEntityImage(
        account: ServerAccount,
        entityType: String, // "artist", "playlist", or "radio"
        remoteId: String,
    ): Boolean {
        val resp = http.request(
            account = account,
            url = "${account.normalizedBaseUrl}/api/$entityType/$remoteId/image",
            method = "DELETE",
        )
        return resp.statusCode in 200..299
    }

    suspend fun refreshMetadata(
        account: ServerAccount,
        kindPrefix: String, // "al" (album) or "ar" (artist)
        remoteId: String,
    ): Boolean {
        val resp = http.request(
            account = account,
            url = "${account.normalizedBaseUrl}/api/metadata/$kindPrefix/$remoteId/refresh",
            method = "POST",
            bodyBytes = ByteArray(0),
        )
        return resp.statusCode in 200..299
    }

    // --- Admin & Maintenance ---

    suspend fun getMissingFiles(account: ServerAccount, start: Int = 0, end: Int = 100): List<MissingFileItem> {
        val resp = http.request(
            account = account,
            url = "${account.normalizedBaseUrl}/api/missing?_start=$start&_end=$end&_sort=path&_order=ASC",
            method = "GET",
        )
        if (resp.statusCode !in 200..299) return emptyList()
        return MiniJson.parseArray(resp.body).objects().map { m ->
            MissingFileItem(
                id = m.str("id"),
                title = m.str("title"),
                artist = m.str("artist"),
                album = m.str("album"),
                path = m.str("path"),
                size = m.long("size", 0L),
            )
        }
    }

    suspend fun deleteMissingFiles(account: ServerAccount, ids: List<String> = emptyList()): Boolean {
        val query = if (ids.isEmpty()) "" else "?" + ids.joinToString("&") { "id=${URLEncoder.encode(it, "UTF-8")}" }
        val resp = http.request(
            account = account,
            url = "${account.normalizedBaseUrl}/api/missing$query",
            method = "DELETE",
        )
        return resp.statusCode in 200..299
    }

    suspend fun inspectMediaFile(account: ServerAccount, songRemoteId: String): JsonObj? {
        val resp = http.request(
            account = account,
            url = "${account.normalizedBaseUrl}/api/inspect?id=${URLEncoder.encode(songRemoteId, "UTF-8")}",
            method = "GET",
        )
        if (resp.statusCode !in 200..299) return null
        return MiniJson.parseObject(resp.body)
    }

    suspend fun keepAlive(account: ServerAccount): Boolean {
        val resp = http.request(
            account = account,
            url = "${account.normalizedBaseUrl}/api/keepalive/harmony",
            method = "GET",
        )
        return resp.statusCode in 200..299
    }

    private fun parseNativePlaylist(p: JsonObj): NativePlaylistSummary {
        val remoteId = p.str("id")
        val rulesObj = p.obj("rules")
        val isSmart = rulesObj != null && (rulesObj["all"] != null || rulesObj["any"] != null || rulesObj["expression"] != null)
        val imageHash = p.strOrNull("imageHash")
        val imageAbsent = p.bool("imageAbsent", false)
        val coverArtId = if (imageAbsent || remoteId.isBlank()) null else {
            if (!imageHash.isNullOrBlank()) "pl-${remoteId}_$imageHash" else "pl-$remoteId"
        }
        return NativePlaylistSummary(
            remoteId = remoteId,
            name = p.str("name", "Playlist"),
            comment = p.strOrNull("comment"),
            ownerName = p.strOrNull("ownerName"),
            isPublic = p.bool("public", false),
            songCount = p.int("songCount", 0),
            durationSec = p.double("duration", 0.0).toFloat(),
            isSmart = isSmart,
            rulesJson = rulesObj?.toJsonString(),
            evaluatedAtMs = p.strOrNull("evaluatedAt")?.let(SubsonicApiClient::parseIsoTimeMs)?.takeIf { it > 0L },
            coverArtId = coverArtId,
            thumbHash = p.strOrNull("thumbHash"),
            dominantColor = p.strOrNull("dominantColor"),
            userRating = p.int("rating", 0),
            starred = p.bool("starred", false),
            updatedAtMs = SubsonicApiClient.parseIsoTimeMs(p.strOrNull("updatedAt")),
        )
    }
}
