package com.scrapw.chatbox.ui.mainScreen

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.filled.PauseCircleFilled
import androidx.compose.material.icons.filled.PlayCircleFilled
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.scrapw.chatbox.ui.ChatboxViewModel

private enum class TabPage { Cycle, NowPlaying, Debug }

@Composable
fun MessageField(
    chatboxViewModel: ChatboxViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var tab by remember { mutableStateOf(TabPage.Cycle) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {

        ElevatedCard {
            Column(Modifier.fillMaxWidth()) {
                TabRow(selectedTabIndex = tab.ordinal) {
                    Tab(selected = tab == TabPage.Cycle, onClick = { tab = TabPage.Cycle }, text = { Text("Cycle") })
                    Tab(selected = tab == TabPage.NowPlaying, onClick = { tab = TabPage.NowPlaying }, text = { Text("Now Playing") })
                    Tab(selected = tab == TabPage.Debug, onClick = { tab = TabPage.Debug }, text = { Text("Debug") })
                }

                when (tab) {

                    TabPage.Cycle -> {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {

                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Text("Enable Cycle", style = MaterialTheme.typography.titleMedium)
                                Spacer(Modifier.weight(1f))
                                Switch(
                                    checked = chatboxViewModel.cycleEnabled,
                                    onCheckedChange = {
                                        chatboxViewModel.cycleEnabled = it
                                        if (!it) chatboxViewModel.stopCycle()
                                    }
                                )
                            }

                            // AFK toggle (requested)
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Text("AFK", style = MaterialTheme.typography.titleMedium)
                                Spacer(Modifier.weight(1f))
                                Switch(
                                    checked = chatboxViewModel.afkEnabled,
                                    onCheckedChange = { chatboxViewModel.afkEnabled = it }
                                )
                            }

                            if (chatboxViewModel.cycleEnabled) {

                                TextField(
                                    value = chatboxViewModel.cycleMessages,
                                    onValueChange = { chatboxViewModel.cycleMessages = it },
                                    modifier = Modifier.fillMaxWidth(),
                                    minLines = 2,
                                    maxLines = 6,
                                    placeholder = { Text("One message per line") }
                                )

                                TextField(
                                    value = chatboxViewModel.cycleIntervalSeconds.toString(),
                                    onValueChange = { raw ->
                                        raw.toIntOrNull()?.let { n ->
                                            chatboxViewModel.cycleIntervalSeconds = n.coerceAtLeast(1)
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true,
                                    placeholder = { Text("Cycle speed (seconds)") }
                                )

                                Row(
                                    Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Button(
                                        onClick = { chatboxViewModel.startCycle() },
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Icon(Icons.Filled.PlayCircleFilled, contentDescription = null)
                                        Spacer(Modifier.width(8.dp))
                                        Text("Start")
                                    }

                                    OutlinedButton(
                                        onClick = { chatboxViewModel.stopCycle() },
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Icon(Icons.Filled.PauseCircleFilled, contentDescription = null)
                                        Spacer(Modifier.width(8.dp))
                                        Text("Stop")
                                    }
                                }
                            } else {
                                Text(
                                    "Turn on Cycle to rotate messages.\nNow Playing (if enabled) will be placed under it automatically.",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    }

                    TabPage.NowPlaying -> {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text("Now Playing block (phone music)", style = MaterialTheme.typography.titleMedium)

                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Text("Enable block")
                                Spacer(Modifier.weight(1f))
                                Switch(
                                    checked = chatboxViewModel.spotifyEnabled,
                                    onCheckedChange = { chatboxViewModel.setSpotifyEnabledFlag(it) }
                                )
                            }

                            OutlinedButton(
                                onClick = { context.startActivity(chatboxViewModel.notificationAccessIntent()) },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Filled.Settings, contentDescription = null)
                                Spacer(Modifier.width(8.dp))
                                Text("Grant Notification Access")
                            }

                            TextField(
                                value = chatboxViewModel.musicRefreshSeconds.toString(),
                                onValueChange = { raw ->
                                    raw.toIntOrNull()?.let { n ->
                                        chatboxViewModel.musicRefreshSeconds = n.coerceAtLeast(1)
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                placeholder = { Text("Music refresh (seconds)") }
                            )

                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Preset", style = MaterialTheme.typography.labelLarge)
                                Spacer(Modifier.width(6.dp))
                                (1..5).forEach { p ->
                                    val selected = chatboxViewModel.spotifyPreset == p
                                    val colors =
                                        if (selected) ButtonDefaults.buttonColors()
                                        else ButtonDefaults.outlinedButtonColors()

                                    Button(
                                        onClick = { chatboxViewModel.updateSpotifyPreset(p) },
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                        colors = colors
                                    ) { Text("$p") }
                                }
                            }

                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Text("Demo mode")
                                Spacer(Modifier.weight(1f))
                                Switch(
                                    checked = chatboxViewModel.spotifyDemoEnabled,
                                    onCheckedChange = { chatboxViewModel.setSpotifyDemoFlag(it) }
                                )
                            }

                            Text(
                                "Status: " + if (chatboxViewModel.nowPlayingIsPlaying) "Playing" else "Paused/Stopped",
                                style = MaterialTheme.typography.bodySmall
                            )

                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Button(
                                    onClick = { chatboxViewModel.startNowPlayingSender() },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.Filled.PlayCircleFilled, contentDescription = null)
                                    Spacer(Modifier.width(8.dp))
                                    Text("Start Sending")
                                }

                                OutlinedButton(
                                    onClick = { chatboxViewModel.stopNowPlayingSender() },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.Filled.PauseCircleFilled, contentDescription = null)
                                    Spacer(Modifier.width(8.dp))
                                    Text("Stop Sending")
                                }
                            }

                            OutlinedButton(
                                onClick = { chatboxViewModel.sendNowPlayingOnce() },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Send once now (test)")
                            }
                        }
                    }

                    TabPage.Debug -> {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text("Debug", style = MaterialTheme.typography.titleMedium)

                            Text("Listener connected: ${chatboxViewModel.listenerConnected}")
                            Text("Detected: ${chatboxViewModel.nowPlayingDetected}")
                            Text("Active package: ${chatboxViewModel.activePackage}")
                            Text("Artist: ${chatboxViewModel.lastNowPlayingArtist}")
                            Text("Title: ${chatboxViewModel.lastNowPlayingTitle}")
                            Text("Playing: ${chatboxViewModel.nowPlayingIsPlaying}")

                            val lastSent = chatboxViewModel.lastSentToVrchatAtMs
                            Text("Last sent: ${if (lastSent == 0L) "never" else "$lastSent"}")

                            Text(
                                "If Detected=false:\n• Enable Notification Access for Chatbox\n• Restart Chatbox\n• Toggle access OFF then ON\n• Music app must show a media notification",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            }
        }

        // Manual input row (always visible)
        ElevatedCard {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextField(
                    value = chatboxViewModel.messageText.value,
                    onValueChange = { chatboxViewModel.onMessageTextChange(it) },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    placeholder = { Text("Write a message") }
                )

                Spacer(Modifier.width(10.dp))

                IconButton(onClick = { chatboxViewModel.stashMessage() }) {
                    Icon(Icons.Filled.BookmarkAdd, contentDescription = "Quick message")
                }

                Button(onClick = { chatboxViewModel.sendMessage() }) {
                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send")
                }
            }
        }
    }
}
