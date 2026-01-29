package com.scrapw.chatbox.ui.mainScreen

import android.app.Activity
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Logout
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
    val activity = context as? Activity

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
                        placeholder = { Text("One message per line (Spotify always shows under cycle automatically).") }
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
                        "Cycle sends:\n• your cycle line\n• Spotify block underneath (if enabled)",
                        style = MaterialTheme.typography.bodySmall
                    )
                } else {
                    Text(
                        "Enable cycle to send messages repeatedly.\nSpotify output also sends under cycle when enabled.",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }

        // ============================
        // Spotify controls (Auth + preset)
        // ============================
        ElevatedCard {
            Column(
                Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text("Spotify", style = MaterialTheme.typography.titleMedium)

                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("Enable Spotify block (VRChat)")
                    Spacer(Modifier.weight(1f))
                    Switch(
                        checked = chatboxViewModel.spotifyEnabled,
                        onCheckedChange = { chatboxViewModel.setSpotifyEnabledFlag(it) }
                    )
                }

                TextField(
                    value = chatboxViewModel.spotifyClientId,
                    onValueChange = { chatboxViewModel.setSpotifyClientIdValue(it) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    placeholder = { Text("Spotify Client ID (from Spotify Developer Dashboard)") }
                )

                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = {
                            if (activity != null) {
                                chatboxViewModel.beginSpotifyLogin(activity)
                            }
                        },
                        modifier = Modifier.weight(1f),
                        enabled = activity != null && chatboxViewModel.spotifyClientId.isNotBlank()
                    ) {
                        Icon(Icons.Filled.Link, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Login")
                    }

                    OutlinedButton(
                        onClick = { chatboxViewModel.logoutSpotify() },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Filled.Logout, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Logout")
                    }
                }

                // Presets 1..5 always visible
                Row(
                    Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Progress preset", style = MaterialTheme.typography.labelLarge)
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

                // Debug (demo mode hidden here)
                var debugExpanded by remember { mutableStateOf(false) }
                OutlinedButton(
                    onClick = { debugExpanded = !debugExpanded },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(if (debugExpanded) "Hide Debug" else "Show Debug")
                }

                if (debugExpanded) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("Demo mode (no Spotify needed)")
                        Spacer(Modifier.weight(1f))
                        Switch(
                            checked = chatboxViewModel.spotifyDemoEnabled,
                            onCheckedChange = { chatboxViewModel.setSpotifyDemoFlag(it) }
                        )
                    }

                    Text(
                        "Demo only helps testing presets.\nReal Spotify requires Client ID + Login + Spotify playing music.",
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                Text(
                    "Note: Spotify block is automatic.\nYou do NOT type {spotify} anywhere.",
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
