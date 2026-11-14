package com.mrgnstr.harmony

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mrgnstr.harmony.core.artwork.ArtworkColorStore
import com.mrgnstr.harmony.core.data.NavidromeRepository
import com.mrgnstr.harmony.core.database.HarmonyDatabase
import com.mrgnstr.harmony.core.datastore.SettingsStore
import com.mrgnstr.harmony.core.device.NetworkMonitor
import com.mrgnstr.harmony.core.model.AuthMethod
import com.mrgnstr.harmony.core.model.RemoteIds
import com.mrgnstr.harmony.core.model.SearchQuery
import com.mrgnstr.harmony.core.network.NavidromeHttpClient
import com.mrgnstr.harmony.core.network.NavidromeNativeClient
import com.mrgnstr.harmony.core.network.NavidromeSseClient
import com.mrgnstr.harmony.core.network.SubsonicApiClient
import com.mrgnstr.harmony.core.network.TranscodeNegotiator
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.concurrent.thread
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NavidromeSyncDeviceTest {
    private lateinit var ctx: Context
    private lateinit var db: HarmonyDatabase
    private lateinit var serverSocket: ServerSocket
    private lateinit var baseUrl: String
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val recordedCalls = CopyOnWriteArrayList<String>()

    @Before
    fun setUp() {
        ctx = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(ctx, HarmonyDatabase::class.java).build()
        serverSocket = ServerSocket(0, 50, InetAddress.getByName("127.0.0.1"))
        baseUrl = "http://127.0.0.1:${serverSocket.localPort}"
        thread(isDaemon = true) {
            while (!serverSocket.isClosed) {
                val client = runCatching { serverSocket.accept() }.getOrNull() ?: break
                thread(isDaemon = true) { handleClient(client) }
            }
        }
    }

    @After
    fun tearDown() {
        runCatching { serverSocket.close() }
        db.close()
    }

    private fun handleClient(socket: Socket) {
        socket.use { s ->
            val reader = s.getInputStream().bufferedReader(Charsets.UTF_8)
            val requestLine = reader.readLine() ?: return
            val rawPath = requestLine.split(' ').getOrNull(1) ?: "/"
            val path = rawPath.substringBefore('?')
            var contentLength = 0
            while (true) {
                val h = reader.readLine() ?: break
                if (h.isEmpty()) break
                if (h.startsWith("Content-Length:", ignoreCase = true)) {
                    contentLength = h.substringAfter(':').trim().toIntOrNull() ?: 0
                }
            }
            if (contentLength > 0) {
                val buf = CharArray(contentLength)
                var read = 0
                while (read < contentLength) {
                    val r = reader.read(buf, read, contentLength - read)
                    if (r <= 0) break
                    read += r
                }
            }
            recordedCalls += path
            val (extraHeader, body) = routeResponse(path)
            val bytes = body.toByteArray(Charsets.UTF_8)
            val out = s.getOutputStream()
            val header = buildString {
                append("HTTP/1.1 200 OK\r\n")
                append("Content-Type: application/json\r\n")
                append("Content-Length: ${bytes.size}\r\n")
                if (extraHeader != null) append("$extraHeader\r\n")
                append("Connection: close\r\n\r\n")
            }
            out.write(header.toByteArray(Charsets.UTF_8))
            out.write(bytes)
            out.flush()
        }
    }

    private fun routeResponse(path: String): Pair<String?, String> = when (path) {
        "/auth/login" -> null to """{"id":"u1","username":"tester","name":"Tester","isAdmin":true,"token":"jwt_tok_1","subsonicSalt":"s123","subsonicToken":"t123"}"""
        "/rest/ping" -> null to """{"subsonic-response":{"status":"ok","version":"1.16.1","type":"navidrome","serverVersion":"0.64.2","openSubsonic":true}}"""
        "/rest/getOpenSubsonicExtensions" -> null to """{"subsonic-response":{"status":"ok","version":"1.16.1","openSubsonicExtensions":[{"name":"transcoding","versions":[1]},{"name":"songLyrics","versions":[1,2]},{"name":"indexBasedQueue","versions":[1]},{"name":"sonicSimilarity","versions":[1]},{"name":"formPost","versions":[1]}]}}"""
        "/rest/getUser" -> null to """{"subsonic-response":{"status":"ok","version":"1.16.1","user":{"username":"tester","adminRole":true,"downloadRole":true,"shareRole":true,"jukeboxRole":true,"scrobblingEnabled":true}}}"""
        "/rest/getMusicFolders" -> null to """{"subsonic-response":{"status":"ok","version":"1.16.1","musicFolders":{"musicFolder":[{"id":1,"name":"Main Library"}]}}}"""
        "/api/album" -> "X-Total-Count: 1" to """[{"id":"nd_alb_10","name":"Discovery","albumArtist":"Daft Punk","maxYear":2001,"songCount":2,"duration":600.0,"imageHash":"1122334455667788","thumbHash":"H/gNBxpwh4dwd3eIiHd3iHeHeJ+dcH8I","dominantColor":"#336699","rating":5,"tags":{"recordlabel":["Virgin"]}}]"""
        "/api/artist" -> "X-Total-Count: 1" to """[{"id":"nd_ar_1","name":"Daft Punk","biography":"French electronic duo.","dominantColor":"#336699"}]"""
        "/api/song" -> "X-Total-Count: 2" to """[
            {"id":"nd_s_1","title":"One More Time","artist":"Daft Punk","albumArtist":"Daft Punk","album":"Discovery","albumId":"nd_alb_10","duration":320.0,"trackNumber":1,"discNumber":1,"year":2001,"genre":"House","codec":"flac","bitRate":950,"sampleRate":44100,"bitDepth":16,"channels":2,"rgTrackGain":-4.5,"rgAlbumGain":-5.0},
            {"id":"nd_s_2","title":"Aerodynamic","artist":"Daft Punk","albumArtist":"Daft Punk","album":"Discovery","albumId":"nd_alb_10","duration":212.0,"trackNumber":2,"discNumber":1,"year":2001,"genre":"House","codec":"flac","bitRate":940,"sampleRate":44100,"bitDepth":16,"channels":2}
        ]"""
        "/rest/getStarred2" -> null to """{"subsonic-response":{"status":"ok","version":"1.16.1","starred2":{"song":[{"id":"nd_s_1","title":"One More Time","artist":"Daft Punk","album":"Discovery","albumId":"nd_alb_10"}]}}}"""
        "/api/playlist" -> null to """[{"id":"nd_pl_1","name":"Electronic Favorites","songCount":2,"duration":532.0,"public":true,"ownerName":"tester","rules":{"all":[{"is":{"loved":true}}]}}]"""
        "/rest/getPlaylists" -> null to """{"subsonic-response":{"status":"ok","version":"1.16.1","playlists":{"playlist":[{"id":"nd_pl_1","name":"Electronic Favorites","songCount":2,"duration":532,"public":true,"owner":"tester","readonly":true}]}}}"""
        "/rest/getPlaylist" -> null to """{"subsonic-response":{"status":"ok","version":"1.16.1","playlist":{"id":"nd_pl_1","name":"Electronic Favorites","songCount":2,"duration":532,"public":true,"owner":"tester","readonly":true,"entry":[{"id":"nd_s_1","title":"One More Time","artist":"Daft Punk","album":"Discovery","albumId":"nd_alb_10","duration":320}]}}}"""
        "/rest/getInternetRadioStations" -> null to """{"subsonic-response":{"status":"ok","version":"1.16.1","internetRadioStations":{"internetRadioStation":[{"id":"ra_1","name":"FIP Radio","streamUrl":"https://icecast.radiofrance.fr/fip-hifi.aac"}]}}}"""
        "/rest/getBookmarks" -> null to """{"subsonic-response":{"status":"ok","version":"1.16.1","bookmarks":{"bookmark":[{"position":45000,"comment":"Guitar solo","entry":{"id":"nd_s_2","title":"Aerodynamic","artist":"Daft Punk","album":"Discovery","albumId":"nd_alb_10"}}]}}}"""
        else -> null to """{"subsonic-response":{"status":"ok","version":"1.16.1"}}"""
    }

    @Test
    fun fullSyncSeedsCatalogColoursFtsSearchPlaylistsRadiosAndBookmarks() = runBlocking {
        val http = NavidromeHttpClient()
        val subsonic = SubsonicApiClient(http)
        val nativeClient = NavidromeNativeClient(http, subsonic)
        val sse = NavidromeSseClient(http)
        val negotiator = TranscodeNegotiator(subsonic)
        val colorStore = ArtworkColorStore(ctx, db.albumColorDao())
        val settings = SettingsStore(ctx)

        val repo = NavidromeRepository(
            db = db,
            navidromeDao = db.navidromeDao(),
            songDao = db.songDao(),
            playlistDao = db.playlistDao(),
            favouritesDao = db.favouritesDao(),
            subsonic = subsonic,
            nativeClient = nativeClient,
            sseClient = sse,
            http = http,
            transcodeNegotiator = negotiator,
            colorStore = colorStore,
            settings = settings,
            network = NetworkMonitor(ctx),
            scope = scope,
        )

        val account = repo.loginAndSaveAccount(
            name = "Test Navidrome",
            baseUrl = baseUrl,
            username = "tester",
            passwordOrKey = "secret",
            authMethod = AuthMethod.Password,
        )
        repo.syncNow(account)

        // 1. Songs synced with negative IDs and ReplayGain
        val songs = db.songDao().observeAll().first()
        assertEquals(2, songs.size)
        val oneMoreTime = songs.first { it.title == "One More Time" }
        assertTrue(oneMoreTime.id < 0L)
        assertEquals(-4.5, oneMoreTime.rgTrackGain!!, 0.01)

        // 2. First-frame colour engine seeded from dominantColor "#336699"
        val expectedAlbumId = RemoteIds.albumId(account.id, "nd_alb_10")
        val seededPalette = colorStore.peek(expectedAlbumId)
        assertNotNull(seededPalette)
        assertEquals(0xFF336699.toInt(), seededPalette!!.primary)

        // 3. FTS4 search finds synced remote tracks
        val ftsMatches = db.searchDao().songs(requireNotNull(SearchQuery.match("aerodynamic"))).first()
        assertEquals(1, ftsMatches.size)
        assertEquals("Aerodynamic", ftsMatches.single().title)

        // 4. Starred song synced into FavouritesDao
        val favSongIds = db.favouritesDao().observeSongIds().first()
        assertTrue(oneMoreTime.id in favSongIds)

        // 5. Smart Playlist synced
        val playlists = db.playlistDao().observePlaylists().first()
        assertEquals(1, playlists.size)
        assertEquals("Electronic Favorites", playlists.single().name)
        assertTrue(playlists.single().isSmart)

        // 6. Radio stations & bookmarks synced
        val radios = repo.radios.first()
        assertEquals(1, radios.size)
        assertEquals("FIP Radio", radios.single().name)

        val bookmarks = repo.bookmarks.first()
        assertEquals(1, bookmarks.size)
        assertEquals(45_000L, bookmarks.single().positionMs)
        assertEquals("Guitar solo", bookmarks.single().comment)

        // 7. Rating mutation updates Room and calls /rest/setRating
        repo.setRating(songId = oneMoreTime.id, rating = 5)
        assertEquals(5, db.songDao().getById(oneMoreTime.id)?.userRating)
        assertTrue("/rest/setRating" in recordedCalls)
    }
}
