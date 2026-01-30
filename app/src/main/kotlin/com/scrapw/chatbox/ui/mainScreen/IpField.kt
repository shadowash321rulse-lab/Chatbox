package com.scrapw.chatbox.ui.mainScreen

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.scrapw.chatbox.ui.ChatboxViewModel
import com.scrapw.chatbox.ui.MessengerUiState
import kotlinx.coroutines.flow.StateFlow

@Composable
fun IpField(
    chatboxViewModel: ChatboxViewModel,
    uiState: MessengerUiState,
    modifier: Modifier = Modifier
) {
    // Local text state so typing doesn't get overwritten by recompositions/flows
    var ipText by remember(uiState.ipAddress) { mutableStateOf(uiState.ipAddress) }

    ElevatedCard(modifier = modifier.fillMaxWidth()) {
        Column(
            Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("OSC Host (Quest IP)", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.weight(1f))
                IconButton(
                    onClick = {
                        chatboxViewModel.ipAddressLocked = !chatboxViewModel.ipAddressLocked
                        // when unlocking, keep current uiState as base
                        if (!chatboxViewModel.ipAddressLocked) {
                            ipText = uiState.ipAddress
                        }
                    }
                ) {
                    Icon(
                        if (chatboxViewModel.ipAddressLocked) Icons.Filled.Lock else Icons.Filled.LockOpen,
                        contentDescription = null
                    )
                }
            }

            OutlinedTextField(
                value = ipText,
                onValueChange = {
                    // if locked, don't allow edits
                    if (!chatboxViewModel.ipAddressLocked) {
                        ipText = it
                        chatboxViewModel.onIpAddressChange(it)
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = !chatboxViewModel.ipAddressLocked,
                singleLine = true,
                label = { Text("IP Address") },
                supportingText = { Text("Example: 192.168.0.25") }
            )

            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = {
                        val clean = ipText.trim()
                        if (clean.isNotEmpty()) {
                            chatboxViewModel.ipAddressApply(clean)
                            chatboxViewModel.ipAddressLocked = true
                        }
                    },
                    modifier = Modifier.weight(1f),
                    enabled = !chatboxViewModel.ipAddressLocked
                ) {
                    Text("Apply")
                }

                OutlinedButton(
                    onClick = {
                        // reset to current saved/state IP
                        ipText = uiState.ipAddress
                        chatboxViewModel.onIpAddressChange("")
                    },
                    modifier = Modifier.weight(1f),
                    enabled = !chatboxViewModel.ipAddressLocked
                ) {
                    Text("Reset")
                }
            }
        }
    }
}
