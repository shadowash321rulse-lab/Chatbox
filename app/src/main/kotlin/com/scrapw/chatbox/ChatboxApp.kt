package com.scrapw.chatbox

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.scrapw.chatbox.ui.ChatboxViewModel
import com.scrapw.chatbox.ui.mainScreen.DebugScreen
import com.scrapw.chatbox.ui.mainScreen.MainDashboardScreen
import com.scrapw.chatbox.ui.mainScreen.NowPlayingScreen
import com.scrapw.chatbox.ui.mainScreen.SettingsSlimScreen

private enum class AppPage(val label: String) {
    Main("Main"),
    NowPlaying("Now Playing"),
    Debug("Debug"),
    Settings("Settings"),
}

@Composable
fun ChatboxApp(
    chatboxViewModel: ChatboxViewModel = viewModel(factory = ChatboxViewModel.Factory)
) {
    var page by remember { mutableStateOf(AppPage.Main) }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(title = { Text("VRC-A") })
        },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = page == AppPage.Main,
                    onClick = { page = AppPage.Main },
                    icon = { Icon(Icons.Filled.Home, contentDescription = null) },
                    label = { Text(AppPage.Main.label) }
                )
                NavigationBarItem(
                    selected = page == AppPage.NowPlaying,
                    onClick = { page = AppPage.NowPlaying },
                    icon = { Icon(Icons.Filled.LibraryMusic, contentDescription = null) },
                    label = { Text(AppPage.NowPlaying.label) }
                )
                NavigationBarItem(
                    selected = page == AppPage.Debug,
                    onClick = { page = AppPage.Debug },
                    icon = { Icon(Icons.Filled.BugReport, contentDescription = null) },
                    label = { Text(AppPage.Debug.label) }
                )
                NavigationBarItem(
                    selected = page == AppPage.Settings,
                    onClick = { page = AppPage.Settings },
                    icon = { Icon(Icons.Filled.Settings, contentDescription = null) },
                    label = { Text(AppPage.Settings.label) }
                )
            }
        }
    ) { _ ->
        when (page) {
            AppPage.Main -> MainDashboardScreen(chatboxViewModel, Modifier)
            AppPage.NowPlaying -> NowPlayingScreen(chatboxViewModel, Modifier)
            AppPage.Debug -> DebugScreen(chatboxViewModel, Modifier)
            AppPage.Settings -> SettingsSlimScreen(chatboxViewModel, Modifier)
        }
    }
}
