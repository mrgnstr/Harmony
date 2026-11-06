package com.mrgnstr.harmony.core.database

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.mrgnstr.harmony.core.model.ReplayGainInfo
import com.mrgnstr.harmony.core.model.Song

/** Index of one MediaStore or Navidrome audio row. [id] is positive for MediaStore, negative for Navidrome. */
@Entity(tableName = "songs", indices = [Index("albumId"), Index("artist"), Index("album"), Index("serverId")])
data class SongEntity(
    @PrimaryKey val id: Long,
    val title: String,
    val artist: String,
    val albumArtist: String,
    val album: String,
    val albumId: Long,
    val durationMs: Long,
    val trackNumber: Int,
    val discNumber: Int,
    val dateAddedSec: Long,
    val dateModifiedSec: Long,
    val genre: String?,
    @ColumnInfo(defaultValue = "0") val year: Int = 0,
    @ColumnInfo(defaultValue = "0") val lossless: Boolean = false,
    @ColumnInfo(defaultValue = "0") val serverId: Long = 0L,
    val remoteId: String? = null,
    val coverArtId: String? = null,
    val streamUri: String? = null,
    val codec: String? = null,
    @ColumnInfo(defaultValue = "0") val bitRateKbps: Int = 0,
    @ColumnInfo(defaultValue = "0") val sampleRateHz: Int = 0,
    @ColumnInfo(defaultValue = "0") val bitDepth: Int = 0,
    @ColumnInfo(defaultValue = "2") val channels: Int = 2,
    @ColumnInfo(defaultValue = "0") val userRating: Int = 0,
    val rgTrackGain: Double? = null,
    val rgTrackPeak: Double? = null,
    val rgAlbumGain: Double? = null,
    val rgAlbumPeak: Double? = null,
    @ColumnInfo(defaultValue = "0") val bookmarkPositionMs: Long = 0L,
    val explicitStatus: String? = null,
    @ColumnInfo(defaultValue = "0") val bpm: Int = 0,
    val comment: String? = null,
    val discSubtitle: String? = null,
    val downloadedPath: String? = null,
)

/** What sync compares to decide whether a row changed. */
data class SongStamp(val id: Long, val dateModifiedSec: Long, val genre: String?, val year: Int = 0, val lossless: Boolean = false)

fun SongEntity.toModel() = Song(
    id = id,
    title = title,
    artist = artist,
    albumArtist = albumArtist,
    album = album,
    albumId = albumId,
    durationMs = durationMs,
    trackNumber = trackNumber,
    discNumber = discNumber,
    dateAddedSec = dateAddedSec,
    dateModifiedSec = dateModifiedSec,
    genre = genre,
    year = year,
    lossless = lossless,
    serverId = serverId,
    remoteId = remoteId,
    coverArtId = coverArtId,
    streamUri = streamUri,
    codec = codec,
    bitRateKbps = bitRateKbps,
    sampleRateHz = sampleRateHz,
    bitDepth = bitDepth,
    channels = channels,
    userRating = userRating,
    replayGain = if (rgTrackGain != null || rgAlbumGain != null || rgTrackPeak != null || rgAlbumPeak != null) {
        ReplayGainInfo(
            trackGainDb = rgTrackGain,
            albumGainDb = rgAlbumGain,
            trackPeak = rgTrackPeak,
            albumPeak = rgAlbumPeak,
        )
    } else null,
    bookmarkPositionMs = bookmarkPositionMs,
    explicitStatus = explicitStatus,
    bpm = bpm,
    comment = comment,
    discSubtitle = discSubtitle,
    downloadedPath = downloadedPath,
)

fun Song.toEntity() = SongEntity(
    id = id,
    title = title,
    artist = artist,
    albumArtist = albumArtist,
    album = album,
    albumId = albumId,
    durationMs = durationMs,
    trackNumber = trackNumber,
    discNumber = discNumber,
    dateAddedSec = dateAddedSec,
    dateModifiedSec = dateModifiedSec,
    genre = genre,
    year = year,
    lossless = lossless,
    serverId = serverId,
    remoteId = remoteId,
    coverArtId = coverArtId,
    streamUri = streamUri,
    codec = codec,
    bitRateKbps = bitRateKbps,
    sampleRateHz = sampleRateHz,
    bitDepth = bitDepth,
    channels = channels,
    userRating = userRating,
    rgTrackGain = replayGain?.trackGainDb,
    rgTrackPeak = replayGain?.trackPeak,
    rgAlbumGain = replayGain?.albumGainDb,
    rgAlbumPeak = replayGain?.albumPeak,
    bookmarkPositionMs = bookmarkPositionMs,
    explicitStatus = explicitStatus,
    bpm = bpm,
    comment = comment,
    discSubtitle = discSubtitle,
    downloadedPath = downloadedPath,
)
