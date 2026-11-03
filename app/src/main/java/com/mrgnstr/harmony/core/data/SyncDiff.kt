package com.mrgnstr.harmony.core.data

import com.mrgnstr.harmony.core.database.SongStamp
import com.mrgnstr.harmony.core.model.Song

internal data class SyncDiff(val upserts: List<Song>, val deletedIds: List<Long>) {
    val isEmpty get() = upserts.isEmpty() && deletedIds.isEmpty()

    companion object {
        /** New or changed rows (by modified time, genre, year or format) to write, and ids that vanished from MediaStore. */
        fun compute(existing: List<SongStamp>, scanned: List<Song>): SyncDiff {
            val byId = existing.associateBy { it.id }
            val upserts = scanned.filter {
                val old = byId[it.id]
                old == null || old.dateModifiedSec != it.dateModifiedSec || old.genre != it.genre || old.year != it.year || old.lossless != it.lossless
            }
            val scannedIds = HashSet<Long>(scanned.size * 2).also { set -> scanned.forEach { set += it.id } }
            val deleted = existing.mapNotNull { if (it.id in scannedIds) null else it.id }
            return SyncDiff(upserts, deleted)
        }
    }
}
