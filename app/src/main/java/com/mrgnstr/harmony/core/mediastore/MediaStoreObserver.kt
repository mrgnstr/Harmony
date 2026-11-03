package com.mrgnstr.harmony.core.mediastore

import android.content.Context
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.channels.BufferOverflow

@Singleton
class MediaStoreObserver @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    /** Emits whenever MediaStore audio changes. Callers debounce: a bulk copy fires hundreds of events. */
    fun changes(): Flow<Unit> = callbackFlow {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) { trySend(Unit) }
        }
        context.contentResolver.registerContentObserver(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, /* notifyForDescendants = */ true, observer,
        )
        awaitClose { context.contentResolver.unregisterContentObserver(observer) }
    }.buffer(1, BufferOverflow.DROP_OLDEST)
}
