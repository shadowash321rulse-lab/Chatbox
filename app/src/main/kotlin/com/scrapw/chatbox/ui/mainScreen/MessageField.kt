package com.scrapw.chatbox.ui.mainScreen

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import com.scrapw.chatbox.ui.ChatboxViewModel

private data class UiPreset(
    val name: String,
    val intervalSeconds: Int,
    val messages: String
)

@Composable
fun MessageField(
    chatboxViewModel: ChatboxViewModel,
    modifier: Modifier = Modifier
) {
    // Built-in presets (5)
    val builtIns = remember {
        listOf(
            UiPreset(
                name = "Cute intro 💕",
                intervalSeconds = 3,
                messages = "hi hi 💗\nfollow me on vrchat\n{SPOTIFY}"
            ),
            UiPreset(
                name = "Chill ✨",
                intervalSeconds = 5,
                messages = "vibing ✨\nbe kind 🤍\n{SPOTIFY}"
            ),
            UiPreset(
                name = "AFK 🌙",
                intervalSeconds = 8,
                messages = "AFK 🌙\nback soon"
            ),
            UiPreset(
                name = "Party 💿",
                intervalSeconds = 4,
                messages = "let’s goooo 💿\n{SPOTIFY}\njoin us!"
            ),
            UiPreset(
                name = "Minimal 🤍",
                intervalSeconds = 6,
                messages = "{SPOTIFY}"
            )
        )
    }

    var presetMenuOpen by remember { mutableStateOf(false) }

    // This is your “personal preset”: whatever is currently saved in the app already.
    // (Since your cycleMessages/interval persist, this effectively survives app restarts.)
    val myCurrentPresetName = "My current ⭐"

    fun applyPreset(p: UiPreset) {
        chatboxViewModel.cycleEnabled = true
        chatboxViewModel.stopCycle()
        chatboxViewModel.cycleIntervalSeconds = p.intervalSeconds
        chatboxViewModel.cycleMessages = p.messages
    }

    fun focusCursorEnd() {
        val t = chatboxViewModel.cycleMessages
        chatboxViewModel.messageText.value = TextFieldValue(
            chatboxViewModel.messageText.value.text,
            TextRange(chatboxViewModel.messageText.value.text.length)
        )
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // ===== Preset pill (spacey header) =====
        ElevatedCard(
            modifier = Modifier.fillMaxWidth(),
            elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Preset",
                    style = MaterialTheme.typography.labelLarge
                )
                Spacer(Modifier.weight(1f))

                // Pill button
                OutlinedButton(
                    onClick = { presetMenuOpen = true },
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Text(text = "Choose ▾")
                }

                DropdownMenu(
                    expanded = presetMenuOpen,
                    onDismissRequest = { presetMenuOpen = false }
                ) {
                    // Your personal/current
                    DropdownMenuItem(
                        text = {
                            Text("$myCurrentPresetName  •  ${chatboxViewModel.cycleIntervalSeconds}s")
                        },
                        onClick = {
                            presetMenuOpen = false
                            // “My current” = do nothing; it’s already your current values
                            // (but we turn cycle on so it’s usable immediately)
                            chatboxViewModel.cycleEnabled = true
                        }
                    )
                    Divider()

                    builtIns.forEach { p ->
                        DropdownMenuItem(
                            text = { Text("${p.name}  •  ${p.intervalSeconds}s") },
                            onClick = {
                                presetMenuOpen = false
                                applyPreset(p)
                            }
                        )
                    }
                }
            }
        }

        // ===== Cycle card =====
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
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
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
                            .heightIn(min = 120.dp),
                        label = { Text("Messages (one per line)") },
                        placeholder = { Text("hi hi 💗\nfollow me\n{SPOTIFY}") },
                        singleLine = false
                    )

                    // Interval slider (spacey + simple)
                    val interval = chatboxViewModel.cycleIntervalSeconds.coerceIn(1, 30)
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Interval", style = MaterialTheme.typography.labelLarge)
                            Spacer(Modifier.weight(1f))
                            Text("${interval}s", style = MaterialTheme.typography.labelLarge)
                        }
                        Slider(
                            value = interval.toFloat(),
                            onValueChange = { v ->
                                chatboxViewModel.cycleIntervalSeconds = v.toInt().coerceAtLeast(1)
                            },
                            valueRange = 1f..30f,
                            steps = 28
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Button(
                            modifier = Modifier.weight(1f),
                            onClick = { chatboxViewModel.startCycle() }
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Start")
                        }
                        OutlinedButton(
                            modifier = Modifier.weight(1f),
                            onClick = { chatboxViewModel.stopCycle() }
                        ) {
                            Icon(Icons.Default.Pause, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Stop")
                        }
                    }
                } else {
                    Text(
                        text = "Enable to rotate messages automatically.",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }

        // ===== Quick Send card =====
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
                    onClick = {
                        if (chatboxViewModel.cycleEnabled) chatboxViewModel.startCycle()
                        else chatboxViewModel.sendMessage()
                        focusCursorEnd()
                    }
                ) {
                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send")
                }
            }
        }
    }
}
