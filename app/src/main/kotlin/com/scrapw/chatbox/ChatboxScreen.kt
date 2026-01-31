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
    Updates("Updates"),
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
                            Icon(Icons.Filled.Send, contentDescription = "Send music once")
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
            BottomTab(AppPage.Settings, current, Icons.Filled.Settings, onSelect)
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
            subtitle = "Put your headset/device IP here. VRChat OSC must be enabled (see Info → Tutorial)."
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
            subtitle = "Sends one message instantly (does not change Cycle/AFK/Music)."
        ) {
            OutlinedTextField(
                value = vm.messageText.value,
                onValueChange = { vm.onMessageTextChange(it) },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2,
                label = { Text("Message") }
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(onClick = { vm.sendMessage() }, modifier = Modifier.weight(1f)) { Text("Send") }
                OutlinedButton(onClick = { vm.stashMessage() }, modifier = Modifier.weight(1f)) { Text("Stash") }
            }
        }
    }
}

@Composable
private fun CyclePage(vm: ChatboxViewModel) {
    PageContainer {

        // AFK block
        SectionCard(
            title = "AFK (Top Line)",
            subtitle = "AFK shows above Cycle + Music. Interval is forced to keep VRChat stable."
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("AFK enabled")
                Switch(checked = vm.afkEnabled, onCheckedChange = { vm.afkEnabled = it })
            }

            OutlinedTextField(
                value = vm.afkMessage,
                onValueChange = { vm.updateAfkText(it) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text("AFK text") }
            )

            // Presets (compact)
            Text("AFK Presets", style = MaterialTheme.typography.labelLarge)
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                (1..3).forEach { slot ->
                    ElevatedCard {
                        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                OutlinedTextField(
                                    value = vm.getAfkPresetName(slot),
                                    onValueChange = { vm.updateAfkPresetName(slot, it.take(24)) },
                                    label = { Text("Name") },
                                    modifier = Modifier.weight(1f),
                                    singleLine = true
                                )
                                Spacer(Modifier.width(10.dp))
                                OutlinedButton(onClick = { vm.loadAfkPreset(slot) }) { Text("Load") }
                                Spacer(Modifier.width(8.dp))
                                Button(onClick = { vm.saveAfkPreset(slot) }) { Text("Save") }
                            }
                            val preview = vm.getAfkPresetText(slot).ifBlank { "(empty)" }
                            Text("Saved: $preview", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = { vm.startAfkSender() },
                    modifier = Modifier.weight(1f),
                    enabled = vm.afkEnabled
                ) { Text("Start AFK") }

                OutlinedButton(
                    onClick = { vm.stopAfkSender(clearFromChatbox = true) },
                    modifier = Modifier.weight(1f)
                ) { Text("Stop AFK") }
            }
        }

        // Cycle block
        SectionCard(
            title = "Cycle (Rotating Lines)",
            subtitle = "Add up to 10 lines. Cycle sends them one-by-one."
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Cycle enabled")
                Switch(
                    checked = vm.cycleEnabled,
                    onCheckedChange = { vm.setCycleEnabled(it) }
                )
            }

            // Line editor (no “press enter” messaging, fully public-friendly)
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (vm.cycleLines.isEmpty()) {
                    Text("No lines yet. Tap “Add line”.", style = MaterialTheme.typography.bodySmall)
                }

                vm.cycleLines.forEachIndexed { idx, line ->
                    ElevatedCard {
                        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = line,
                                onValueChange = { vm.updateCycleLine(idx, it) },
                                label = { Text("Line ${idx + 1}") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                OutlinedButton(
                                    onClick = { vm.removeCycleLine(idx) },
                                    modifier = Modifier.weight(1f)
                                ) { Text("Remove") }
                            }
                        }
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(
                        onClick = { vm.addCycleLine("") },
                        enabled = vm.cycleLines.size < 10,
                        modifier = Modifier.weight(1f)
                    ) { Text("Add line") }

                    OutlinedButton(
                        onClick = { vm.clearCycleLines() },
                        modifier = Modifier.weight(1f)
                    ) { Text("Clear") }
                }

                OutlinedTextField(
                    value = vm.cycleIntervalSeconds.toString(),
                    onValueChange = { raw ->
                        raw.toIntOrNull()?.let { vm.setCycleIntervalSeconds(it.coerceAtLeast(2)) }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("Cycle speed (seconds) (min 2)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
            }

            // Cycle presets (compact)
            Spacer(Modifier.height(4.dp))
            Text("Cycle Presets", style = MaterialTheme.typography.labelLarge)
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                (1..5).forEach { slot ->
                    ElevatedCard {
                        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                OutlinedTextField(
                                    value = vm.getCyclePresetName(slot),
                                    onValueChange = { vm.updateCyclePresetName(slot, it.take(24)) },
                                    label = { Text("Name") },
                                    modifier = Modifier.weight(1f),
                                    singleLine = true
                                )
                                Spacer(Modifier.width(10.dp))
                                OutlinedButton(onClick = { vm.loadCyclePreset(slot) }) { Text("Load") }
                                Spacer(Modifier.width(8.dp))
                                Button(onClick = { vm.saveCyclePreset(slot) }) { Text("Save") }
                            }
                            val preview = vm.getCyclePresetFirstLine(slot).ifBlank { "(empty)" }
                            Text("Saved: $preview", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(onClick = { vm.startCycle() }, modifier = Modifier.weight(1f), enabled = vm.cycleEnabled) {
                    Text("Start Cycle")
                }
                OutlinedButton(onClick = { vm.stopCycle(clearFromChatbox = true) }, modifier = Modifier.weight(1f)) {
                    Text("Stop Cycle")
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
            title = "Now Playing (Phone Music)",
            subtitle = "Uses Notification Access. Works with apps that show a media notification."
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Music enabled")
                Switch(
                    checked = vm.musicEnabled,
                    onCheckedChange = { vm.setMusicEnabled(it) }
                )
            }

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Demo mode")
                Switch(
                    checked = vm.spotifyDemoEnabled,
                    onCheckedChange = { vm.setMusicDemo(it) }
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
                label = { Text("Refresh speed (seconds) (min 2)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
            )

            Text("Progress styles (names are locked):", style = MaterialTheme.typography.labelLarge)
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                (1..5).forEach { p ->
                    ElevatedCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { vm.setMusicPreset(p) }
                    ) {
                        Row(
                            Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(vm.getMusicPresetName(p), style = MaterialTheme.typography.titleSmall)
                                Text(
                                    "Preview: (live in VRChat; uses current song progress)",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                            val selected = (vm.musicPreset == p)
                            AssistChip(
                                onClick = { vm.setMusicPreset(p) },
                                label = { Text(if (selected) "Selected" else "Select") }
                            )
                        }
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = { vm.startNowPlayingSender() },
                    modifier = Modifier.weight(1f),
                    enabled = vm.musicEnabled
                ) { Text("Start Music") }

                OutlinedButton(
                    onClick = { vm.stopNowPlayingSender(clearFromChatbox = true) },
                    modifier = Modifier.weight(1f)
                ) { Text("Stop Music") }
            }

            OutlinedButton(
                onClick = { vm.sendNowPlayingOnce() },
                modifier = Modifier.fillMaxWidth(),
                enabled = vm.musicEnabled
            ) { Text("Send once (test)") }
        }

        SectionCard(
            title = "Detected / Preview",
            subtitle = "If blank: enable access → restart app → play music."
        ) {
            Text("Listener connected: ${vm.listenerConnected}")
            Text("Detected: ${vm.nowPlayingDetected}")
            Text("App: ${vm.activePackage}")
            Text("Artist: ${vm.lastNowPlayingArtist}")
            Text("Title: ${vm.lastNowPlayingTitle}")
            Text("Status: ${if (vm.nowPlayingIsPlaying) "Playing" else "Paused"}")
        }
    }
}

@Composable
private fun DebugPage(vm: ChatboxViewModel) {
    PageContainer {
        SectionCard(
            title = "OSC Output Preview",
            subtitle = "Shows exactly what AFK / Cycle / Music are generating, plus the combined message."
        ) {
            SelectionContainer {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("AFK:", style = MaterialTheme.typography.labelLarge)
                    Text(vm.debugLastAfkOsc, fontFamily = FontFamily.Monospace)

                    Text("Cycle:", style = MaterialTheme.typography.labelLarge)
                    Text(vm.debugLastCycleOsc, fontFamily = FontFamily.Monospace)

                    Text("Music:", style = MaterialTheme.typography.labelLarge)
                    Text(vm.debugLastMusicOsc, fontFamily = FontFamily.Monospace)

                    Text("Combined:", style = MaterialTheme.typography.labelLarge)
                    Text(vm.debugLastCombinedOsc, fontFamily = FontFamily.Monospace)
                }
            }
        }

        SectionCard(title = "Last send time") {
            Text("Last sent to VRChat (ms): ${vm.lastSentToVrchatAtMs}")
        }
    }
}

@Composable
private fun InfoPage(vm: ChatboxViewModel) {
    val ctx = LocalContext.current
    var tab by rememberSaveable { mutableStateOf(InfoTab.Overview) }

    val overview = remember {
        """
VRC-A (VRChat Assistant)
Made by: Ashoska Mitsu Sisko
Base: ScrapW’s Chatbox base (heavily revamped)

VRC-A sends text to VRChat’s Chatbox using OSC over your Wi-Fi network.
It’s designed for mobile/standalone setups and includes debug indicators
to show what is working or failing.
        """.trimIndent()
    }

    val features = remember {
        """
FEATURES
- Manual send (instant)
- Cycle: rotating lines (up to 10)
- AFK: top line (separate sender)
- Now Playing: phone music detection (Notification Access)
- Progress styles (locked names): Love / Minimal / Crystal / Soundwave / Geometry
- Debug: shows OSC output for AFK/Cycle/Music/Combined
- Presets:
  - AFK presets (3): name + saved text
  - Cycle presets (5): name + saved list + speed
- No chatbox send sound effect (forced off)
        """.trimIndent()
    }

    val tutorial = remember {
        """
TUTORIAL (EASY)
1) Turn on OSC in VRChat:
   VRChat → Settings → OSC → Enable OSC

2) Make sure phone + headset are on the SAME Wi-Fi.

3) Find headset IP address:
   Headset Settings → Wi-Fi → tap your network → IP Address

4) Put the IP in VRC-A:
   Dashboard → Headset IP → Apply

5) Test:
   Dashboard → Manual Send → type hello → Send

6) Enable Now Playing:
   Now Playing → Open Notification Access → enable VRC-A → restart app

7) Start modules:
   - Cycle page → Start Cycle
   - Cycle page → Start AFK
   - Now Playing page → Start Music
        """.trimIndent()
    }

    val bugs = remember {
        """
KNOWN ISSUES / NOTES
- Some music apps only update progress when you interact. VRC-A estimates progress
  between updates using the last position + playback speed.
- If nothing sends: wrong IP or different Wi-Fi is the #1 cause.
- Some routers have “client isolation” which blocks device-to-device traffic.
        """.trimIndent()
    }

    PageContainer {
        SectionCard(
            title = "Information",
            subtitle = "Everything about VRC-A (what it is, tutorial, features, updates)."
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
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                    ) { Text(t.title) }
                }
            }

            when (tab) {
                InfoTab.Overview -> SelectionContainer { Text(overview) }
                InfoTab.Features -> SelectionContainer { Text(features) }
                InfoTab.Tutorial -> SelectionContainer { Text(tutorial) }
                InfoTab.Bugs -> SelectionContainer { Text(bugs) }
                InfoTab.FullDoc -> SelectionContainer { Text(vm.fullInfoDocumentText, fontFamily = FontFamily.Monospace) }

                InfoTab.Updates -> {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Updates (in-app)", style = MaterialTheme.typography.titleSmall)
                        Text(
                            "Android will always ask you to confirm installation. That is normal.",
                            style = MaterialTheme.typography.bodySmall
                        )

                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Button(onClick = { vm.checkForUpdates() }, modifier = Modifier.weight(1f)) {
                                Text("Check")
                            }
                            OutlinedButton(
                                onClick = { vm.openUnknownSourcesSettings(ctx) },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Allow installs")
                            }
                        }

                        Text("Status: ${vm.updateStatus}")
                        if (vm.latestVersionLabel.isNotBlank()) {
                            Text("Latest: ${vm.latestVersionLabel}", style = MaterialTheme.typography.bodyMedium)
                        }

                        if (vm.latestChangelog.isNotBlank()) {
                            Text("Changelog:", style = MaterialTheme.typography.labelLarge)
                            SelectionContainer {
                                Text(vm.latestChangelog, style = MaterialTheme.typography.bodySmall)
                            }
                        }

                        Button(
                            onClick = { vm.downloadAndInstallUpdate(ctx) },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = vm.latestVersionLabel.isNotBlank()
                        ) {
                            Text("Download & Install")
                        }
                    }
                }
            }
        }

        SectionCard(title = "About") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Info, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(
                    "VRC-A = VRChat Assistant\n" +
                        "Made by Ashoska Mitsu Sisko\n" +
                        "Based on ScrapW’s Chatbox base (heavily revamped)."
                )
            }
        }
    }
}
