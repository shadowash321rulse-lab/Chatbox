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
import androidx.compose.material.icons.filled.Close
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
                AppPage.Settings -> SettingsPage()
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
            title = "Connection (OSC Target)",
            subtitle = "This is where your messages are sent. Use the IP of the headset/device running VRChat."
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
                "Important: Your headset and phone MUST be on the same Wi-Fi.",
                style = MaterialTheme.typography.bodySmall
            )
        }

        SectionCard(
            title = "Manual Send",
            subtitle = "This sends one message instantly. It does not change AFK/Cycle/Now Playing."
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

            Text(
                "Tip: VRC-A disables the VRChat “send sound” by default.",
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

@Composable
private fun CyclePage(vm: ChatboxViewModel) {
    // UI list editing (no Enter needed)
    var newCycleLine by rememberSaveable { mutableStateOf("") }
    val cycleLinesState = rememberSaveable { mutableStateOf(listOf<String>()) }

    LaunchedEffect(vm.cycleMessages) {
        val parsed = vm.cycleMessages
            .lines()
            .map { it.trim() }
            .filter { it.isNotEmpty() }

        if (parsed != cycleLinesState.value) {
            cycleLinesState.value = parsed
        }
    }

    fun pushCycleLinesToVm(lines: List<String>) {
        cycleLinesState.value = lines
        vm.updateCycleMessages(lines.joinToString("\n"))
    }

    PageContainer {
        SectionCard(
            title = "How your chatbox is built",
            subtitle = null
        ) {
            Text("VRC-A sends one combined message with up to 3 parts:", style = MaterialTheme.typography.bodyMedium)
            Text("1) AFK (top)  2) Cycle (middle)  3) Now Playing (bottom)", style = MaterialTheme.typography.bodySmall)
            Text(
                "These parts DO NOT override each other anymore. VRC-A merges them and sends at a safe speed (min 2s).",
                style = MaterialTheme.typography.bodySmall
            )
        }

        SectionCard(
            title = "AFK (Top line)",
            subtitle = "AFK sits above everything. The AFK interval is forced to avoid spam."
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column(Modifier.weight(1f)) {
                    Text("AFK enabled")
                    Text("Enable AFK, then press Start AFK to begin sending.", style = MaterialTheme.typography.bodySmall)
                }
                Switch(
                    checked = vm.afkEnabled,
                    onCheckedChange = { vm.afkEnabled = it }
                )
            }

            OutlinedTextField(
                value = vm.afkMessage,
                onValueChange = { vm.updateAfkMessage(it) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text("AFK text") },
                placeholder = { Text("Example: AFK — grabbing water") }
            )

            Text("AFK Presets (saved even if app closes):", style = MaterialTheme.typography.labelLarge)
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                (1..3).forEach { slot ->
                    ElevatedCard {
                        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("Slot $slot", style = MaterialTheme.typography.labelLarge)
                            Text(
                                vm.getAfkPresetPreview(slot),
                                style = MaterialTheme.typography.bodySmall,
                                fontFamily = FontFamily.Monospace
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(onClick = { vm.loadAfkPreset(slot) }) { Text("Load") }
                                Button(onClick = { vm.saveAfkPreset(slot, vm.afkMessage) }) { Text("Save") }
                            }
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
                    onClick = { vm.stopAfkSender() },
                    modifier = Modifier.weight(1f)
                ) { Text("Stop AFK") }
            }
        }

        SectionCard(
            title = "Cycle (Middle line)",
            subtitle = "Add messages below. You do NOT need to press Enter anymore."
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column(Modifier.weight(1f)) {
                    Text("Cycle enabled")
                    Text("When running, Cycle rotates one message at a time.", style = MaterialTheme.typography.bodySmall)
                }
                Switch(
                    checked = vm.cycleEnabled,
                    onCheckedChange = { vm.setCycleEnabledFlag(it) }
                )
            }

            OutlinedTextField(
                value = newCycleLine,
                onValueChange = { newCycleLine = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text("Add a cycle message") },
                placeholder = { Text("Example: Streaming • Song requests open") }
            )

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = {
                        val t = newCycleLine.trim()
                        if (t.isNotEmpty()) {
                            pushCycleLinesToVm(cycleLinesState.value + t)
                            newCycleLine = ""
                        }
                    },
                    modifier = Modifier.weight(1f),
                    enabled = newCycleLine.trim().isNotEmpty()
                ) { Text("Add") }

                OutlinedButton(
                    onClick = { pushCycleLinesToVm(emptyList()) },
                    modifier = Modifier.weight(1f),
                    enabled = cycleLinesState.value.isNotEmpty()
                ) { Text("Clear") }
            }

            if (cycleLinesState.value.isEmpty()) {
                Text("No cycle messages yet. Add one above.", style = MaterialTheme.typography.bodySmall)
            } else {
                ElevatedCard {
                    Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Current cycle list:", style = MaterialTheme.typography.labelLarge)
                        cycleLinesState.value.forEachIndexed { idx, line ->
                            Row(
                                Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("${idx + 1}. $line", modifier = Modifier.weight(1f))
                                IconButton(onClick = {
                                    val next = cycleLinesState.value.toMutableList().also { it.removeAt(idx) }
                                    pushCycleLinesToVm(next)
                                }) {
                                    Icon(Icons.Filled.Close, contentDescription = "Remove")
                                }
                            }
                        }
                    }
                }
            }

            OutlinedTextField(
                value = vm.cycleIntervalSeconds.toString(),
                onValueChange = { raw ->
                    raw.toIntOrNull()?.let { vm.setCycleIntervalSecondsFlag(it.coerceAtLeast(2)) }
                },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text("Cycle speed (seconds) — minimum 2") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
            )

            Text("Cycle Presets (saved even if app closes):", style = MaterialTheme.typography.labelLarge)
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                (1..5).forEach { slot ->
                    ElevatedCard {
                        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("Slot $slot", style = MaterialTheme.typography.labelLarge)
                            Text(
                                vm.getCyclePresetPreview(slot),
                                style = MaterialTheme.typography.bodySmall,
                                fontFamily = FontFamily.Monospace
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(onClick = { vm.loadCyclePreset(slot) }) { Text("Load") }
                                Button(onClick = { vm.saveCyclePreset(slot, vm.cycleMessages) }) { Text("Save") }
                            }
                        }
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = { vm.startCycle() },
                    modifier = Modifier.weight(1f),
                    enabled = vm.cycleEnabled && cycleLinesState.value.isNotEmpty()
                ) { Text("Start") }

                OutlinedButton(
                    onClick = { vm.stopCycle() },
                    modifier = Modifier.weight(1f)
                ) { Text("Stop") }
            }

            Text(
                "If VRChat randomly cuts out: increase delays and keep everything at 2 seconds or higher.",
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

@Composable
private fun NowPlayingPage(vm: ChatboxViewModel) {
    val ctx = LocalContext.current

    PageContainer {
        SectionCard(
            title = "Now Playing (Bottom block)",
            subtitle = "Reads your phone’s media notification. Shows under AFK + Cycle."
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column(Modifier.weight(1f)) {
                    Text("Enable Now Playing")
                    Text("Then press Start to begin sending.", style = MaterialTheme.typography.bodySmall)
                }
                Switch(
                    checked = vm.spotifyEnabled,
                    onCheckedChange = { vm.setSpotifyEnabledFlag(it) }
                )
            }

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column(Modifier.weight(1f)) {
                    Text("Demo mode")
                    Text("Shows a fake song if nothing is detected.", style = MaterialTheme.typography.bodySmall)
                }
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
                    raw.toIntOrNull()?.let { vm.updateMusicRefreshSeconds(it.coerceAtLeast(2)) }
                },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text("Music refresh speed (seconds) — minimum 2") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
            )

            Text("Progress bar presets:", style = MaterialTheme.typography.labelLarge)
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                (1..5).forEach { p ->
                    val selected = vm.spotifyPreset == p
                    val colors =
                        if (selected) ButtonDefaults.buttonColors()
                        else ButtonDefaults.outlinedButtonColors()

                    Button(
                        onClick = { vm.updateSpotifyPreset(p) },
                        colors = colors,
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                    ) { Text("Preset $p") }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(onClick = { vm.startNowPlayingSender() }, modifier = Modifier.weight(1f)) { Text("Start") }
                OutlinedButton(onClick = { vm.stopNowPlayingSender() }, modifier = Modifier.weight(1f)) { Text("Stop") }
            }

            OutlinedButton(
                onClick = { vm.sendNowPlayingOnce() },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Send once now (test)") }

            Text(
                "Note: VRC-A forces NO send sound effect by default.",
                style = MaterialTheme.typography.bodySmall
            )
        }

        SectionCard(
            title = "Detected / Preview",
            subtitle = "If blank: enable Notification Access, restart app, then play music."
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
            title = "Listener Status",
            subtitle = "This confirms whether the phone is allowing VRC-A to read media notifications."
        ) {
            Text("Listener connected: ${vm.listenerConnected}")
            Text("Active package: ${vm.activePackage}")
            Text("Detected: ${vm.nowPlayingDetected}")
            Text("Playing: ${vm.nowPlayingIsPlaying}")
        }

        SectionCard(
            title = "What VRC-A is trying to send",
            subtitle = "If something isn’t working, this shows which part is blank."
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
            title = "Last send time",
            subtitle = "Useful if VRChat seems to be rate-limiting."
        ) {
            Text("Last sent to VRChat (ms): ${vm.lastSentToVrchatAtMs}")
            Text("Minimum cooldown is forced to 2 seconds to reduce cutouts.", style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun SettingsPage() {
    var tab by rememberSaveable { mutableStateOf(InfoTab.Overview) }

    val fullDoc = remember {
        """
VRC-A (VRChat Assistant)
Made by: Ashoska Mitsu Sisko
Base: ScrapW’s Chatbox base (heavily revamped)

============================================================
WHAT THIS APP IS
============================================================
VRC-A is an Android app that sends text to VRChat’s Chatbox using OSC.
It’s meant for standalone / mobile-friendly setups where you want:
- A quick “Send message” tool
- A cycling status / rotating messages system
- A live “Now Playing” music block (from your phone’s media notifications)
- An AFK tag at the very top

VRC-A is designed to be “easy to test” with debug indicators so you can tell
what’s failing (connection, permissions, detection, etc).

IMPORTANT DEFAULTS (NEW)
- VRC-A disables the VRChat “send sound effect” by default.
- All modules are merged into one combined message so they do not cancel each other out.
- All speeds are clamped so they cannot go below 2 seconds (helps VRChat cutouts).
- When you stop AFK / Cycle / Music, VRC-A clears that block immediately.

============================================================
WHAT VRC-A IS FOR
============================================================
Use VRC-A if you want your VRChat Chatbox to show:
- A rotating status message (Cycle)
- What you’re listening to on your phone (Now Playing)
- An AFK label on top

============================================================
FEATURES
============================================================
A) Manual Send
- Send a message instantly from Dashboard.

B) AFK (Top line)
- Toggle AFK on/off
- Start/Stop AFK sender
- AFK text is saved even if the app closes
- 3 AFK presets saved to slots 1–3

C) Cycle (Middle line)
- Add messages using an “Add” button (no Enter needed)
- Remove messages from the list
- Start/Stop Cycle sender
- 5 Cycle presets saved to slots 1–5

D) Now Playing (Bottom block)
- Reads media notifications via Notification Access
- Works with players that show a media notification
- Shows a progress bar preset (including fixed Geometry fill)

E) Debug
- Shows what AFK/Cycle/Music is generating
- Shows the combined message

============================================================
TUTORIAL (DUMBED DOWN)
============================================================
STEP 1: Same Wi-Fi
Phone and headset must be on the same Wi-Fi.

STEP 2: Find headset IP
Headset Settings → Wi-Fi → your network → IP Address.

STEP 3: Put IP into VRC-A
Dashboard → enter IP → Apply.

STEP 4: Test Manual Send
Type “hello” → Send → VRChat should show it.

STEP 5: Enable Now Playing
Now Playing tab → Open Notification Access settings → enable VRC-A.
Restart the app, play music, then check “Detected”.

============================================================
KNOWN ISSUES
============================================================
- Some music apps only update progress when you interact (pause/seek/next).
- Some routers isolate devices (client isolation) which breaks OSC.
============================================================
END
============================================================
        """.trimIndent()
    }

    val overview = remember {
        """
VRC-A (VRChat Assistant)
Made by: Ashoska Mitsu Sisko
Based on ScrapW’s Chatbox base (heavily revamped)

VRC-A sends text to VRChat’s Chatbox using OSC over your Wi-Fi.

Defaults:
- No send sound effect
- Minimum 2s update speed
- AFK/Cycle/Music merged so nothing cancels out
- Stopping a module clears it immediately
        """.trimIndent()
    }

    val features = remember {
        """
FEATURES (quick list)
- Manual Send
- AFK (top) + 3 saved presets
- Cycle (middle) + Add button system + 5 saved presets
- Now Playing (bottom) + progress presets
- Debug shows what each module is generating
        """.trimIndent()
    }

    val tutorial = remember {
        """
TUTORIAL (short)
1) Same Wi-Fi (headset + phone)
2) Find headset IP (Wi-Fi details)
3) Dashboard → IP → Apply
4) Manual Send test
5) Now Playing → enable Notification Access
6) Start Now Playing, Start Cycle, Start AFK
        """.trimIndent()
    }

    val bugs = remember {
        """
COMMON PROBLEMS
- Nothing appears: wrong IP or not same Wi-Fi
- Now Playing blank: Notification Access not enabled / app not restarted
- Progress not moving: depends on music player updates
- VRChat cutouts: keep speeds 2 seconds or higher
        """.trimIndent()
    }

    val help = remember {
        """
QUICK HELP
If nothing sends:
- Recheck headset IP
- Same Wi-Fi
- VRChat OSC enabled

If Now Playing blank:
- Enable Notification Access
- Restart VRC-A
- Try a different music app to test
        """.trimIndent()
    }

    PageContainer {
        SectionCard(
            title = "Information",
            subtitle = "Everything you need to use VRC-A (what it is, setup, and troubleshooting)."
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

            val text = when (tab) {
                InfoTab.Overview -> overview
                InfoTab.Features -> features
                InfoTab.Tutorial -> tutorial
                InfoTab.Bugs -> bugs
                InfoTab.Troubleshoot -> help
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
