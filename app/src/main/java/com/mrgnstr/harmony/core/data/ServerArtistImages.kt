package com.mrgnstr.harmony.core.data

import android.content.Context
import android.graphics.BitmapFactory
import com.mrgnstr.harmony.core.artwork.RemoteArtworkBridge
import com.mrgnstr.harmony.core.common.ApplicationScope
import com.mrgnstr.harmony.core.database.ArtistCoverRow
import com.mrgnstr.harmony.core.database.NavidromeDao
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.withContext

/**
 * The server's pictures of its artists, kept as files beside the user's own photos ([ArtistImageStore]) so that everything that shows
 * an artist reads one kind of thing: a file path. A picture is downloaded once; its name carries the server's id for it (which holds
 * the image's hash), so a changed picture is fetched again and the old file dropped.
 */
@Singleton
class ServerArtistImages @Inject constructor(
    @ApplicationContext private val context: Context,
    private val navidromeDao: NavidromeDao,
    @ApplicationScope private val scope: CoroutineScope,
) {
    private val dir get() = File(context.filesDir, "artist_server").apply { mkdirs() }
    private val _images = MutableStateFlow(scan())
    val images: StateFlow<ArtistImages> get() = _images

    init {
        // Room re-emits after every sync, which also retries downloads that failed (offline, account not ready yet).
        scope.launch(Dispatchers.IO) { navidromeDao.observeArtistCovers().collectLatest { sync(it) } }
    }

    private fun scan(): ArtistImages = ArtistImages(
        dir.listFiles().orEmpty().filter { it.extension == "jpg" }
            .associate { it.name.substringBefore('-') to ArtistImage(it.absolutePath, ratioOf(it)) },
    )

    private fun ratioOf(file: File): Float {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        return if (bounds.outWidth > 0) bounds.outHeight / bounds.outWidth.toFloat() else 1f
    }

    private suspend fun sync(rows: List<ArtistCoverRow>) {
        val fetch = RemoteArtworkBridge.remoteByteFetcher
        val wanted = rows.associate { ArtistImageStore.keyOf(it.name) to "${ArtistImageStore.keyOf(it.name)}-${it.coverArtId}.jpg" }
        val existing = dir.listFiles().orEmpty()
        // Pictures the server no longer has, or has replaced.
        existing.filter { it.name !in wanted.values }.forEach { it.delete() }
        val have = existing.mapTo(HashSet()) { it.name }
        val missing = rows.filter { wanted.getValue(ArtistImageStore.keyOf(it.name)) !in have }
        if (fetch != null && missing.isNotEmpty()) {
            val limit = Semaphore(4)
            var done = 0
            withContext(Dispatchers.IO) {
                missing.map { row ->
                    async {
                        limit.withPermit {
                            val bytes = runCatching { fetch(row.coverArtId, PICTURE_PX) }.getOrNull()
                            if (bytes != null && bytes.isNotEmpty() && BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BOUNDS) == null) {
                                val file = File(dir, wanted.getValue(ArtistImageStore.keyOf(row.name)))
                                val tmp = File(dir, "${file.name}.tmp")
                                runCatching { tmp.writeBytes(bytes); tmp.renameTo(file) }
                            }
                            // Show them as they arrive rather than all at the end.
                            if (++done % 25 == 0) _images.value = scan()
                        }
                    }
                }.awaitAll()
            }
        }
        _images.value = scan()
    }

    private companion object {
        const val PICTURE_PX = 800
        val BOUNDS = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    }
}
