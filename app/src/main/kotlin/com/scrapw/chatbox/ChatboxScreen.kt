package com.scrapw.chatbox

import androidx.compose.animation.AnimatedVisibility
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
                AppPage.Settings -> InfoPage()
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
private fun ExpandableHeader(
    title: String,
    subtitle: String?,
    expanded: Boolean,
    onToggle: () -> Unit
) {
    Column(
        Modifier
            .fillMaxWidth()
            .clickable { onToggle() }
            .padding(vertical = 2.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            Text(if (expanded) "Hide" else "Show", style = MaterialTheme.typography.labelMedium)
        }
        if (!subtitle.isNullOrBlank()) {
            Spacer(Modifier.height(2.dp))
            Text(subtitle, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun PresetRow(
    label: String,
    preview: String,
    onLoad: () -> Unit,
    onSave: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
            OutlinedButton(
                onClick = onLoad,
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
            ) { Text("Load") }
            Spacer(Modifier.width(8.dp))
            Button(
                onClick = onSave,
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
            ) { Text("Save") }
        }

        if (preview.isBlank()) {
            Text("Empty", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            Text(preview, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun DashboardPage(vm: ChatboxViewModel) {
    val uiState by vm.messengerUiState.collectAsState()

    // local input so typing doesn't fight flow
    var ipInput by rememberSaveable { mutableStateOf(uiState.ipAddress) }
    LaunchedEffect(uiState.ipAddress) {
        if (ipInput.isBlank()) ipInput = uiState.ipAddress
    }

    PageContainer {
        SectionCard(
            title = "Connection",
            subtitle = "Enter your headset/phone IP then tap Apply."
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

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(onClick = { vm.sendMessage() }, modifier = Modifier.weight(1f)) { Text("Send") }
                OutlinedButton(onClick = { vm.stashMessage() }, modifier = Modifier.weight(1f)) { Text("Stash") }
            }
        }
    }
}

@Composable
private fun CyclePage(vm: ChatboxViewModel) {
    var afkExpanded by rememberSaveable { mutableStateOf(true) }
    var cycleExpanded by rememberSaveable { mutableStateOf(true) }

    PageContainer {

        ElevatedCard {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {

                ExpandableHeader(
                    title = "AFK (top line)",
                    subtitle = "AFK stays above Cycle + Now Playing. Interval is forced.",
                    expanded = afkExpanded,
                    onToggle = { afkExpanded = !afkExpanded }
                )

                AnimatedVisibility(afkExpanded) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("AFK enabled")
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
                            label = { Text("AFK text") }
                        )

                        Text("Presets (AFK)", style = MaterialTheme.typography.labelLarge)
                        PresetRow(
                            label = "Slot 1",
                            preview = vm.getAfkPresetPreview(1),
                            onLoad = { vm.loadAfkPreset(1) },
                            onSave = { vm.saveAfkPreset(1, vm.afkMessage) }
                        )
                        Divider()
                        PresetRow(
                            label = "Slot 2",
                            preview = vm.getAfkPresetPreview(2),
                            onLoad = { vm.loadAfkPreset(2) },
                            onSave = { vm.saveAfkPreset(2, vm.afkMessage) }
                        )
                        Divider()
                        PresetRow(
                            label = "Slot 3",
                            preview = vm.getAfkPresetPreview(3),
                            onLoad = { vm.loadAfkPreset(3) },
                            onSave = { vm.saveAfkPreset(3, vm.afkMessage) }
                        )

                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Button(
                                onClick = { vm.startAfkSender() },
                                modifier = Modifier.weight(1f),
                                enabled = vm.afkEnabled
                            ) { Text("Start") }

                            OutlinedButton(
                                onClick = { vm.stopAfkSender() },
                                modifier = Modifier.weight(1f)
                            ) { Text("Stop") }
                        }

                        OutlinedButton(
                            onClick = { vm.sendAfkNow() },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = vm.afkEnabled
                        ) { Text("Send once") }
                    }
                }
            }
        }

        ElevatedCard {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {

                ExpandableHeader(
                    title = "Cycle Messages",
                    subtitle = "Rotates lines. Minimum interval is 2 seconds.",
                    expanded = cycleExpanded,
                    onToggle = { cycleExpanded = !cycleExpanded }
                )

                AnimatedVisibility(cycleExpanded) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {

                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Cycle enabled")
                            Switch(
                                checked = vm.cycleEnabled,
                                onCheckedChange = { vm.setCycleEnabledFlag(it) }
                            )
                        }

                        OutlinedTextField(
                            value = vm.cycleMessages,
                            onValueChange = { vm.updateCycleMessages(it) },
                            modifier = Modifier.fillMaxWidth(),
                            minLines = 4,
                            label = { Text("Lines (one per line)") }
                        )

                        OutlinedTextField(
                            value = vm.cycleIntervalSeconds.toString(),
                            onValueChange = { raw ->
                                raw.toIntOrNull()?.let { vm.setCycleIntervalSecondsFlag(it) }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            label = { Text("Cycle speed (seconds) (min 2)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                        )

                        Text("Presets (Cycle)", style = MaterialTheme.typography.labelLarge)
                        (1..5).forEach { slot ->
                            PresetRow(
                                label = "Slot $slot",
                                preview = vm.getCyclePresetPreview(slot),
                                onLoad = { vm.loadCyclePreset(slot) },
                                onSave = { vm.saveCyclePreset(slot, vm.cycleMessages) }
                            )
                            if (slot != 5) Divider()
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Button(
                                onClick = { vm.startCycle() },
                                modifier = Modifier.weight(1f),
                                enabled = vm.cycleEnabled
                            ) { Text("Start") }

                            OutlinedButton(
                                onClick = { vm.stopCycle() },
                                modifier = Modifier.weight(1f)
                            ) { Text("Stop") }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun NowPlayingPage(vm: ChatboxViewModel) {
    val ctx = LocalContext.current
    var musicExpanded by rememberSaveable { mutableStateOf(true) }
    var previewExpanded by rememberSaveable { mutableStateOf(true) }

    PageContainer {

        ElevatedCard {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                ExpandableHeader(
                    title = "Now Playing (phone music)",
                    subtitle = "Uses Notification Access. Min refresh is 2 seconds.",
                    expanded = musicExpanded,
                    onToggle = { musicExpanded = !musicExpanded }
                )

                AnimatedVisibility(musicExpanded) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
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

                        Text("Progress bar preset:", style = MaterialTheme.typography.labelLarge)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
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
                            Button(
                                onClick = { vm.startNowPlayingSender() },
                                modifier = Modifier.weight(1f),
                                enabled = vm.spotifyEnabled
                            ) { Text("Start") }

                            OutlinedButton(
                                onClick = { vm.stopNowPlayingSender() },
                                modifier = Modifier.weight(1f)
                            ) { Text("Stop") }
                        }

                        OutlinedButton(
                            onClick = { vm.sendNowPlayingOnce() },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("Send once (test)") }
                    }
                }
            }
        }

        ElevatedCard {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                ExpandableHeader(
                    title = "Detected / Preview",
                    subtitle = "If blank: enable access, restart app, then play music.",
                    expanded = previewExpanded,
                    onToggle = { previewExpanded = !previewExpanded }
                )

                AnimatedVisibility(previewExpanded) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Detected: ${vm.nowPlayingDetected}")
                        Text("Artist: ${vm.lastNowPlayingArtist}")
                        Text("Title: ${vm.lastNowPlayingTitle}")
                        Text("App: ${vm.activePackage}")
                        Text("Status: ${if (vm.nowPlayingIsPlaying) "Playing" else "Paused"}")
                    }
                }
            }
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
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    DebugBlock("AFK", vm.debugLastAfkOsc)
                    DebugBlock("Cycle", vm.debugLastCycleOsc)
                    DebugBlock("Music", vm.debugLastMusicOsc)
                    DebugBlock("Combined", vm.debugLastCombinedOsc)
                }
            }
        }

        SectionCard(title = "VRChat send status") {
            Text("Last sent to VRChat (ms): ${vm.lastSentToVrchatAtMs}")
            Text(
                "Tip: Stop buttons send a blank message to clear the Chatbox immediately.",
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

@Composable
private fun DebugBlock(title: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, style = MaterialTheme.typography.labelLarge)
        Text(
            if (value.isBlank()) "(empty)" else value,
            fontFamily = FontFamily.Monospace,
            style = MaterialTheme.typography.bodySmall
        )
        Divider()
    }
}

@Composable
private fun InfoPage() {
    var tab by rememberSaveable { mutableStateOf(InfoTab.Overview) }

    val overview = remember {
        """
VRC-A (VRChat Assistant)
Made by: Ashoska Mitsu Sisko
Base: ScrapW’s Chatbox base (heavily revamped)

VRC-A sends text to VRChat’s Chatbox using OSC over your Wi-Fi network.
It is designed for standalone/mobile-friendly setups and includes debug tools
so you can quickly tell what’s failing (connection, permissions, detection).
        """.trimIndent()
    }

    val features = remember {
        """
FEATURES
A) Connection / Sending
- Set target headset/device IP and send one-off messages.

B) Cycle (Rotating Messages)
- Toggle Cycle on/off
- Multi-line rotation (one line per row)
- Adjustable interval (minimum 2 seconds)
- Presets: 5 slots (messages + interval saved)

C) Now Playing (Phone Music)
- Reads phone media via Notification Access / MediaSession
- Demo mode for testing
- Refresh interval minimum 2 seconds
- 5 progress bar presets
- Shows “Paused” when playback is paused (when player reports it)

D) AFK (Top Line)
- AFK displays above Cycle + Now Playing
- Forced interval (no chooser)
- Presets: 3 slots (text saved)

E) Debug
- Shows listener connection + active package + detected state
- Shows OSC output preview for: AFK / Cycle / Music / Combined

QoL changes
- Stop buttons send a blank message to clear VRChat chatbox immediately.
- Minimum send intervals help reduce VRChat “random cut-out” from sending too fast.
        """.trimIndent()
    }

    val tutorial = remember {
        """
TUTORIAL (DUMBED DOWN)
1) Same Wi-Fi
- Your phone and headset must be on the same network.

2) Find headset IP
Quest:
Settings → Wi-Fi → tap network → look for “IP address”
Example: 192.168.1.23

3) Put IP into VRC-A
Dashboard → Headset IP address → Apply

4) Test sending
Manual Send → type hello → Send
If it shows in VRChat, connection is good.

5) Enable Now Playing
Now Playing page → Open Notification Access settings
Enable VRC-A
Restart VRC-A
Play music (Spotify / YouTube Music / etc)

6) Start modules
Cycle page → enable Cycle → Start
Now Playing page → enable Now Playing → Start
AFK page → enable AFK → Start
        """.trimIndent()
    }

    val bugs = remember {
        """
FIXED / IMPROVED
- Build/plugin issues fixed (Gradle / missing plugins)
- Redeclaration conflicts removed (duplicate composables/enums)
- Now Playing stuck on blank fixed via NowPlayingState → ViewModel fields
- Progress time live-updating restored using elapsedRealtime position tracking
- Module stop clears chatbox via blank-send (no “stuck message” waiting)

KNOWN / POSSIBLE ISSUES
- Some music apps only update position on interaction (pause/seek/skip).
  Live progress depends on the player reporting PlaybackState properly.
- If a non-media notification slips through, stricter filtering may still be needed
  in the NotificationListener to ignore irrelevant notifications.
- Routers with “client isolation” can block phone→headset traffic.
        """.trimIndent()
    }

    val help = remember {
        """
HELP / TROUBLESHOOTING
Nothing appears in VRChat:
- Re-check IP
- Make sure OSC is enabled in VRChat
- Same Wi-Fi
- Try Manual Send first

Now Playing blank:
- Enable Notification Access for VRC-A
- Restart app (and sometimes phone)
- Make sure music app shows a media notification

Progress dot not moving:
- Depends on music player updates
- Try a different music app to compare
        """.trimIndent()
    }

    // If you want the full mega-document embedded later, paste it into this string.
    val fullDoc = remember {
        """
Full document can be pasted here when you want the entire “manual” stored inside the app.
(Currently the Info tabs cover the important parts in a cleaner layout.)
        """.trimIndent()
    }

    PageContainer {
        SectionCard(
            title = "Information",
            subtitle = "Overview, features, tutorial, bugs, and help."
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
