package com.mrgnstr.harmony.app

import android.app.Application
import android.util.Log
import coil3.ImageLoader
import coil3.SingletonImageLoader
import com.mrgnstr.harmony.core.artwork.ArtworkImageLoader
import com.mrgnstr.harmony.core.device.DeviceCapabilities
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class HarmonyApplication : Application(), SingletonImageLoader.Factory {
    @Inject lateinit var capabilities: DeviceCapabilities

    override fun onCreate() {
        super.onCreate()
        Log.i("Harmony", "visual tier=${capabilities.tier} sdk=${capabilities.sdk} ram=${capabilities.totalMemBytes shr 20}MB cores=${capabilities.cores} lowRam=${capabilities.isLowRam}")
    }

    override fun newImageLoader(context: coil3.PlatformContext): ImageLoader =
        ArtworkImageLoader.create(this, capabilities.tier)
}
