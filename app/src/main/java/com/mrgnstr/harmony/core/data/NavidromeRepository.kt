package com.mrgnstr.harmony.core.data

import android.util.Log
import androidx.room.withTransaction
import com.mrgnstr.harmony.core.artwork.ArtworkColorStore
import com.mrgnstr.harmony.core.artwork.RemoteArtworkBridge
import com.mrgnstr.harmony.core.common.ApplicationScope
import com.mrgnstr.harmony.core.database.FavouritesDao
import com.mrgnstr.harmony.core.database.HarmonyDatabase
import com.mrgnstr.harmony.core.database.NavidromeDao
import com.mrgnstr.harmony.core.database.PendingMutationEntity
import com.mrgnstr.harmony.core.database.PlaylistDao
import com.mrgnstr.harmony.core.database.PlaylistEntity
import com.mrgnstr.harmony.core.database.PlaylistSongEntity
import com.mrgnstr.harmony.core.database.SongDao
import com.mrgnstr.harmony.core.database.toEntity
import com.mrgnstr.harmony.core.database.toModel
import com.mrgnstr.harmony.core.datastore.SettingsStore
import com.mrgnstr.harmony.core.model.AlbumExtraInfo
import com.mrgnstr.harmony.core.model.ArtistExtraInfo
import com.mrgnstr.harmony.core.model.AudioBookmark
import com.mrgnstr.harmony.core.model.AuthMethod
import com.mrgnstr.harmony.core.model.Lyrics
import com.mrgnstr.harmony.core.model.MissingFileItem
import com.mrgnstr.harmony.core.model.MusicFolder
import com.mrgnstr.harmony.core.model.NowPlayingSession
import com.mrgnstr.harmony.core.model.QuickConnectChallenge
import com.mrgnstr.harmony.core.model.RadioStation
import com.mrgnstr.harmony.core.model.RemoteIds
import com.mrgnstr.harmony.core.model.ScanStatusInfo
import com.mrgnstr.harmony.core.model.ServerAccount
import com.mrgnstr.harmony.core.model.ShareLink
import com.mrgnstr.harmony.core.model.SmartPlaylistCriteria
import com.mrgnstr.harmony.core.model.Song
import com.mrgnstr.harmony.core.model.SonicMatchItem
import com.mrgnstr.harmony.core.model.normalizeServerUrl
import com.mrgnstr.harmony.core.network.MiniJson
import com.mrgnstr.harmony.core.network.NavidromeHttpClient
import com.mrgnstr.harmony.core.network.NavidromeNativeClient
import com.mrgnstr.harmony.core.network.NavidromeServerEvent
import com.mrgnstr.harmony.core.network.NavidromeSseClient
import com.mrgnstr.harmony.core.network.SubsonicApiClient
import com.mrgnstr.harmony.core.network.SubsonicAuth
import com.mrgnstr.harmony.core.network.TranscodeNegotiator
import com.mrgnstr.harmony.core.network.buildJsonObj
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

@Singleton
class NavidromeRepository @Inject constructor(
    private val db: HarmonyDatabase,
    private val navidromeDao: NavidromeDao,
    private val songDao: SongDao,
    private val playlistDao: PlaylistDao,
    private val favouritesDao: FavouritesDao,
    private val subsonic: SubsonicApiClient,
    private val nativeClient: NavidromeNativeClient,
    private val sseClient: NavidromeSseClient,
    private val http: NavidromeHttpClient,
    private val transcodeNegotiator: TranscodeNegotiator,
    private val colorStore: ArtworkColorStore,
    private val settings: SettingsStore,
    @ApplicationScope private val scope: CoroutineScope,
) {
    private val syncMutex = Mutex()
    private var sseJob: Job? = null

    val accounts: StateFlow<List<ServerAccount>> = navidromeDao.observeAccounts()
        .map { list -> list.map { it.toModel() } }
        .stateIn(scope, SharingStarted.Eagerly, emptyList())

    val activeAccount: StateFlow<ServerAccount?> = navidromeDao.observeActiveAccount()
        .map { it?.toModel() }
        .stateIn(scope, SharingStarted.Eagerly, null)

    private val _recentAlbums = MutableStateFlow<List<Pair<Long, Long>>>(emptyList())
    /** (local album id, time of the last play) of the albums played most recently on any device, as of the last refresh. */
    val recentAlbums: StateFlow<List<Pair<Long, Long>>> = _recentAlbums.asStateFlow()

    /** Reads the server's Recently Played albums: plays on other devices only show up there. Failures keep the last list. */
    suspend fun refreshRecentAlbums() {
        val account = activeAccount.value ?: return
        val serverId = account.id.coerceAtLeast(1L)
        runCatching { subsonic.getRecentAlbums(account) }.onSuccess { list ->
            _recentAlbums.value = list.map { (remoteId, playedMs) -> RemoteIds.albumId(serverId, remoteId) to playedMs }
        }
    }

    private val _syncState = MutableStateFlow(SyncState())
    val syncState: StateFlow<SyncState> = _syncState.asStateFlow()

    private val _scanStatus = MutableStateFlow(ScanStatusInfo())
    val scanStatus: StateFlow<ScanStatusInfo> = _scanStatus.asStateFlow()

    private val _musicFolders = MutableStateFlow<List<MusicFolder>>(emptyList())
    val musicFolders: StateFlow<List<MusicFolder>> = _musicFolders.asStateFlow()


    val radios: Flow<List<RadioStation>> = navidromeDao.observeRadioStations()
        .map { list -> list.map { it.toModel() } }
        .flowOn(Dispatchers.Default)

    val bookmarks: Flow<List<AudioBookmark>> = navidromeDao.observeBookmarks()
        .map { list -> list.map { it.toModel() } }
        .flowOn(Dispatchers.Default)

    init {
        http.onJwtRefreshed = { serverId, newJwt ->
            scope.launch { navidromeDao.updateJwtToken(serverId, newJwt) }
        }
        RemoteArtworkBridge.remoteByteFetcher = { coverArtId, sizePx ->
            val account = activeAccount.value ?: navidromeDao.activeAccount()?.toModel()
            if (account == null) null
            else {
                val url = subsonic.buildCoverArtUrl(account, coverArtId, sizePx = sizePx)
                http.getBytes(account, url)
            }
        }
        scope.launch {
            hydrateArtworkBridgeFromDb()
            activeAccount
                .distinctUntilChangedBy { it?.id to it?.selectedMusicFolders }
                .collectLatest { account ->
                    sseJob?.cancel()
                    if (account != null) {
                        launch { refreshMusicFolders(account) }
                        launch { flushPendingMutations(account) }
                        if (System.currentTimeMillis() - account.lastSyncMs > 60_000L) {
                            launch { syncNow(account) }
                        }
                        sseJob = launch { observeServerEvents(account) }
                    }
                }
        }
    }

    private suspend fun hydrateArtworkBridgeFromDb() = withContext(Dispatchers.IO) {
        for (album in navidromeDao.allAlbumExtras()) {
            RemoteArtworkBridge.registerAlbum(album.albumId, album.coverArtId, album.thumbHash, album.dominantColor)
        }
        for (artist in navidromeDao.allArtistExtras()) {
            val id = RemoteIds.artistId(artist.serverId, artist.remoteId)
            RemoteArtworkBridge.registerAlbum(id, artist.coverArtId, artist.thumbHash, artist.dominantColor)
        }
    }

    // --- Authentication & Account Setup ---

    suspend fun loginAndSaveAccount(
        name: String,
        baseUrl: String,
        username: String,
        passwordOrKey: String,
        authMethod: AuthMethod = AuthMethod.Password,
        allowSelfSignedTls: Boolean = false,
    ): ServerAccount = withContext(Dispatchers.IO) {
        val cleanUrl = normalizeServerUrl(baseUrl)
        require(cleanUrl.isNotEmpty()) { "Server URL cannot be empty" }

        var jwt: String? = null
        var salt = SubsonicAuth.generateSalt()
        var token = ""
        var resolvedUser = username.trim()
        var isAdmin = false

        if (authMethod == AuthMethod.Password) {
            // Try Navidrome Native /auth/login first to obtain both JWT and Subsonic token.
            val nativeLogin = runCatching {
                nativeClient.login(cleanUrl, resolvedUser, passwordOrKey, allowSelfSignedTls)
            }.getOrNull()
            if (nativeLogin != null) {
                jwt = nativeLogin.jwtToken
                salt = nativeLogin.subsonicSalt
                token = nativeLogin.subsonicToken
                resolvedUser = nativeLogin.username
                isAdmin = nativeLogin.isAdmin
            } else {
                token = SubsonicAuth.computeToken(passwordOrKey, salt)
            }
        }

        val draft = ServerAccount(
            name = name.ifBlank { cleanUrl.substringAfter("://") },
            baseUrl = cleanUrl,
            username = resolvedUser,
            authMethod = authMethod,
            passwordOrToken = if (authMethod == AuthMethod.Password) passwordOrKey else "",
            subsonicSalt = salt,
            subsonicToken = token,
            apiKey = if (authMethod == AuthMethod.ApiKey) passwordOrKey.trim() else null,
            jwtToken = jwt,
            allowSelfSignedTls = allowSelfSignedTls,
            active = true,
        )

        val ping = subsonic.ping(draft)
        val extensions = runCatching { subsonic.getOpenSubsonicExtensions(draft).map { it.name }.toSet() }.getOrDefault(emptySet())
        val draftWithExt = draft.copy(
            serverType = ping.type,
            serverVersion = ping.serverVersion,
            openSubsonic = ping.openSubsonic,
            extensions = extensions,
        )

        if (authMethod == AuthMethod.ApiKey && resolvedUser.isBlank()) {
            resolvedUser = runCatching { subsonic.tokenInfo(draftWithExt) }.getOrNull().orEmpty().ifBlank { "api-key-user" }
        }
        val perms = runCatching { subsonic.getUser(draftWithExt, resolvedUser) }.getOrNull()
        val finalAccount = draftWithExt.copy(
            username = resolvedUser,
            isAdmin = isAdmin || (perms?.isAdmin == true),
            downloadRole = perms?.downloadRole ?: true,
            shareRole = perms?.shareRole ?: false,
            jukeboxRole = perms?.jukeboxRole ?: false,
            scrobblingEnabled = perms?.scrobblingEnabled ?: true,
        )

        val newId = db.withTransaction {
            val inserted = navidromeDao.insertAccount(finalAccount.toEntity())
            navidromeDao.setActiveAccount(inserted)
            inserted
        }
        val saved = finalAccount.copy(id = newId)
        scope.launch { syncNow(saved) }
        saved
    }

    suspend fun initiateQuickConnect(baseUrl: String, deviceName: String, allowSelfSignedTls: Boolean): QuickConnectChallenge =
        nativeClient.initiateQuickConnect(baseUrl, deviceName, allowSelfSignedTls)

    suspend fun pollQuickConnect(baseUrl: String, secret: String, allowSelfSignedTls: Boolean): QuickConnectChallenge =
        nativeClient.checkQuickConnectStatus(baseUrl, secret, allowSelfSignedTls)

    suspend fun lookupQuickConnectCode(code: String): QuickConnectChallenge {
        val account = activeAccount.value ?: throw IllegalStateException("No active Navidrome server")
        return nativeClient.lookupQuickConnectCode(account, code)
    }

    suspend fun authorizeQuickConnectCode(code: String): QuickConnectChallenge {
        val account = activeAccount.value ?: throw IllegalStateException("No active Navidrome server")
        return nativeClient.authorizeQuickConnectCode(account, code)
    }

    suspend fun switchActiveAccount(serverId: Long) {
        navidromeDao.setActiveAccount(serverId)
    }

    suspend fun removeAccount(serverId: Long) {
        db.withTransaction {
            songDao.deleteByServerId(serverId)
            playlistDao.deleteByServerId(serverId)
            navidromeDao.deleteAlbumExtrasByServer(serverId)
            navidromeDao.deleteArtistExtrasByServer(serverId)
            navidromeDao.deleteRadioStationsByServer(serverId)
            navidromeDao.deleteBookmarksByServer(serverId)
            navidromeDao.deleteMutationsByServer(serverId)
            navidromeDao.deleteAccount(serverId)
        }
    }

    suspend fun refreshMusicFolders(account: ServerAccount? = activeAccount.value): List<MusicFolder> {
        val acc = account ?: return emptyList()
        val folders = runCatching { subsonic.getMusicFolders(acc) }.getOrDefault(emptyList())
        _musicFolders.value = folders
        return folders
    }

    suspend fun setSelectedMusicFolders(folderIds: Set<Int>) {
        val current = activeAccount.value ?: return
        val updated = current.copy(selectedMusicFolders = folderIds)
        navidromeDao.upsertAccount(updated.toEntity())
        scope.launch { syncNow(updated) }
    }

    // --- Catalog Sync Engine ---

    suspend fun syncActiveServer() {
        val account = activeAccount.value ?: navidromeDao.activeAccount()?.toModel() ?: return
        syncNow(account)
    }

    suspend fun syncNow(account: ServerAccount) = syncMutex.withLock {
        val serverId = account.id.coerceAtLeast(1L)
        _syncState.update { it.copy(syncing = true, failed = false) }
        try {
            flushPendingMutations(account)

            // 1. Fetch Albums, Artists, and Songs (using Native API if JWT is available and single/all folders, else OpenSubsonic)
            val useNativeCatalog = !account.jwtToken.isNullOrBlank() && account.selectedMusicFolders.size <= 1
            val remoteSongs: List<Song>
            val albumExtras: List<AlbumExtraInfo>
            val artistExtras: List<ArtistExtraInfo>

            if (useNativeCatalog) {
                val nativeResult = runCatching { syncCatalogViaNativeApi(account) }.getOrNull()
                if (nativeResult != null) {
                    remoteSongs = nativeResult.first
                    albumExtras = nativeResult.second
                    artistExtras = nativeResult.third
                } else {
                    val subResult = syncCatalogViaSubsonicApi(account)
                    remoteSongs = subResult.first
                    albumExtras = subResult.second
                    artistExtras = subResult.third
                }
            } else {
                val subResult = syncCatalogViaSubsonicApi(account)
                remoteSongs = subResult.first
                albumExtras = subResult.second
                artistExtras = subResult.third
            }

            // Preserve any existing offline downloadedPath values on songs
            val existingSongs = songDao.observeAll().first().filter { it.serverId == serverId }
            val downloadedBySongId = existingSongs.mapNotNull { s -> s.downloadedPath?.let { s.id to it } }.toMap()
            val mergedSongs = remoteSongs.map { s ->
                val dl = downloadedBySongId[s.id]
                if (dl != null && File(dl.removePrefix("file://")).exists()) s.copy(downloadedPath = dl) else s
            }

            // Register artwork & pre-seed dominantColor / thumbHash into ArtworkColorStore
            for (album in albumExtras) {
                RemoteArtworkBridge.registerAlbum(album.albumId, album.coverArtId, album.thumbHash, album.dominantColor)
                colorStore.seedRemotePalette(album.albumId, album.dominantColor, album.thumbHash)
            }
            for (song in mergedSongs) {
                RemoteArtworkBridge.registerSong(song.id, song.albumId, song.coverArtId)
            }

            val diff = SyncDiff.compute(songDao.remoteStamps(serverId), mergedSongs)
            db.withTransaction {
                if (!diff.isEmpty) {
                    songDao.upsert(diff.upserts.map { it.toEntity() })
                    diff.deletedIds.chunked(500).forEach { songDao.deleteByIds(it) }
                } else if (mergedSongs.isNotEmpty()) {
                    // Also update metadata like ratings / bookmarks
                    songDao.upsert(mergedSongs.map { it.toEntity() })
                }
                navidromeDao.upsertAlbumExtras(albumExtras.map { it.toEntity() })
                navidromeDao.upsertArtistExtras(artistExtras.map { it.toEntity() })
            }

            // 2. Sync Starred (Favourites)
            runCatching { syncStarred(account, albumExtras) }

            // 3. Sync Playlists (including Smart Playlists)
            runCatching { syncPlaylists(account) }

            // 4. Sync Internet Radio Stations & Bookmarks
            runCatching {
                val stations = subsonic.getInternetRadioStations(account)
                db.withTransaction {
                    navidromeDao.deleteRadioStationsByServer(serverId)
                    navidromeDao.upsertRadioStations(stations.map { it.toEntity() })
                }
            }
            runCatching {
                val bmks = subsonic.getBookmarks(account)
                db.withTransaction {
                    navidromeDao.deleteBookmarksByServer(serverId)
                    navidromeDao.upsertBookmarks(bmks.map { it.toEntity(serverId) })
                }
            }

            refreshRecentAlbums()

            val now = System.currentTimeMillis()
            navidromeDao.updateLastSync(serverId, now)
            _syncState.update { SyncState(syncing = false, completedOnce = true, generation = it.generation + 1) }
            Log.i(TAG, "Navidrome synced: ${mergedSongs.size} songs, ${albumExtras.size} albums, ${artistExtras.size} artists")
        } catch (e: CancellationException) {
            _syncState.update { it.copy(syncing = false) }
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Navidrome sync failed", e)
            _syncState.update { it.copy(syncing = false, failed = true) }
        }
    }

    private suspend fun syncCatalogViaNativeApi(
        account: ServerAccount,
    ): Triple<List<Song>, List<AlbumExtraInfo>, List<ArtistExtraInfo>> {
        val pageSize = 500
        val albums = ArrayList<AlbumExtraInfo>()
        var offset = 0
        while (true) {
            val (batch, total) = nativeClient.fetchAlbumsPage(account, offset, offset + pageSize)
            albums += batch
            offset += batch.size
            if (batch.size < pageSize || (total > 0 && offset >= total)) break
        }

        val artists = ArrayList<ArtistExtraInfo>()
        offset = 0
        while (true) {
            val (batch, total) = nativeClient.fetchArtistsPage(account, offset, offset + pageSize)
            artists += batch
            offset += batch.size
            if (batch.size < pageSize || (total > 0 && offset >= total)) break
        }

        val songs = ArrayList<Song>()
        offset = 0
        while (true) {
            val (batch, total) = nativeClient.fetchSongsPage(account, offset, offset + pageSize)
            songs += batch
            offset += batch.size
            if (batch.size < pageSize || (total > 0 && offset >= total)) break
        }
        return Triple(songs, albums, artists)
    }

    private suspend fun syncCatalogViaSubsonicApi(
        account: ServerAccount,
    ): Triple<List<Song>, List<AlbumExtraInfo>, List<ArtistExtraInfo>> {
        val artists = subsonic.getArtists(account)
        val albums = ArrayList<AlbumExtraInfo>()
        val songs = ArrayList<Song>()
        val pageSize = 500
        var offset = 0
        while (true) {
            val page = subsonic.getAlbumList2(account, type = "alphabeticalByName", size = pageSize, offset = offset)
            if (page.isEmpty()) break
            for (albumSummary in page) {
                val full = runCatching { subsonic.getAlbum(account, albumSummary.extra.remoteId) }.getOrNull() ?: albumSummary
                albums += full.extra
                songs += full.songs
            }
            offset += page.size
            if (page.size < pageSize) break
        }
        return Triple(songs, albums, artists)
    }

    private suspend fun syncStarred(account: ServerAccount, albumExtras: List<AlbumExtraInfo>) {
        val starred = subsonic.getStarred2(account)
        val now = System.currentTimeMillis()
        val allSongs = songDao.observeAll().first()
        val albumTitleById = allSongs.groupBy { it.albumId }.mapValues { (_, list) -> list.first().album }
        db.withTransaction {
            starred.songs.forEach { s -> favouritesDao.setSongFavourite(s.id, true, now) }
            starred.albums.forEach { a ->
                val title = albumTitleById[a.albumId]
                    ?: albumExtras.firstOrNull { it.albumId == a.albumId }?.remoteId
                if (!title.isNullOrBlank()) {
                    favouritesDao.setAlbumFavourite(a.albumId, title, true, now)
                }
            }
            starred.artists.forEach { ar ->
                if (ar.name.isNotBlank()) favouritesDao.setArtistFavourite(ar.name, true, now)
            }
        }
    }

    private suspend fun syncPlaylists(account: ServerAccount) {
        val serverId = account.id.coerceAtLeast(1L)
        val nativeSummaries = if (!account.jwtToken.isNullOrBlank()) {
            runCatching { nativeClient.getNativePlaylists(account) }.getOrDefault(emptyList()).associateBy { it.remoteId }
        } else emptyMap()

        val remotePlaylists = subsonic.getPlaylists(account)
        val now = System.currentTimeMillis()
        // Local edits not yet on the server win over the pull, and a playlist deleted here must not come back before its delete lands.
        val pending = navidromeDao.pendingMutations(account.id)
        val dirty = pending.filter { it.type == PendingMutationEntity.TYPE_PLAYLIST_PUSH }.mapNotNull { it.targetId.toLongOrNull() }.toSet()
        val deleting = pending.filter { it.type == PendingMutationEntity.TYPE_PLAYLIST_DELETE }.map { it.targetId }.toSet()
        // Playlists deleted on the server (or by another client) go here too.
        val onServer = remotePlaylists.map { it.remoteId }.toSet()
        playlistDao.serverPlaylistIds(serverId).filter { it.remoteId !in onServer && it.id !in dirty }.forEach { playlistDao.delete(it.id) }
        for (rp in remotePlaylists) {
            if (rp.remoteId in deleting) continue
            if (playlistDao.getByRemoteId(serverId, rp.remoteId)?.let { it.id in dirty } == true) continue
            val detail = runCatching { subsonic.getPlaylist(account, rp.remoteId) }.getOrDefault(rp)
            val nativeMeta = nativeSummaries[rp.remoteId]
            val existing = playlistDao.getByRemoteId(serverId, rp.remoteId)
            val entity = PlaylistEntity(
                id = existing?.id ?: 0L,
                name = detail.name,
                coverPath = existing?.coverPath,
                createdAtMs = detail.createdMs.takeIf { it > 0L } ?: now,
                updatedAtMs = detail.changedMs.takeIf { it > 0L } ?: now,
                serverId = serverId,
                remoteId = detail.remoteId,
                comment = detail.comment ?: nativeMeta?.comment,
                isPublic = detail.isPublic,
                owner = detail.owner ?: nativeMeta?.ownerName,
                isSmart = nativeMeta?.isSmart ?: (detail.readonly && detail.validUntilMs != null),
                readonly = detail.readonly,
                validUntilMs = detail.validUntilMs,
                rulesJson = nativeMeta?.rulesJson,
                coverArtId = detail.coverArtId ?: nativeMeta?.coverArtId,
                userRating = nativeMeta?.userRating ?: 0,
                starred = nativeMeta?.starred ?: false,
            )
            db.withTransaction {
                val localPlaylistId = if (entity.id == 0L) {
                    playlistDao.insert(entity)
                } else {
                    playlistDao.upsertPlaylist(entity)
                    entity.id
                }
                playlistDao.clearRemoteSongs(localPlaylistId)
                playlistDao.shiftLocalSongs(localPlaylistId, detail.songs.size)
                if (detail.songs.isNotEmpty()) {
                    playlistDao.insertSongs(
                        detail.songs.mapIndexed { idx, s ->
                            PlaylistSongEntity(
                                playlistId = localPlaylistId,
                                songId = s.id,
                                position = idx,
                                addedAtMs = now,
                            )
                        },
                    )
                }
            }
        }
    }

    // --- Real-Time SSE Events ---

    private suspend fun observeServerEvents(account: ServerAccount) {
        sseClient.events(account).collect { event ->
            when (event) {
                is NavidromeServerEvent.ScanStatus -> {
                    val wasScanning = _scanStatus.value.scanning
                    _scanStatus.value = event.status
                    if (wasScanning && !event.status.scanning) {
                        syncNow(account)
                    }
                }
                is NavidromeServerEvent.RefreshResource -> {
                    syncNow(account)
                }
                else -> Unit
            }
        }
    }

    // --- Optimistic Mutations & Offline Outbox ---

    suspend fun syncStarSong(songId: Long, starred: Boolean) {
        val song = songDao.getById(songId) ?: return
        val remoteId = song.remoteId ?: return
        val account = accountFor(song.serverId) ?: return
        val ok = runCatching { subsonic.setStar(account, starred, ids = listOf(remoteId)); true }.getOrDefault(false)
        if (!ok) {
            enqueueMutation(
                serverId = account.id,
                type = if (starred) PendingMutationEntity.TYPE_STAR else PendingMutationEntity.TYPE_UNSTAR,
                targetId = remoteId,
                targetKind = "song",
            )
        }
    }

    suspend fun syncStarAlbum(albumId: Long, starred: Boolean) {
        val extra = navidromeDao.albumExtra(albumId) ?: return
        val account = accountFor(extra.serverId) ?: return
        val ok = runCatching { subsonic.setStar(account, starred, albumIds = listOf(extra.remoteId)); true }.getOrDefault(false)
        if (!ok) {
            enqueueMutation(
                serverId = account.id,
                type = if (starred) PendingMutationEntity.TYPE_STAR else PendingMutationEntity.TYPE_UNSTAR,
                targetId = extra.remoteId,
                targetKind = "album",
            )
        }
    }

    suspend fun syncStarArtist(artistName: String, starred: Boolean) {
        val extra = navidromeDao.artistExtraByName(artistName) ?: return
        val account = accountFor(extra.serverId) ?: return
        val ok = runCatching { subsonic.setStar(account, starred, artistIds = listOf(extra.remoteId)); true }.getOrDefault(false)
        if (!ok) {
            enqueueMutation(
                serverId = account.id,
                type = if (starred) PendingMutationEntity.TYPE_STAR else PendingMutationEntity.TYPE_UNSTAR,
                targetId = extra.remoteId,
                targetKind = "artist",
            )
        }
    }

    suspend fun setRating(songId: Long? = null, albumId: Long? = null, artistName: String? = null, rating: Int) {
        val clamped = rating.coerceIn(0, 5)
        when {
            songId != null -> {
                songDao.updateRating(songId, clamped)
                val song = songDao.getById(songId) ?: return
                val remoteId = song.remoteId ?: return
                val account = accountFor(song.serverId) ?: return
                val ok = runCatching { subsonic.setRating(account, remoteId, clamped); true }.getOrDefault(false)
                if (!ok) {
                    enqueueMutation(account.id, PendingMutationEntity.TYPE_RATING, remoteId, "song", buildJsonObj { put("rating", clamped) }.toJsonString())
                }
            }
            albumId != null -> {
                navidromeDao.updateAlbumRating(albumId, clamped)
                val extra = navidromeDao.albumExtra(albumId) ?: return
                val account = accountFor(extra.serverId) ?: return
                val ok = runCatching { subsonic.setRating(account, extra.remoteId, clamped); true }.getOrDefault(false)
                if (!ok) {
                    enqueueMutation(account.id, PendingMutationEntity.TYPE_RATING, extra.remoteId, "album", buildJsonObj { put("rating", clamped) }.toJsonString())
                }
            }
            artistName != null -> {
                navidromeDao.updateArtistRating(artistName, clamped)
                val extra = navidromeDao.artistExtraByName(artistName) ?: return
                val account = accountFor(extra.serverId) ?: return
                val ok = runCatching { subsonic.setRating(account, extra.remoteId, clamped); true }.getOrDefault(false)
                if (!ok) {
                    enqueueMutation(account.id, PendingMutationEntity.TYPE_RATING, extra.remoteId, "artist", buildJsonObj { put("rating", clamped) }.toJsonString())
                }
            }
        }
    }

    suspend fun reportScrobble(songId: Long, playedAtMs: Long, submission: Boolean = true, positionMs: Long = 0L, state: String = "playing") {
        if (!settings.scrobbleToServer.first()) return
        val song = songDao.getById(songId) ?: return
        val remoteId = song.remoteId ?: return
        val account = accountFor(song.serverId) ?: return
        if (!account.scrobblingEnabled) return

        if (!submission) {
            runCatching {
                if (account.supportsExtension("playbackReport")) {
                    subsonic.reportPlayback(account, remoteId, positionMs, state)
                } else if (state == "playing") {
                    subsonic.scrobble(account, listOf(remoteId), submission = false, positionSec = (positionMs / 1000L).toInt())
                }
            }
            return
        }

        val ok = runCatching {
            subsonic.scrobble(account, listOf(remoteId), listOf(playedAtMs), submission = true)
            true
        }.getOrDefault(false)
        if (!ok) {
            enqueueMutation(
                serverId = account.id,
                type = PendingMutationEntity.TYPE_SCROBBLE,
                targetId = remoteId,
                targetKind = "song",
                payloadJson = buildJsonObj { put("time", playedAtMs) }.toJsonString(),
            )
        }
    }

    suspend fun flushPendingMutations(account: ServerAccount? = activeAccount.value) {
        val acc = account ?: return
        val pending = navidromeDao.pendingMutations(acc.id)
        for (m in pending) {
            val succeeded = runCatching {
                when (m.type) {
                    PendingMutationEntity.TYPE_STAR, PendingMutationEntity.TYPE_UNSTAR -> {
                        val star = m.type == PendingMutationEntity.TYPE_STAR
                        when (m.targetKind) {
                            "album" -> subsonic.setStar(acc, star, albumIds = listOf(m.targetId))
                            "artist" -> subsonic.setStar(acc, star, artistIds = listOf(m.targetId))
                            else -> subsonic.setStar(acc, star, ids = listOf(m.targetId))
                        }
                        true
                    }
                    PendingMutationEntity.TYPE_RATING -> {
                        val r = MiniJson.parseObject(m.payloadJson).int("rating", 0)
                        subsonic.setRating(acc, m.targetId, r)
                        true
                    }
                    PendingMutationEntity.TYPE_SCROBBLE -> {
                        val t = MiniJson.parseObject(m.payloadJson).long("time", m.createdAtMs)
                        subsonic.scrobble(acc, listOf(m.targetId), listOf(t), submission = true)
                        true
                    }
                    PendingMutationEntity.TYPE_PLAYLIST_PUSH -> pushPlaylist(acc, m.targetId.toLong())
                    PendingMutationEntity.TYPE_PLAYLIST_DELETE -> {
                        subsonic.deletePlaylist(acc, m.targetId)
                        true
                    }
                    PendingMutationEntity.TYPE_BOOKMARK_CREATE -> {
                        val obj = MiniJson.parseObject(m.payloadJson)
                        subsonic.createBookmark(acc, m.targetId, obj.long("positionMs", 0L), obj.str("comment"))
                        true
                    }
                    PendingMutationEntity.TYPE_BOOKMARK_DELETE -> {
                        subsonic.deleteBookmark(acc, m.targetId)
                        true
                    }
                    else -> true
                }
            }.getOrDefault(false)
            if (succeeded) {
                navidromeDao.deleteMutation(m.id)
            } else {
                break // Still offline; retry next time
            }
        }
    }

    /** Queues "make the server's copy of this playlist match the local one" (one per playlist) and tries it right away. */
    suspend fun queuePlaylistPush(localId: Long) {
        val account = activeAccount.value ?: return
        val id = localId.toString()
        val queued = navidromeDao.pendingMutations(account.id).any { it.type == PendingMutationEntity.TYPE_PLAYLIST_PUSH && it.targetId == id }
        if (!queued) enqueueMutation(account.id, PendingMutationEntity.TYPE_PLAYLIST_PUSH, id, "playlist")
        scope.launch { flushPendingMutations(account) }
    }

    /** The playlist is gone locally: forget its pending push and delete the server's copy (nothing to do if it never got one). */
    suspend fun queuePlaylistDelete(localId: Long, remoteId: String?) {
        val account = activeAccount.value ?: return
        navidromeDao.pendingMutations(account.id)
            .filter { it.type == PendingMutationEntity.TYPE_PLAYLIST_PUSH && it.targetId == localId.toString() }
            .forEach { navidromeDao.deleteMutation(it.id) }
        if (remoteId.isNullOrBlank()) return
        enqueueMutation(account.id, PendingMutationEntity.TYPE_PLAYLIST_DELETE, remoteId, "playlist")
        scope.launch { flushPendingMutations(account) }
    }

    /** True when done (also when the playlist no longer exists). Create-with-id replaces the song list; the name goes through update. */
    private suspend fun pushPlaylist(account: ServerAccount, localId: Long): Boolean {
        val playlist = playlistDao.getById(localId) ?: return true
        val songIds = playlistDao.remoteSongIds(localId)
        val remoteId = playlist.remoteId
        if (remoteId.isNullOrBlank()) {
            val created = subsonic.createPlaylist(account, playlist.name, songIds)
            if (created.remoteId.isBlank()) return false
            playlistDao.setRemoteId(localId, created.remoteId)
        } else {
            subsonic.createPlaylist(account, playlist.name, songIds, playlistRemoteId = remoteId)
            subsonic.updatePlaylist(account, remoteId, name = playlist.name)
        }
        return true
    }

    private suspend fun enqueueMutation(
        serverId: Long,
        type: String,
        targetId: String,
        targetKind: String,
        payloadJson: String = "{}",
    ) {
        navidromeDao.insertMutation(
            PendingMutationEntity(
                serverId = serverId,
                type = type,
                targetId = targetId,
                targetKind = targetKind,
                payloadJson = payloadJson,
                createdAtMs = System.currentTimeMillis(),
            ),
        )
    }

    // --- Rich Metadata, Lyrics, Sonic Similarity & Top Songs ---

    fun observeAlbumExtra(albumId: Long): Flow<AlbumExtraInfo?> =
        navidromeDao.observeAlbumExtra(albumId).map { it?.toModel() }

    suspend fun enrichAlbumInfo(albumId: Long) {
        val current = navidromeDao.albumExtra(albumId)?.toModel() ?: return
        val account = accountFor(current.serverId) ?: return
        val (notes, mbzId, lastFmUrl) = runCatching { subsonic.getAlbumInfo2(account, current.remoteId) }.getOrNull() ?: return
        if (notes != null || mbzId != null || lastFmUrl != null) {
            val updated = current.copy(
                description = notes ?: current.description,
                mbzAlbumId = mbzId ?: current.mbzAlbumId,
                externalUrl = lastFmUrl ?: current.externalUrl,
            )
            navidromeDao.upsertAlbumExtras(listOf(updated.toEntity()))
        }
    }

    fun observeArtistExtra(artistName: String): Flow<ArtistExtraInfo?> =
        navidromeDao.observeArtistExtraByName(artistName).map { it?.toModel() }

    suspend fun enrichArtistInfo(artistName: String) {
        val current = navidromeDao.artistExtraByName(artistName)?.toModel() ?: return
        val account = accountFor(current.serverId) ?: return
        val info = runCatching { subsonic.getArtistInfo2(account, current.remoteId) }.getOrNull() ?: return
        val updated = current.copy(
            biography = info.biography ?: current.biography,
            mbzArtistId = info.mbzArtistId ?: current.mbzArtistId,
            externalUrl = info.externalUrl ?: current.externalUrl,
            similarArtists = info.similarArtists.ifEmpty { current.similarArtists },
        )
        navidromeDao.upsertArtistExtras(listOf(updated.toEntity()))
    }

    suspend fun fetchRemoteLyrics(songId: Long): Lyrics? {
        val song = songDao.getById(songId) ?: return null
        val remoteId = song.remoteId ?: return null
        val account = accountFor(song.serverId) ?: return null
        return runCatching {
            subsonic.getLyricsBySongId(account, remoteId, enhanced = true)
                ?: subsonic.getLegacyLyrics(account, song.artist, song.title)
        }.getOrNull()
    }

    suspend fun instantMixForSong(song: Song, count: Int = 30): List<Song> {
        val remoteId = song.remoteId ?: return emptyList()
        val account = accountFor(song.serverId) ?: return emptyList()
        if (account.supportsExtension("sonicSimilarity")) {
            val sonic = runCatching { subsonic.getSonicSimilarTracks(account, remoteId, count) }.getOrDefault(emptyList())
            if (sonic.isNotEmpty()) return listOf(song) + sonic.map { it.song }
        }
        val similar = runCatching { subsonic.getSimilarSongs2(account, remoteId, count) }.getOrDefault(emptyList())
        return if (similar.isNotEmpty()) listOf(song) + similar else listOf(song)
    }

    // --- Playlists, Smart Playlists & M3U ---

    suspend fun createSmartPlaylist(
        name: String,
        comment: String,
        isPublic: Boolean,
        criteria: SmartPlaylistCriteria,
    ): Boolean {
        val account = activeAccount.value ?: return false
        return runCatching {
            nativeClient.createSmartPlaylist(account, name, comment, isPublic, criteria)
            syncPlaylists(account)
            true
        }.getOrDefault(false)
    }

    suspend fun importM3uPlaylist(m3uContent: String): Boolean {
        val account = activeAccount.value ?: return false
        return runCatching {
            nativeClient.importM3uPlaylist(account, m3uContent)
            syncPlaylists(account)
            true
        }.getOrDefault(false)
    }

    suspend fun exportM3uPlaylist(playlistId: Long): String? {
        val entity = playlistDao.getById(playlistId) ?: return null
        val remoteId = entity.remoteId ?: return null
        val account = accountFor(entity.serverId) ?: return null
        return runCatching { nativeClient.exportM3uPlaylist(account, remoteId) }.getOrNull()
    }

    // --- Shares & Radio ---

    suspend fun getShares(): List<ShareLink> {
        val account = activeAccount.value ?: return emptyList()
        return runCatching { subsonic.getShares(account) }.getOrDefault(emptyList())
    }

    suspend fun createShareForSong(song: Song, description: String = "", expiresMs: Long? = null, downloadable: Boolean = true): ShareLink? {
        val remoteId = song.remoteId ?: return null
        val account = accountFor(song.serverId) ?: return null
        return runCatching { subsonic.createShare(account, listOf(remoteId), description, expiresMs, downloadable) }.getOrNull()
    }

    suspend fun createShareForAlbum(albumId: Long, description: String = "", expiresMs: Long? = null, downloadable: Boolean = true): ShareLink? {
        val extra = navidromeDao.albumExtra(albumId) ?: return null
        val account = accountFor(extra.serverId) ?: return null
        return runCatching { subsonic.createShare(account, listOf(extra.remoteId), description, expiresMs, downloadable) }.getOrNull()
    }

    suspend fun createShareForPlaylist(playlistId: Long, description: String = "", expiresMs: Long? = null, downloadable: Boolean = true): ShareLink? {
        val pl = playlistDao.getById(playlistId) ?: return null
        val remoteId = pl.remoteId ?: return null
        val account = accountFor(pl.serverId) ?: return null
        return runCatching { subsonic.createShare(account, listOf(remoteId), description, expiresMs, downloadable) }.getOrNull()
    }

    suspend fun deleteShare(shareId: String) {
        val account = activeAccount.value ?: return
        runCatching { subsonic.deleteShare(account, shareId) }
    }

    suspend fun createRadioStation(name: String, streamUrl: String, homepageUrl: String? = null) {
        val account = activeAccount.value ?: return
        runCatching {
            subsonic.createInternetRadioStation(account, name, streamUrl, homepageUrl)
            val stations = subsonic.getInternetRadioStations(account)
            navidromeDao.deleteRadioStationsByServer(account.id.coerceAtLeast(1L))
            navidromeDao.upsertRadioStations(stations.map { it.toEntity() })
        }
    }

    suspend fun deleteRadioStation(station: RadioStation) {
        val account = accountFor(station.serverId) ?: return
        navidromeDao.deleteRadioStation(station.id)
        runCatching { subsonic.deleteInternetRadioStation(account, station.remoteId) }
    }

    // --- Admin & Maintenance ---

    suspend fun refreshScanStatus(): ScanStatusInfo {
        val account = activeAccount.value ?: return ScanStatusInfo()
        val st = runCatching { subsonic.getScanStatus(account) }.getOrDefault(ScanStatusInfo())
        _scanStatus.value = st
        return st
    }

    suspend fun triggerServerScan(fullScan: Boolean = false): ScanStatusInfo {
        val account = activeAccount.value ?: return ScanStatusInfo()
        val st = runCatching { subsonic.startScan(account, fullScan) }.getOrDefault(ScanStatusInfo())
        _scanStatus.value = st
        return st
    }

    suspend fun getMissingFiles(): List<MissingFileItem> {
        val account = activeAccount.value ?: return emptyList()
        return runCatching { nativeClient.getMissingFiles(account) }.getOrDefault(emptyList())
    }

    suspend fun purgeMissingFiles(ids: List<String> = emptyList()): Boolean {
        val account = activeAccount.value ?: return false
        return runCatching { nativeClient.deleteMissingFiles(account, ids) }.getOrDefault(false)
    }

    suspend fun getNowPlayingSessions(): List<NowPlayingSession> {
        val account = activeAccount.value ?: return emptyList()
        return runCatching { subsonic.getNowPlaying(account) }.getOrDefault(emptyList())
    }

    suspend fun refreshEntityMetadata(kindPrefix: String, remoteId: String): Boolean {
        val account = activeAccount.value ?: return false
        return runCatching { nativeClient.refreshMetadata(account, kindPrefix, remoteId) }.getOrDefault(false)
    }

    /** The artist's rating on the server, or null for an artist that is not on one (ratings are a Navidrome feature). */
    suspend fun remoteArtistRating(artistName: String): Int? = navidromeDao.artistExtraByName(artistName)?.userRating

    suspend fun uploadCustomArtistImage(artistName: String, imageBytes: ByteArray): Boolean {
        val extra = navidromeDao.artistExtraByName(artistName) ?: return false
        val account = accountFor(extra.serverId) ?: return false
        return runCatching { nativeClient.uploadEntityImage(account, "artist", extra.remoteId, imageBytes) }.getOrDefault(false)
    }

    suspend fun deleteCustomArtistImage(artistName: String): Boolean {
        val extra = navidromeDao.artistExtraByName(artistName) ?: return false
        val account = accountFor(extra.serverId) ?: return false
        return runCatching { nativeClient.deleteEntityImage(account, "artist", extra.remoteId) }.getOrDefault(false)
    }

    private suspend fun accountFor(serverId: Long): ServerAccount? =
        if (serverId > 0L) navidromeDao.accountById(serverId)?.toModel() ?: activeAccount.value
        else activeAccount.value ?: navidromeDao.activeAccount()?.toModel()

    private companion object {
        const val TAG = "NavidromeRepo"
    }
}
