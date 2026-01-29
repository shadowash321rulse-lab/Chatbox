package com.scrapw.chatbox.ui.mainScreen

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.scrapw.chatbox.R
import com.scrapw.chatbox.ui.ChatboxViewModel

@Composable
fun MessageField(
    chatboxViewModel: ChatboxViewModel,
    modifier: Modifier = Modifier
) {
    Column(modifier.fillMaxWidth()) {

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Cycle messages")
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
                placeholder = { Text("One message per line") }
            )

            TextField(
                value = chatboxViewModel.cycleIntervalSeconds.toString(),
                onValueChange = {
                    it.toIntOrNull()?.let { n ->
                        chatboxViewModel.cycleIntervalSeconds = n.coerceAtLeast(1)
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Seconds between messages") }
            )
        }

        Row(
            modifier = Modifier.padding(top = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextField(
                value = chatboxViewModel.messageText.value,
                onValueChange = { chatboxViewModel.onMessageTextChange(it) },
                modifier = Modifier.weight(1f),
                placeholder = { Text("Write a message") }
            )

            Button(onClick = {
                if (chatboxViewModel.cycleEnabled) {
                    chatboxViewModel.startCycle()
                } else {
                    chatboxViewModel.sendMessage()
                }
            }) {
                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null)
            }
        }
    }
}
