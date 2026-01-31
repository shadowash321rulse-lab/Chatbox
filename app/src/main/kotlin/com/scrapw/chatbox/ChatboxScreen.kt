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
import kotlinx.coroutines.launch

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
            subtitle = "Enter your headset IP then tap Apply. Make sure VRChat OSC is enabled (see Info tab)."
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
            subtitle = "One-off message (doesn’t affect Cycle / Now Playing / AFK)."
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CyclePage(vm: ChatboxViewModel) {
    val scope = rememberCoroutineScope()

    PageContainer {

        // =======================
        // AFK block (dropdown slots)
        // =======================
        SectionCard(
            title = "AFK (top line)",
            subtitle = "AFK always stays on the very top. It has a forced interval (no speed slider)."
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

            // Slot dropdown + preview + Load/Save (compact)
            PresetDropdownRow(
                title = "AFK Preset",
                slotCount = 3,
                selectedSlot = vm.afkPresetSlot,
                onSelectSlot = { vm.afkPresetSlot = it },
                previewText = vm.getAfkPresetPreview(vm.afkPresetSlot),
                onLoad = {
                    scope.launch { vm.loadAfkPreset(vm.afkPresetSlot) }
                },
                onSave = {
                    scope.launch { vm.saveAfkPreset(vm.afkPresetSlot, vm.afkMessage) }
                }
            )

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

        // =======================
        // Cycle block (10 lines max, add/remove UI)
        // =======================
        SectionCard(
            title = "Cycle Messages",
            subtitle = "No “press Enter” needed. Add up to 10 lines. Cycle stays above Music."
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Cycle enabled")
                Switch(
                    checked = vm.cycleEnabled,
                    onCheckedChange = { enabled ->
                        vm.cycleEnabled = enabled
                        if (!enabled) vm.stopCycle(clearFromChatbox = true)
                    }
                )
            }

            Text(
                "Cycle lines (${vm.cycleLines.size}/10)",
                style = MaterialTheme.typography.labelLarge
            )

            vm.cycleLines.forEachIndexed { idx, line ->
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = line,
                        onValueChange = { vm.updateCycleLine(idx, it) },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        label = { Text("Line ${idx + 1}") }
                    )
                    Spacer(Modifier.width(8.dp))
                    OutlinedButton(
                        onClick = { vm.removeCycleLine(idx) }
                    ) { Text("Del") }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = { vm.addCycleLine() },
                    enabled = vm.cycleLines.size < 10
                ) { Text("+ Add line") }

                OutlinedButton(
                    onClick = { vm.clearCycleLines() },
                    enabled = vm.cycleLines.isNotEmpty()
                ) { Text("Clear") }
            }

            PresetDropdownRow(
                title = "Cycle Preset",
                slotCount = 5,
                selectedSlot = vm.cyclePresetSlot,
                onSelectSlot = { vm.cyclePresetSlot = it },
                previewText = vm.getCyclePresetPreview(vm.cyclePresetSlot),
                onLoad = {
                    scope.launch { vm.loadCyclePreset(vm.cyclePresetSlot) }
                },
                onSave = {
                    scope.launch { vm.saveCyclePreset(vm.cyclePresetSlot, vm.cycleLines) }
                }
            )

            OutlinedTextField(
                value = vm.cycleIntervalSeconds.toString(),
                onValueChange = { raw ->
                    raw.toIntOrNull()?.let { vm.cycleIntervalSeconds = it.coerceAtLeast(2) }
                },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text("Cycle speed (seconds) (min 2)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
            )

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(onClick = { vm.startCycle() }, modifier = Modifier.weight(1f)) { Text("Start") }
                OutlinedButton(onClick = { vm.stopCycle(clearFromChatbox = true) }, modifier = Modifier.weight(1f)) { Text("Stop") }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NowPlayingPage(vm: ChatboxViewModel) {
    val ctx = LocalContext.current

    PageContainer {
        SectionCard(
            title = "Now Playing (phone music)",
            subtitle = "Uses Notification Access. Works with Spotify / YouTube Music / etc."
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
                label = { Text("Music refresh speed (seconds) (min 2)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
            )

            // Music progress preset: dropdown + live preview
            PresetDropdownRow(
                title = "Progress style",
                slotCount = 5,
                selectedSlot = vm.spotifyPreset,
                onSelectSlot = { vm.updateSpotifyPreset(it) },
                previewText = vm.getMusicPresetPreview(vm.spotifyPreset),
                onLoad = null,
                onSave = null
            )

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
            Text("Min send interval: ${vm.minSendIntervalSeconds}s (hard floor)")
        }
    }
}

@Composable
private fun InfoPage(vm: ChatboxViewModel) {
    var tab by rememberSaveable { mutableStateOf(InfoTab.Overview) }

    val overview = remember {
        """
VRC-A (VRChat Assistant)
Made by: Ashoska Mitsu Sisko
Base: ScrapW’s Chatbox base (heavily revamped)

VRC-A sends text to VRChat’s Chatbox using OSC over your Wi-Fi network.
It’s designed for standalone / mobile-friendly setups, with debug indicators
so you can quickly tell what’s failing (connection, permissions, detection).
        """.trimIndent()
    }

    val features = remember {
        """
FEATURES
- Manual sending
- Cycle messages (up to 10 lines)
- Now Playing block (phone notifications)
- 5 progress styles (dropdown + preview)
- AFK tag at top (forced interval)
- Debug indicators including OSC output preview
- Presets saved even after closing:
  - AFK presets: 3 slots
  - Cycle presets: 5 slots
- Built-in send throttling to avoid VRChat cutting out (min interval)
- Stop buttons can clear text from chatbox immediately
        """.trimIndent()
    }

    val tutorial = remember {
        """
TUTORIAL (step-by-step)
1) Put your headset + phone on the SAME Wi-Fi.

2) Turn ON OSC in VRChat:
   VRChat → Settings → OSC → Enable OSC.

3) Find your headset IP address (Quest example):
   Headset Settings → Wi-Fi → tap your network → IP Address.
   (Sometimes under “Advanced”.)

4) Put the IP into VRC-A:
   Dashboard → “Headset IP address” → Apply.

5) Test:
   Dashboard → Manual Send → type “hello” → Send.
   If it shows in VRChat chatbox, you’re connected.

6) Enable Now Playing:
   Now Playing tab → “Open Notification Access settings” → enable VRC-A.
   Restart VRC-A after enabling.
   Start music in Spotify/YouTube Music/etc.
   Check Detected / Artist / Title.

7) Run modules:
   - AFK: Cycle tab → enable AFK → Start AFK.
   - Cycle: Cycle tab → enable Cycle → Start.
   - Music: Now Playing tab → enable → Start.
        """.trimIndent()
    }

    val bugs = remember {
        """
KNOWN ISSUES / NOTES
- Some music apps only update progress on interaction (pause/seek/next).
  VRC-A estimates progress between updates, but players vary.
- If unrelated notifications appear, filtering may need tightening for your device.
- If nothing appears in VRChat: wrong IP or different Wi-Fi is the #1 cause.
        """.trimIndent()
    }

    val help = remember {
        """
HELP / QUICK FIXES
Nothing appears in VRChat:
- Double-check IP
- Make sure OSC is enabled in VRChat
- Same Wi-Fi on both devices
- Try Manual Send first

Now Playing is blank:
- Enable Notification Access
- Restart VRC-A
- Toggle Notification Access off/on
- Make sure your music app shows a media notification

Progress dot not moving:
- Some players don’t report continuously
- Try another music player to compare
        """.trimIndent()
    }

    val fullDoc = remember {
        vm.fullInfoDocumentText
    }

    PageContainer {
        SectionCard(
            title = "Information",
            subtitle = "What VRC-A is, how to set it up, and how to troubleshoot."
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
    }
}

/**
 * Compact "Option B" preset row:
 * Slot dropdown + preview + (optional) load/save buttons.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PresetDropdownRow(
    title: String,
    slotCount: Int,
    selectedSlot: Int,
    onSelectSlot: (Int) -> Unit,
    previewText: String,
    onLoad: (() -> Unit)?,
    onSave: (() -> Unit)?
) {
    var expanded by rememberSaveable { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(title, style = MaterialTheme.typography.labelLarge)

        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = !expanded }
        ) {
            OutlinedTextField(
                value = "Slot $selectedSlot",
                onValueChange = {},
                readOnly = true,
                modifier = Modifier
                    .menuAnchor()
                    .fillMaxWidth(),
                label = { Text("Preset slot") }
            )

            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                (1..slotCount).forEach { slot ->
                    DropdownMenuItem(
                        text = { Text("Slot $slot") },
                        onClick = {
                            onSelectSlot(slot)
                            expanded = false
                        }
                    )
                }
            }
        }

        Text(
            text = "Preview: ${previewText.ifBlank { "(empty)" }}",
            style = MaterialTheme.typography.bodySmall
        )

        if (onLoad != null || onSave != null) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (onLoad != null) {
                    OutlinedButton(onClick = onLoad, modifier = Modifier.weight(1f)) { Text("Load") }
                }
                if (onSave != null) {
                    Button(onClick = onSave, modifier = Modifier.weight(1f)) { Text("Save") }
                }
            }
        }
    }
}
