package com.example.tuner

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.tuner.ui.channels.ChannelListScreen
import com.example.tuner.ui.channels.ChannelListViewModel
import com.example.tuner.ui.settings.SettingsScreen
import com.example.tuner.ui.sources.CustomSourceManagerScreen
import com.example.tuner.ui.splash.SplashScreen
import com.example.tuner.ui.theme.TunerBackground
import com.example.tuner.ui.theme.TunerTheme

/**
 * Single-activity app. Screen switching (list / custom-source manager / settings) is local
 * UI state — no nav graph needed for three screens.
 *
 * Extends [FragmentActivity] (not plain ComponentActivity) because the Cast button's device
 * picker is shown as a DialogFragment and crashes without a FragmentManager host.
 */
class MainActivity : FragmentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val app = application as TunerApplication

        setContent {
            val viewModel: ChannelListViewModel = viewModel(
                factory = ChannelListViewModel.factory(
                    channelRepository = app.channelRepository,
                    customSourceRepository = app.customSourceRepository,
                    appStateRepository = app.appStateRepository,
                    favoritesRepository = app.favoritesRepository,
                    historyRepository = app.historyRepository,
                    parentalControlRepository = app.parentalControlRepository
                )
            )
            val uiState by viewModel.uiState.collectAsState()

            TunerTheme(themeMode = uiState.themeMode) {
                TunerApp(viewModel)
            }
        }
    }

    // Parent unlock is meant for "the parent is holding the phone right now" — once the app
    // leaves the foreground, the next person to open it has to enter the PIN again.
    override fun onStop() {
        super.onStop()
        (application as TunerApplication).parentalControlRepository.lock()
    }
}

private enum class Screen { CHANNELS, CUSTOM_SOURCES, SETTINGS }

@Composable
private fun TunerApp(viewModel: ChannelListViewModel) {
    var screen by remember { mutableStateOf(Screen.CHANNELS) }
    var showSplash by remember { mutableStateOf(true) }

    // App content draws edge-to-edge (enableEdgeToEdge()), so pad the status bar in here
    // rather than letting the system status bar icons overlap the TUNER header/player.
    val rootModifier = Modifier
        .fillMaxSize()
        .background(TunerBackground)
        .statusBarsPadding()

    if (showSplash) {
        SplashScreen(onFinished = { showSplash = false })
        return
    }

    when (screen) {
        Screen.CUSTOM_SOURCES -> CustomSourceManagerScreen(
            viewModel = viewModel,
            onBack = { screen = Screen.CHANNELS },
            modifier = rootModifier
        )
        Screen.SETTINGS -> SettingsScreen(
            viewModel = viewModel,
            onBack = { screen = Screen.CHANNELS },
            modifier = rootModifier
        )
        Screen.CHANNELS -> ChannelListScreen(
            viewModel = viewModel,
            onOpenCustomSourceManager = { screen = Screen.CUSTOM_SOURCES },
            onOpenSettings = { screen = Screen.SETTINGS },
            modifier = rootModifier
        )
    }
}
