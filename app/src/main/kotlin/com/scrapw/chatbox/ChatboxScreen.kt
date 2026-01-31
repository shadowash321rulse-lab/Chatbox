package com.scrapw.chatbox

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
    Settings("Settings")
}

private enum class InfoTab(val title: String) {
    Overview("Overview"),
    Features("Features"),
    Tutorial("Tutorial"),
    Bugs("Bugs"),
    Troubleshoot("Help"),
    FullDoc("Full Doc")
}

private data class MusicPresetUi(val id: Int, val name: String)

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
        bottomBar = { SlimBottomBar(current = page, onSelect = { page = it }) }
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
private fun SlimBottomBar(current: AppPage, onSelect: (AppPage) -> Unit) {
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
private fun SectionCard(title: String, subtitle: String? = null, content: @Composable ColumnScope.() -> Unit) {
    ElevatedCard {
        Column(
            Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            if (!subtitle.isNullOrBlank()) Text(subtitle, style = MaterialTheme.typography.bodySmall)
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
            subtitle = "Enter your headset IP and tap Apply. Make sure VRChat OSC is ON (VRChat → Settings → OSC)."
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
                Button(onClick = { vm.ipAddressApply(ipInput.trim()) }, modifier = Modifier.weight(1f)) { Text("Apply") }
                OutlinedButton(onClick = { ipInput = uiState.ipAddress }, modifier = Modifier.weight(1f)) { Text("Reset") }
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
            Button(onClick = { vm.sendMessage() }, modifier = Modifier.fillMaxWidth()) { Text("Send") }
        }
    }
}

@Composable
private fun CyclePage(vm: ChatboxViewModel) {
    PageContainer {

        SectionCard(
            title = "AFK (top line)",
            subtitle = "AFK sits above Cycle + Now Playing. AFK interval is forced (simple + safer)."
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

            PresetStrip(
                title = "AFK Presets",
                slots = 3,
                getName = { vm.getAfkPresetName(it) },
                setName = { slot, name -> vm.updateAfkPresetName(slot, name) },
                getPreview = { vm.getAfkPresetTextPreview(it) },
                onLoad = { vm.loadAfkPresetSlot(it) },
                onSave = { vm.saveAfkPresetSlot(it) }
            )

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(onClick = { vm.startAfkSender() }, modifier = Modifier.weight(1f), enabled = vm.afkEnabled) { Text("Start AFK") }
                OutlinedButton(onClick = { vm.stopAfkSender(clearFromChatbox = true) }, modifier = Modifier.weight(1f)) { Text("Stop AFK") }
            }

            OutlinedButton(
                onClick = { vm.sendAfkNow() },
                modifier = Modifier.fillMaxWidth(),
                enabled = vm.afkEnabled
            ) { Text("Send AFK once") }
        }

        SectionCard(
            title = "Cycle Messages",
            subtitle = "No more “press enter to add”. Add up to 10 lines. Each line cycles."
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Cycle enabled")
                Switch(
                    checked = vm.cycleEnabled,
                    onCheckedChange = {
                        vm.cycleEnabled = it
                        vm.persistCycleEnabledNow()
                        if (!it) vm.stopCycle(clearFromChatbox = true)
                    }
                )
            }

            // Line editor (max 10)
            vm.cycleLines.forEachIndexed { idx, line ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = line,
                        onValueChange = { vm.updateCycleLine(idx, it) },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        label = { Text("Line ${idx + 1}") }
                    )
                    Spacer(Modifier.width(8.dp))
                    OutlinedButton(
                        onClick = { vm.removeCycleLine(idx) },
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 10.dp)
                    ) { Text("✕") }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = { vm.addCycleLine() },
                    enabled = vm.cycleLines.size < 10,
                    modifier = Modifier.weight(1f)
                ) { Text("Add line") }
                OutlinedButton(onClick = { vm.clearCycleLines() }, modifier = Modifier.weight(1f)) { Text("Clear") }
            }

            OutlinedTextField(
                value = vm.cycleIntervalSeconds.toString(),
                onValueChange = { raw -> raw.toIntOrNull()?.let { vm.updateCycleInterval(it) } },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text("Cycle speed (seconds) (min 2)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
            )

            PresetStrip(
                title = "Cycle Presets",
                slots = 5,
                getName = { vm.getCyclePresetName(it) },
                setName = { slot, name -> vm.updateCyclePresetName(slot, name) },
                getPreview = { vm.getCyclePresetFirstLinePreview(it) },
                onLoad = { vm.loadCyclePresetSlot(it) },
                onSave = { vm.saveCyclePresetSlot(it) }
            )

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

    val musicPresets = remember {
        listOf(
            MusicPresetUi(1, "Love"),
            MusicPresetUi(2, "Minimal"),
            MusicPresetUi(3, "Crystal"),
            MusicPresetUi(4, "Soundwave"),
            MusicPresetUi(5, "Geometry")
        )
    }

    PageContainer {
        SectionCard(
            title = "Now Playing (phone music)",
            subtitle = "Enable Notification Access. Works with Spotify / YouTube Music / etc."
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Enable Now Playing block")
                Switch(checked = vm.spotifyEnabled, onCheckedChange = { vm.setSpotifyEnabledFlag(it) })
            }

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Demo mode (testing)")
                Switch(checked = vm.spotifyDemoEnabled, onCheckedChange = { vm.setSpotifyDemoFlag(it) })
            }

            OutlinedButton(
                onClick = { ctx.startActivity(vm.notificationAccessIntent()) },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Open Notification Access settings") }

            OutlinedTextField(
                value = vm.musicRefreshSeconds.toString(),
                onValueChange = { raw -> raw.toIntOrNull()?.let { vm.musicRefreshSeconds = it.coerceAtLeast(2) } },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text("Music refresh speed (seconds) (min 2)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
            )

            Text("Progress bar presets (animated preview):", style = MaterialTheme.typography.labelLarge)

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                musicPresets.forEach { p ->
                    MusicPresetRow(
                        name = p.name,
                        selected = (vm.spotifyPreset == p.id),
                        preview = { t -> vm.renderMusicPreview(p.id, t) },
                        onSelect = { vm.updateSpotifyPreset(p.id) }
                    )
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
        SectionCard(title = "Listener", subtitle = "Confirms Notification Access + media detection.") {
            Text("Listener connected: ${vm.listenerConnected}")
            Text("Active package: ${vm.activePackage}")
            Text("Detected: ${vm.nowPlayingDetected}")
            Text("Playing: ${vm.nowPlayingIsPlaying}")
        }

        SectionCard(
            title = "OSC Output Preview",
            subtitle = "Shows what AFK / Cycle / Music are generating, plus the combined message."
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

        SectionCard(title = "VRChat send status") {
            Text("Last sent to VRChat (ms): ${vm.lastSentToVrchatAtMs}")
        }
    }
}

@Composable
private fun SettingsPage(vm: ChatboxViewModel) {
    var tab by rememberSaveable { mutableStateOf(InfoTab.Overview) }

    val overview = remember {
        """
VRC-A (VRChat Assistant)
Made by: Ashoska Mitsu Sisko
Base: ScrapW’s Chatbox base (heavily revamped)

VRC-A sends text to VRChat’s Chatbox using OSC over your Wi-Fi.
You can show:
- AFK (top line)
- Cycle (rotating lines)
- Now Playing (phone media notification)

IMPORTANT: VRChat OSC must be enabled:
VRChat → Settings → OSC → Enable OSC
        """.trimIndent()
    }

    val features = remember {
        """
FEATURES
- Manual sending (no SFX)
- Cycle: up to 10 lines, speed has minimum (2s)
- Cycle presets (5): each has a name + saved lines + saved speed
- Now Playing: Notification Access listener, separate refresh speed (min 2s)
- Music bar presets are named (Love/Minimal/Crystal/Soundwave/Geometry)
- AFK: forced interval, sits at top
- AFK presets (3): each has a name + saved text
- Debug: shows what each module is generating (AFK/Cycle/Music/Combined)
        """.trimIndent()
    }

    val tutorial = remember {
        """
TUTORIAL (DUMBED DOWN)
1) Put phone + headset on the same Wi-Fi.
2) Enable VRChat OSC:
   VRChat → Settings → OSC → Enable OSC
3) Find headset IP:
   Quest/Android headset → Settings → Wi-Fi → tap your network → “IP Address”
4) Put that IP in Dashboard → Apply
5) Test Manual Send (type hello → Send)
6) Now Playing:
   Now Playing tab → Open Notification Access settings → enable VRC-A
   Restart VRC-A, play music, check Detected
7) Start Now Playing sender / Cycle / AFK as needed
        """.trimIndent()
    }

    val bugs = remember { "See Full Doc for detailed history + current known issues." }

    val help = remember {
        """
HELP
Nothing appears in VRChat:
- Wrong IP OR not same Wi-Fi
- VRChat OSC not enabled
- Router “client isolation” can block devices talking

Now Playing blank:
- Notification Access not enabled
- Restart app after enabling
- Some music apps don’t expose proper media info
        """.trimIndent()
    }

    val fullDoc = remember { vm.fullInfoDocumentText }

    PageContainer {
        SectionCard(
            title = "Information",
            subtitle = "Everything about VRC-A (what it is, tutorial, features, and bugs)."
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
                Text(text = text, style = MaterialTheme.typography.bodyMedium, fontFamily = FontFamily.Monospace)
            }
        }

        SectionCard(title = "About (short)") {
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

/**
 * Compact preset UI:
 * - Shows name (editable)
 * - Shows preview (what’s saved)
 * - Load + Save buttons aligned neatly
 */
@Composable
private fun PresetStrip(
    title: String,
    slots: Int,
    getName: (Int) -> String,
    setName: (Int, String) -> Unit,
    getPreview: (Int) -> String,
    onLoad: (Int) -> Unit,
    onSave: (Int) -> Unit
) {
    Text(title, style = MaterialTheme.typography.labelLarge)

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        (1..slots).forEach { slot ->
            ElevatedCard {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = getName(slot),
                        onValueChange = { setName(slot, it) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        label = { Text("Preset name") }
                    )

                    val prev = getPreview(slot).ifBlank { "(empty)" }
                    Text("Saved preview: $prev", style = MaterialTheme.typography.bodySmall)

                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedButton(onClick = { onLoad(slot) }, modifier = Modifier.weight(1f)) { Text("Load") }
                        Button(onClick = { onSave(slot) }, modifier = Modifier.weight(1f)) { Text("Save") }
                    }
                }
            }
        }
    }
}

/**
 * Music preset row:
 * - named
 * - animated dot preview
 */
@Composable
private fun MusicPresetRow(
    name: String,
    selected: Boolean,
    preview: (Float) -> String,
    onSelect: () -> Unit
) {
    val trans = rememberInfiniteTransition(label = "musicpreview")
    val t by trans.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1800, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "t"
    )

    val colors =
        if (selected) ButtonDefaults.buttonColors()
        else ButtonDefaults.outlinedButtonColors()

    Button(
        onClick = onSelect,
        colors = colors,
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp)
    ) {
        Column(Modifier.fillMaxWidth()) {
            Text(name, style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.height(2.dp))
            Text(preview(t), fontFamily = FontFamily.Monospace)
        }
    }
}
