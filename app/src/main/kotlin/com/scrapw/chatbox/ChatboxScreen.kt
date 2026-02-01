package com.scrapw.chatbox

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Delete
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
    Info("Info")
}

private enum class InfoTab(val title: String) {
    Overview("Overview"),
    Tutorial("Tutorial"),
    Features("Features"),
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
                AppPage.Info -> InfoPage(chatboxViewModel)
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
            BottomTab(AppPage.Info, current, Icons.Filled.Info, onSelect)
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
            text = page.title,
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
            Text(text = title, style = MaterialTheme.typography.titleMedium)
            if (!subtitle.isNullOrBlank()) {
                Text(text = subtitle, style = MaterialTheme.typography.bodySmall)
            }
            content()
        }
    }
}

@Composable
private fun DashboardPage(vm: ChatboxViewModel) {
    val uiState by vm.messengerUiState.collectAsState()
    val ctx = LocalContext.current

    var ipInput by rememberSaveable { mutableStateOf(uiState.ipAddress) }
    LaunchedEffect(uiState.ipAddress) {
        if (ipInput.isBlank()) ipInput = uiState.ipAddress
    }

    PageContainer {
        SectionCard(
            title = "Connection",
            subtitle = "Enter your headset IP then tap Apply."
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
                text = "Current target: ${uiState.ipAddress}",
                style = MaterialTheme.typography.bodySmall
            )
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

        // ✅ NEW: permission helpers on Dashboard
        SectionCard(
            title = "Permissions & Background",
            subtitle = "Open system screens needed for Now Playing + keeping the app alive."
        ) {
            val pkg = ctx.packageName
            val overlayAllowed = android.provider.Settings.canDrawOverlays(ctx)

            val powerManager =
                ctx.getSystemService(android.content.Context.POWER_SERVICE) as android.os.PowerManager
            val ignoringBatteryOpt =
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                    powerManager.isIgnoringBatteryOptimizations(pkg)
                } else true

            Text(
                text = "Overlay permission: " + (if (overlayAllowed) "Allowed" else "Not allowed"),
                style = MaterialTheme.typography.bodySmall
            )
            Text(
                text = "Battery optimisation: " +
                    (if (ignoringBatteryOpt) "Not optimised" else "Optimised (may be killed)"),
                style = MaterialTheme.typography.bodySmall
            )

            OutlinedButton(
                onClick = { ctx.startActivity(vm.notificationAccessIntent()) },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Open Notification Access settings") }

            OutlinedButton(
                onClick = {
                    val i = android.content.Intent(
                        android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        android.net.Uri.parse("package:$pkg")
                    ).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                    ctx.startActivity(i)
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Open Overlay permission settings") }

            OutlinedButton(
                onClick = {
                    val i =
                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                            if (!powerManager.isIgnoringBatteryOptimizations(pkg)) {
                                android.content.Intent(
                                    android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                                    android.net.Uri.parse("package:$pkg")
                                )
                            } else {
                                android.content.Intent(
                                    android.provider.Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS
                                )
                            }
                        } else {
                            android.content.Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                                .setData(android.net.Uri.parse("package:$pkg"))
                        }
                    ctx.startActivity(i.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Open Battery optimisation settings") }

            OutlinedButton(
                onClick = {
                    val i = android.content.Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                        .setData(android.net.Uri.parse("package:$pkg"))
                        .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                    ctx.startActivity(i)
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Open App info (background restrictions)") }
        }
    }
}

@Composable
private fun CyclePage(vm: ChatboxViewModel) {
    val scope = rememberCoroutineScope()

    PageContainer {
        SectionCard(
            title = "AFK (top line)",
            subtitle = "AFK is always the top line. Forced interval. Stop clears it instantly."
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(text = "AFK enabled")
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

            Text(text = "AFK Presets (3):", style = MaterialTheme.typography.labelLarge)

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                (1..3).forEach { slot ->
                    ElevatedCard {
                        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            val preview = vm.getAfkPresetPreview(slot).ifBlank { "(empty)" }

                            Text(
                                text = "Preset $slot — $preview",
                                style = MaterialTheme.typography.bodyMedium
                            )

                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(
                                    onClick = { scope.launch { vm.loadAfkPreset(slot) } },
                                    modifier = Modifier.weight(1f)
                                ) { Text("Load") }

                                Button(
                                    onClick = { scope.launch { vm.saveAfkPreset(slot, vm.afkMessage) } },
                                    modifier = Modifier.weight(1f)
                                ) { Text("Save") }
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
            title = "Cycle Messages",
            subtitle = "No more ‘press enter’. Add up to 10 lines. Stop clears instantly."
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(text = "Cycle enabled")
                Switch(
                    checked = vm.cycleEnabled,
                    onCheckedChange = { vm.cycleEnabled = it }
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (vm.cycleLines.isEmpty()) {
                    Text(
                        text = "No lines yet. Tap Add Line.",
                        style = MaterialTheme.typography.bodySmall
                    )
                }

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
                        IconButton(onClick = { vm.removeCycleLine(idx) }) {
                            Icon(Icons.Filled.Delete, contentDescription = "Remove line")
                        }
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = { vm.addCycleLine() },
                        enabled = vm.cycleLines.size < 10,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Filled.Add, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text("Add line (${vm.cycleLines.size}/10)")
                    }

                    OutlinedButton(
                        onClick = { vm.clearCycleLines() },
                        enabled = vm.cycleLines.isNotEmpty(),
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
                label = { Text("Cycle speed (seconds) — min 2") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
            )

            Text(text = "Cycle Presets (5):", style = MaterialTheme.typography.labelLarge)

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                (1..5).forEach { slot ->
                    ElevatedCard {
                        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            val preview = vm.getCyclePresetPreview(slot).ifBlank { "(empty)" }
                            Text(
                                text = "Preset $slot — $preview",
                                style = MaterialTheme.typography.bodyMedium
                            )

                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(
                                    onClick = { scope.launch { vm.loadCyclePreset(slot) } },
                                    modifier = Modifier.weight(1f)
                                ) { Text("Load") }

                                Button(
                                    onClick = { scope.launch { vm.saveCyclePreset(slot, vm.cycleLines.toList()) } },
                                    modifier = Modifier.weight(1f)
                                ) { Text("Save") }
                            }
                        }
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = { vm.startCycle() },
                    modifier = Modifier.weight(1f),
                    enabled = vm.cycleEnabled && vm.cycleLines.any { it.trim().isNotEmpty() }
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

    var previewT by remember { mutableStateOf(0f) }
    LaunchedEffect(vm.spotifyPreset) {
        previewT = 0f
        while (true) {
            previewT += 0.02f
            if (previewT > 1f) previewT = 0f
            kotlinx.coroutines.delay(120)
        }
    }

    PageContainer {
        SectionCard(
            title = "Now Playing (phone music)",
            subtitle = "Uses Notification Access. Stop clears instantly."
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(text = "Enable Now Playing block")
                Switch(
                    checked = vm.spotifyEnabled,
                    onCheckedChange = { vm.setSpotifyEnabledFlag(it) }
                )
            }

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(text = "Demo mode (testing)")
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

            Text(text = "Progress bar preset:", style = MaterialTheme.typography.labelLarge)

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                (1..5).forEach { p ->
                    val selected = (vm.spotifyPreset == p)
                    val name = vm.getMusicPresetName(p)
                    val preview = vm.renderMusicPresetPreview(p, previewT)

                    ElevatedCard(
                        colors = if (selected) CardDefaults.elevatedCardColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer
                        ) else CardDefaults.elevatedCardColors()
                    ) {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(10.dp)
                                .clickable { vm.updateSpotifyPreset(p) },
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(text = name, style = MaterialTheme.typography.titleSmall)
                                Text(
                                    text = preview,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                            if (selected) {
                                Text(text = "Selected", style = MaterialTheme.typography.labelMedium)
                            }
                        }
                    }
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
                modifier = Modifier.fillMaxWidth(),
                enabled = vm.spotifyEnabled
            ) { Text("Send once now (test)") }
        }

        SectionCard(
            title = "Detected / Preview",
            subtitle = "If blank: enable access, restart app, then play music."
        ) {
            Text(text = "Detected: ${vm.nowPlayingDetected}")
            Text(text = "Artist: ${vm.lastNowPlayingArtist}")
            Text(text = "Title: ${vm.lastNowPlayingTitle}")
            Text(text = "App: ${vm.activePackage}")
            Text(text = "Status: ${if (vm.nowPlayingIsPlaying) "Playing" else "Paused"}")
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
            Text(text = "Listener connected: ${vm.listenerConnected}")
            Text(text = "Active package: ${vm.activePackage}")
            Text(text = "Detected: ${vm.nowPlayingDetected}")
            Text(text = "Playing: ${vm.nowPlayingIsPlaying}")
        }

        SectionCard(
            title = "OSC Output Preview",
            subtitle = "Shows what each module is generating, plus the combined message."
        ) {
            SelectionContainer {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(text = "AFK:", style = MaterialTheme.typography.labelLarge)
                    Text(text = vm.debugLastAfkOsc, fontFamily = FontFamily.Monospace)

                    Text(text = "Cycle:", style = MaterialTheme.typography.labelLarge)
                    Text(text = vm.debugLastCycleOsc, fontFamily = FontFamily.Monospace)

                    Text(text = "Music:", style = MaterialTheme.typography.labelLarge)
                    Text(text = vm.debugLastMusicOsc, fontFamily = FontFamily.Monospace)

                    Text(text = "Combined:", style = MaterialTheme.typography.labelLarge)
                    Text(text = vm.debugLastCombinedOsc, fontFamily = FontFamily.Monospace)
                }
            }
        }

        SectionCard(
            title = "VRChat send status",
            subtitle = null
        ) {
            Text(text = "Last sent to VRChat (ms): ${vm.lastSentToVrchatAtMs}")
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
It’s designed for standalone/mobile setups with debug indicators so you can
quickly tell what’s failing (connection, permissions, detection).
        """.trimIndent()
    }

    val tutorial = remember {
        """
TUTORIAL (Step by Step)

1) Turn OSC ON in VRChat:
   VRChat → Settings → OSC → Enable OSC

2) Same Wi-Fi:
   Your phone and headset MUST be on the same Wi-Fi network.

3) Find your headset IP:
   Quest / Android headset:
   Settings → Wi-Fi → tap your network → Advanced → IP Address
   Example: 192.168.1.23

4) Put IP into VRC-A:
   Dashboard → Headset IP address → Apply

5) Test manual send:
   Dashboard → Manual Send → type “hello” → Send

6) Enable Now Playing:
   Dashboard → Permissions & Background → Open Notification Access settings → enable VRC-A
   Then restart VRC-A and play music.

7) Start Now Playing sender:
   Toggle Enable Now Playing block → Start

8) Cycle:
   Cycle tab → Enable Cycle → Add up to 10 lines → Start

9) AFK:
   Cycle tab → Enable AFK → type AFK text → Start
        """.trimIndent()
    }

    val features = remember {
        """
FEATURES

Connection / Manual Send
- Set headset IP
- Send one-off messages instantly

AFK (Top Line)
- AFK appears above Cycle + Music
- Forced interval (no slider)
- 3 saved presets (persist)

Cycle
- Up to 10 lines per cycle list
- Min interval 2 seconds
- 5 saved presets (persist)

Now Playing
- Reads phone media notifications (Notification Access)
- Demo mode for testing
- Independent refresh speed (min 2 seconds)
- Hard locked progress presets:
  Love / Minimal / Crystal / Soundwave / Geometry

Debug
- Listener status
- Detected title/artist/app
- Shows what AFK/Cycle/Music are generating + combined output
        """.trimIndent()
    }

    val bugs = remember {
        """
KNOWN ISSUES / NOTES

- Some music players do not provide continuous playback position updates.
  In that case, the bar may update only when the player refreshes state.

- If your router has “client isolation”, phone → headset OSC traffic can be blocked.

- If nothing shows in VRChat:
  - Check IP
  - Ensure OSC is enabled in VRChat
  - Same Wi-Fi
        """.trimIndent()
    }

    val help = remember {
        """
QUICK TROUBLESHOOTING

Nothing appears in VRChat:
- Check headset IP
- VRChat OSC enabled
- Same Wi-Fi

Now Playing blank:
- Enable Notification Access
- Restart app
- Start playing music (must show media notification)

Progress not moving:
- Depends on player. Try a different music app to compare.
        """.trimIndent()
    }

    val fullDoc = remember {
        """
VRC-A (VRChat Assistant)
Made by: Ashoska Mitsu Sisko
Base: ScrapW’s Chatbox base (heavily revamped)
        """.trimIndent()
    }

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
                    val selected = (t == tab)
                    val colors = if (selected) ButtonDefaults.buttonColors()
                    else ButtonDefaults.outlinedButtonColors()

                    Button(
                        onClick = { tab = t },
                        colors = colors,
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text(text = t.title)
                    }
                }
            }

            val text: String = when (tab) {
                InfoTab.Overview -> overview
                InfoTab.Tutorial -> tutorial
                InfoTab.Features -> features
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
