package com.scrapw.chatbox

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.scrapw.chatbox.ui.ChatboxViewModel
import com.scrapw.chatbox.ui.mainScreen.MainScreen

// Keep this public so anything referencing ChatboxScreen.Main / ChatboxScreen.Settings still works.
enum class ChatboxScreen {
    Main,
    Settings
}

/**
 * NOTE:
 * - navController is optional ONLY so older code that calls ChatboxScreen(navController = ...) will compile.
 * - We don't require navigation libs/graphs to be wired up yet.
 */
@Composable
fun ChatboxScreen(
    navController: NavHostController? = null,
    chatboxViewModel: ChatboxViewModel
) {
    var page by remember { mutableStateOf(ChatboxScreen.Main) }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("VRC-A") }
            )
        },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = page == ChatboxScreen.Main,
                    onClick = { page = ChatboxScreen.Main },
                    icon = { Icon(Icons.Filled.Home, contentDescription = null) },
                    label = { Text("Main") }
                )
                NavigationBarItem(
                    selected = page == ChatboxScreen.Settings,
                    onClick = { page = ChatboxScreen.Settings },
                    icon = { Icon(Icons.Filled.Settings, contentDescription = null) },
                    label = { Text("Settings") }
                )
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            when (page) {
                ChatboxScreen.Main -> {
                    // Your real app UI lives here already
                    MainScreen(chatboxViewModel = chatboxViewModel)
                }

                ChatboxScreen.Settings -> {
                    // TEMP safe placeholder so we compile even if Settings screen is mid-refactor.
                    SettingsPlaceholder()
                }
            }
        }
    }
}

@Composable
private fun SettingsPlaceholder() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        horizontalAlignment = Alignment.Start
    ) {
        Text("Settings", style = MaterialTheme.typography.titleLarge)
        Text(
            "This page is temporarily simplified to keep builds stable.\n" +
                "We’ll re-add the clean SlimeVR-style settings layout next.",
            style = MaterialTheme.typography.bodyMedium
        )
    }
}
