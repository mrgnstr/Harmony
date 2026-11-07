package com.mrgnstr.harmony.core.network

import com.mrgnstr.harmony.core.model.AuthMethod
import com.mrgnstr.harmony.core.model.ServerAccount
import com.mrgnstr.harmony.core.model.SmartConjunction
import com.mrgnstr.harmony.core.model.SmartField
import com.mrgnstr.harmony.core.model.SmartOperator
import com.mrgnstr.harmony.core.model.SmartPlaylistCriteria
import com.mrgnstr.harmony.core.model.SmartRuleCondition
import com.mrgnstr.harmony.core.model.StreamQuality
import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class SubsonicAndNativeApiTest {
    private lateinit var server: HttpServer
    private lateinit var baseUrl: String
    private val http = NavidromeHttpClient()
    private val subsonic = SubsonicApiClient(http)
    private val nativeClient = NavidromeNativeClient(http, subsonic)
    private val negotiator = TranscodeNegotiator(subsonic)
    private val sse = NavidromeSseClient(http)

    @Before
    fun setUp() {
        server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.start()
        baseUrl = "http://127.0.0.1:${server.address.port}"
    }

    @After
    fun tearDown() {
        server.stop(0)
    }

    @Test
    fun subsonicAuthGeneratesSaltedMd5AndSeparatesApiKey() {
        val token = SubsonicAuth.computeToken("sesame", "c19b2d")
        assertEquals("26719a1196d2a940705a59634eb18eab", token)

        val hex = SubsonicAuth.hexEncodePassword("secret")
        assertEquals("enc:736563726574", hex)
        assertEquals("secret", SubsonicAuth.hexDecodePassword(hex))

        val apiKeyAccount = ServerAccount(
            id = 1L,
            name = "Home",
            baseUrl = "https://music.example.com",
            username = "alice",
            authMethod = AuthMethod.ApiKey,
            apiKey = "nds_live_12345",
        )
        val params = SubsonicAuth.authParams(apiKeyAccount).toMap()
        assertEquals("nds_live_12345", params["apiKey"])
        // Navidrome rejects requests that mix apiKey with u, p, t, s, or jwt.
        assertFalse("u" in params)
        assertFalse("t" in params)
        assertFalse("s" in params)
        assertFalse("jwt" in params)
    }

    @Test
    fun nativeLoginAndJwtRefreshAndCatalogHydration() = runBlocking {
        var refreshedJwtSeen: String? = null
        http.onJwtRefreshed = { _, newJwt -> refreshedJwtSeen = newJwt }

        server.createContext("/auth/login") { ex ->
            val resp = """{"id":"u1","username":"alice","name":"Alice","isAdmin":true,"token":"jwt_initial","subsonicSalt":"abc123","subsonicToken":"md5tok"}"""
            val bytes = resp.toByteArray()
            ex.sendResponseHeaders(200, bytes.size.toLong())
            ex.responseBody.use { it.write(bytes) }
        }

        server.createContext("/api/album") { ex ->
            ex.responseHeaders.add("X-Total-Count", "1")
            ex.responseHeaders.add("X-ND-Authorization", "jwt_refreshed_2")
            val resp = """[
                {
                    "id":"alb_1",
                    "name":"Random Access Memories",
                    "albumArtist":"Daft Punk",
                    "maxYear":2013,
                    "songCount":13,
                    "duration":4464.0,
                    "imageHash":"deadbeef12345678",
                    "thumbHash":"H/gNBxpwh4dwd3eIiHd3iHeHeJ+dcH8I",
                    "dominantColor":"#2a4b7c",
                    "catalogNum":"88883716862",
                    "originalDate":"2013-05-17",
                    "releaseDate":"2013-05-21",
                    "description":"Fourth studio album.",
                    "rating":5,
                    "discs":{"1":"Side A & B","2":"Side C & D"},
                    "tags":{"releasetype":["album"],"recordlabel":["Columbia"],"albumversion":["Deluxe"]},
                    "participants":{"composer":[{"id":"ar_1","name":"Thomas Bangalter"}],"producer":[{"id":"ar_2","name":"Guy-Manuel"}]}
                }
            ]"""
            val bytes = resp.toByteArray()
            ex.sendResponseHeaders(200, bytes.size.toLong())
            ex.responseBody.use { it.write(bytes) }
        }

        val login = nativeClient.login(baseUrl, "alice", "password")
        assertEquals("jwt_initial", login.jwtToken)
        assertEquals("abc123", login.subsonicSalt)
        assertEquals("md5tok", login.subsonicToken)
        assertTrue(login.isAdmin)

        val account = ServerAccount(
            id = 7L,
            name = "Test",
            baseUrl = baseUrl,
            username = "alice",
            jwtToken = login.jwtToken,
            subsonicSalt = login.subsonicSalt,
            subsonicToken = login.subsonicToken,
        )

        val (albums, total) = nativeClient.fetchAlbumsPage(account, 0, 50)
        assertEquals(1, total)
        assertEquals("jwt_refreshed_2", refreshedJwtSeen)
        val extra = albums.single()
        assertEquals("alb_1", extra.remoteId)
        assertEquals("al-alb_1_deadbeef12345678", extra.coverArtId)
        assertEquals("#2a4b7c", extra.dominantColor)
        assertEquals("H/gNBxpwh4dwd3eIiHd3iHeHeJ+dcH8I", extra.thumbHash)
        assertEquals(listOf("Columbia"), extra.recordLabels)
        assertEquals("Deluxe", extra.version)
        assertEquals(2, extra.discTitles.size)
        assertEquals("Side A & B", extra.discTitles[0].title)
        assertEquals(2, extra.participants.size)
        assertEquals(5, extra.userRating)
    }

    @Test
    fun structuredLyricsV2ParsesWordCuesAgentsAndTranslations() {
        val json = MiniJson.parseArray(
            """[
                {
                    "lang":"jpn",
                    "kind":"main",
                    "synced":true,
                    "offset":100,
                    "agents":[{"id":"v1","role":"main","name":"Lead"},{"id":"v2","role":"bg","name":"Duet"}],
                    "line":[
                        {"start":1100,"value":"Hello world"},
                        {"start":3100,"value":"Second line"}
                    ],
                    "cueLine":[
                        {
                            "index":0,
                            "start":1100,
                            "end":2500,
                            "value":"Hello world",
                            "agentId":"v1",
                            "cue":[
                                {"start":1100,"end":1600,"byteStart":0,"byteEnd":4,"value":"Hello"},
                                {"start":1600,"end":2500,"byteStart":6,"byteEnd":10,"value":"world"}
                            ]
                        }
                    ]
                },
                {
                    "lang":"eng",
                    "kind":"translation",
                    "synced":true,
                    "offset":100,
                    "line":[
                        {"start":1100,"value":"Translated hello"},
                        {"start":3100,"value":"Translated second"}
                    ]
                },
                {
                    "lang":"ja-Latn",
                    "kind":"pronunciation",
                    "synced":true,
                    "offset":100,
                    "line":[
                        {"start":1100,"value":"Konnichiwa sekai"},
                        {"start":3100,"value":"Ni banme"}
                    ]
                }
            ]""",
        ).objects()

        val lyrics = subsonic.parseStructuredLyrics(json)
        assertNotNull(lyrics)
        lyrics!!
        assertTrue(lyrics.synced)
        assertTrue(lyrics.hasWordCues)
        assertTrue(lyrics.hasTranslations)
        assertTrue(lyrics.hasPronunciations)
        assertEquals(2, lyrics.agents.size)
        assertEquals(2, lyrics.lines.size)
        val first = lyrics.lines[0]
        assertEquals(1000L, first.startMs) // 1100 - 100 offset
        assertEquals("v1", first.agentId)
        assertEquals("Translated hello", first.translation)
        assertEquals("Konnichiwa sekai", first.pronunciation)
        assertEquals(2, first.cues.size)
        assertEquals("Hello", first.cues[0].text)
        assertEquals(1000L, first.cues[0].startMs)
        assertEquals("world", first.cues[1].text)
    }

    @Test
    fun structuredLyricsV2DuetBackgroundAndPronunciationCues() {
        val json = MiniJson.parseArray(
            """[
                {"lang":"jpn","kind":"main","synced":true,
                 "agents":[{"id":"v1","role":"main"},{"id":"v2","role":"voice"},{"id":"b1","role":"bg"}],
                 "line":[{"start":1000,"value":"Hello ü"},{"start":4000,"value":"Reply"},{"start":6000,"value":"Both"}],
                 "cueLine":[
                    {"index":0,"agentId":"v1","start":1000,"end":2000,"value":"Hello ü","cue":[
                        {"start":1000,"end":1500,"byteStart":0,"byteEnd":5,"value":"Hello "},
                        {"start":1500,"end":2000,"byteStart":6,"byteEnd":7,"value":"ü"}]},
                    {"index":0,"agentId":"b1","start":1400,"end":2200,"value":"(ooh)","cue":[
                        {"start":1400,"end":2200,"byteStart":1,"byteEnd":3,"value":"ooh"}]},
                    {"index":1,"agentId":"v2","start":4000,"end":5000,"value":"Reply","cue":[
                        {"start":4000,"end":5000,"byteStart":0,"byteEnd":4,"value":"Reply"}]},
                    {"index":2,"agentId":"v1","start":6000,"end":7000,"value":"Both"},
                    {"index":2,"agentId":"v2","start":6200,"end":7000,"value":"Both"}
                 ]},
                {"lang":"ja-Latn","kind":"pronunciation","synced":true,
                 "line":[{"start":1000,"value":"heroh"},{"start":4000,"value":"ripurai"},{"start":6000,"value":"bosu"}],
                 "cueLine":[{"index":0,"start":1000,"end":2000,"value":"heroh","cue":[
                    {"start":1000,"end":1500,"byteStart":0,"byteEnd":2,"value":"her"},
                    {"start":1500,"end":2000,"byteStart":3,"byteEnd":4,"value":"oh"}]}]}
            ]""",
        ).objects()
        val l = subsonic.parseStructuredLyrics(json)!!
        // line 2 has a second voice beside the main one: it follows as its own duet line.
        assertEquals(listOf("Hello ü", "Reply", "Both", "Both"), l.lines.map { it.text })
        val a = l.lines[0]
        assertFalse(a.duet)
        assertEquals(listOf("Hello ", "ü"), a.cues.map { it.text })
        assertEquals("ooh", a.background!!.text)
        assertEquals(listOf("ooh"), a.background!!.cues.map { it.text })
        assertEquals(1400L, a.background!!.startMs)
        assertEquals("heroh", a.pronunciation)
        assertEquals(listOf("her", "oh"), a.pronunciationCues.map { it.text })
        assertTrue(l.lines[1].duet)
        assertEquals("v2", l.lines[1].agentId)
        assertEquals("ripurai", l.lines[1].pronunciation)
        assertFalse(l.lines[2].duet)
        assertTrue(l.lines[3].duet)
        assertEquals(6200L, l.lines[3].startMs)
    }

    @Test
    fun transcodeNegotiatorBuildsDecisionAndStreamUrl() = runBlocking {
        server.createContext("/rest/getTranscodeDecision") { ex ->
            val body = ex.requestBody.bufferedReader().readText()
            val reqJson = MiniJson.parseObject(body)
            // Balanced = 192 kbps = 192000 bps in OpenSubsonic ClientInfo
            assertEquals(192_000, reqJson.int("maxAudioBitrate"))
            val resp = """{
                "subsonic-response":{
                    "status":"ok",
                    "version":"1.16.1",
                    "type":"navidrome",
                    "openSubsonic":true,
                    "transcodeDecision":{
                        "canDirectPlay":false,
                        "canTranscode":true,
                        "transcodeReason":["AudioBitrateNotSupported"],
                        "transcodeParams":"jwt_transcode_token_xyz",
                        "sourceStream":{"codec":"flac","container":"flac","audioBitrate":960000,"audioSamplerate":96000,"audioBitdepth":24,"audioChannels":2},
                        "transcodeStream":{"codec":"opus","container":"opus","audioBitrate":192000,"audioSamplerate":48000,"audioChannels":2}
                    }
                }
            }"""
            val bytes = resp.toByteArray()
            ex.sendResponseHeaders(200, bytes.size.toLong())
            ex.responseBody.use { it.write(bytes) }
        }

        val account = ServerAccount(
            id = 1L,
            name = "Server",
            baseUrl = baseUrl,
            username = "alice",
            subsonicSalt = "s1",
            subsonicToken = "t1",
            extensions = setOf("transcoding", "formPost"),
        )
        val resolved = negotiator.resolveStream(account, "song_99", StreamQuality.Balanced)
        assertFalse(resolved.isDirectPlay)
        assertNotNull(resolved.decision)
        assertEquals("FLAC → OPUS 192k", resolved.decision!!.badgeText)
        assertTrue(resolved.url.contains("getTranscodeStream"))
        assertTrue(resolved.url.contains("transcodeParams=jwt_transcode_token_xyz"))
    }

    @Test
    fun sseParserHandlesScanStatusAndRefreshResource() {
        val scanEvt = sse.parseEvent("scanStatus", """{"scanning":true,"count":4200,"folderCount":180,"scanType":"full","elapsedTime":5000000000}""")
        assertTrue(scanEvt is NavidromeServerEvent.ScanStatus)
        val scan = (scanEvt as NavidromeServerEvent.ScanStatus).status
        assertTrue(scan.scanning)
        assertEquals(4200L, scan.count)
        assertEquals(5000L, scan.elapsedTimeMs)

        val refreshEvt = sse.parseEvent("refreshResource", """{"album":["al_1","al_2"],"playlist":["*"]}""")
        assertTrue(refreshEvt is NavidromeServerEvent.RefreshResource)
        val map = (refreshEvt as NavidromeServerEvent.RefreshResource).resources
        assertEquals(listOf("al_1", "al_2"), map["album"])
        assertEquals(listOf("*"), map["playlist"])

        assertNull(sse.parseEvent("unknownEvent", "{}"))
    }

    @Test
    fun smartPlaylistCriteriaSerializesValidNspJson() {
        val criteria = SmartPlaylistCriteria(
            conjunction = SmartConjunction.All,
            rules = listOf(
                SmartRuleCondition(SmartField.Loved, SmartOperator.Is, "true"),
                SmartRuleCondition(SmartField.Year, SmartOperator.InTheRange, "2010", "2025"),
                SmartRuleCondition(SmartField.Composer, SmartOperator.Contains, "Sakamoto"),
            ),
            sort = "year",
            ascending = false,
            limit = 50,
        )
        val json = MiniJson.parseObject(criteria.toJsonString())
        val all = json.arr("all").objects()
        assertEquals(3, all.size)
        assertEquals(true, all[0].obj("is")?.bool("loved"))
        assertEquals(listOf(2010, 2025), all[1].obj("inTheRange")?.arr("year")?.ints())
        assertEquals("Sakamoto", all[2].obj("contains")?.str("composer"))
        assertEquals("year", json.str("sort"))
        assertEquals("desc", json.str("order"))
        assertEquals(50, json.int("limit"))
    }
}
