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
        // Cycle controls
        // ============================
        ElevatedCard {
            Column(
                Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Cycle messages", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.weight(1f))
                    Switch(
                        checked = chatboxViewModel.cycleEnabled,
                        onCheckedChange = {
                            chatboxViewModel.cycleEnabled = it
                            if (!it) chatboxViewModel.stopCycle()
                        }
                    )
                }

                if (chatboxViewModel.cycleEnabled) {
                    TextField(
                        value = chatboxViewModel.cycleMessages,
                        onValueChange = { chatboxViewModel.cycleMessages = it },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 2,
                        maxLines = 6,
                        placeholder = { Text("One message per line (Now Playing shows under cycle automatically).") }
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
                        placeholder = { Text("Seconds between messages (min 1)") }
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

                    Text(
                        "Cycle sends:\n• your cycle line\n• Now Playing block underneath (if enabled)",
                        style = MaterialTheme.typography.bodySmall
                    )
                } else {
                    Text(
                        "Enable cycle to send messages repeatedly.\nNow Playing output sends under cycle when enabled.",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }

        // ============================
        // Now Playing controls
        // ============================
        ElevatedCard {
            Column(
                Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text("Now Playing (no Spotify login)", style = MaterialTheme.typography.titleMedium)

                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("Enable Now Playing block (VRChat)")
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

                var debugExpanded by remember { mutableStateOf(false) }
                OutlinedButton(
                    onClick = { debugExpanded = !debugExpanded },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(if (debugExpanded) "Hide Debug" else "Show Debug")
                }

                if (debugExpanded) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("Demo mode (shows without music)")
                        Spacer(Modifier.weight(1f))
                        Switch(
                            checked = chatboxViewModel.spotifyDemoEnabled,
                            onCheckedChange = { chatboxViewModel.setSpotifyDemoFlag(it) }
                        )
                    }
                }

                Text(
                    "No {spotify} tag. This block is always automatic and separate under cycle.",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }

        // ============================
        // Manual message input + quick + send
        // ============================
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
