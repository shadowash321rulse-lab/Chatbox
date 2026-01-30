package com.scrapw.chatbox.ui.mainScreen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.scrapw.chatbox.ui.ChatboxViewModel

@Composable
fun IpField(
    chatboxViewModel: ChatboxViewModel,
    modifier: Modifier = Modifier
) {
    val uiState by chatboxViewModel.messengerUiState.collectAsState()

    // Local buffer so typing doesn't fight StateFlow updates
    var ipInput by rememberSaveable { mutableStateOf(uiState.ipAddress) }

    LaunchedEffect(uiState.ipAddress) {
        if (ipInput.isBlank()) ipInput = uiState.ipAddress
    }

    ElevatedCard(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = "OSC Host",
                style = MaterialTheme.typography.titleMedium
            )

            OutlinedTextField(
                value = ipInput,
                onValueChange = { ipInput = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text("Headset / Target IP") },
                placeholder = { Text("Example: 192.168.1.23") }
                // NOTE: no KeyboardOptions here to avoid build issues
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = { chatboxViewModel.ipAddressApply(ipInput.trim()) },
                    modifier = Modifier.weight(1f)
                ) { Text("Apply") }

                OutlinedButton(
                    onClick = { ipInput = uiState.ipAddress },
                    modifier = Modifier.weight(1f)
                ) { Text("Reset") }
            }

            Spacer(Modifier.height(2.dp))
            Text(
                text = "Current target: ${uiState.ipAddress}",
                style = MaterialTheme.typography.bodySmall
            )
            Spacer(Modifier.width(1.dp))
        }
    }
}
