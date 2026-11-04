package com.mrgnstr.harmony.app

import android.graphics.Color as AndroidColor
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.lifecycleScope
import com.mrgnstr.harmony.core.datastore.SettingsStore
import com.mrgnstr.harmony.core.designsystem.theme.SchemeFactory
import com.mrgnstr.harmony.core.device.DeviceCapabilities
import com.mrgnstr.harmony.core.model.BlurMode
import com.mrgnstr.harmony.core.model.ThemeMode
import com.mrgnstr.harmony.core.playback.EXTRA_OPEN_PLAYER
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.launch

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject lateinit var capabilities: DeviceCapabilities
    @Inject lateinit var settings: SettingsStore

    /** Null until the saved setting has been read; the splash screen stays up until then, so the wrong theme never flashes. */
    private var themeMode by mutableStateOf<ThemeMode?>(null)
    private var chromeBlur by mutableStateOf<BlurMode?>(null)
    private var coverBlur by mutableStateOf<BlurMode?>(null)
    /** Bumped each time the notification asks for Now Playing. */
    private var openPlayer by mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen().setKeepOnScreenCondition { themeMode == null || chromeBlur == null || coverBlur == null }
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) handle(intent)
        lifecycleScope.launch { settings.themeMode.collect { themeMode = it } }
        lifecycleScope.launch { settings.glassBlur.collect { chromeBlur = it } }
        lifecycleScope.launch { settings.coverBlur.collect { coverBlur = it } }
        setContent {
            val dark = when (themeMode ?: ThemeMode.System) {
                ThemeMode.System -> isSystemInDarkTheme()
                ThemeMode.Light -> false
                ThemeMode.Dark -> true
            }
            SystemBars(dark)
            val context = LocalContext.current
            val schemes = remember(context) { SchemeFactory(context) }
            HarmonyApp(dark, capabilities.tier, chromeBlur ?: BlurMode.Auto, coverBlur ?: BlurMode.Auto, schemes, openPlayer)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handle(intent)
    }

    private fun handle(intent: Intent?) {
        if (intent?.getBooleanExtra(EXTRA_OPEN_PLAYER, false) == true) openPlayer++
    }
}

/** Edge-to-edge, with status and navigation bar icons that suit the chosen theme (not only the system's). */
@Composable
private fun SystemBars(dark: Boolean) {
    val activity = LocalContext.current as ComponentActivity
    DisposableEffect(dark) {
        val style = if (dark) SystemBarStyle.dark(AndroidColor.TRANSPARENT) else SystemBarStyle.light(AndroidColor.TRANSPARENT, AndroidColor.TRANSPARENT)
        activity.enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
        // Gesture navigation: stop the system drawing its own contrast scrim behind the gesture pill.
        if (Build.VERSION.SDK_INT >= 29) activity.window.isNavigationBarContrastEnforced = false
        onDispose {}
    }
}
