package com.scrapw.chatbox

import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private enum class AppPage(val title: String) {
    Dashboard("Dashboard"),
    Cycle("Cycle"),
    NowPlaying("Now Playing"),
    Debug("Debug"),
    Settings("Settings")
}

private enum class InfoTab(val title: String) {
    Overview("Overview"),
    Features("Features"),
    Tutorial("Tutorial"),
    Bugs("Bugs"),
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
                AppPage.Settings -> SettingsPage(chatboxViewModel)
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
            subtitle = "Put your headset IP here (Quest Settings → Wi-Fi → your network → IP address)."
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
            Text(
                "Reminder: In VRChat → Settings → OSC → Enable OSC.",
                style = MaterialTheme.typography.bodySmall
            )
        }

        SectionCard(
            title = "Manual Send",
            subtitle = "One-off message. (No sound effect.)"
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
    val scope = rememberCoroutineScope()

    PageContainer {
        // ---------------- AFK ----------------
        SectionCard(
            title = "AFK (top line)",
            subtitle = "AFK always appears above Cycle + Now Playing. Interval is forced (no slider)."
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

            // Compact preset rows (3) with editable names + preview
            Text("AFK Presets (saved even if the app closes):", style = MaterialTheme.typography.labelLarge)

            (1..3).forEach { slot ->
                ElevatedCard {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            OutlinedTextField(
                                value = vm.getAfkPresetName(slot),
                                onValueChange = { vm.updateAfkPresetName(slot, it) },
                                modifier = Modifier.weight(1f),
                                singleLine = true,
                                label = { Text("Name") }
                            )
                            Spacer(Modifier.width(8.dp))
                            OutlinedButton(
                                onClick = { vm.loadAfkPreset(slot) }
                            ) { Text("Load") }
                            Spacer(Modifier.width(8.dp))
                            Button(
                                onClick = { vm.saveAfkPreset(slot) }
                            ) { Text("Save") }
                        }

                        val preview = vm.getAfkPresetTextPreview(slot)
                        Text(
                            text = if (preview.isBlank()) "Saved text: (empty)" else "Saved text: $preview",
                            style = MaterialTheme.typography.bodySmall
                        )
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

            OutlinedButton(
                onClick = { vm.sendAfkNow() },
                modifier = Modifier.fillMaxWidth(),
                enabled = vm.afkEnabled
            ) { Text("Send AFK once") }
        }

        // ---------------- Cycle ----------------
        SectionCard(
            title = "Cycle Messages",
            subtitle = "Up to 10 lines. Each line is its own box (no pressing Enter)."
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Cycle enabled")
                Switch(
                    checked = vm.cycleEnabled,
                    onCheckedChange = {
                        vm.cycleEnabled = it
                        if (!it) vm.stopCycle(clearFromChatbox = true)
                    }
                )
            }

            // Cycle list editor
            if (vm.cycleLines.isEmpty()) {
                Text("No cycle lines yet. Tap Add line.", style = MaterialTheme.typography.bodySmall)
            }

            vm.cycleLines.forEachIndexed { index, value ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = value,
                        onValueChange = { vm.updateCycleLine(index, it) },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        label = { Text("Line ${index + 1}") }
                    )
                    Spacer(Modifier.width(8.dp))
                    IconButton(onClick = { vm.removeCycleLine(index) }) {
                        Icon(Icons.Filled.Delete, contentDescription = "Remove line")
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(
                    onClick = { vm.addCycleLine() },
                    enabled = vm.cycleLines.size < 10
                ) {
                    Icon(Icons.Filled.Add, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("Add line (${vm.cycleLines.size}/10)")
                }

                OutlinedButton(
                    onClick = { vm.clearCycleLines() },
                    enabled = vm.cycleLines.isNotEmpty()
                ) { Text("Clear") }
            }

            OutlinedTextField(
                value = vm.cycleIntervalSeconds.toString(),
                onValueChange = { raw ->
                    raw.toIntOrNull()?.let { vm.cycleIntervalSeconds = it.coerceAtLeast(2) }
                },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text("Cycle speed (seconds) — min 2") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
            )

            // Cycle presets (5) compact, editable names + preview
            Text("Cycle Presets (saved even if the app closes):", style = MaterialTheme.typography.labelLarge)

            (1..5).forEach { slot ->
                ElevatedCard {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            OutlinedTextField(
                                value = vm.getCyclePresetName(slot),
                                onValueChange = { vm.updateCyclePresetName(slot, it) },
                                modifier = Modifier.weight(1f),
                                singleLine = true,
                                label = { Text("Name") }
                            )
                            Spacer(Modifier.width(8.dp))
                            OutlinedButton(onClick = { vm.loadCyclePreset(slot) }) { Text("Load") }
                            Spacer(Modifier.width(8.dp))
                            Button(onClick = { vm.saveCyclePreset(slot) }) { Text("Save") }
                        }

                        val preview = vm.getCyclePresetFirstLinePreview(slot)
                        Text(
                            text = if (preview.isBlank()) "Saved lines: (empty)" else "Saved first line: $preview",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(onClick = { vm.startCycle() }, modifier = Modifier.weight(1f)) { Text("Start") }
                OutlinedButton(onClick = { vm.stopCycle(clearFromChatbox = true) }, modifier = Modifier.weight(1f)) { Text("Stop") }
            }
        }
    }
}

@Composable
private fun NowPlayingPage(vm: ChatboxViewModel) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()

    // Animated preview ticker (purely for UI preview)
    var previewFraction by remember { mutableStateOf(0f) }
    LaunchedEffect(Unit) {
        while (true) {
            previewFraction += 0.02f
            if (previewFraction > 1f) previewFraction = 0f
            delay(140)
        }
    }

    PageContainer {
        SectionCard(
            title = "Now Playing (phone music)",
            subtitle = "Reads your phone’s media notification. Works with Spotify / YouTube Music etc."
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Enable Now Playing block")
                Switch(
                    checked = vm.spotifyEnabled,
                    onCheckedChange = { vm.setSpotifyEnabledFlag(it) }
                )
            }

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Demo mode (testing)")
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
                label = { Text("Music refresh speed (seconds) — min 2") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
            )

            // Named + animated preset picker
            Text("Music Progress Style:", style = MaterialTheme.typography.labelLarge)

            (1..5).forEach { p ->
                val selected = vm.spotifyPreset == p
                ElevatedCard {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(
                                selected = selected,
                                onClick = { vm.updateSpotifyPreset(p) }
                            )
                            Spacer(Modifier.width(8.dp))
                            OutlinedTextField(
                                value = vm.getMusicPresetName(p),
                                onValueChange = { vm.updateMusicPresetName(p, it) },
                                modifier = Modifier.weight(1f),
                                singleLine = true,
                                label = { Text("Preset name") }
                            )
                            Spacer(Modifier.width(8.dp))
                            Button(onClick = { vm.updateSpotifyPreset(p) }) { Text("Use") }
                        }

                        // Animated preview (uses the SAME renderer as runtime)
                        val bar = vm.renderMusicPresetPreview(p, previewFraction)
                        Text(
                            text = bar,
                            fontFamily = FontFamily.Monospace,
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Text(
                            text = "Preview animates here. In VRChat it follows the real song position.",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(onClick = { vm.startNowPlayingSender() }, modifier = Modifier.weight(1f)) { Text("Start") }
                OutlinedButton(onClick = { vm.stopNowPlayingSender(clearFromChatbox = true) }, modifier = Modifier.weight(1f)) { Text("Stop") }
            }

            OutlinedButton(
                onClick = { vm.sendNowPlayingOnce() },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Send once now (test)") }
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
            subtitle = "Shows what each module is generating, plus the combined message."
        ) {
            SelectionContainer {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
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

        SectionCard(
            title = "VRChat send status",
            subtitle = null
        ) {
            Text("Last sent to VRChat (ms): ${vm.lastSentToVrchatAtMs}")
        }
    }
}

@Composable
private fun SettingsPage(vm: ChatboxViewModel) {
    val ctx = LocalContext.current
    var tab by rememberSaveable { mutableStateOf(InfoTab.Overview) }

    val overview = remember {
        """
VRC-A (VRChat Assistant)
Made by: Ashoska Mitsu Sisko
Base: ScrapW’s Chatbox base (heavily revamped)

VRC-A sends text to VRChat’s Chatbox using OSC over your Wi-Fi network.
It supports AFK (top line), Cycle messages, and Now Playing from your phone.
        """.trimIndent()
    }

    val features = remember {
        """
FEATURES
- Manual Send (no sound effect)
- Cycle Messages (up to 10 lines, separate boxes)
- Cycle Presets (5): editable name + saved lines + saved speed
- AFK (top line) forced interval
- AFK Presets (3): editable name + saved text
- Now Playing: title/artist + progress bar
- Music presets named (Love/Minimal/Crystal/Soundwave/Geometry) + editable names
- Debug: shows what AFK/Cycle/Music are generating + combined output
        """.trimIndent()
    }

    val tutorial = remember {
        """
TUTORIAL (STEP BY STEP)

1) Turn OSC ON in VRChat:
VRChat → Settings → OSC → Enable OSC.

2) Put your headset + phone on the SAME Wi-Fi.

3) Find your headset IP address (Quest/Android headset):
Headset Settings → Wi-Fi → your network → IP address (sometimes under Advanced).
Example: 192.168.1.23

4) Put that IP into VRC-A:
Dashboard → Headset IP address → Apply

5) Test:
Dashboard → Manual Send → type “hello” → Send

6) Enable Now Playing permission:
Now Playing → Open Notification Access settings → enable VRC-A → restart VRC-A → play music.

7) Keep VRC-A alive (recommended):
Use the buttons below for Overlay + Battery settings.
        """.trimIndent()
    }

    val bugs = remember {
        """
KNOWN / POSSIBLE ISSUES
- Some music apps don’t update progress smoothly; VRC-A estimates it while playing.
- If your router blocks device-to-device traffic (client isolation), OSC may fail.
- If your headset IP changes (router restart), re-enter the IP in Dashboard.
        """.trimIndent()
    }

    val fullDoc = remember { vm.fullInfoDocumentText }

    PageContainer {
        SectionCard(
            title = "Permissions & Keep Alive",
            subtitle = "If Android kills the app, Now Playing and send loops can stop."
        ) {
            OutlinedButton(
                onClick = { ctx.startActivity(vm.notificationAccessIntent()) },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Open Notification Access settings") }

            OutlinedButton(
                onClick = { ctx.startActivity(vm.overlayPermissionIntent()) },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Open 'Display over other apps' (overlay) settings") }

            OutlinedButton(
                onClick = { ctx.startActivity(vm.batteryOptimizationIntent()) },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Open Battery optimization settings") }
        }

        SectionCard(
            title = "Information",
            subtitle = "What VRC-A is, how to set it up, features, and known issues."
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
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

            val text = when (tab) {
                InfoTab.Overview -> overview
                InfoTab.Features -> features
                InfoTab.Tutorial -> tutorial
                InfoTab.Bugs -> bugs
                InfoTab.FullDoc -> fullDoc
            }

            SelectionContainer {
                Text(
                    text = text,
                    style = MaterialTheme.typography.bodyMedium,
                    fontFamily = FontFamily.Monospace
                )
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
