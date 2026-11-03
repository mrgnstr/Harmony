package com.mrgnstr.harmony.core.data

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Custom playlist covers: the picked image is cropped to a square, scaled down and kept in app storage. */
@Singleton
class PlaylistCoverStore @Inject constructor(@ApplicationContext private val context: Context) {
    private val dir get() = File(context.filesDir, "playlist_covers").apply { mkdirs() }

    /** @return the saved file's path, or null if the image could not be read. */
    suspend fun save(uri: String): String? = withContext(Dispatchers.IO) {
        val file = File(dir, "cover-${System.nanoTime()}.jpg")
        if (saveSquareJpeg(context, uri, file)) file.absolutePath else null
    }

    fun delete(path: String?) {
        if (path != null) File(path).delete()
    }
}
