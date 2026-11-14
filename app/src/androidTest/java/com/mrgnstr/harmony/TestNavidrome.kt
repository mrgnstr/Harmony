package com.mrgnstr.harmony

import android.content.Context
import com.mrgnstr.harmony.core.artwork.ArtworkColorStore
import com.mrgnstr.harmony.core.data.NavidromeRepository
import com.mrgnstr.harmony.core.database.HarmonyDatabase
import com.mrgnstr.harmony.core.datastore.SettingsStore
import com.mrgnstr.harmony.core.device.NetworkMonitor
import com.mrgnstr.harmony.core.network.NavidromeHttpClient
import com.mrgnstr.harmony.core.network.NavidromeNativeClient
import com.mrgnstr.harmony.core.network.NavidromeSseClient
import com.mrgnstr.harmony.core.network.SubsonicApiClient
import com.mrgnstr.harmony.core.network.TranscodeNegotiator
import kotlinx.coroutines.CoroutineScope

/** A [NavidromeRepository] over [db] for tests that only need one to exist (no account, nothing is contacted). */
internal fun testNavidromeRepository(ctx: Context, db: HarmonyDatabase, scope: CoroutineScope): NavidromeRepository {
    val http = NavidromeHttpClient()
    val subsonic = SubsonicApiClient(http)
    return NavidromeRepository(
        db = db,
        navidromeDao = db.navidromeDao(),
        songDao = db.songDao(),
        playlistDao = db.playlistDao(),
        favouritesDao = db.favouritesDao(),
        subsonic = subsonic,
        nativeClient = NavidromeNativeClient(http, subsonic),
        sseClient = NavidromeSseClient(http),
        http = http,
        transcodeNegotiator = TranscodeNegotiator(subsonic),
        colorStore = ArtworkColorStore(ctx, db.albumColorDao()),
        settings = SettingsStore(ctx),
        network = NetworkMonitor(ctx),
        scope = scope,
    )
}
