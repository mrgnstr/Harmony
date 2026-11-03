package com.mrgnstr.harmony

import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mrgnstr.harmony.core.database.HarmonyDatabase
import com.mrgnstr.harmony.core.database.MIGRATION_10_11
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/** v10 → v11 seeds play counts from the history; new plays count up and outlive the history's pruning. */
@RunWith(AndroidJUnit4::class)
class PlayCountDeviceTest {
    @Test
    fun migrationSeedsCountsAndRecordingBumpsThem() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val name = "play-count-test.db"
        context.deleteDatabase(name)
        val path = context.getDatabasePath(name)
        path.parentFile!!.mkdirs()
        try {
            val json = InstrumentationRegistry.getInstrumentation().context.assets
                .open("com.mrgnstr.harmony.core.database.HarmonyDatabase/10.json")
                .bufferedReader().use { JSONObject(it.readText()).getJSONObject("database") }
            SQLiteDatabase.openOrCreateDatabase(path, null).use { old ->
                val entities = json.getJSONArray("entities")
                for (i in 0 until entities.length()) {
                    val entity = entities.getJSONObject(i)
                    val table = entity.getString("tableName")
                    old.execSQL(entity.getString("createSql").replace("\${TABLE_NAME}", table))
                    val indices = entity.optJSONArray("indices")
                    if (indices != null) for (j in 0 until indices.length()) {
                        old.execSQL(indices.getJSONObject(j).getString("createSql").replace("\${TABLE_NAME}", table))
                    }
                }
                val setup = json.getJSONArray("setupQueries")
                for (i in 0 until setup.length()) old.execSQL(setup.getString(i))
                old.execSQL("INSERT INTO history (songId, playedAtMs) VALUES (1, 10), (2, 20), (1, 30)")
                old.version = 10
            }
            val db = Room.databaseBuilder(context, HarmonyDatabase::class.java, name).addMigrations(MIGRATION_10_11).build()
            try {
                val dao = db.historyDao()
                assertEquals(mapOf(1L to 2, 2L to 1), dao.observePlayCounts().first().associate { it.songId to it.count })
                dao.record(2, 40, keep = 1)
                dao.record(3, 50, keep = 1)
                assertEquals(mapOf(1L to 2, 2L to 2, 3L to 1), dao.observePlayCounts().first().associate { it.songId to it.count })

            } finally {
                db.close()
            }
        } finally {
            context.deleteDatabase(name)
        }
    }
}
