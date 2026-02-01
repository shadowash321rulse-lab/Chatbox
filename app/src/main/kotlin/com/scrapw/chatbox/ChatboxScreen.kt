package com.scrapw.chatbox

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
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
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
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
import androidx.compose.ui.text.input.TextFieldValue
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
                AppPage.Info -> InfoPage()
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
    val ctx = LocalContext.current
    val uiState by vm.messengerUiState.collectAsState()

    // ✅ IP input uses TextFieldValue to avoid cursor jumps
    var ipInput by rememberSaveable(stateSaver = TextFieldValue.Saver) {
        mutableStateOf(TextFieldValue(uiState.ipAddress))
    }
    LaunchedEffect(uiState.ipAddress) {
        if (ipInput.text.isBlank()) ipInput = TextFieldValue(uiState.ipAddress)
    }

    // ✅ Small “slide” / collapsible system card (not its own nav tab)
    var sysCollapsed by rememberSaveable { mutableStateOf(true) }

    fun openOverlaySettings() {
        val intent = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:${ctx.packageName}")
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        ctx.startActivity(intent)
    }

    fun openBatteryOptimizationSettings() {
        // We keep this simple: open the system list/settings screen.
        val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        ctx.startActivity(intent)
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
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text)
            )

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = { vm.ipAddressApply(ipInput.text.trim()) },
                    modifier = Modifier.weight(1f)
                ) { Text("Apply") }

                OutlinedButton(
                    onClick = { ipInput = TextFieldValue(uiState.ipAddress) },
                    modifier = Modifier.weight(1f)
                ) { Text("Reset") }
            }

            Text(
                text = "Current target: ${uiState.ipAddress}",
                style = MaterialTheme.typography.bodySmall
            )
        }

        // ✅ Permissions & System “slide” (collapsed by default)
        ElevatedCard {
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { sysCollapsed = !sysCollapsed },
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Permissions & System", style = MaterialTheme.typography.titleSmall)
                        if (sysCollapsed) {
                            Text(
                                "Notification access • Overlay • Battery optimization",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Icon(
                        imageVector = if (sysCollapsed) Icons.Filled.ExpandMore else Icons.Filled.ExpandLess,
                        contentDescription = null
                    )
                }

                if (!sysCollapsed) {
                    OutlinedButton(
                        onClick = { ctx.startActivity(vm.notificationAccessIntent()) },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Open Notification Access settings") }

                    OutlinedButton(
                        onClick = { openOverlaySettings() },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Open Overlay permission (draw over apps)") }

                    OutlinedButton(
                        onClick = { openBatteryOptimizationSettings() },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Open Battery optimization settings") }

                    if (Build.VERSION.SDK_INT >= 31) {
                        Text(
                            text = "Tip: On some phones you must disable optimization manually after opening the screen.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
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
    val scope = rememberCoroutineScope()

    // ✅ Fix cursor-jump for numeric interval input (Cycle)
    var cycleIntervalInput by rememberSaveable(stateSaver = TextFieldValue.Saver) {
        mutableStateOf(TextFieldValue(vm.cycleIntervalSeconds.toString()))
    }
    LaunchedEffect(vm.cycleIntervalSeconds) {
        val target = vm.cycleIntervalSeconds.toString()
        if (cycleIntervalInput.text != target) cycleIntervalInput = TextFieldValue(target)
    }

    // ✅ Fix cursor-jump for cycle lines by keeping TextFieldValue per-row
    val cycleLineFields = remember { mutableStateMapOf<Int, TextFieldValue>() }

    fun syncCycleLineFieldsFromVm() {
        val validKeys = vm.cycleLines.indices.toSet()
        cycleLineFields.keys.toList().forEach { k -> if (k !in validKeys) cycleLineFields.remove(k) }
        vm.cycleLines.forEachIndexed { idx, text ->
            val existing = cycleLineFields[idx]
            if (existing == null || existing.text != text) cycleLineFields[idx] = TextFieldValue(text)
        }
    }

    LaunchedEffect(vm.cycleLines.size) { syncCycleLineFieldsFromVm() }
    LaunchedEffect(vm.cycleLines.toList()) { syncCycleLineFieldsFromVm() }

    fun afkPresetsPreview(): String {
        val parts = (1..3).map { slot ->
            val p = vm.getAfkPresetPreview(slot).ifBlank { "empty" }
            "$slot:$p"
        }
        return parts.joinToString("  •  ").take(80).let { if (it.length >= 80) it.take(79) + "…" else it }
    }

    fun cyclePresetsPreview(): String {
        val parts = (1..5).map { slot ->
            val p = vm.getCyclePresetPreview(slot).ifBlank { "empty" }
            "$slot:$p"
        }
        return parts.joinToString("  •  ").take(80).let { if (it.length >= 80) it.take(79) + "…" else it }
    }

    PageContainer {
        SectionCard(
            title = "AFK (top line)",
            subtitle = "AFK is always the top line. Forced interval. Stop clears it instantly."
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

            // ✅ Collapsible presets block
            ElevatedCard {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { vm.setAfkPresetsCollapsed(!vm.afkPresetsCollapsed) },
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("AFK Presets (3)", style = MaterialTheme.typography.titleSmall)
                            if (vm.afkPresetsCollapsed) {
                                Text(
                                    afkPresetsPreview(),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Icon(
                            imageVector = if (vm.afkPresetsCollapsed) Icons.Filled.ExpandMore else Icons.Filled.ExpandLess,
                            contentDescription = null
                        )
                    }

                    if (!vm.afkPresetsCollapsed) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            (1..3).forEach { slot ->
                                ElevatedCard {
                                    Column(
                                        Modifier.padding(10.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        val preview = vm.getAfkPresetPreview(slot).ifBlank { "(empty)" }
                                        Text("Preset $slot — $preview")

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
            subtitle = "Add up to 10 lines. Stop clears instantly."
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Cycle enabled")
                Switch(checked = vm.cycleEnabled, onCheckedChange = { vm.cycleEnabled = it })
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (vm.cycleLines.isEmpty()) {
                    Text("No lines yet. Tap Add Line.", style = MaterialTheme.typography.bodySmall)
                }

                vm.cycleLines.forEachIndexed { idx, _ ->
                    val fieldValue = cycleLineFields[idx] ?: TextFieldValue(vm.cycleLines.getOrNull(idx).orEmpty())
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = fieldValue,
                            onValueChange = { newValue ->
                                cycleLineFields[idx] = newValue
                                vm.updateCycleLine(idx, newValue.text)
                            },
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
                value = cycleIntervalInput,
                onValueChange = { v ->
                    cycleIntervalInput = v
                    v.text.toIntOrNull()?.let { vm.cycleIntervalSeconds = it.coerceAtLeast(2) }
                },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text("Cycle speed (seconds) — min 2") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
            )

            // ✅ Collapsible presets block
            ElevatedCard {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { vm.setCyclePresetsCollapsed(!vm.cyclePresetsCollapsed) },
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("Cycle Presets (5)", style = MaterialTheme.typography.titleSmall)
                            if (vm.cyclePresetsCollapsed) {
                                Text(
                                    cyclePresetsPreview(),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Icon(
                            imageVector = if (vm.cyclePresetsCollapsed) Icons.Filled.ExpandMore else Icons.Filled.ExpandLess,
                            contentDescription = null
                        )
                    }

                    if (!vm.cyclePresetsCollapsed) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            (1..5).forEach { slot ->
                                ElevatedCard {
                                    Column(
                                        Modifier.padding(10.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        val preview = vm.getCyclePresetPreview(slot).ifBlank { "(empty)" }
                                        Text("Preset $slot — $preview")

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

    // ✅ Fix cursor-jump for numeric refresh input (Music)
    var refreshInput by rememberSaveable(stateSaver = TextFieldValue.Saver) {
        mutableStateOf(TextFieldValue(vm.musicRefreshSeconds.toString()))
    }
    LaunchedEffect(vm.musicRefreshSeconds) {
        val target = vm.musicRefreshSeconds.toString()
        if (refreshInput.text != target) refreshInput = TextFieldValue(target)
    }

    // Animated preview (UI-only)
    var previewT by remember { mutableStateOf(0f) }
    LaunchedEffect(vm.spotifyPreset) {
        previewT = 0f
        while (true) {
            previewT += 0.02f
            if (previewT > 1f) previewT = 0f
            delay(120)
        }
    }

    PageContainer {
        SectionCard(
            title = "Now Playing (phone music)",
            subtitle = "Uses Notification Access. Stop clears instantly."
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Enable Now Playing block")
                Switch(checked = vm.spotifyEnabled, onCheckedChange = { vm.setSpotifyEnabledFlag(it) })
            }

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Demo mode (testing)")
                Switch(checked = vm.spotifyDemoEnabled, onCheckedChange = { vm.setSpotifyDemoFlag(it) })
            }

            OutlinedTextField(
                value = refreshInput,
                onValueChange = { v ->
                    refreshInput = v
                    v.text.toIntOrNull()?.let { vm.musicRefreshSeconds = it.coerceAtLeast(2) }
                },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text("Music refresh speed (seconds) — min 2") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
            )

            Text("Progress bar preset:", style = MaterialTheme.typography.labelLarge)

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
                                Text(name, style = MaterialTheme.typography.titleSmall)
                                Text(preview, fontFamily = FontFamily.Monospace)
                            }
                            if (selected) Text("Selected", style = MaterialTheme.typography.labelMedium)
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

            OutlinedButton(
                onClick = { ctx.startActivity(vm.notificationAccessIntent()) },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Open Notification Access settings") }
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

        SectionCard(title = "VRChat send status") {
            Text("Last sent to VRChat (ms): ${vm.lastSentToVrchatAtMs}")
        }
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
        """.trimIndent()
    }

    val tutorial = remember {
        """
TUTORIAL

1) Turn OSC ON in VRChat:
   VRChat → Settings → OSC → Enable OSC

2) Same Wi-Fi:
   Phone + headset must be on the same Wi-Fi.

3) Headset IP:
   Settings → Wi-Fi → your network → Advanced → IP Address

4) Put IP into VRC-A:
   Dashboard → Apply

5) Test:
   Manual Send → Send
        """.trimIndent()
    }

    val features = remember {
        """
FEATURES

- Manual send
- Cycle (10 lines, presets)
- AFK (top line, presets)
- Now Playing (notification listener, progress presets)
- Debug preview (combined output)
        """.trimIndent()
    }

    val bugs = remember {
        """
NOTES

- Some players don't provide continuous playback position updates.
- Some routers block client-to-client traffic.
        """.trimIndent()
    }

    val help = remember {
        """
HELP

Nothing in VRChat:
- OSC enabled
- correct IP
- same Wi-Fi
        """.trimIndent()
    }

    val fullDoc = remember {
        """
VRC-A (VRChat Assistant)

This app sends OSC text to VRChat Chatbox.
Use Dashboard to set IP, Cycle for rotating lines, Now Playing for music,
and Debug to see the combined output.
        """.trimIndent()
    }

    PageContainer {
        SectionCard(
            title = "Information",
            subtitle = "Tutorial, features, and troubleshooting."
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                InfoTab.entries.forEach { t ->
                    val selected = (t == tab)
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

            val text: String = when (tab) {
                InfoTab.Overview -> overview
                InfoTab.Tutorial -> tutorial
                InfoTab.Features -> features
                InfoTab.Bugs -> bugs
                InfoTab.Troubleshoot -> help
                InfoTab.FullDoc -> fullDoc
            }

            SelectionContainer {
                Text(text, fontFamily = FontFamily.Monospace)
            }
        }
    }
}
