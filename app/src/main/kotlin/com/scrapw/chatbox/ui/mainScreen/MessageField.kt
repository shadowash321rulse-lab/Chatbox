package com.scrapw.chatbox.ui.mainScreen

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.PauseCircleFilled
import androidx.compose.material.icons.filled.PlayCircleFilled
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
    var tab by rememberSaveable { mutableStateOf(TabPage.Cycle) }

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
                        Column(
                            Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {

                            // ============================
                            // AFK (above everything)
                            // ============================
                            Text("AFK (Top Line)", style = MaterialTheme.typography.titleMedium)

                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Text("Enable AFK")
                                Spacer(Modifier.weight(1f))
                                Switch(
                                    checked = chatboxViewModel.afkEnabled,
                                    onCheckedChange = {
                                        chatboxViewModel.afkEnabled = it
                                        if (it) chatboxViewModel.startAfkSender()
                                        else chatboxViewModel.stopAfkSender()
                                    }
                                )
                            }

                            OutlinedTextField(
                                value = chatboxViewModel.afkMessage,
                                onValueChange = { chatboxViewModel.afkMessage = it },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                enabled = chatboxViewModel.afkEnabled,
                                label = { Text("AFK message") }
                            )

                            OutlinedTextField(
                                value = chatboxViewModel.afkIntervalSeconds.toString(),
                                onValueChange = { raw ->
                                    raw.toIntOrNull()?.let { n -> chatboxViewModel.afkIntervalSeconds = n.coerceAtLeast(3) }
                                },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                enabled = chatboxViewModel.afkEnabled,
                                label = { Text("AFK refresh (seconds)") }
                            )

                            Divider()

                            // ============================
                            // Cycle
                            // ============================
                            Text("Cycle", style = MaterialTheme.typography.titleMedium)

                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Text("Enable Cycle")
                                Spacer(Modifier.weight(1f))
                                Switch(
                                    checked = chatboxViewModel.cycleEnabled,
                                    onCheckedChange = {
                                        chatboxViewModel.cycleEnabled = it
                                        if (!it) chatboxViewModel.stopCycle()
                                    }
                                )
                            }

                            OutlinedTextField(
                                value = chatboxViewModel.cycleMessages,
                                onValueChange = { chatboxViewModel.cycleMessages = it },
                                modifier = Modifier.fillMaxWidth(),
                                minLines = 2,
                                maxLines = 6,
                                enabled = chatboxViewModel.cycleEnabled,
                                label = { Text("Cycle lines (one per line)") }
                            )

                            OutlinedTextField(
                                value = chatboxViewModel.cycleIntervalSeconds.toString(),
                                onValueChange = { raw ->
                                    raw.toIntOrNull()?.let { n -> chatboxViewModel.cycleIntervalSeconds = n.coerceAtLeast(1) }
                                },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                enabled = chatboxViewModel.cycleEnabled,
                                label = { Text("Cycle speed (seconds)") }
                            )

                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Button(
                                    onClick = { chatboxViewModel.startCycle() },
                                    modifier = Modifier.weight(1f),
                                    enabled = chatboxViewModel.cycleEnabled
                                ) {
                                    Icon(Icons.Filled.PlayCircleFilled, contentDescription = null)
                                    Spacer(Modifier.width(8.dp))
                                    Text("Start Cycle")
                                }

                                OutlinedButton(
                                    onClick = { chatboxViewModel.stopCycle() },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.Filled.PauseCircleFilled, contentDescription = null)
                                    Spacer(Modifier.width(8.dp))
                                    Text("Stop Cycle")
                                }
                            }
                        }
                    }

                    TabPage.NowPlaying -> {
                        Column(
                            Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text("Now Playing", style = MaterialTheme.typography.titleMedium)

                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Text("Enable block")
                                Spacer(Modifier.weight(1f))
                                Switch(
                                    checked = chatboxViewModel.spotifyEnabled,
                                    onCheckedChange = {
                                        chatboxViewModel.setSpotifyEnabledFlag(it)
                                        if (!it) chatboxViewModel.stopNowPlayingSender()
                                    }
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

                            OutlinedTextField(
                                value = chatboxViewModel.musicRefreshSeconds.toString(),
                                onValueChange = { raw ->
                                    raw.toIntOrNull()?.let { n -> chatboxViewModel.musicRefreshSeconds = n.coerceAtLeast(1) }
                                },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                enabled = chatboxViewModel.spotifyEnabled,
                                label = { Text("Music refresh (seconds)") }
                            )

                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Preset", style = MaterialTheme.typography.labelLarge)
                                (1..5).forEach { p ->
                                    val selected = chatboxViewModel.spotifyPreset == p
                                    val colors =
                                        if (selected) ButtonDefaults.buttonColors()
                                        else ButtonDefaults.outlinedButtonColors()

                                    Button(
                                        onClick = { chatboxViewModel.updateSpotifyPreset(p) },
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                        colors = colors,
                                        enabled = chatboxViewModel.spotifyEnabled
                                    ) { Text("$p") }
                                }
                            }

                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Text("Demo mode")
                                Spacer(Modifier.weight(1f))
                                Switch(
                                    checked = chatboxViewModel.spotifyDemoEnabled,
                                    onCheckedChange = { chatboxViewModel.setSpotifyDemoFlag(it) },
                                    enabled = chatboxViewModel.spotifyEnabled
                                )
                            }

                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Button(
                                    onClick = { chatboxViewModel.startNowPlayingSender() },
                                    modifier = Modifier.weight(1f),
                                    enabled = chatboxViewModel.spotifyEnabled
                                ) {
                                    Icon(Icons.Filled.PlayCircleFilled, contentDescription = null)
                                    Spacer(Modifier.width(8.dp))
                                    Text("Start Sending")
                                }

                                OutlinedButton(
                                    onClick = { chatboxViewModel.stopNowPlayingSender() },
                                    modifier = Modifier.weight(1f),
                                    enabled = chatboxViewModel.spotifyEnabled
                                ) {
                                    Icon(Icons.Filled.PauseCircleFilled, contentDescription = null)
                                    Spacer(Modifier.width(8.dp))
                                    Text("Stop")
                                }
                            }

                            OutlinedButton(
                                onClick = { chatboxViewModel.sendNowPlayingOnce() },
                                modifier = Modifier.fillMaxWidth(),
                                enabled = chatboxViewModel.spotifyEnabled
                            ) {
                                Text("Send once (test)")
                            }
                        }
                    }

                    TabPage.Debug -> {
                        Column(
                            Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text("Debug", style = MaterialTheme.typography.titleMedium)
                            Text("Listener connected: ${chatboxViewModel.listenerConnected}")
                            Text("Active package: ${chatboxViewModel.activePackage}")
                            Text("Detected: ${chatboxViewModel.nowPlayingDetected}")
                            Text("Artist: ${chatboxViewModel.lastNowPlayingArtist}")
                            Text("Title: ${chatboxViewModel.lastNowPlayingTitle}")
                            Text("Last sent (ms): ${chatboxViewModel.lastSentToVrchatAtMs}")
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

