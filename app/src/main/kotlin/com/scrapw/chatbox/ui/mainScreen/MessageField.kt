package com.scrapw.chatbox.ui.mainScreen

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardOptions
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.scrapw.chatbox.R
import com.scrapw.chatbox.ui.ChatboxViewModel

@Composable
fun MessageField(
    chatboxViewModel: ChatboxViewModel,
    modifier: Modifier = Modifier
) {
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
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {

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
                        placeholder = { Text("Seconds between messages (min 1)") },
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done)
                    )
                }
            }
        }

        // ============================
        // Spotify controls
        // ============================
        ElevatedCard {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {

                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Spotify", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.weight(1f))
                    Switch(
                        checked = chatboxViewModel.spotifyEnabled,
                        onCheckedChange = { enabled ->
                            // ✅ NEW function names (no JVM clash)
                            chatboxViewModel.updateSpotifyEnabled(enabled)
                        }
                    )
                }

                if (chatboxViewModel.spotifyEnabled) {

                    TextField(
                        value = chatboxViewModel.spotifyClientId,
                        onValueChange = { id ->
                            // ✅ NEW function names (no JVM clash)
                            chatboxViewModel.updateSpotifyClientId(id)
                        },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        placeholder = { Text("Spotify Client ID") }
                    )

                    // Preset selector (1..5)
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Preset", modifier = Modifier.padding(end = 10.dp))
                        Spacer(Modifier.weight(1f))

                        // Small segmented-like buttons
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            (1..5).forEach { p ->
                                val selected = chatboxViewModel.spotifyPreset == p
                                Button(
                                    onClick = { chatboxViewModel.updateSpotifyPreset(p) },
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                                    colors = if (selected) {
                                        ButtonDefaults.buttonColors()
                                    } else {
                                        ButtonDefaults.outlinedButtonColors()
                                    }
                                ) { Text("$p") }
                            }
                        }
                    }

                    // Optional: show preview of what will be sent (nice for debugging)
                    val preview = chatboxViewModel.buildSpotifyBlockOrEmpty()
                    if (preview.isNotBlank()) {
                        Text(
                            text = "Preview (VRChat):",
                            style = MaterialTheme.typography.labelMedium
                        )
                        Text(
                            text = preview,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        }

        // ============================
        // Message send row
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
                    placeholder = { Text("Write a message") },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send)
                )

                Spacer(Modifier.width(10.dp))

                Button(
                    onClick = {
                        if (chatboxViewModel.cycleEnabled) {
                            chatboxViewModel.startCycle()
                        } else {
                            chatboxViewModel.sendMessage()
                        }
                    }
                ) {
                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null)
                }
            }
        }
    }
}
