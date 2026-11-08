package com.mrgnstr.harmony.core.database

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.mrgnstr.harmony.core.model.AlbumExtraInfo
import com.mrgnstr.harmony.core.model.ArtistExtraInfo
import com.mrgnstr.harmony.core.model.AudioBookmark
import com.mrgnstr.harmony.core.model.AuthMethod
import com.mrgnstr.harmony.core.model.DiscTitleInfo
import com.mrgnstr.harmony.core.model.ParticipantCredit
import com.mrgnstr.harmony.core.model.RadioStation
import com.mrgnstr.harmony.core.model.ServerAccount
import com.mrgnstr.harmony.core.model.SimilarArtistRef
import com.mrgnstr.harmony.core.network.JsonArr
import com.mrgnstr.harmony.core.network.JsonStr
import com.mrgnstr.harmony.core.network.MiniJson
import com.mrgnstr.harmony.core.network.buildJsonObj

@Entity(tableName = "server_accounts")
data class ServerAccountEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val name: String,
    val baseUrl: String,
    val username: String,
    val authMethod: String,
    val passwordOrToken: String,
    val subsonicSalt: String,
    val subsonicToken: String,
    val apiKey: String?,
    val jwtToken: String?,
    val serverType: String,
    val serverVersion: String,
    val openSubsonic: Boolean,
    val extensionsCsv: String,
    val selectedFoldersCsv: String,
    val isAdmin: Boolean,
    val downloadRole: Boolean,
    val shareRole: Boolean,
    val jukeboxRole: Boolean,
    val scrobblingEnabled: Boolean,
    val allowSelfSignedTls: Boolean,
    val active: Boolean,
    val lastSyncMs: Long,
)

fun ServerAccountEntity.toModel() = ServerAccount(
    id = id,
    name = name,
    baseUrl = baseUrl,
    username = username,
    authMethod = AuthMethod.entries.firstOrNull { it.name == authMethod } ?: AuthMethod.Password,
    passwordOrToken = passwordOrToken,
    subsonicSalt = subsonicSalt,
    subsonicToken = subsonicToken,
    apiKey = apiKey,
    jwtToken = jwtToken,
    serverType = serverType,
    serverVersion = serverVersion,
    openSubsonic = openSubsonic,
    extensions = extensionsCsv.split(',').map { it.trim() }.filter { it.isNotEmpty() }.toSet(),
    selectedMusicFolders = selectedFoldersCsv.split(',').mapNotNull { it.trim().toIntOrNull() }.toSet(),
    isAdmin = isAdmin,
    downloadRole = downloadRole,
    shareRole = shareRole,
    jukeboxRole = jukeboxRole,
    scrobblingEnabled = scrobblingEnabled,
    allowSelfSignedTls = allowSelfSignedTls,
    active = active,
    lastSyncMs = lastSyncMs,
)

fun ServerAccount.toEntity() = ServerAccountEntity(
    id = id,
    name = name,
    baseUrl = baseUrl,
    username = username,
    authMethod = authMethod.name,
    passwordOrToken = passwordOrToken,
    subsonicSalt = subsonicSalt,
    subsonicToken = subsonicToken,
    apiKey = apiKey,
    jwtToken = jwtToken,
    serverType = serverType,
    serverVersion = serverVersion,
    openSubsonic = openSubsonic,
    extensionsCsv = extensions.joinToString(","),
    selectedFoldersCsv = selectedMusicFolders.joinToString(","),
    isAdmin = isAdmin,
    downloadRole = downloadRole,
    shareRole = shareRole,
    jukeboxRole = jukeboxRole,
    scrobblingEnabled = scrobblingEnabled,
    allowSelfSignedTls = allowSelfSignedTls,
    active = active,
    lastSyncMs = lastSyncMs,
)

@Entity(tableName = "remote_albums", indices = [Index("serverId"), Index("remoteId")])
data class RemoteAlbumEntity(
    @PrimaryKey val albumId: Long,
    val serverId: Long,
    val remoteId: String,
    val coverArtId: String?,
    val thumbHash: String?,
    val dominantColor: String?,
    val releaseTypesJson: String,
    val version: String?,
    val recordLabelsJson: String,
    val catalogNum: String?,
    val originalReleaseDate: String?,
    val releaseDate: String?,
    val isCompilation: Boolean,
    val description: String?,
    val mbzAlbumId: String?,
    val externalUrl: String?,
    val userRating: Int,
    val averageRating: Double,
    val discTitlesJson: String,
    val participantsJson: String,
)

fun AlbumExtraInfo.toEntity(): RemoteAlbumEntity = RemoteAlbumEntity(
    albumId = albumId,
    serverId = serverId,
    remoteId = remoteId,
    coverArtId = coverArtId,
    thumbHash = thumbHash,
    dominantColor = dominantColor,
    releaseTypesJson = JsonArr(releaseTypes.map(::JsonStr)).toJsonString(),
    version = version,
    recordLabelsJson = JsonArr(recordLabels.map(::JsonStr)).toJsonString(),
    catalogNum = catalogNum,
    originalReleaseDate = originalReleaseDate,
    releaseDate = releaseDate,
    isCompilation = isCompilation,
    description = description,
    mbzAlbumId = mbzAlbumId,
    externalUrl = externalUrl,
    userRating = userRating,
    averageRating = averageRating,
    discTitlesJson = JsonArr(
        discTitles.map { d ->
            buildJsonObj {
                put("disc", d.disc)
                put("title", d.title)
                put("coverArtId", d.coverArtId)
            }
        },
    ).toJsonString(),
    participantsJson = JsonArr(
        participants.map { p ->
            buildJsonObj {
                put("id", p.id)
                put("name", p.name)
                put("role", p.role)
                put("subRole", p.subRole)
            }
        },
    ).toJsonString(),
)

fun RemoteAlbumEntity.toModel(): AlbumExtraInfo = AlbumExtraInfo(
    albumId = albumId,
    serverId = serverId,
    remoteId = remoteId,
    coverArtId = coverArtId,
    thumbHash = thumbHash,
    dominantColor = dominantColor,
    releaseTypes = runCatching { MiniJson.parseArray(releaseTypesJson).strings() }.getOrDefault(emptyList()),
    version = version,
    recordLabels = runCatching { MiniJson.parseArray(recordLabelsJson).strings() }.getOrDefault(emptyList()),
    catalogNum = catalogNum,
    originalReleaseDate = originalReleaseDate,
    releaseDate = releaseDate,
    isCompilation = isCompilation,
    description = description,
    mbzAlbumId = mbzAlbumId,
    externalUrl = externalUrl,
    userRating = userRating,
    averageRating = averageRating,
    discTitles = runCatching {
        MiniJson.parseArray(discTitlesJson).objects().map {
            DiscTitleInfo(it.int("disc", 1), it.str("title"), it.strOrNull("coverArtId"))
        }
    }.getOrDefault(emptyList()),
    participants = runCatching {
        MiniJson.parseArray(participantsJson).objects().map {
            ParticipantCredit(it.str("id"), it.str("name"), it.str("role"), it.strOrNull("subRole"))
        }
    }.getOrDefault(emptyList()),
)

@Entity(tableName = "remote_artists", indices = [Index("name"), Index("serverId")])
data class RemoteArtistEntity(
    @PrimaryKey val remoteId: String,
    val serverId: Long,
    val name: String,
    val sortName: String?,
    val coverArtId: String?,
    val thumbHash: String?,
    val dominantColor: String?,
    val biography: String?,
    val mbzArtistId: String?,
    val externalUrl: String?,
    val rolesJson: String,
    val similarArtistsJson: String,
    val userRating: Int,
    val averageRating: Double,
)

fun ArtistExtraInfo.toEntity(): RemoteArtistEntity = RemoteArtistEntity(
    remoteId = remoteId,
    serverId = serverId,
    name = name,
    sortName = sortName,
    coverArtId = coverArtId,
    thumbHash = thumbHash,
    dominantColor = dominantColor,
    biography = biography,
    mbzArtistId = mbzArtistId,
    externalUrl = externalUrl,
    rolesJson = JsonArr(roles.map(::JsonStr)).toJsonString(),
    similarArtistsJson = JsonArr(
        similarArtists.map { s ->
            buildJsonObj {
                put("id", s.id)
                put("name", s.name)
                put("coverArtId", s.coverArtId)
                put("albumCount", s.albumCount)
                put("imageUrl", s.imageUrl)
            }
        },
    ).toJsonString(),
    userRating = userRating,
    averageRating = averageRating,
)

fun RemoteArtistEntity.toModel(): ArtistExtraInfo = ArtistExtraInfo(
    name = name,
    serverId = serverId,
    remoteId = remoteId,
    sortName = sortName,
    coverArtId = coverArtId,
    thumbHash = thumbHash,
    dominantColor = dominantColor,
    biography = biography,
    mbzArtistId = mbzArtistId,
    externalUrl = externalUrl,
    roles = runCatching { MiniJson.parseArray(rolesJson).strings() }.getOrDefault(emptyList()),
    similarArtists = runCatching {
        MiniJson.parseArray(similarArtistsJson).objects().map {
            SimilarArtistRef(it.str("id"), it.str("name"), it.strOrNull("coverArtId"), it.int("albumCount"), it.strOrNull("imageUrl"))
        }
    }.getOrDefault(emptyList()),
    userRating = userRating,
    averageRating = averageRating,
)

@Entity(tableName = "radio_stations", indices = [Index("serverId")])
data class RadioStationEntity(
    @PrimaryKey val id: Long,
    val serverId: Long,
    val remoteId: String,
    val name: String,
    val streamUrl: String,
    val homePageUrl: String?,
    val coverArtId: String?,
)

fun RadioStation.toEntity() = RadioStationEntity(id, serverId, remoteId, name, streamUrl, homePageUrl, coverArtId)
fun RadioStationEntity.toModel() = RadioStation(id, serverId, remoteId, name, streamUrl, homePageUrl, coverArtId)

@Entity(tableName = "audio_bookmarks", indices = [Index("serverId")])
data class AudioBookmarkEntity(
    @PrimaryKey val songId: Long,
    val serverId: Long,
    val remoteSongId: String,
    val songTitle: String,
    val artist: String,
    val albumId: Long,
    val positionMs: Long,
    val comment: String,
    val createdMs: Long,
    val changedMs: Long,
)

fun AudioBookmark.toEntity(serverId: Long) = AudioBookmarkEntity(
    songId = songId,
    serverId = serverId,
    remoteSongId = remoteSongId,
    songTitle = songTitle,
    artist = artist,
    albumId = albumId,
    positionMs = positionMs,
    comment = comment,
    createdMs = createdMs,
    changedMs = changedMs,
)

fun AudioBookmarkEntity.toModel() = AudioBookmark(
    songId = songId,
    remoteSongId = remoteSongId,
    songTitle = songTitle,
    artist = artist,
    albumId = albumId,
    positionMs = positionMs,
    comment = comment,
    createdMs = createdMs,
    changedMs = changedMs,
)

@Entity(tableName = "pending_mutations", indices = [Index("serverId")])
data class PendingMutationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val serverId: Long,
    val type: String,
    val targetId: String,
    val targetKind: String,
    val payloadJson: String,
    val createdAtMs: Long,
) {
    companion object {
        const val TYPE_STAR = "STAR"
        const val TYPE_UNSTAR = "UNSTAR"
        const val TYPE_RATING = "RATING"
        const val TYPE_SCROBBLE = "SCROBBLE"
        /** targetId = local playlist id: make the server copy match it (create it first when it has no remoteId). */
        const val TYPE_PLAYLIST_PUSH = "PLAYLIST_PUSH"
        /** targetId = the playlist's remote id. */
        const val TYPE_PLAYLIST_DELETE = "PLAYLIST_DELETE"
        const val TYPE_BOOKMARK_CREATE = "BOOKMARK_CREATE"
        const val TYPE_BOOKMARK_DELETE = "BOOKMARK_DELETE"
    }
}
