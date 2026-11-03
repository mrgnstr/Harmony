package com.mrgnstr.harmony

import android.content.Context
import android.os.Build
import android.util.Log
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mrgnstr.harmony.core.datastore.SettingsStore
import com.mrgnstr.harmony.core.data.LibrarySync
import com.mrgnstr.harmony.core.database.HarmonyDatabase
import com.mrgnstr.harmony.core.mediastore.MediaStoreObserver
import com.mrgnstr.harmony.core.mediastore.MediaStoreScanner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Runs against the device's real MediaStore (needs the app's audio permission already granted). */
@RunWith(AndroidJUnit4::class)
class LibrarySyncDeviceTest {
    private lateinit var ctx: Context
    private lateinit var db: HarmonyDatabase
    private lateinit var scanner: MediaStoreScanner
    private lateinit var sync: LibrarySync

    @Before fun setUp() {
        ctx = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(ctx, HarmonyDatabase::class.java).build()
        scanner = MediaStoreScanner(ctx)
        sync = LibrarySync(scanner, MediaStoreObserver(ctx), db, db.songDao(), SettingsStore(ctx), CoroutineScope(SupervisorJob() + Dispatchers.Default))
    }

    @After fun tearDown() = db.close()

    @Test fun scanReportsGenres() = runBlocking {
        val songs = scanner.scan()
        val withGenre = songs.filter { it.genre != null }
        val top = withGenre.groupingBy { it.genre!! }.eachCount().entries.sortedByDescending { it.value }.take(8)
        Log.i("GenreCheck", "sdk=${Build.VERSION.SDK_INT} songs=${songs.size} withGenre=${withGenre.size} top=$top")
        assertTrue("library is empty, nothing to verify", songs.isNotEmpty())
        assertTrue("no song has a genre", withGenre.isNotEmpty())
        assertTrue("blank genre leaked", withGenre.none { it.genre!!.isBlank() || it.genre == "<unknown>" })
    }

    @Test fun scannerFiltersDurationAndFoldersWithoutLosingFolderChoices() = runBlocking {
        val folders = scanner.musicFolders()
        assertTrue("no music folders available", folders.isNotEmpty())
        val all = scanner.scan(minDurationMs = 0L)
        assertTrue(all.isNotEmpty())
        assertEquals(all.filter { it.durationMs >= 60_000L }.map { it.id }, scanner.scan(minDurationMs = 60_000L).map { it.id })
        assertTrue(scanner.scan(minDurationMs = Long.MAX_VALUE).isEmpty())
        assertTrue(scanner.scan(minDurationMs = 0L, excludedFolders = folders.toSet()).isEmpty())
        assertEquals(folders, scanner.musicFolders())
        assertEquals(all.map { it.id }, scanner.scan(minDurationMs = 0L).map { it.id })
    }

    @Test fun syncMirrorsMediaStoreAndSecondSyncIsNoOp() = runBlocking {
        val filters = SettingsStore(ctx).libraryFilters.first()
        val scanned = scanner.scan(filters.minimumDurationMs, filters.excludedFolders)
        sync.syncNow()
        val rows = db.songDao().observeAll().first()
        assertEquals(scanned.size, rows.size)
        assertEquals(scanned.map { it.id }.toSet(), rows.map { it.id }.toSet())
        assertEquals(scanned.associate { it.id to it.genre }, rows.associate { it.id to it.genre })
        assertTrue(sync.state.value.completedOnce)

        // Corrupt one row and drop another: sync must repair both and leave the rest alone.
        val victim = rows.first()
        db.songDao().upsert(listOf(victim.copy(dateModifiedSec = -1, genre = "zzz")))
        db.songDao().deleteByIds(listOf(rows.last().id))
        sync.syncNow()
        val after = db.songDao().observeAll().first()
        assertEquals(scanned.size, after.size)
        assertEquals(victim.dateModifiedSec, after.first { it.id == victim.id }.dateModifiedSec)
        assertEquals(victim.genre, after.first { it.id == victim.id }.genre)
    }
}
