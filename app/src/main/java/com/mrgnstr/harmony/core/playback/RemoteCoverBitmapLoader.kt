package com.mrgnstr.harmony.core.playback

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.media3.common.MediaMetadata
import androidx.media3.common.util.BitmapLoader
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSourceBitmapLoader
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.SettableFuture
import com.mrgnstr.harmony.core.artwork.ArtworkDecoder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * The session's bitmap loader. Remote songs carry no artwork URI (the cover needs the server login), so their cover comes from the
 * app's own artwork pipeline (cached, authenticated, ThumbHash fallback) by song and album id, for the notification, lock screen and
 * Bluetooth. Anything that does carry artwork goes to Media3's default loader.
 */
@UnstableApi
internal class RemoteCoverBitmapLoader(private val context: Context) : BitmapLoader {
    private val default = DataSourceBitmapLoader(context)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun supportsMimeType(mimeType: String) = default.supportsMimeType(mimeType)

    override fun decodeBitmap(data: ByteArray) = default.decodeBitmap(data)

    override fun loadBitmap(uri: Uri) = default.loadBitmap(uri)

    override fun loadBitmapFromMetadata(metadata: MediaMetadata): ListenableFuture<Bitmap>? {
        val extras = metadata.extras
        val songId = extras?.getLong(EXTRA_SONG_ID, 0L) ?: 0L
        if (metadata.artworkData != null || metadata.artworkUri != null || songId >= 0L) return default.loadBitmapFromMetadata(metadata)
        val albumId = extras?.getLong(EXTRA_ALBUM_ID, 0L) ?: 0L
        val future = SettableFuture.create<Bitmap>()
        scope.launch {
            val bitmap = runCatching { ArtworkDecoder.decodeAsync(context, songId, albumId, COVER_PX) }.getOrNull()
            if (bitmap != null) future.set(bitmap) else future.setException(IllegalStateException("no cover"))
        }
        return future
    }

    private companion object { const val COVER_PX = 512 }
}
