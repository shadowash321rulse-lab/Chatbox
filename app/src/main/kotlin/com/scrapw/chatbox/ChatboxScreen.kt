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
            .padding(12.dp),
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
    ElevatedCard(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(text = title, style = MaterialTheme.typography.titleMedium)
            if (!subtitle.isNullOrBlank()) {
                Text(text = subtitle, style = MaterialTheme.typography.bodyMedium)
            }
            content()
        }
    }
}

@Composable
private fun DashboardPage(vm: ChatboxViewModel) {
    val scope = rememberCoroutineScope()

    PageContainer {
        SectionCard(
            title = "Quick Send",
            subtitle = "Send a message instantly to your Chatbox receiver."
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(
                    onClick = { vm.sendMessage("Hello!") },
                    modifier = Modifier.weight(1f)
                ) { Text("Hello!") }
                OutlinedButton(
                    onClick = { vm.sendMessage("Testing...") },
                    modifier = Modifier.weight(1f)
                ) { Text("Testing") }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = {
                        scope.launch { vm.sendMessage(vm.customMessage) }
                    },
                    modifier = Modifier.weight(1f)
                ) { Icon(Icons.Default.Send, null); Spacer(Modifier.width(8.dp)); Text("Send") }

                OutlinedButton(
                    onClick = { vm.customMessage = "" },
                    modifier = Modifier.weight(1f)
                ) { Icon(Icons.Default.Delete, null); Spacer(Modifier.width(8.dp)); Text("Clear") }
            }

            OutlinedTextField(
                value = vm.customMessage,
                onValueChange = { vm.customMessage = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Message") },
                singleLine = true
            )
        }

        SectionCard(
            title = "Connection",
            subtitle = "Configure your receiver connection."
        ) {
            OutlinedTextField(
                value = vm.ip,
                onValueChange = { vm.ip = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("IP Address") },
                singleLine = true
            )
            OutlinedTextField(
                value = vm.port.toString(),
                onValueChange = { raw ->
                    raw.toIntOrNull()?.let { vm.port = it }
                },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Port") },
                singleLine = true,
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Number)
            )

            Button(
                onClick = { vm.saveConnection() },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Settings, null)
                Spacer(Modifier.width(8.dp))
                Text("Save Connection")
            }
        }
    }
}

@Composable
private fun CyclePage(vm: ChatboxViewModel) {
    PageContainer {
        SectionCard(
            title = "Cycle Sender",
            subtitle = "Cycles through messages at an interval."
        ) {
            Text("This page is unchanged by the permissions work.")
        }
    }
}

@Composable
private fun NowPlayingPage(vm: ChatboxViewModel) {
    val ctx = LocalContext.current

    // Animated preview (UI-only)
    var previewT by remember { mutableStateOf(0f) }
    LaunchedEffect(vm.spotifyPreset) {
        previewT = 0f
        while (true) {
            previewT += 0.02f
            if (previewT > 1f) previewT = 0f
            kotlinx.coroutines.delay(120)
        }
    }

    // Quick permission status + deep links
    val pkg = ctx.packageName
    val overlayAllowed = android.provider.Settings.canDrawOverlays(ctx)
    val powerManager =
        ctx.getSystemService(android.content.Context.POWER_SERVICE) as android.os.PowerManager
    val ignoringBatteryOpt =
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            powerManager.isIgnoringBatteryOptimizations(pkg)
        } else {
            true
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

            // NEW: buttons like the Notification Access one, to help keep the app working in background
            Divider(Modifier.padding(vertical = 10.dp))

            Text(
                text = "Overlay permission: " + (if (overlayAllowed) "Allowed" else "Not allowed"),
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                text = "Battery optimisation: " +
                    (if (ignoringBatteryOpt) "Not optimised" else "Optimised (may be killed)"),
                style = MaterialTheme.typography.bodyMedium
            )

            OutlinedButton(
                onClick = {
                    val i = android.content.Intent(
                        android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        android.net.Uri.parse("package:$pkg")
                    )
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
                    ctx.startActivity(i)
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Open Battery optimisation settings") }

            OutlinedButton(
                onClick = {
                    val i = android.content.Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                        .setData(android.net.Uri.parse("package:$pkg"))
                    ctx.startActivity(i)
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Open App info (background restrictions)") }

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

            // Hard locked preset names (Love/Minimal/Crystal/Soundwave/Geometry)
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
            title = "Logs (last 60)",
            subtitle = "Quick view; full logs in logcat."
        ) {
            val logs = vm.debugLogs
            if (logs.isEmpty()) {
                Text("No logs yet.")
            } else {
                SelectionContainer {
                    Text(logs.joinToString("\n"))
                }
            }
        }
    }
}

@Composable
private fun InfoPage(vm: ChatboxViewModel) {
    var tab by rememberSaveable { mutableStateOf(InfoTab.Overview) }

    PageContainer {
        SectionCard(title = "Info Tabs") {
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                InfoTab.entries.forEach { t ->
                    FilterChip(
                        selected = tab == t,
                        onClick = { tab = t },
                        label = { Text(t.title) }
                    )
                }
            }
        }

        when (tab) {
            InfoTab.Overview -> OverviewTab(vm)
            InfoTab.Tutorial -> TutorialTab(vm)
            InfoTab.Features -> FeaturesTab(vm)
            InfoTab.Bugs -> BugsTab(vm)
            InfoTab.Troubleshoot -> TroubleshootTab(vm)
            InfoTab.FullDoc -> FullDocTab(vm)
        }
    }
}

@Composable
private fun OverviewTab(vm: ChatboxViewModel) {
    SectionCard(title = "Overview") {
        Text("See README / docs in-app.")
    }
}

@Composable
private fun TutorialTab(vm: ChatboxViewModel) {
    SectionCard(title = "Tutorial") {
        Text("Tutorial content here.")
    }
}

@Composable
private fun FeaturesTab(vm: ChatboxViewModel) {
    SectionCard(title = "Features") {
        Text("Features list here.")
    }
}

@Composable
private fun BugsTab(vm: ChatboxViewModel) {
    SectionCard(title = "Bugs") {
        Text("Known bugs here.")
    }
}

@Composable
private fun TroubleshootTab(vm: ChatboxViewModel) {
    SectionCard(title = "Help") {
        Text("Troubleshooting here.")
    }
}

@Composable
private fun FullDocTab(vm: ChatboxViewModel) {
    SectionCard(title = "Full Doc") {
        SelectionContainer { Text("Full documentation here.") }
    }
}
