package com.scrapw.chatbox.ui.mainScreen

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import com.scrapw.chatbox.ui.ChatboxViewModel
import kotlinx.coroutines.launch

@Composable
fun MessageField(
    chatboxViewModel: ChatboxViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // Helper: first non-empty cycle line for preview
    fun firstCycleLine(): String {
        return chatboxViewModel.cycleMessages
            .lines()
            .map { it.trim() }
            .firstOrNull { it.isNotEmpty() }
            .orEmpty()
    }

    // Spotify preset previews (Spotify block only)
    fun presetPreview(p: Int): String {
        val title = "🎧 Lana Del Rey — Cinnamon Girl"
        val line2 = when (p) {
            1 -> "♡━━◉────────♡ 0:58 / 1:20"
            2 -> "━━◉────────── 0:58/1:20"
            3 -> "⟡⟡⟡◉⟡⟡⟡⟡⟡ 0:58 / 1:20"
            4 -> "▁▂▃▄▅●▅▄▃▂▁ 0:58 / 1:20"
            else -> "▣▣▣◉▢▢▢▢▢▢▢ 0:58 / 1:20"
        }
        return "$title\n$line2"
    }

    // Live preview content
    val spotifyBlock = chatboxViewModel.buildSpotifyBlockOrEmpty()
    val cycleLine = firstCycleLine()
    val combinedPreview = listOf(
        cycleLine.takeIf { it.isNotBlank() },
        spotifyBlock.takeIf { it.isNotBlank() }
    ).filterNotNull().joinToString("\n")

    val charCount = combinedPreview.length
    val overLimit = charCount > 144

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // ───────── Cycle card (separate) ─────────
        ElevatedCard(
            modifier = Modifier.fillMaxWidth(),
            elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Cycle", style = MaterialTheme.typography.titleSmall)
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
                    OutlinedTextField(
                        value = chatboxViewModel.cycleMessages,
                        onValueChange = { chatboxViewModel.cycleMessages = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 100.dp),
                        label = { Text("Cycle lines (one per line)") },
                        placeholder = { Text("hi hi 💗\ncome say hi~") },
                        singleLine = false
                    )

                    val interval = chatboxViewModel.cycleIntervalSeconds.coerceIn(1, 30)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Interval", style = MaterialTheme.typography.labelLarge)
                        Spacer(Modifier.weight(1f))
                        Text("${interval}s", style = MaterialTheme.typography.labelLarge)
                    }
                    Slider(
                        value = interval.toFloat(),
                        onValueChange = { v -> chatboxViewModel.cycleIntervalSeconds = v.toInt().coerceAtLeast(1) },
                        valueRange = 1f..30f,
                        steps = 28
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            modifier = Modifier.weight(1f),
                            onClick = { chatboxViewModel.startCycle() }
                        ) { Text("Start") }

                        OutlinedButton(
                            modifier = Modifier.weight(1f),
                            onClick = { chatboxViewModel.stopCycle() }
                        ) { Text("Stop") }
                    }
                } else {
                    Text(
                        "Cycle sends one line at a time. Spotify is separate and will be appended below.",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }

        // ───────── Spotify card (separate) ─────────
        ElevatedCard(
            modifier = Modifier.fillMaxWidth(),
            elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Spotify", style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.weight(1f))
                    Switch(
                        checked = chatboxViewModel.spotifyEnabled,
                        onCheckedChange = { chatboxViewModel.setSpotifyEnabled(it) }
                    )
                }

                if (chatboxViewModel.spotifyEnabled) {
                    OutlinedTextField(
                        value = chatboxViewModel.spotifyClientId,
                        onValueChange = { chatboxViewModel.setSpotifyClientId(it) },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Spotify Client ID") },
                        placeholder = { Text("Paste your Client ID here") },
                        singleLine = true
                    )

                    // Preset selector
                    var presetMenu by remember { mutableStateOf(false) }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Style preset", style = MaterialTheme.typography.labelLarge)
                        Spacer(Modifier.weight(1f))
                        OutlinedButton(onClick = { presetMenu = true }) {
                            Text("Preset ${chatboxViewModel.spotifyPreset} ▾")
                        }
                        DropdownMenu(expanded = presetMenu, onDismissRequest = { presetMenu = false }) {
                            (1..5).forEach { p ->
                                DropdownMenuItem(
                                    text = { Text("Preset $p") },
                                    onClick = {
                                        presetMenu = false
                                        chatboxViewModel.setSpotifyPreset(p)
                                    }
                                )
                            }
                        }
                    }

                    // Preset preview (example)
                    Surface(
                        tonalElevation = 1.dp,
                        shape = MaterialTheme.shapes.large
                    ) {
                        Text(
                            text = presetPreview(chatboxViewModel.spotifyPreset),
                            modifier = Modifier.padding(12.dp),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Button(
                            modifier = Modifier.weight(1f),
                            enabled = chatboxViewModel.spotifyClientId.isNotBlank(),
                            onClick = {
                                scope.launch {
                                    runCatching { chatboxViewModel.buildSpotifyAuthUrl() }
                                        .onSuccess { url ->
                                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                                            context.startActivity(intent)
                                        }
                                }
                            }
                        ) {
                            Text("Connect")
                        }

                        OutlinedButton(
                            modifier = Modifier.weight(1f),
                            onClick = { chatboxViewModel.disconnectSpotify() }
                        ) {
                            Text("Disconnect")
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedButton(
                            onClick = { scope.launch { chatboxViewModel.refreshSpotifyNowPlaying() } }
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Refresh now playing")
                        }

                        if (chatboxViewModel.spotifyStatus.isNotBlank()) {
                            Text(
                                chatboxViewModel.spotifyStatus,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }

                    if (spotifyBlock.isNotBlank()) {
                        Surface(
                            tonalElevation = 1.dp,
                            shape = MaterialTheme.shapes.large
                        ) {
                            Text(
                                text = spotifyBlock,
                                modifier = Modifier.padding(12.dp),
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                } else {
                    Text(
                        "Enable to append Spotify below your cycle line in VRChat.",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }

        // ───────── Preview card (exact combined output) ─────────
        ElevatedCard(
            modifier = Modifier.fillMaxWidth(),
            elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("VRChat preview", style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.weight(1f))
                    Text(
                        text = "$charCount/144",
                        style = MaterialTheme.typography.labelLarge,
                        color = if (overLimit) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Surface(tonalElevation = 1.dp, shape = MaterialTheme.shapes.large) {
                    Text(
                        text = if (combinedPreview.isBlank()) "Nothing to preview yet." else combinedPreview,
                        modifier = Modifier.padding(12.dp),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }

        // ───────── Quick send (unchanged minimal) ─────────
        ElevatedCard(
            modifier = Modifier.fillMaxWidth(),
            elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = chatboxViewModel.messageText.value,
                    onValueChange = { chatboxViewModel.onMessageTextChange(it) },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    label = { Text("Quick send") },
                    placeholder = { Text("Write a message") }
                )

                FilledIconButton(
                    onClick = { chatboxViewModel.sendMessage() }
                ) {
                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send")
                }
            }
        }
    }
}
