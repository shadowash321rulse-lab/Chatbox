package com.scrapw.chatbox.ui.mainScreen

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.PauseCircleFilled
import androidx.compose.material.icons.filled.PlayCircleFilled
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PaddingValues
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.scrapw.chatbox.ui.ChatboxViewModel

@Composable
fun MessageField(
    chatboxViewModel: ChatboxViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // ============================
        // AFK (top line)
        // ============================
        ElevatedCard {
            Column(
                Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text("AFK", style = MaterialTheme.typography.titleMedium)

                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("Enable AFK (shows above everything)")
                    Spacer(Modifier.weight(1f))
                    Switch(
                        checked = chatboxViewModel.afkEnabled,
                        onCheckedChange = { chatboxViewModel.afkEnabled = it }
                    )
                }

                OutlinedTextField(
                    value = chatboxViewModel.afkMessage,
                    onValueChange = { chatboxViewModel.afkMessage = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("AFK message") }
                )

                OutlinedButton(
                    onClick = { chatboxViewModel.sendAfkNow() },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Send AFK once now")
                }
            }
        }

        // ============================
        // Cycle controls
        // ============================
        ElevatedCard {
            Column(
                Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
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
                    minLines = 3,
                    label = { Text("Cycle messages (one per line)") }
                )

                OutlinedTextField(
                    value = chatboxViewModel.cycleIntervalSeconds.toString(),
                    onValueChange = { raw ->
                        raw.toIntOrNull()?.let { n ->
                            chatboxViewModel.cycleIntervalSeconds = n.coerceAtLeast(1)
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("Cycle speed (seconds)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )

                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
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
            }
        }

        // ============================
        // Now Playing (phone music)
        // ============================
        ElevatedCard {
            Column(
                Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text("Now Playing", style = MaterialTheme.typography.titleMedium)

                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("Enable Now Playing block")
                    Spacer(Modifier.weight(1f))
                    Switch(
                        checked = chatboxViewModel.spotifyEnabled,
                        onCheckedChange = { chatboxViewModel.setSpotifyEnabledFlag(it) }
                    )
                }

                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("Demo mode")
                    Spacer(Modifier.weight(1f))
                    Switch(
                        checked = chatboxViewModel.spotifyDemoEnabled,
                        onCheckedChange = { chatboxViewModel.setSpotifyDemoFlag(it) }
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
                        raw.toIntOrNull()?.let { n ->
                            chatboxViewModel.musicRefreshSeconds = n.coerceAtLeast(1)
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("Music refresh speed (seconds)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
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
                            if (selected) androidx.compose.material3.ButtonDefaults.buttonColors()
                            else androidx.compose.material3.ButtonDefaults.outlinedButtonColors()

                        Button(
                            onClick = { chatboxViewModel.updateSpotifyPreset(p) },
                            colors = colors,
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                        ) { Text("$p") }
                    }
                }

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
                        Text("Start")
                    }

                    OutlinedButton(
                        onClick = { chatboxViewModel.stopNowPlayingSender() },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Filled.PauseCircleFilled, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Stop")
                    }
                }

                OutlinedButton(
                    onClick = { chatboxViewModel.sendNowPlayingOnce() },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Send once now (test)")
                }

                Text(
                    "Detected: ${chatboxViewModel.nowPlayingDetected} • " +
                        "Status: ${if (chatboxViewModel.nowPlayingIsPlaying) "Playing" else "Paused"}",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }

        // ============================
        // Manual message send (simple)
        // ============================
        ElevatedCard {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = chatboxViewModel.messageText.value,
                    onValueChange = { chatboxViewModel.onMessageTextChange(it) },
                    modifier = Modifier.weight(1f),
                    label = { Text("Message") }
                )

                Spacer(Modifier.width(10.dp))

                IconButton(onClick = { chatboxViewModel.sendMessage() }) {
                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send")
                }
            }
        }
    }
}
