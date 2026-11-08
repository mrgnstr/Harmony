package com.mrgnstr.harmony.feature.artist

import com.mrgnstr.harmony.core.playback.QueueSourceKind
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mrgnstr.harmony.core.artwork.ArtworkColorStore
import com.mrgnstr.harmony.core.data.*
import com.mrgnstr.harmony.core.model.*
import com.mrgnstr.harmony.core.playback.PlayerConnection
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

import java.io.File

internal data class ArtistUiState(
    val name: String,
    val loaded: Boolean = false,
    val songs: List<Song> = emptyList(),
    val rankedSongs: List<Song> = emptyList(),
    val albums: List<Album> = emptyList(),
    val singles: List<Album> = emptyList(),
    val featuredOn: List<Album> = emptyList(),
    val latest: Album? = null,
    val hero: Album? = null,
    val accent: Int? = null,
    val imagePath: String? = null,
    /** The picture shown: the user's own photo, else the server's. */
    val imageRatio: Float = 1f,
    /** Whether [imagePath] is the user's own photo (which can be reset), not the server's. */
    val customImage: Boolean = false,
    val logoPath: String? = null,
    val playingId: Long? = null,
    val favourite: Boolean = false,
    val remoteExtra: ArtistExtraInfo? = null,
)
    /** The library's artists by lower-cased name, to show a similar artist the way every other artist tile does. */
    val libraryArtists: Map<String, Artist> = emptyMap(),

@HiltViewModel(assistedFactory = ArtistViewModel.Factory::class)
class ArtistViewModel @AssistedInject constructor(
    @Assisted private val name: String,
    repository: LibraryRepository,
    history: HistoryRepository,
    private val images: ArtistImageStore,
    private val favourites: FavouritesRepository,
    private val navidrome: NavidromeRepository,
    private val player: PlayerConnection,
    colors: ArtworkColorStore,
) : ViewModel() {
    @AssistedFactory interface Factory { fun create(name: String): ArtistViewModel }

    init {
        viewModelScope.launch { navidrome.enrichArtistInfo(name) }
    }

    private val library = combine(repository.songs, repository.albums, history.playCounts) { songs, albums, counts ->
        val index = ArtistIndex.of(songs)
        val own = albums.filter { index.credits(it.artist, name) }
        val mine = songs.filter { index.credits(it.owner, name) }
            .sortedWith(compareByDescending<Song> { it.year }.thenBy { it.album }.thenBy { it.discNumber }.thenBy { it.trackNumber })
        val guests = songs.filter { !index.credits(it.owner, name) && it.featuresArtist(name, index) }
        val discography = discographyOf(own, guests, albums)
        ArtistUiState(
            name = name, loaded = true, songs = mine,
            rankedSongs = topSongs(mine, own, counts, limit = mine.size),
            albums = discography.albums, singles = discography.singles, featuredOn = discography.featuredOn,
            latest = own.sortedWith(NewestFirst).mainEditions().firstOrNull(), hero = heroOf(own),
        )
    }.flowOn(Dispatchers.Default).shareIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), replay = 1)

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    private val accent = combine(library.map { it.hero }, images.images, serverImages.images) { hero, photos, server ->
        hero to (photos[name] ?: server[name])?.path
    }.distinctUntilChanged().mapLatest { (hero, customImage) ->
        if (customImage != null) colors.accentForImage(customImage)
        else hero?.let { colors.paletteFor(it.artSongId, it.id)?.takeIf { p -> p.hasArt }?.primary }
    }
    private val libraryArtists = repository.artists.map { list -> list.associateBy { it.name.lowercase() } }
    private class ArtParts(val photo: ArtistImage?, val custom: Boolean, val logo: ArtistImage?, val extra: ArtistExtraInfo?, val libraryArtists: Map<String, Artist>)
    private val art = combine(combine(images.images, serverImages.images) { own, server -> own[name] to server[name] }, images.logos, navidrome.observeArtistExtra(name), libraryArtists) { (own, server), logos, extra, local ->
        ArtParts(own ?: server, own != null, logos[name], extra, local)
    }
    internal val state = combine(library, accent, art, player.state, favourites.artists) { base, accent, parts, playback, favourites ->
        base.copy(
            accent = accent,
            imagePath = parts.photo?.path,
            customImage = parts.custom,
            imageRatio = parts.photo?.ratio ?: 1f,
            logoPath = parts.logo?.path,
            playingId = playback.mediaId?.toLongOrNull(),
            favourite = name in favourites,
            remoteExtra = parts.extra,
            libraryArtists = parts.libraryArtists,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ArtistUiState(name))

    val imageError = MutableStateFlow<String?>(null)
    fun play(songs: List<Song>, index: Int = 0) { if (songs.isNotEmpty()) player.playQueue(songs, index, source = name, kind = QueueSourceKind.Artist) }
    fun shuffle() = play(state.value.songs.shuffled())
    fun playRelease(album: Album) = play(state.value.songs.filter { it.albumId == album.id && it.album == album.title })
    fun playNext(song: Song) = player.playNext(listOf(song))
    fun addToQueue(song: Song) = player.playLast(listOf(song))
    fun toggleFavourite() { viewModelScope.launch { favourites.toggleArtist(name) } }
    fun setImage(uri: String, logo: Boolean = false) {
        viewModelScope.launch {
            val saved = if (logo) images.setLogo(name, uri) else images.set(name, uri)
            if (!saved) {
                imageError.value = "This image could not be read. Try another image."
            } else if (!logo) {
                images.images.value[name]?.path?.let { path ->
                    runCatching { File(path).readBytes() }.getOrNull()?.let { bytes ->
                        navidrome.uploadCustomArtistImage(name, bytes)
                    }
                }
            }
        }
    }
    fun clearImage(logo: Boolean = false) {
        viewModelScope.launch {
            if (logo) images.clearLogo(name)
            else {
                images.clear(name)
                navidrome.deleteCustomArtistImage(name)
            }
        }
    }
    fun refreshServerMetadata() {
        val remoteId = state.value.remoteExtra?.remoteId ?: return
        viewModelScope.launch {
            navidrome.refreshEntityMetadata("ar", remoteId)
            navidrome.enrichArtistInfo(name)
        }
    }
}
