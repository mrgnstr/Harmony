package com.mrgnstr.harmony

import android.content.Context
import com.mrgnstr.harmony.core.playback.QueueStore

/**
 * The app's QueueStore singleton. The service injects it, so a test's PlayerConnection must share it to see the same
 * source and unshuffled order (reflection on the generated Hilt component: no test Hilt setup needed).
 */
fun appQueueStore(ctx: Context): QueueStore {
    val component = (ctx.applicationContext as dagger.hilt.internal.GeneratedComponentManagerHolder).componentManager().generatedComponent()
    val provider = generateSequence<Class<*>>(component.javaClass) { it.superclass }
        .flatMap { it.declaredFields.asSequence() }.first { it.name == "queueStoreProvider" }
        .apply { isAccessible = true }.get(component) as javax.inject.Provider<*>
    return provider.get() as QueueStore
}
