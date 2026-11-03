package com.mrgnstr.harmony.core.data

import com.mrgnstr.harmony.core.database.SongStamp
import com.mrgnstr.harmony.core.model.Song
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncDiffTest {
    private fun song(id: Long, modified: Long = 1, genre: String? = null) = Song(
        id, "t$id", "a", "a", "al", 1, 1000, 1, 1, 1, modified, genre,
    )

    @Test fun firstSyncInsertsEverything() {
        val d = SyncDiff.compute(emptyList(), listOf(song(1), song(2)))
        assertEquals(listOf(1L, 2L), d.upserts.map { it.id })
        assertTrue(d.deletedIds.isEmpty())
    }

    @Test fun unchangedLibraryIsEmptyDiff() {
        val d = SyncDiff.compute(listOf(SongStamp(1, 1, null)), listOf(song(1)))
        assertTrue(d.isEmpty)
    }

    @Test fun modifiedTimeOrGenreChangeIsUpserted() {
        val existing = listOf(SongStamp(1, 1, null), SongStamp(2, 1, "Pop"), SongStamp(3, 1, null))
        val d = SyncDiff.compute(existing, listOf(song(1, modified = 5), song(2, genre = "Rock"), song(3)))
        assertEquals(listOf(1L, 2L), d.upserts.map { it.id })
        assertTrue(d.deletedIds.isEmpty())
    }

    @Test fun yearChangeIsUpserted() {
        val d = SyncDiff.compute(listOf(SongStamp(1, 1, null, 0)), listOf(song(1).copy(year = 2026)))
        assertEquals(listOf(1L), d.upserts.map { it.id })
    }

    @Test fun vanishedSongsAreDeleted() {
        val d = SyncDiff.compute(listOf(SongStamp(1, 1, null), SongStamp(2, 1, null)), listOf(song(2)))
        assertEquals(listOf(1L), d.deletedIds)
        assertTrue(d.upserts.isEmpty())
    }
}
