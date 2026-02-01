package com.scrapw.chatbox

import androidx.compose.foundation.Canvas
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
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.scrapw.chatbox.ui.ChatboxViewModel

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
                title = { Text("VRC-A") }
                // ✅ no random send button in header anymore
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DashboardPage(vm: ChatboxViewModel) {
    val uiState by vm.messengerUiState.collectAsState()
    val scope = rememberCoroutineScope()
    val ctx = LocalContext.current

    // ✅ IP uses TextFieldValue so cursor never jumps; allow dots
    var ipInput by rememberSaveable(stateSaver = TextFieldValue.Saver) {
        mutableStateOf(TextFieldValue(uiState.ipAddress))
    }
    LaunchedEffect(uiState.ipAddress) {
        if (ipInput.text.isBlank()) ipInput = TextFieldValue(uiState.ipAddress)
    }

    // ✅ System sheet (small, noticeable, not a nav button)
    var showSystemSheet by remember { mutableStateOf(false) }
    if (showSystemSheet) {
        ModalBottomSheet(
            onDismissRequest = { showSystemSheet = false }
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text("System & Permissions", style = MaterialTheme.typography.titleMedium)

                ElevatedCard {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            "These keep VRC-A working reliably (Now Playing, overlay staying alive, fewer kills by Android).",
                            style = MaterialTheme.typography.bodySmall
                        )

                        Button(
                            onClick = { ctx.startActivity(vm.notificationAccessIntent()) },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("Open Notification Access") }

                        OutlinedButton(
                            onClick = { ctx.startActivity(vm.overlayPermissionIntent()) },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("Open Overlay Permission") }

                        OutlinedButton(
                            onClick = { ctx.startActivity(vm.batteryOptimizationIntent()) },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("Disable Battery Optimization (request)") }
                    }
                }

                Spacer(Modifier.height(6.dp))
            }
        }
    }

    PageContainer {
        // ✅ Live preview / avatar + chat bubble + panic switch
        SectionCard(
            title = "VRChat Preview",
            subtitle = "Live preview of what will be shown in VRChat (combined output)."
        ) {
            val previewText = vm.debugLastCombinedOsc.ifBlank { "(nothing active)" }

            ElevatedCard {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AssistChip(
                            onClick = { showSystemSheet = true },
                            label = { Text("System") },
                            leadingIcon = { Icon(Icons.Filled.Settings, contentDescription = null) }
                        )

                        // ✅ PANIC button: disables senders + clears VRChat chatbox
                        Button(
                            onClick = { vm.panicStopAndClear() },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                        ) {
                            Text("PANIC", color = MaterialTheme.colorScheme.onError)
                        }
                    }

                    // Avatar + bubble
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(240.dp)
                    ) {
                        // Simple avatar silhouette (placeholder)
                        Canvas(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .fillMaxHeight()
                                .width(160.dp)
                        ) {
                            val w = size.width
                            val h = size.height

                            // Head
                            drawCircle(
                                color = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.10f),
                                radius = w * 0.18f,
                                center = Offset(w * 0.5f, h * 0.22f)
                            )
                            // Body
                            val path = Path().apply {
                                moveTo(w * 0.50f, h * 0.40f)
                                cubicTo(w * 0.18f, h * 0.44f, w * 0.18f, h * 0.84f, w * 0.50f, h * 0.86f)
                                cubicTo(w * 0.82f, h * 0.84f, w * 0.82f, h * 0.44f, w * 0.50f, h * 0.40f)
                                close()
                            }
                            drawPath(
                                path = path,
                                color = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.08f)
                            )
                        }

                        // Chat bubble above head
                        Surface(
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .padding(top = 6.dp)
                                .fillMaxWidth(),
                            tonalElevation = 3.dp,
                            shape = MaterialTheme.shapes.large
                        ) {
                            SelectionContainer {
                                Text(
                                    text = previewText,
                                    modifier = Modifier.padding(12.dp),
                                    fontFamily = FontFamily.Monospace,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        }
                    }

                    // Quick toggles (only enable/disable here — details live in tabs)
                    ElevatedCard {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text("Quick Toggles", style = MaterialTheme.typography.titleSmall)

                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("AFK")
                                Switch(
                                    checked = vm.afkEnabled,
                                    onCheckedChange = { vm.setAfkEnabledFlag(it) }
                                )
                            }
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Cycle")
                                Switch(
                                    checked = vm.cycleEnabled,
                                    onCheckedChange = { vm.setCycleEnabledFlag(it) }
                                )
                            }
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Now Playing")
                                Switch(
                                    checked = vm.spotifyEnabled,
                                    onCheckedChange = { vm.setSpotifyEnabledFlag(it) }
                                )
                            }
                        }
                    }
                }
            }
        }

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

    var cycleIntervalInput by rememberSaveable(stateSaver = TextFieldValue.Saver) {
        mutableStateOf(TextFieldValue(vm.cycleIntervalSeconds.toString()))
    }
    LaunchedEffect(vm.cycleIntervalSeconds) {
        val target = vm.cycleIntervalSeconds.toString()
        if (cycleIntervalInput.text != target) cycleIntervalInput = TextFieldValue(target)
    }

    val cycleLineFields = remember { mutableStateMapOf<Int, TextFieldValue>() }
    fun syncCycleLineFieldsFromVm() {
        val valid = vm.cycleLines.indices.toSet()
        cycleLineFields.keys.toList().forEach { if (it !in valid) cycleLineFields.remove(it) }
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
            "${slot}:${p}"
        }
        return parts.joinToString("  •  ").let { if (it.length > 80) it.take(79) + "…" else it }
    }

    fun cyclePresetsPreview(): String {
        val parts = (1..5).map { slot ->
            val p = vm.getCyclePresetPreview(slot).ifBlank { "empty" }
            "${slot}:${p}"
        }
        return parts.joinToString("  •  ").let { if (it.length > 80) it.take(79) + "…" else it }
    }

    PageContainer {
        SectionCard(
            title = "AFK (top line)",
            subtitle = "AFK is always the top line. Forced interval. Stop clears it instantly."
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(text = "AFK enabled")
                Switch(
                    checked = vm.afkEnabled,
                    onCheckedChange = { vm.setAfkEnabledFlag(it) }
                )
            }

            OutlinedTextField(
                value = vm.afkMessage,
                onValueChange = { vm.updateAfkText(it) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text("AFK text") }
            )

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
                            .clickable { vm.updateAfkPresetsCollapsed(!vm.afkPresetsCollapsed) },
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(text = "AFK Presets (3)", style = MaterialTheme.typography.titleSmall)
                            if (vm.afkPresetsCollapsed) {
                                Text(
                                    text = afkPresetsPreview(),
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
                Text(text = "Cycle enabled")
                Switch(
                    checked = vm.cycleEnabled,
                    onCheckedChange = { vm.setCycleEnabledFlag(it) }
                )
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
                            .clickable { vm.updateCyclePresetsCollapsed(!vm.cyclePresetsCollapsed) },
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(text = "Cycle Presets (5)", style = MaterialTheme.typography.titleSmall)
                            if (vm.cyclePresetsCollapsed) {
                                Text(
                                    text = cyclePresetsPreview(),
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

    var refreshInput by rememberSaveable(stateSaver = TextFieldValue.Saver) {
        mutableStateOf(TextFieldValue(vm.musicRefreshSeconds.toString()))
    }
    LaunchedEffect(vm.musicRefreshSeconds) {
        val target = vm.musicRefreshSeconds.toString()
        if (refreshInput.text != target) refreshInput = TextFieldValue(target)
    }

    // Animated preview uses time, but Soundwave itself is tied to song position when actually playing.
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

            // ✅ Send once belongs here, not in the header
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

        SectionCard(title = "VRChat send status") {
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

VRC-A sends text to VRChat’s Chatbox using OSC over Wi-Fi.
Dashboard shows a live preview so you know exactly what will appear in VR.
        """.trimIndent()
    }

    val tutorial = remember {
        """
TUTORIAL (Step by Step)

1) Turn OSC ON in VRChat:
   VRChat → Settings → OSC → Enable OSC

2) Same Wi-Fi:
   Phone and headset must be on the same network.

3) Find headset IP:
   Settings → Wi-Fi → (your network) → Advanced → IP Address

4) Put IP into VRC-A:
   Dashboard → Connection → Apply

5) Test manual send:
   Dashboard → Manual Send → “hello” → Send

6) Now Playing:
   Dashboard → System → Notification Access → enable VRC-A
   Restart VRC-A, then play music.
        """.trimIndent()
    }

    val features = remember {
        """
FEATURES

- Live VR preview (combined output)
- Panic button (stops + clears VRChat chatbox)
- AFK (forced interval), 3 presets
- Cycle (10 lines), 5 presets
- Now Playing (Notification Access)
- Persistent progress bar preset
- Collapsible preset sections to reduce clutter
- System sheet: Notification / Overlay / Battery optimization
        """.trimIndent()
    }

    val bugs = remember {
        """
KNOWN NOTES

- Some music players don’t provide continuous position updates.
- Some routers block device-to-device traffic (client isolation).
        """.trimIndent()
    }

    val help = remember {
        """
QUICK TROUBLESHOOTING

Nothing in VRChat:
- Check headset IP
- OSC enabled
- Same Wi-Fi

Now Playing blank:
- Enable Notification Access
- Restart app
- Start playing music (media notification must exist)
        """.trimIndent()
    }

    val fullDoc = remember {
        """
VRC-A (VRChat Assistant)
Made by: Ashoska Mitsu Sisko

Dashboard shows you exactly what will be sent to VRChat.
Use PANIC if something is stuck sending—this stops and clears the chatbox.
        """.trimIndent()
    }

    PageContainer {
        SectionCard(
            title = "Information",
            subtitle = "What it is, tutorial, features, and troubleshooting."
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
                    ) { Text(text = t.title) }
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
