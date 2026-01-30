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

/**
 * Compatible bottom bar that does NOT depend on NavigationBarItem.
 */
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
private fun DashboardPage(vm: com.scrapw.chatbox.ui.ChatboxViewModel) {
    val uiState by vm.messengerUiState.collectAsState()

    // Local input so typing doesn't fight the flow/state
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
    PageContainer {
        SectionCard(
            title = "AFK (top line)",
            subtitle = "AFK shows above Cycle + Now Playing. You can run AFK by itself."
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
                onValueChange = { vm.afkMessage = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text("AFK text") }
            )

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

            OutlinedButton(
                onClick = { vm.sendAfkNow() },
                modifier = Modifier.fillMaxWidth(),
                enabled = vm.afkEnabled
            ) { Text("Send AFK once") }
        }

        SectionCard(
            title = "Cycle Messages",
            subtitle = "Rotates your lines. Now Playing stays underneath automatically."
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Cycle enabled")
                Switch(
                    checked = vm.cycleEnabled,
                    onCheckedChange = {
                        vm.cycleEnabled = it
                        if (!it) vm.stopCycle()
                    }
                )
            }

            OutlinedTextField(
                value = vm.cycleMessages,
                onValueChange = { vm.cycleMessages = it },
                modifier = Modifier.fillMaxWidth(),
                minLines = 4,
                label = { Text("Lines (one per line)") }
            )

            OutlinedTextField(
                value = vm.cycleIntervalSeconds.toString(),
                onValueChange = { raw ->
                    raw.toIntOrNull()?.let { vm.cycleIntervalSeconds = it.coerceAtLeast(1) }
                },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text("Cycle speed (seconds)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
            )

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(onClick = { vm.startCycle() }, modifier = Modifier.weight(1f)) { Text("Start") }
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
            title = "Now Playing (phone music)",
            subtitle = "Uses Notification Access. Works with Spotify/YouTube Music/etc."
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
                    raw.toIntOrNull()?.let { vm.musicRefreshSeconds = it.coerceAtLeast(1) }
                },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text("Music refresh speed (seconds)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
            )

            Text("Progress bar presets:", style = MaterialTheme.typography.labelLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                (1..5).forEach { p ->
                    val selected = vm.spotifyPreset == p
                    val colors =
                        if (selected) ButtonDefaults.buttonColors()
                        else ButtonDefaults.outlinedButtonColors()

                    Button(
                        onClick = { vm.updateSpotifyPreset(p) },
                        colors = colors,
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                    ) { Text("$p") }
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
            title = "VRChat send status",
            subtitle = null
        ) {
            Text("Last sent to VRChat (ms): ${vm.lastSentToVrchatAtMs}")
        }
    }
}

@Composable
private fun SettingsPage() {
    var tab by rememberSaveable { mutableStateOf(InfoTab.Overview) }

    val fullDoc = remember {
        // This is exactly your document, stored in-app.
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

============================================================
WHAT VRC-A IS FOR
============================================================
Use VRC-A if you want your VRChat Chatbox to show:
- A rotating status message (Cycle)
- What you’re listening to on your phone (Now Playing)
- An AFK label on top

It works by sending OSC messages over your local Wi-Fi network to your headset
(or VRChat device running OSC input).

============================================================
FEATURES (EVERYTHING THE APP CAN DO)
============================================================

A) Connection / Sending
- Send a manual message instantly to VRChat chatbox.
- Set the OSC target IP address (your headset/device IP).
- Uses your saved message options (send immediately, typing indicator, etc) if enabled in the app.

B) Cycle (Rotating Messages)
- Toggle Cycle on/off.
- Type multiple lines (one per line).
- Set Cycle speed (seconds between switching lines).
- Start/Stop Cycle sending.
- When enabled, Cycle text appears above Now Playing automatically.

C) Now Playing (Phone Music)
- Pulls “Now Playing” from your phone using Notification Access / MediaSession.
- Works with apps that show a media notification (Spotify, YouTube Music, etc).
- Has a Demo mode for testing without real music.
- Uses its OWN refresh speed (seconds between progress updates).
- Start/Stop Now Playing sending.
- “Send once” test button.

D) Progress Bar Presets (5)
Each preset has a moving dot that updates with the song progress:
1) Love preset:     ♡━━━◉━━━━♡
2) Minimal preset:  ━━◉──────────
3) Crystal preset:  ⟡⟡⟡◉⟡⟡⟡⟡⟡
4) Soundwave:       ▁▂▃▄▅●▅▄▃▂▁
5) Geometry:        ▣▣▣◉▢▢▢▢▢▢▢

(Exact visuals may vary slightly depending on how the app trims to VRChat’s char limit.)

E) AFK (Top Line)
- Toggle AFK on/off.
- Choose AFK text (example: “AFK”, “AFK - grabbing water”, etc).
- AFK is intended to be the very top line above Cycle + Now Playing.
- AFK has its own sending system (not tied to Cycle or Now Playing).

F) Debug / Status
- Shows whether Notification Listener is connected.
- Shows whether Now Playing is detected.
- Shows last detected Artist/Title.
- Shows active package (which music app it’s seeing).
- Shows last time it sent to VRChat.
- (Optional/Planned) show exactly what AFK / Cycle / Now Playing are currently generating.

============================================================
TUTORIAL (DUMBED DOWN STEP BY STEP)
============================================================

STEP 1: Put your headset and phone on the same Wi-Fi
- Both devices MUST be on the same local network.
- If you’re on different Wi-Fi, messages will not reach VRChat.

STEP 2: Find your headset/device IP address
You need the IP address of the device that VRChat is running on (your headset).

Quest / Android headset (typical):
1) Open Settings
2) Go to Wi-Fi
3) Tap your connected network
4) Find “IP Address” (example: 192.168.1.23)

If you can’t find it:
- Some devices hide it under “Advanced” in the Wi-Fi network details.

STEP 3: Put the IP into VRC-A
1) Open VRC-A
2) Go to Dashboard
3) Type your headset IP in “Headset IP address”
4) Tap Apply

STEP 4: Test sending
1) In “Manual Send”, type “hello”
2) Press Send
If VRChat chatbox shows it → your connection is working.

If nothing shows:
- Recheck the IP address
- Make sure VRChat OSC is enabled
- Make sure both devices are on the same Wi-Fi

STEP 5: Enable Now Playing (music detection)
Now Playing reads the phone’s media notifications.

1) Go to Now Playing page
2) Tap “Open Notification Access settings”
3) Enable Notification Access for VRC-A (Chatbox)
4) Close settings
5) Restart VRC-A (recommended after enabling)
6) Play music in Spotify / YouTube Music / etc
7) Go back to Now Playing page and check Detected / Artist / Title

If it still shows blank:
- Toggle Notification Access OFF then ON again
- Restart the phone (sometimes fixes listener permissions)
- Make sure your music app actually shows a media notification
- Try another music app to confirm it’s not the player

STEP 6: Start Now Playing sending
1) Toggle “Enable Now Playing block”
2) Press Start
3) Your Now Playing block should begin sending to VRChat
4) If you need to test quickly, use “Send once now (test)”

STEP 7: Set your progress bar preset
1) On Now Playing page, pick preset 1–5
2) The dot should move as the song progresses

STEP 8: Use Cycle messages
1) Go to Cycle page
2) Enable Cycle
3) Put one message per line
4) Set the speed (seconds)
5) Press Start

STEP 9: Use AFK
1) Go to Cycle page
2) Enable AFK
3) Type AFK text
4) Press “Start AFK” (or “Send AFK once” for quick test)

============================================================
BUGS THAT WERE FIXED DURING DEVELOPMENT (HISTORY)
============================================================
(These are issues that happened while building VRC-A and were resolved.)

- Build system plugin issues (Gradle plugin/version setup errors).
- Spotify developer/API auth approach removed/replaced (Spotify apps not available → switched to phone Now Playing detection).
- Duplicate composables / duplicate enums caused compile conflicts (redeclaration errors).
- Now Playing state stuck on false/blank/blank (fixed by collecting NowPlayingState into ViewModel fields).
- Now Playing service setup in manifest for Notification Listener.
- Preset progress bars overflowing the chatbox (shortened bars to match the smaller preset size).
- UI layout problems (buttons missing, Spotify block placement issues) replaced with a more organised layout.

============================================================
CURRENT / KNOWN BUGS (THINGS THAT MAY STILL HAPPEN)
============================================================

Now Playing detection:
- Some phones/music apps do not update progress constantly.
  If the player only updates on interaction (pause/seek/next), the app may not get continuous position updates.
  (This depends on how the music app reports PlaybackState.)

Other notifications being detected:
- If Notification Access isn’t filtered strictly to media sessions, the listener may catch unrelated notifications.
  If that happens, the app needs stricter filtering (media-style + active MediaSession only).

Sync / delays:
- If Cycle + Now Playing + AFK share a single sending loop, one can “cancel” or override the timing of another.
  The goal is that each system sends on its own timer and the final message is merged together.

Paused status display:
- “Paused” should show when your music is paused, but some players report states inconsistently.

General networking:
- Wrong IP / different Wi-Fi = nothing sends.
- Some routers isolate wireless clients (client isolation) which blocks device-to-device traffic.

============================================================
HELP / QUICK TROUBLESHOOTING
============================================================

Nothing appears in VRChat:
- Check IP address
- Confirm VRChat OSC enabled
- Same Wi-Fi on both devices
- Try “Manual Send” first

Now Playing stays blank:
- Enable Notification Access
- Restart app
- Toggle access off/on
- Play music with a visible media notification

Progress dot not moving:
- Some players do not provide continuous updates
- Try a different music app to compare

============================================================
END
============================================================
        """.trimIndent()
    }

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
A) Connection / Sending
- Send a manual message instantly to VRChat chatbox.
- Set the OSC target IP address (your headset/device IP).

B) Cycle (Rotating Messages)
- Toggle Cycle on/off, one line per message.
- Separate speed control + Start/Stop.
- Cycle text appears above Now Playing automatically.

C) Now Playing (Phone Music)
- Reads Now Playing via Notification Access / MediaSession.
- Works with apps that show a media notification.
- Demo mode + separate refresh speed.
- Start/Stop + Send once test.

D) Progress Bar Presets (5)
1) ♡━━━◉━━━━♡
2) ━━◉──────────
3) ⟡⟡⟡◉⟡⟡⟡⟡⟡
4) ▁▂▃▄▅●▅▄▃▂▁
5) ▣▣▣◉▢▢▢▢▢▢▢

E) AFK (Top Line)
- Toggle AFK on/off
- Choose AFK text
- Intended to sit above Cycle + Now Playing

F) Debug / Status
- Listener connected, detected state, artist/title, active package, last sent time
        """.trimIndent()
    }

    val tutorial = remember {
        """
STEP 1: Same Wi-Fi
- Phone and headset must be on the same network.

STEP 2: Find your headset IP
Quest / Android headset:
Settings → Wi-Fi → your network → IP Address (maybe under Advanced)

STEP 3: Put IP into VRC-A
Dashboard → Headset IP address → Apply

STEP 4: Test sending
Manual Send → type “hello” → Send

STEP 5: Enable Now Playing
Now Playing page → Open Notification Access settings → enable VRC-A
Restart VRC-A, then play music in a player that shows a media notification.

STEP 6: Start Now Playing sending
Enable Now Playing block → Start
(Use Send once to test quickly)

STEP 7: Pick progress preset
Choose preset 1–5. Dot should move as song progresses.

STEP 8: Use Cycle
Cycle page → Enable Cycle → lines → speed → Start

STEP 9: Use AFK
Cycle page → Enable AFK → set AFK text → Start AFK (or Send once)
        """.trimIndent()
    }

    val bugs = remember {
        """
FIXED (History)
- Gradle plugin/version build errors fixed during setup.
- Spotify API auth approach removed/replaced (Spotify apps unavailable → phone Now Playing).
- Duplicate composables/enums caused redeclaration compile errors (fixed).
- Now Playing stuck on false/blank/blank (fixed by collecting NowPlayingState into ViewModel fields).
- Notification Listener service declared in manifest (required for detection).
- Progress bars overflowing VRChat (bars shortened).
- UI layout issues replaced with organised layout.

CURRENT / KNOWN BUGS
- Some music apps only update position when you interact (pause/seek/skip).
- Notification listener may catch non-music notifications if filtering isn’t strict enough.
- If multiple senders share one loop, timings can override each other.
- Some players report paused/playing inconsistently.
- Network issues: wrong IP / different Wi-Fi / router client isolation blocks sending.
        """.trimIndent()
    }

    val help = remember {
        """
Nothing appears in VRChat:
- Check IP address
- Confirm VRChat OSC enabled
- Same Wi-Fi on both devices
- Try Manual Send first

Now Playing stays blank:
- Enable Notification Access
- Restart app
- Toggle access off/on
- Ensure the music app shows a media notification

Progress dot not moving:
- Some players don’t provide continuous progress updates
- Try another music app to compare
        """.trimIndent()
    }

    PageContainer {
        SectionCard(
            title = "Information",
            subtitle = "Everything about VRC-A (what it is, tutorial, features, and bugs)."
        ) {
            // Simple segmented tab row
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
                    ) {
                        Text(t.title)
                    }
                }
            }

            // Selectable text so users can copy the guide
            SelectionContainer {
                val text = when (tab) {
                    InfoTab.Overview -> overview
                    InfoTab.Features -> features
                    InfoTab.Tutorial -> tutorial
                    InfoTab.Bugs -> bugs
                    InfoTab.Troubleshoot -> help
                    InfoTab.FullDoc -> fullDoc
                }

                Text(
                    text = text,
                    style = MaterialTheme.typography.bodyMedium,
                    fontFamily = FontFamily.Monospace
                )
            }
        }

        SectionCard(
            title = "About (short)",
            subtitle = null
        ) {
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
