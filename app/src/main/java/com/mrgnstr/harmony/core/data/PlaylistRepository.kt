package com.mrgnstr.harmony.core.data

import androidx.room.withTransaction
import com.mrgnstr.harmony.core.database.HarmonyDatabase
import com.mrgnstr.harmony.core.database.PlaylistDao
import com.mrgnstr.harmony.core.database.PlaylistEntity
import com.mrgnstr.harmony.core.database.PlaylistSongEntity
import com.mrgnstr.harmony.core.database.toModel
import com.mrgnstr.harmony.core.model.ArtRef
import com.mrgnstr.harmony.core.model.FAVOURITES_PLAYLIST_ID
import com.mrgnstr.harmony.core.model.Playlist
import com.mrgnstr.harmony.core.model.PlaylistEntry
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map

@Singleton
class PlaylistRepository @Inject constructor(
    private val db: HarmonyDatabase,
    private val dao: PlaylistDao,
    private val covers: PlaylistCoverStore,
    private val favourites: FavouritesRepository,
) {
    val playlists: Flow<List<Playlist>> = combine(dao.observePlaylists(), dao.observeMembers()) { playlists, members ->
        val byPlaylist = members.groupBy { it.playlistId }
        playlists.map { p ->
            val rows = byPlaylist[p.id].orEmpty()
            Playlist(
                id = p.id, name = p.name, coverPath = p.coverPath, songCount = rows.size,
                durationMs = rows.sumOf { it.durationMs }, updatedAtMs = p.updatedAtMs,
                // The generated cover uses the first four songs that have different album covers.
                art = rows.distinctBy { it.albumId }.take(4).map { ArtRef(it.songId, it.albumId) },
            )
        }
    }.flowOn(Dispatchers.Default)

    /** Every playlist for the Playlists page: the built-in Favourites first, then the user's own. */
    val playlistsWithFavourites: Flow<List<Playlist>> = combine(favourites.playlist, playlists) { fav, own -> listOf(fav) + own }

    fun playlist(id: Long): Flow<Playlist?> =
        if (id == FAVOURITES_PLAYLIST_ID) favourites.playlist else playlists.map { list -> list.firstOrNull { it.id == id } }

    fun entries(playlistId: Long): Flow<List<PlaylistEntry>> =
        if (playlistId == FAVOURITES_PLAYLIST_ID) favourites.entries else dao.observeEntries(playlistId).map { rows -> rows.map { PlaylistEntry(it.entryId, it.song.toModel()) } }.flowOn(Dispatchers.Default)

    /** @param coverUri an image the user picked, copied into app storage; null keeps the generated cover. */
    suspend fun create(name: String, coverUri: String? = null, songIds: List<Long> = emptyList()): Long {
        val coverPath = coverUri?.let { covers.save(it) }
        val now = System.currentTimeMillis()
        return db.withTransaction {
            val id = dao.insert(PlaylistEntity(name = name.trim(), coverPath = coverPath, createdAtMs = now, updatedAtMs = now))
            if (songIds.isNotEmpty()) dao.insertSongs(songIds.mapIndexed { i, s -> PlaylistSongEntity(playlistId = id, songId = s, position = i, addedAtMs = now) })
            id
        }
    }

    suspend fun addSongs(playlistId: Long, songIds: List<Long>) {
        if (songIds.isEmpty()) return
        val now = System.currentTimeMillis()
        db.withTransaction {
            val start = (dao.lastPosition(playlistId) ?: -1) + 1
            dao.insertSongs(songIds.mapIndexed { i, s -> PlaylistSongEntity(playlistId = playlistId, songId = s, position = start + i, addedAtMs = now) })
            dao.touch(playlistId, now)
        }
    }

    suspend fun removeEntry(playlistId: Long, entryId: Long) {
        db.withTransaction {
            dao.deleteEntry(entryId)
            dao.touch(playlistId, System.currentTimeMillis())
        }
    }

    /** Renames, and when [newCoverUri] is given replaces the cover; [clearCover] goes back to the generated one. */
    suspend fun edit(id: Long, name: String, newCoverUri: String? = null, clearCover: Boolean = false) {
        val now = System.currentTimeMillis()
        val old = dao.coverPath(id)
        val saved = newCoverUri?.let { covers.save(it) }
        db.withTransaction {
            dao.rename(id, name.trim(), now)
            if (saved != null) dao.setCover(id, saved, now) else if (clearCover) dao.setCover(id, null, now)
        }
        if (saved != null || clearCover) covers.delete(old)
    }

    suspend fun delete(id: Long) {
        val cover = dao.coverPath(id)
        dao.delete(id)
        covers.delete(cover)
    }
}
