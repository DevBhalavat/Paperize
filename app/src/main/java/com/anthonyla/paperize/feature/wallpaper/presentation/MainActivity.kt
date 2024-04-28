package com.anthonyla.paperize.feature.wallpaper.presentation

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import com.anthonyla.paperize.core.SettingsConstants
import com.anthonyla.paperize.data.settings.SettingsDataStore
import com.anthonyla.paperize.feature.wallpaper.presentation.album.AlbumsEvent
import com.anthonyla.paperize.feature.wallpaper.presentation.album.AlbumsViewModel
import com.anthonyla.paperize.feature.wallpaper.presentation.settings_screen.SettingsEvent
import com.anthonyla.paperize.feature.wallpaper.presentation.settings_screen.SettingsViewModel
import com.anthonyla.paperize.feature.wallpaper.presentation.themes.PaperizeTheme
import com.anthonyla.paperize.feature.wallpaper.presentation.wallpaper_screen.WallpaperEvent
import com.anthonyla.paperize.feature.wallpaper.presentation.wallpaper_screen.WallpaperScreenViewModel
import com.anthonyla.paperize.feature.wallpaper.wallpaperservice.WallpaperService
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.runBlocking
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val albumsViewModel: AlbumsViewModel by viewModels()
    private val settingsViewModel: SettingsViewModel by viewModels()
    private val wallpaperScreenViewModel: WallpaperScreenViewModel by viewModels()
    private val context = this
    @Inject lateinit var settingsDataStoreImpl: SettingsDataStore
    override fun onCreate(savedInstanceState: Bundle?) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 0)
        }
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.SET_WALLPAPER) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(context as Activity, arrayOf(Manifest.permission.SET_WALLPAPER), 0)
        }
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val splashScreen = installSplashScreen()
        splashScreen.setKeepOnScreenCondition { settingsViewModel.setKeepOnScreenCondition }
        setContent {
            // Refresh albums when the app is resumed
            val lifecycleEvent = rememberLifecycleEvent()
            LaunchedEffect(lifecycleEvent) {
                if (lifecycleEvent == Lifecycle.Event.ON_RESUME) {
                    albumsViewModel.onEvent(AlbumsEvent.RefreshAlbums)
                    wallpaperScreenViewModel.onEvent(WallpaperEvent.Refresh)
                    if (!WallpaperService.isRunning && wallpaperScreenViewModel.state.value.selectedAlbum != null) {
                        val timeInMinutes = settingsDataStoreImpl.getInt("wallpaper_change_interval") ?: SettingsConstants.WALLPAPER_CHANGE_INTERVAL_DEFAULT
                        val intent = Intent(this@MainActivity, WallpaperService::class.java).apply {
                            action = WallpaperService.Actions.START.toString()
                            putExtra("timeInMinutes", timeInMinutes)
                        }
                        this@MainActivity.startForegroundService(intent)
                    }
                }
            }
            val isFirstLaunch = runBlocking { settingsDataStoreImpl.getBoolean(SettingsConstants.FIRST_LAUNCH) } ?: true
            PaperizeTheme(settingsViewModel.state) {
                Surface(tonalElevation = 5.dp) {
                    PaperizeApp(isFirstLaunch)
                }
            }
        }
    }
}

/**
 * Return the latest lifecycle event of the [LifecycleOwner].
 */
@Composable
fun rememberLifecycleEvent(lifecycleOwner: LifecycleOwner = LocalLifecycleOwner.current): Lifecycle.Event {
    var state by remember { mutableStateOf(Lifecycle.Event.ON_ANY) }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event -> state = event }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }
    return state
}