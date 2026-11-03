package com.mrgnstr.harmony.core.device

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/** Computed once at startup. User overrides ("reduce transparency/motion") join in with the settings screen. */
data class DeviceCapabilities(
    val sdk: Int,
    val isLowRam: Boolean,
    val totalMemBytes: Long,
    val cores: Int,
    val tier: VisualTier,
) {
    companion object {
        fun from(context: Context): DeviceCapabilities {
            val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            val info = ActivityManager.MemoryInfo().also(am::getMemoryInfo)
            val sdk = Build.VERSION.SDK_INT
            val cores = Runtime.getRuntime().availableProcessors()
            return DeviceCapabilities(sdk, am.isLowRamDevice, info.totalMem, cores,
                VisualTier.resolve(sdk, am.isLowRamDevice, info.totalMem, cores))
        }
    }
}

@Module
@InstallIn(SingletonComponent::class)
object DeviceModule {
    @Provides
    @Singleton
    fun capabilities(@ApplicationContext context: Context): DeviceCapabilities = DeviceCapabilities.from(context)
}
