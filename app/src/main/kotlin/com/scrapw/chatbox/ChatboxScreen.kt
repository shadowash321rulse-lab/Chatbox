package com.scrapw.chatbox

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.scrapw.chatbox.ui.ChatboxViewModel
import com.scrapw.chatbox.ui.mainScreen.MainScreen
import com.scrapw.chatbox.ui.settingsScreen.SettingsScreen

private enum class AppPage { Main, Settings }

@Composable
fun ChatboxScreen(
    modifier: Modifier = Modifier,
    chatboxViewModel: ChatboxViewModel = viewModel(factory = ChatboxViewModel.Factory)
) {
    var page by remember { mutableStateOf(AppPage.Main) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = if (page == AppPage.Main) "VRC-A" else "Settings",
                        style = MaterialTheme.typography.titleLarge
                    )
                },
                navigationIcon = {
                    if (page == AppPage.Settings) {
                        IconButton(onClick = { page = AppPage.Main }) {
                            Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                        }
                    }
                },
                actions = {
                    if (page == AppPage.Main) {
                        IconButton(onClick = { page = AppPage.Settings }) {
                            Icon(Icons.Filled.Settings, contentDescription = "Settings")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors()
            )
        }
    ) { padding ->
        when (page) {
            AppPage.Main -> MainScreen(
                modifier = modifier,
                chatboxViewModel = chatboxViewModel
            )

            AppPage.Settings -> SettingsScreen(
                chatboxViewModel = chatboxViewModel,
                modifier = modifier
            )
        }
    }
}
