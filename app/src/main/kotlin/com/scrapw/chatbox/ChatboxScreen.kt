package com.scrapw.chatbox

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.scrapw.chatbox.ui.ChatboxViewModel

private enum class AppPage(val title: String) {
    Dashboard("Dashboard"),
    Cycle("Cycle"),
    NowPlaying("Now Playing"),
    Debug("Debug"),
    Settings("Info")
}

private enum class InfoTab(val title: String) {
    Overview("Overview"),
    Features("Features"),
    Tutorial("Tutorial"),
    Bugs("Bugs"),
    Troubleshoot("Help"),
    FullDoc("Full Doc")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatboxScreen(
    chatboxViewModel: ChatboxViewModel = viewModel(factory = ChatboxViewModel.Factory)
) {
    var page by rememberSaveable { mutableStateOf(AppPage.Dashboard) }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("VRC-A") },
                actions = {
                    if (page == AppPage.NowPlaying) {
                        IconButton(onClick = { chatboxViewModel.sendNowPlayingOnce() }) {
                            Icon(Icons.Filled.Send, contentDescription = "Send now playing once")
                        }
                    }
                }
            )
        },
        bottomBar = {
            SlimBottomBar(
                current = page,
                onSelect = { page = it }
            )
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
                AppPage.Settings -> InfoPage(chatboxViewModel)
            }
        }
    }
}

@Composable
private fun SlimBottomBar(
    current: AppPage,
    onSelect: (AppPage) -> Unit
) {
    Surface(tonalElevation = 3.dp) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            BottomTab(AppPage.Dashboard, current, Icons.Filled.Home, onSelect)
            BottomTab(AppPage.Cycle, current, Icons.Filled.Sync, onSelect)
            BottomTab(AppPage.NowPlaying, current, Icons.Filled.MusicNote, onSelect)
            BottomTab(AppPage.Debug, current, Icons.Filled.BugReport, onSelect)
            BottomTab(AppPage.Settings, current, Icons.Filled.Info, onSelect)
        }
    }
}

@Composable
private fun BottomTab(
    page: AppPage,
    current: AppPage,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onSelect: (AppPage) -> Unit
) {
    val selected = page == current
    val contentColor =
        if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant

    Column(
        Modifier
            .widthIn(min = 64.dp)
            .clickable { onSelect(page) }
            .padding(horizontal = 8.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(icon, contentDescription = page.title, tint = contentColor)
        Spacer(Modifier.height(2.dp))
        Text(
            page.title,
            style = MaterialTheme.typography.labelSmall,
            color = contentColor,
            maxLines = 1
        )
    }
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

    var ipInput by rememberSaveable { mutableStateOf(uiState.ipAddress) }
    LaunchedEffect(uiState.ipAddress) {
        if (ipInput.isBlank()) ipInput = uiState.ipAddress
    }

    PageContainer {
        SectionCard(
            title = "Connection",
            subtitle = "Enter your headset IP → Apply. Make sure VRChat OSC is enabled."
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

            Text("Current target: ${uiState.ipAddress}", style = MaterialTheme.typography.bodySmall)
        }

        SectionCard(
            title = "Manual Send",
            subtitle = "One-off message (doesn’t affect Cycle/Now Playing/AFK)."
        ) {
            OutlinedTextField(
                value = vm.messageText.value,
                onValueChange = { vm.onMessageTextChange(it) },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2,
                label = { Text("Message") }
            )
            Button(onClick = { vm.sendMessage() }, modifier = Modifier.fillMaxWidth()) {
                Text("Send")
            }
        }
    }
}

@Composable
private fun CyclePage(vm: ChatboxViewModel) {
    PageContainer {
        SectionCard(
            title = "AFK (Top line)",
            subtitle = "AFK always appears above Cycle + Music. Stop clears instantly."
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("AFK enabled")
                Switch(
                    checked = vm.afkEnabled,
                    onCheckedChange = { vm.afkEnabled = it }
                )
            }

            OutlinedTextField(
                value = vm.afkMessage,
                onValueChange = { vm.updateAfkText(it) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text("AFK text") }
            )

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = { vm.startAfkSender() },
                    modifier = Modifier.weight(1f),
                    enabled = vm.afkEnabled
                ) { Text("Start") }

                OutlinedButton(
                    onClick = { vm.stopAfkSender(clearFromChatbox = true) },
                    modifier = Modifier.weight(1f)
                ) { Text("Stop") }
            }

            OutlinedButton(
                onClick = { vm.sendAfkNow() },
                modifier = Modifier.fillMaxWidth(),
                enabled = vm.afkEnabled
            ) { Text("Send once") }
        }

        SectionCard(
            title = "Cycle Messages (max 10)",
            subtitle = "Add lines using the + button. No “press enter” confusion."
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Cycle enabled")
                Switch(
                    checked = vm.cycleEnabled,
                    onCheckedChange = { vm.cycleEnabled = it }
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                vm.cycleLines.forEachIndexed { i, line ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = line,
                            onValueChange = { vm.updateCycleLine(i, it) },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            label = { Text("Line ${i + 1}") }
                        )
                        Spacer(Modifier.width(8.dp))
                        OutlinedButton(
                            onClick = { vm.removeCycleLine(i) }
                        ) { Text("X") }
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(
                        onClick = { vm.addCycleLine() },
                        modifier = Modifier.weight(1f),
                        enabled = vm.cycleLines.size < 10
                    ) { Text("+ Add line") }

                    OutlinedButton(
                        onClick = { vm.clearCycleLines() },
                        modifier = Modifier.weight(1f)
                    ) { Text("Clear") }
                }
            }

            OutlinedTextField(
                value = vm.cycleIntervalSeconds.toString(),
                onValueChange = { raw ->
                    raw.toIntOrNull()?.let { vm.cycleIntervalSeconds = it.coerceAtLeast(2) }
                },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text("Cycle speed (seconds) min 2") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
            )

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = { vm.startCycle() },
                    modifier = Modifier.weight(1f),
                    enabled = vm.cycleEnabled
                ) { Text("Start") }

                OutlinedButton(
                    onClick = { vm.stopCycle(clearFromChatbox = true) },
                    modifier = Modifier.weight(1f)
                ) { Text("Stop") }
            }
        }
    }
}

@Composable
private fun NowPlayingPage(vm: ChatboxViewModel) {
    val ctx = LocalContext.current

    PageContainer {
        SectionCard(
            title = "Now Playing",
            subtitle = "Requires Notification Access. Stop clears instantly."
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Enable Now Playing")
                Switch(
                    checked = vm.spotifyEnabled,
                    onCheckedChange = { vm.setSpotifyEnabledFlag(it) }
                )
            }

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Demo mode")
                Switch(
                    checked = vm.spotifyDemoEnabled,
                    onCheckedChange = { vm.setSpotifyDemoFlag(it) }
                )
            }

            OutlinedButton(
                onClick = { ctx.startActivity(vm.notificationAccessIntent()) },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Open Notification Access settings") }

            OutlinedTextField(
                value = vm.musicRefreshSeconds.toString(),
                onValueChange = { raw ->
                    raw.toIntOrNull()?.let { vm.musicRefreshSeconds = it.coerceAtLeast(2) }
                },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text("Music refresh speed (seconds) min 2") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
            )

            Text("Progress presets:", style = MaterialTheme.typography.labelLarge)

            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                (1..5).forEach { p ->
                    val selected = vm.spotifyPreset == p
                    val name = vm.getMusicPresetName(p)
                    val colors =
                        if (selected) ButtonDefaults.buttonColors()
                        else ButtonDefaults.outlinedButtonColors()

                    Button(
                        onClick = { vm.updateSpotifyPreset(p) },
                        colors = colors,
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                    ) { Text(name) }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = { vm.startNowPlayingSender() },
                    modifier = Modifier.weight(1f),
                    enabled = vm.spotifyEnabled
                ) { Text("Start") }

                OutlinedButton(
                    onClick = { vm.stopNowPlayingSender(clearFromChatbox = true) },
                    modifier = Modifier.weight(1f)
                ) { Text("Stop") }
            }

            OutlinedButton(
                onClick = { vm.sendNowPlayingOnce() },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Send once (test)") }
        }

        SectionCard(
            title = "Detected / Preview",
            subtitle = "If blank: enable access, restart app, then play music."
        ) {
            Text("Detected: ${vm.nowPlayingDetected}")
            Text("Artist: ${vm.lastNowPlayingArtist}")
            Text("Title: ${vm.lastNowPlayingTitle}")
            Text("App: ${vm.activePackage}")
            Text("Status: ${if (vm.nowPlayingIsPlaying) "Playing" else "Paused"}")
        }
    }
}

@Composable
private fun DebugPage(vm: ChatboxViewModel) {
    PageContainer {
        SectionCard(
            title = "Listener",
            subtitle = "Confirms Notification Access + media detection."
        ) {
            Text("Listener connected: ${vm.listenerConnected}")
            Text("Active package: ${vm.activePackage}")
            Text("Detected: ${vm.nowPlayingDetected}")
            Text("Playing: ${vm.nowPlayingIsPlaying}")
        }

        SectionCard(
            title = "OSC Output Preview",
            subtitle = "Shows what each module is generating + the combined message."
        ) {
            SelectionContainer {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("AFK:\n${vm.debugLastAfkOsc}", fontFamily = FontFamily.Monospace)
                    Text("Cycle:\n${vm.debugLastCycleOsc}", fontFamily = FontFamily.Monospace)
                    Text("Music:\n${vm.debugLastMusicOsc}", fontFamily = FontFamily.Monospace)
                    Text("Combined:\n${vm.debugLastCombinedOsc}", fontFamily = FontFamily.Monospace)
                }
            }
        }

        SectionCard(title = "Send status") {
            Text("Last sent to VRChat (ms): ${vm.lastSentToVrchatAtMs}")
        }
    }
}

@Composable
private fun InfoPage(vm: ChatboxViewModel) {
    var tab by rememberSaveable { mutableStateOf(InfoTab.Overview) }

    val overview: String = remember {
        """
VRC-A (VRChat Assistant)
Made by: Ashoska Mitsu Sisko
Base: ScrapW’s Chatbox base (heavily revamped)

VRC-A sends text to VRChat’s Chatbox using OSC over your Wi-Fi network.
It’s designed for standalone/mobile-friendly setups with debug indicators.
        """.trimIndent()
    }

    val features: String = remember {
        """
FEATURES
- Manual Send (no SFX)
- Cycle (max 10 lines)
- AFK (top line, forced interval)
- Now Playing (Notification Access)
- Progress bars: Love / Minimal / Crystal / Soundwave / Geometry
- Debug: shows OSC output per module + combined
- Stop buttons clear instantly
        """.trimIndent()
    }

    val tutorial: String = remember {
        """
TUTORIAL
1) Put headset + phone on the same Wi-Fi
2) Enable VRChat OSC:
   VRChat → Settings → OSC → Enable OSC
3) Find headset IP:
   Quest/Android headset → Settings → Wi-Fi → your network → IP Address
4) Open VRC-A → Dashboard → paste IP → Apply
5) Manual Send test (“hello”) → if it appears, connection is working
6) Enable Now Playing:
   Now Playing page → Open Notification Access → enable VRC-A
   Restart app, then play music
7) Start Now Playing / Cycle / AFK as needed
        """.trimIndent()
    }

    val bugs: String = remember {
        """
KNOWN ISSUES
- Some music players don’t provide smooth progress updates
- Some routers isolate clients (blocks phone→headset traffic)
- If you change Wi-Fi networks your IP may change
        """.trimIndent()
    }

    val help: String = remember {
        """
HELP
Nothing appears in VRChat:
- Check IP is correct
- Confirm VRChat OSC enabled
- Same Wi-Fi on both devices
- Router client isolation OFF

Now Playing blank:
- Enable Notification Access
- Restart app
- Ensure music app shows a media notification
        """.trimIndent()
    }

    val fullDoc: String = vm.fullInfoDocumentText

    val shownText: String = when (tab) {
        InfoTab.Overview -> overview
        InfoTab.Features -> features
        InfoTab.Tutorial -> tutorial
        InfoTab.Bugs -> bugs
        InfoTab.Troubleshoot -> help
        InfoTab.FullDoc -> fullDoc
    }

    PageContainer {
        SectionCard(
            title = "Information",
            subtitle = "Everything about VRC-A (what it is, tutorial, features, bugs)."
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                InfoTab.entries.forEach { t ->
                    val selected = t == tab
                    val colors =
                        if (selected) ButtonDefaults.buttonColors()
                        else ButtonDefaults.outlinedButtonColors()

                    Button(
                        onClick = { tab = t },
                        colors = colors,
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Text(t.title)
                    }
                }
            }

            SelectionContainer {
                Text(
                    text = shownText, // ✅ always String
                    style = MaterialTheme.typography.bodyMedium,
                    fontFamily = FontFamily.Monospace
                )
            }
        }
    }
}
