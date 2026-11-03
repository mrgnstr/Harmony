package com.mrgnstr.harmony.feature.onboarding

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.mrgnstr.harmony.core.designsystem.icons.SymbolIcon
import com.mrgnstr.harmony.core.designsystem.icons.Symbols

/** Shown until the app may read the music library. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun OnboardingScreen(permission: AudioPermissionState) {
    Column(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface).safeDrawingPadding().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            Modifier.size(120.dp).clip(MaterialShapes.Cookie9Sided.toShape()).background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            SymbolIcon(Symbols.MusicNote, null, size = 56.dp, tint = MaterialTheme.colorScheme.onPrimaryContainer, fill = 1f)
        }
        Spacer(Modifier.height(32.dp))
        Text("Your music, here", style = MaterialTheme.typography.headlineLargeEmphasized, textAlign = TextAlign.Center)
        Spacer(Modifier.height(12.dp))
        Text(
            if (permission.denied) "Harmony needs access to your music files to play them. You can allow it in the app's settings."
            else "Harmony plays the music stored on this device. Allow access to your music files to get started.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(32.dp))
        Button(
            onClick = { if (permission.denied) permission.openAppSettings() else permission.request() },
            shapes = ButtonDefaults.shapes(),
            modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
        ) {
            Text(if (permission.denied) "Open settings" else "Allow access", style = MaterialTheme.typography.titleMedium)
        }
    }
}

/** API 33+: asks once for notifications (without them the media notification is hidden); a denial is simply accepted. */
@Composable
fun NotificationPermissionEffect() {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
    val context = LocalContext.current
    var asked by rememberSaveable { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    LaunchedEffect(asked) {
        if (asked) return@LaunchedEffect
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            asked = true
            launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}
