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

private const val MAX_CYCLE_LINES_UI = 10

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
            SlimBottomBar(current = page, onSelect = { page = it })
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
        Text(page.title, style = MaterialTheme.typography.labelSmall, color = contentColor, maxLines = 1)
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
            title = "Before you start (VRChat OSC must be ON)",
            subtitle = "If OSC is off in VRChat, nothing will show even with the correct IP."
        ) {
            Text("In VRChat:", style = MaterialTheme.typography.bodyMedium)
            Text("• Open the VRChat menu", style = MaterialTheme.typography.bodySmall)
            Text("• Settings → OSC", style = MaterialTheme.typography.bodySmall)
            Text("• Turn OSC ON (Enable OSC)", style = MaterialTheme.typography.bodySmall)
        }

        SectionCard(
            title = "Connection (OSC Target IP)",
            subtitle = "Use the IP of the headset/device running VRChat."
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
            title = "Where to find your headset IP",
            subtitle = "Quest / Android headset (most common path)"
        ) {
            Text("On your headset:", style = MaterialTheme.typography.bodyMedium)
            Text("1) Settings", style = MaterialTheme.typography.bodySmall)
            Text("2) Wi-Fi", style = MaterialTheme.typography.bodySmall)
            Text("3) Tap your connected network", style = MaterialTheme.typography.bodySmall)
            Text("4) Look for IP Address (sometimes under Advanced)", style = MaterialTheme.typography.bodySmall)
            Text("Example: 192.168.1.23", style = MaterialTheme.typography.bodySmall)
            Text("Your headset and phone MUST be on the same Wi-Fi.", style = MaterialTheme.typography.bodySmall)
        }

        SectionCard(
            title = "Manual Send",
            subtitle = "Sends one message instantly. Does not change AFK/Cycle/Now Playing."
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
            Text("VRC-A disables the VRChat send sound by default.", style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun CyclePage(vm: ChatboxViewModel) {
    var newCycleLine by rememberSaveable { mutableStateOf("") }
    val cycleLinesState = rememberSaveable { mutableStateOf(listOf<String>()) }

    LaunchedEffect(vm.cycleMessages) {
        val parsed = vm.cycleMessages
            .lines()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .take(MAX_CYCLE_LINES_UI)

        if (parsed != cycleLinesState.value) {
            cycleLinesState.value = parsed
        }
    }

    fun pushCycleLinesToVm(lines: List<String>) {
        val limited = lines.take(MAX_CYCLE_LINES_UI)
        cycleLinesState.value = limited
        vm.updateCycleMessages(limited.joinToString("\n"))
    }

    PageContainer {
        SectionCard(
            title = "How your chatbox is built",
            subtitle = null
        ) {
            Text("VRC-A sends one combined message with up to 3 parts:", style = MaterialTheme.typography.bodyMedium)
            Text("1) AFK (top)  2) Cycle (middle)  3) Now Playing (bottom)", style = MaterialTheme.typography.bodySmall)
            Text("These parts are merged so they do not cancel each other.", style = MaterialTheme.typography.bodySmall)
        }

        SectionCard(
            title = "AFK (Top line)",
            subtitle = "AFK sits above everything. Interval is forced to avoid spam."
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column(Modifier.weight(1f)) {
                    Text("AFK enabled")
                    Text("Enable AFK, then press Start AFK.", style = MaterialTheme.typography.bodySmall)
                }
                Switch(checked = vm.afkEnabled, onCheckedChange = { vm.afkEnabled = it })
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
                            Text(vm.getAfkPresetPreview(slot), style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(onClick = { vm.loadAfkPreset(slot) }) { Text("Load") }
                                Button(onClick = { vm.saveAfkPreset(slot, vm.afkMessage) }) { Text("Save") }
                            }
                        }
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(onClick = { vm.startAfkSender() }, modifier = Modifier.weight(1f), enabled = vm.afkEnabled) { Text("Start AFK") }
                OutlinedButton(onClick = { vm.stopAfkSender() }, modifier = Modifier.weight(1f)) { Text("Stop AFK") }
            }
        }

        SectionCard(
            title = "Cycle (Middle line)",
            subtitle = "Add up to 10 messages. You do NOT need to press Enter."
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column(Modifier.weight(1f)) {
                    Text("Cycle enabled")
                    Text("Cycle rotates one message at a time.", style = MaterialTheme.typography.bodySmall)
                }
                Switch(checked = vm.cycleEnabled, onCheckedChange = { vm.setCycleEnabledFlag(it) })
            }

            val atLimit = cycleLinesState.value.size >= MAX_CYCLE_LINES_UI

            OutlinedTextField(
                value = newCycleLine,
                onValueChange = { newCycleLine = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text("Add a cycle message") },
                placeholder = { Text("Example: Streaming • Song requests open") },
                supportingText = {
                    Text("Messages: ${cycleLinesState.value.size} / $MAX_CYCLE_LINES_UI")
                }
            )

            if (atLimit) {
                Text(
                    "Cycle list is full (10 max). Remove one to add more.",
                    style = MaterialTheme.typography.bodySmall
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = {
                        val t = newCycleLine.trim()
                        if (t.isNotEmpty() && !atLimit) {
                            pushCycleLinesToVm(cycleLinesState.value + t)
                            newCycleLine = ""
                        }
                    },
                    modifier = Modifier.weight(1f),
                    enabled = newCycleLine.trim().isNotEmpty() && !atLimit
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
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
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
                            Text(vm.getCyclePresetPreview(slot), style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
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

                OutlinedButton(onClick = { vm.stopCycle() }, modifier = Modifier.weight(1f)) { Text("Stop") }
            }
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
                Switch(checked = vm.spotifyEnabled, onCheckedChange = { vm.setSpotifyEnabledFlag(it) })
            }

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column(Modifier.weight(1f)) {
                    Text("Demo mode")
                    Text("Shows a fake song if nothing is detected.", style = MaterialTheme.typography.bodySmall)
                }
                Switch(checked = vm.spotifyDemoEnabled, onCheckedChange = { vm.setSpotifyDemoFlag(it) })
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

            OutlinedButton(onClick = { vm.sendNowPlayingOnce() }, modifier = Modifier.fillMaxWidth()) {
                Text("Send once now (test)")
            }
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
            subtitle = "Confirms whether the phone is allowing VRC-A to read media notifications."
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
            subtitle = "Minimum cooldown is forced to 2 seconds to reduce VRChat cutouts."
        ) {
            Text("Last sent to VRChat (ms): ${vm.lastSentToVrchatAtMs}")
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
IMPORTANT FIRST STEP (VRCHAT OSC MUST BE ON)
============================================================
In VRChat:
1) Open the VRChat menu
2) Settings → OSC
3) Turn OSC ON (Enable OSC)

If OSC is OFF, VRC-A can’t show anything in chatbox even with the correct IP.

============================================================
WHERE TO GET YOUR HEADSET IP (QUEST/ANDROID)
============================================================
On your headset:
1) Settings
2) Wi-Fi
3) Tap your connected network
4) Find “IP Address” (sometimes under “Advanced”)

Example: 192.168.1.23

Phone and headset MUST be on the same Wi-Fi.

============================================================
WHAT THIS APP IS
============================================================
VRC-A sends text to VRChat’s Chatbox using OSC.
It’s made for standalone/mobile setups:
- Manual send
- Cycle messages
- Now Playing (media notifications)
- AFK tag on top
- Debug to show what’s failing

DEFAULTS
- No send sound effect
- Minimum 2s update speed
- AFK/Cycle/Music merged so they do not cancel each other
- Stopping a module clears it immediately
- Cycle list is limited to 10 messages

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

Must-do:
• Turn OSC ON in VRChat (Settings → OSC → Enable OSC)
• Use the headset IP (Quest Settings → Wi-Fi → your network → IP Address)
        """.trimIndent()
    }

    val features = remember {
        """
FEATURES
- Manual Send
- AFK top line + 3 saved presets
- Cycle middle line + Add-button system + 5 saved presets (10 messages max)
- Now Playing bottom block + progress presets
- Debug shows AFK/Cycle/Music output and the combined message
        """.trimIndent()
    }

    val tutorial = remember {
        """
TUTORIAL (STEP BY STEP)

1) Turn OSC ON in VRChat
VRChat menu → Settings → OSC → Enable OSC

2) Same Wi-Fi
Your headset and phone must be on the same Wi-Fi.

3) Find headset IP
Headset Settings → Wi-Fi → tap your network → IP Address (sometimes Advanced)

4) Put IP into VRC-A
Dashboard → type IP → Apply

5) Test sending
Manual Send → type “hello” → Send
If VRChat shows it, your connection works.

6) Enable Now Playing
Now Playing → Open Notification Access settings → enable VRC-A
Restart the app, play music, then check “Detected”.

7) Start modules
Cycle → Start
AFK → Start AFK
Now Playing → Start
        """.trimIndent()
    }

    val bugs = remember {
        """
COMMON ISSUES
- Nothing appears: OSC off in VRChat, wrong IP, or not same Wi-Fi
- Now Playing blank: Notification Access not enabled / app not restarted
- Progress not moving: depends on the music player updates
- VRChat cutouts: keep speeds 2 seconds or higher
        """.trimIndent()
    }

    val help = remember {
        """
QUICK HELP
Nothing sends:
- VRChat OSC enabled? (Settings → OSC → Enable OSC)
- Correct headset IP?
- Same Wi-Fi?

Now Playing blank:
- Enable Notification Access
- Restart VRC-A
- Try another music app to test
        """.trimIndent()
    }

    PageContainer {
        SectionCard(
            title = "Information",
            subtitle = "Setup, help, and full documentation for VRC-A."
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
