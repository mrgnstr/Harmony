package com.mrgnstr.harmony.feature.onboarding

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

/** API 33 split storage into per-type permissions; before that audio is under READ_EXTERNAL_STORAGE. */
val audioPermission: String =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) Manifest.permission.READ_MEDIA_AUDIO
    else Manifest.permission.READ_EXTERNAL_STORAGE

@Stable
class AudioPermissionState internal constructor(
    private val context: Context,
    private val launch: () -> Unit,
) {
    var granted by mutableStateOf(isGranted(context))
        internal set

    /** True once the system dialog has been answered with "deny" at least once. */
    var denied by mutableStateOf(false)
        internal set

    fun request() = launch()

    fun openAppSettings() {
        context.startActivity(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }

    internal fun refresh() { granted = isGranted(context) }

    private companion object {
        fun isGranted(c: Context) =
            ContextCompat.checkSelfPermission(c, audioPermission) == PackageManager.PERMISSION_GRANTED
    }
}

@Composable
fun rememberAudioPermissionState(): AudioPermissionState {
    val context = LocalContext.current
    var state: AudioPermissionState? = null
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        state?.let { it.granted = ok; it.denied = !ok }
    }
    state = remember(context) { AudioPermissionState(context) { launcher.launch(audioPermission) } }

    // The user may grant it from system settings and come back.
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle, state) {
        val observer = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_RESUME) state.refresh() }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    return state
}
