package com.scrapw.chatbox.ui.mainScreen

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.scrapw.chatbox.ui.ChatboxViewModel
import kotlinx.coroutines.delay

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
                        placeholder = { Text("One message per line. Use {SPOTIFY} to insert Spotify block.") }
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
                }
            }
        }

        // ============================
        // Spotify preset tester (NO SPOTIFY REQUIRED)
        // ============================
        ElevatedCard {
            Column(
                Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text("Spotify preset preview (no Spotify needed)", style = MaterialTheme.typography.titleMedium)

                // Local demo playback that moves
                var previewEnabled by remember { mutableStateOf(true) }
                var demoProgressSec by remember { mutableIntStateOf(58) } // default example 0:58
                val demoDurationSec = 80 // 1:20
                val tickMs = 800L

                LaunchedEffect(previewEnabled) {
                    while (previewEnabled) {
                        delay(tickMs)
                        demoProgressSec = (demoProgressSec + 1) % (demoDurationSec + 1)
                    }
                }

                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Preview mode")
                    Spacer(Modifier.weight(1f))
                    Switch(
                        checked = previewEnabled,
                        onCheckedChange = { previewEnabled = it }
                    )
                }

                // Preset selector 1..5
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Preset")
                    Spacer(Modifier.weight(1f))

                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        (1..5).forEach { p ->
                            val selected = chatboxViewModel.spotifyPreset == p
                            Button(
                                onClick = { chatboxViewModel.updateSpotifyPreset(p) },
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                                colors = if (selected) ButtonDefaults.buttonColors()
                                else ButtonDefaults.outlinedButtonColors()
                            ) { Text("$p") }
                        }
                    }
                }

                // Show ALL 5 examples so you can verify they exist
                val p = (demoProgressSec * 1000L)
                val d = (demoDurationSec * 1000L)

                Text("All presets (moving marker):", style = MaterialTheme.typography.labelMedium)

                PresetLine(title = "1) Love", line = formatPresetLine(1, p, d))
                PresetLine(title = "2) Minimal", line = formatPresetLine(2, p, d))
                PresetLine(title = "3) Crystal", line = formatPresetLine(3, p, d))
                PresetLine(title = "4) Soundwave", line = formatPresetLine(4, p, d))
                PresetLine(title = "5) Geometry", line = formatPresetLine(5, p, d))

                Divider()

                // What would be inserted as the Spotify block (once Spotify is wired)
                Text("Selected preset output:", style = MaterialTheme.typography.labelMedium)

                val selectedLine = formatPresetLine(chatboxViewModel.spotifyPreset, p, d)
                // Title line shows artist unless overflow (we emulate that rule)
                val title = clampTitle("Artist Name", "Song Title Example", maxLen = 44) // just for preview readability

                Text(
                    "$title\n$selectedLine",
                    style = MaterialTheme.typography.bodyMedium
                )

                Text(
                    "Note: VRChat output won’t show until Spotify is wired OR you cycle-send preview text manually.",
                    style = MaterialTheme.typography.bodySmall
                )
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
                    placeholder = { Text("Write a message") }
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

@Composable
private fun PresetLine(title: String, line: String) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(title, modifier = Modifier.width(95.dp), style = MaterialTheme.typography.bodySmall)
        Text(line, style = MaterialTheme.typography.bodySmall)
    }
}

/**
 * EXACTLY your 5 presets, with moving marker.
 * 1) ♡━━━◉━━━━♡ 0:58 / 1:20
 * 2) ━━◉────────── 0:58/1:20
 * 3) ⟡⟡⟡◉⟡⟡⟡⟡⟡ 0:58 / 1:20
 * 4) ▁▂▃▄▅●▅▄▃▂▁ 0:58 / 1:20
 * 5) ▣▣▣◉▢▢▢▢▢▢▢ 0:58 / 1:20
 */
private fun formatPresetLine(preset: Int, progressMs: Long, durationMs: Long): String {
    val dur = durationMs.coerceAtLeast(1L)
    val prog = progressMs.coerceIn(0L, dur)

    val left = formatTime(prog)
    val right = formatTime(dur)

    return when (preset.coerceIn(1, 5)) {
        1 -> {
            val inner = movingBar(width = 8, progress = prog.toFloat() / dur.toFloat(), filled = "━", empty = "━", marker = "◉")
            "♡$inner♡ $left / $right"
        }
        2 -> {
            val bar = movingBar(width = 13, progress = prog.toFloat() / dur.toFloat(), filled = "━", empty = "─", marker = "◉")
            "$bar $left/$right"
        }
        3 -> {
            val bar = movingBar(width = 9, progress = prog.toFloat() / dur.toFloat(), filled = "⟡", empty = "⟡", marker = "◉")
            "$bar $left / $right"
        }
        4 -> {
            val wave = movingWave(progress = prog.toFloat() / dur.toFloat())
            "$wave $left / $right"
        }
        else -> {
            val bar = movingBar(width = 11, progress = prog.toFloat() / dur.toFloat(), filled = "▣", empty = "▢", marker = "◉")
            "$bar $left / $right"
        }
    }
}

private fun movingBar(width: Int, progress: Float, filled: String, empty: String, marker: String): String {
    val w = width.coerceAtLeast(2)
    val idx = ((progress.coerceIn(0f, 1f)) * (w - 1)).toInt()
    val sb = StringBuilder()
    for (i in 0 until w) {
        sb.append(
            when {
                i == idx -> marker
                i < idx -> filled
                else -> empty
            }
        )
    }
    return sb.toString()
}

private fun movingWave(progress: Float): String {
    val wave = listOf("▁", "▂", "▃", "▄", "▅", "▅", "▄", "▃", "▂", "▁", "▁")
    val idx = ((progress.coerceIn(0f, 1f)) * (wave.size - 1)).toInt()
    val sb = StringBuilder()
    for (i in wave.indices) sb.append(if (i == idx) "●" else wave[i])
    return sb.toString()
}

private fun formatTime(ms: Long): String {
    val totalSec = (ms / 1000L).coerceAtLeast(0L)
    val m = totalSec / 60L
    val s = totalSec % 60L
    return if (s < 10) "${m}:0${s}" else "${m}:${s}"
}

private fun clampTitle(artist: String, track: String, maxLen: Int): String {
    val full = "$artist — $track"
    if (full.length <= maxLen) return full
    // your rule: remove artist if overflow
    if (track.length <= maxLen) return track
    // ellipsize track
    if (maxLen <= 1) return "…"
    return track.take(maxLen - 1) + "…"
}
