package com.scrapw.chatbox.ui.mainScreen

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.filled.PauseCircleFilled
import androidx.compose.material.icons.filled.PlayCircleFilled
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.scrapw.chatbox.ui.ChatboxViewModel
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

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
                        placeholder = { Text("One message per line. Spotify always shows underneath automatically.") }
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

                    // ✅ Dedicated cycle start/stop buttons
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
                }
            }
        }

        // ============================
        // Spotify preset tester (preview)
        // ============================
        ElevatedCard {
            Column(
                Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text("Spotify preset preview", style = MaterialTheme.typography.titleMedium)

                var previewEnabled by remember { mutableStateOf(true) }
                var demoProgressSec by remember { mutableIntStateOf(0) }
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
                    Switch(checked = previewEnabled, onCheckedChange = { previewEnabled = it })
                }

                // ✅ 5 buttons always accessible
                Row(
                    Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Preset", style = MaterialTheme.typography.labelLarge)
                    Spacer(Modifier.width(4.dp))
                    (1..5).forEach { p ->
                        val selected = chatboxViewModel.spotifyPreset == p
                        Button(
                            onClick = { chatboxViewModel.updateSpotifyPreset(p) },
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            colors = if (selected) ButtonDefaults.buttonColors()
                            else ButtonDefaults.outlinedButtonColors()
                        ) { Text("$p") }
                    }
                }

                val pMs = demoProgressSec * 1000L
                val dMs = demoDurationSec * 1000L

                Text("All presets (moving marker):", style = MaterialTheme.typography.labelMedium)

                PresetLine("1) Love", formatPresetLine(1, pMs, dMs))
                PresetLine("2) Minimal", formatPresetLine(2, pMs, dMs))
                PresetLine("3) Crystal", formatPresetLine(3, pMs, dMs))
                PresetLine("4) Soundwave", formatPresetLine(4, pMs, dMs))
                PresetLine("5) Geometry", formatPresetLine(5, pMs, dMs))
            }
        }

        // ============================
        // Message input + buttons
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

@Composable
private fun PresetLine(title: String, line: String) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(title, modifier = Modifier.width(110.dp), style = MaterialTheme.typography.bodySmall)
        Text(line, style = MaterialTheme.typography.bodySmall)
    }
}

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
    val idx = ((progress.coerceIn(0f, 1f)) * (w - 1)).roundToInt()
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
    val idx = ((progress.coerceIn(0f, 1f)) * (wave.size - 1)).roundToInt()
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

