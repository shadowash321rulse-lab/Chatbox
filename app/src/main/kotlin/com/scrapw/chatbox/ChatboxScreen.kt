package com.scrapw.chatbox

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.scrapw.chatbox.ui.ChatboxViewModel
import kotlinx.coroutines.flow.collectLatest

private enum class AppPage(val title: String) {
    Dashboard("Dashboard"),
    Cycle("Cycle"),
    NowPlaying("Now Playing"),
    Debug("Debug"),
    Settings("Settings")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatboxScreen(
    chatboxViewModel: ChatboxViewModel
) {
    var page by rememberSaveable { mutableStateOf(AppPage.Dashboard) }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("VRC-A") },
                actions = {
                    // quick "send once" on Now Playing page, SlimeVR-ish convenience
                    if (page == AppPage.NowPlaying) {
                        IconButton(onClick = { chatboxViewModel.sendNowPlayingOnce() }) {
                            Icon(Icons.Filled.Send, contentDescription = "Send now playing once")
                        }
                    }
                }
            )
        },
        bottomBar = {
            NavigationBar {
                NavItem(AppPage.Dashboard, page, Icons.Filled.Home) { page = it }
                NavItem(AppPage.Cycle, page, Icons.Filled.Sync) { page = it }
                NavItem(AppPage.NowPlaying, page, Icons.Filled.MusicNote) { page = it }
                NavItem(AppPage.Debug, page, Icons.Filled.BugReport) { page = it }
                NavItem(AppPage.Settings, page, Icons.Filled.Settings) { page = it }
            }
        }
    ) { padding ->
        Box(
            Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            when (page) {
                AppPage.Dashboard -> DashboardPage(chatboxViewModel)
                AppPage.Cycle -> CyclePage(chatboxViewModel)
                AppPage.NowPlaying -> NowPlayingPage(chatboxViewModel)
                AppPage.Debug -> DebugPage(chatboxViewModel)
                AppPage.Settings -> SettingsPage(chatboxViewModel)
            }
        }
    }
}

@Composable
private fun NavItem(
    item: AppPage,
    current: AppPage,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: (AppPage) -> Unit
) {
    NavigationBarItem(
        selected = current == item,
        onClick = { onClick(item) },
        icon = { Icon(icon, contentDescription = item.title) },
        label = { Text(item.title) }
    )
}

@Composable
private fun PageContainer(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        content = content
    )
}

@Composable
private fun SectionCard(
    title: String,
    subtitle: String? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    ElevatedCard {
        Column(
            Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            if (!subtitle.isNullOrBlank()) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall)
            }
            content()
        }
    }
}

@Composable
private fun DashboardPage(vm: ChatboxViewModel) {
    val uiState by vm.messengerUiState.collectAsState()

    // Keep a local input field so the IP field isn't "buggy" / fighting state
    var ipInput by rememberSaveable { mutableStateOf(uiState.ipAddress) }
    LaunchedEffect(uiState.ipAddress) {
        // only update if user hasn't started editing to something else
        if (ipInput.isBlank() || ipInput == "127.0.0.1") ipInput = uiState.ipAddress
    }

    PageContainer {

        SectionCard(
            title = "Connection",
            subtitle = "Set your VRChat device IP (Quest/Phone) then Apply."
        ) {
            OutlinedTextField(
                value = ipInput,
                onValueChange = { ipInput = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text("Headset IP address") },
                placeholder = { Text("Example: 192.168.1.23") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
            )

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = { vm.ipAddressApply(ipInput.trim()) },
                    modifier = Modifier.weight(1f)
                ) { Text("Apply") }

                OutlinedButton(
                    onClick = { ipInput = uiState.ipAddress },
                    modifier = Modifier.weight(1f)
                ) { Text("Reset") }
            }

            Text(
                "Current target: ${uiState.ipAddress}",
                style = MaterialTheme.typography.bodySmall
            )
        }

        SectionCard(
            title = "Quick Status",
            subtitle = "These are the two blocks that can send text to VRChat."
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                AssistChip(
                    onClick = { /* no-op */ },
                    label = { Text(if (vm.cycleEnabled) "Cycle: ON" else "Cycle: OFF") },
                    leadingIcon = { Icon(Icons.Filled.Sync, contentDescription = null) }
                )
                AssistChip(
                    onClick = { /* no-op */ },
                    label = { Text(if (vm.spotifyEnabled) "Now Playing: ON" else "Now Playing: OFF") },
                    leadingIcon = { Icon(Icons.Filled.MusicNote, contentDescription = null) }
                )
            }

            Text(
                "Tip: Use the Cycle page for rotating lines, and Now Playing page for music block.",
                style = MaterialTheme.typography.bodySmall
            )
        }

        SectionCard(
            title = "Manual Send",
            subtitle = "One-off message (doesn’t affect Cycle/Now Playing)."
        ) {
            OutlinedTextField(
                value = vm.messageText.value,
                onValueChange = { vm.onMessageTextChange(it) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = false,
                minLines = 2,
                label = { Text("Message") }
            )

            Button(
                onClick = { vm.sendMessage() },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Send") }
        }
    }
}

@Composable
private fun CyclePage(vm: ChatboxViewModel) {
    PageContainer {

        SectionCard(
            title = "AFK (top line)",
            subtitle = "Shows above Cycle + Now Playing. (We’ll wire separate AFK delay next.)"
        ) {
            // NOTE: these fields must exist in your VM (afkEnabled/afkMessage + sendAfkNow)
            // If your current VM doesn’t have them yet, tell me and I’ll send the VM replacement.
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("AFK enabled")
                Switch(
                    checked = vm.afkEnabled,
                    onCheckedChange = { vm.afkEnabled = it }
                )
            }

            OutlinedTextField(
                value = vm.afkMessage,
                onValueChange = { vm.afkMessage = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text("AFK text") }
            )

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(onClick = { vm.sendAfkNow() }, modifier = Modifier.weight(1f)) {
                    Text("Send AFK once")
                }
            }
        }

        SectionCard(
            title = "Cycle Messages",
            subtitle = "Rotates your lines. Now Playing always stays underneath automatically."
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Cycle enabled")
                Switch(
                    checked = vm.cycleEnabled,
                    onCheckedChange = {
                        vm.cycleEnabled = it
                        if (!it) vm.stopCycle()
                    }
                )
            }

            OutlinedTextField(
                value = vm.cycleMessages,
                onValueChange = { vm.cycleMessages = it },
                modifier = Modifier.fillMaxWidth(),
                minLines = 4,
                label = { Text("Lines (one per line)") },
                placeholder = { Text("Hello!\nBe kind 💕\n…") }
            )

            OutlinedTextField(
                value = vm.cycleIntervalSeconds.toString(),
                onValueChange = { raw ->
                    raw.toIntOrNull()?.let { vm.cycleIntervalSeconds = it.coerceAtLeast(1) }
                },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text("Cycle speed (seconds)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
            )

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(onClick = { vm.startCycle() }, modifier = Modifier.weight(1f)) {
                    Text("Start")
                }
                OutlinedButton(onClick = { vm.stopCycle() }, modifier = Modifier.weight(1f)) {
                    Text("Stop")
                }
            }
        }
    }
}

@Composable
private fun NowPlayingPage(vm: ChatboxViewModel) {
    val ctx = LocalContext.current

    PageContainer {

        SectionCard(
            title = "Now Playing (phone music)",
            subtitle = "Uses Notification Access. Works with Spotify / YouTube Music / etc (any media notification)."
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Enable Now Playing block")
                Switch(
                    checked = vm.spotifyEnabled,
                    onCheckedChange = { vm.setSpotifyEnabledFlag(it) }
                )
            }

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Demo mode (for testing)")
                Switch(
                    checked = vm.spotifyDemoEnabled,
                    onCheckedChange = { vm.setSpotifyDemoFlag(it) }
                )
            }

            OutlinedButton(
                onClick = { ctx.startActivity(vm.notificationAccessIntent()) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Filled.Settings, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Open Notification Access settings")
            }

            OutlinedTextField(
                value = vm.musicRefreshSeconds.toString(),
                onValueChange = { raw ->
                    raw.toIntOrNull()?.let { vm.musicRefreshSeconds = it.coerceAtLeast(1) }
                },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text("Music refresh speed (seconds)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
            )

            Text("Progress bar presets:", style = MaterialTheme.typography.labelLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                (1..5).forEach { p ->
                    val selected = vm.spotifyPreset == p
                    val colors =
                        if (selected) ButtonDefaults.buttonColors()
                        else ButtonDefaults.outlinedButtonColors()

                    Button(
                        onClick = { vm.updateSpotifyPreset(p) },
                        colors = colors,
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                    ) { Text("$p") }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(onClick = { vm.startNowPlayingSender() }, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Filled.Refresh, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Start")
                }
                OutlinedButton(onClick = { vm.stopNowPlayingSender() }, modifier = Modifier.weight(1f)) {
                    Text("Stop")
                }
            }

            OutlinedButton(
                onClick = { vm.sendNowPlayingOnce() },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Send once now (test)") }
        }

        SectionCard(
            title = "Preview / Detected",
            subtitle = "If this stays blank: enable access, restart the app, then play music."
        ) {
            Text("Detected: ${vm.nowPlayingDetected}")
            Text("Artist: ${vm.lastNowPlayingArtist}")
            Text("Title: ${vm.lastNowPlayingTitle}")
            Text("App: ${vm.activePackage}")

            // If your VM exposes playing state, show it:
            if (vm.nowPlayingIsPlaying) {
                Text("Status: Playing")
            } else {
                Text("Status: Paused")
            }
        }
    }
}

@Composable
private fun DebugPage(vm: ChatboxViewModel) {
    PageContainer {
        SectionCard(
            title = "Now Playing Listener",
            subtitle = "This helps confirm your permissions and whether media is detected."
        ) {
            Text("Listener connected: ${vm.listenerConnected}")
            Text("Active package: ${vm.activePackage}")
            Text("Detected: ${vm.nowPlayingDetected}")
            Text("Playing: ${vm.nowPlayingIsPlaying}")
        }

        SectionCard(
            title = "Last Sent",
            subtitle = "Confirms if text is actually being pushed to VRChat over OSC."
        ) {
            Text("Last sent to VRChat (ms): ${vm.lastSentToVrchatAtMs}")
            Text(
                "If you see it sending but VRChat shows nothing:\n" +
                    "• Check the IP\n" +
                    "• Make sure OSC is enabled in VRChat\n" +
                    "• Ensure both devices are on the same Wi-Fi",
                style = MaterialTheme.typography.bodySmall
            )
        }

        SectionCard(
            title = "Troubleshooting checklist",
            subtitle = null
        ) {
            Text(
                "If Now Playing won’t detect:\n" +
                    "1) Settings → Notification access → enable VRC-A\n" +
                    "2) Restart VRC-A\n" +
                    "3) Start music (Spotify/YouTube Music/etc)\n" +
                    "4) Pause/Play once to force a media session update\n\n" +
                    "If it detects random notifications:\n" +
                    "• Only media notifications should be used — we can filter to media sessions only in the service.",
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

@Composable
private fun SettingsPage(vm: ChatboxViewModel) {
    PageContainer {
        SectionCard(
            title = "App",
            subtitle = "Basic app settings. (We’ll add theme toggle/polish after stability.)"
        ) {
            Text(
                "VRC-A = VRChat Assistant\nMade by Ashoska Mitsu Sisko\nBased on ScrapW’s base, fully revamped.",
                style = MaterialTheme.typography.bodyMedium
            )
        }

        SectionCard(
            title = "Info",
            subtitle = null
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Filled.Info, contentDescription = null)
                Text(
                    "Logo changes are done by replacing launcher icon resources (instructions below).",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}
