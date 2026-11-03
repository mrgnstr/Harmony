package com.mrgnstr.harmony.core.database

import androidx.room.Entity
import androidx.room.Fts4
import androidx.room.FtsOptions
import androidx.room.PrimaryKey

/** Room maintains this index with triggers on songs, including edits and library exclusions. */
@Fts4(contentEntity = SongEntity::class, tokenizer = FtsOptions.TOKENIZER_UNICODE61)
@Entity(tableName = "songs_fts")
data class SongSearchEntity(val title: String, val artist: String, val album: String, val albumArtist: String)

@Entity(tableName = "recent_searches")
data class RecentSearchEntity(@PrimaryKey val key: String, val query: String, val usedAtMs: Long)
