package com.scrapw.chatbox.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp

/**
 * BufferedApplyTextField
 *
 * A TextField that behaves like the IP address field:
 * - Local buffer
 * - No auto-commit
 * - Apply / Reset buttons
 * - Stable across recompositions
 */
@Composable
fun BufferedApplyTextField(
    label: String,
    valueFromVm: String,
    onApply: (String) -> Unit,
    modifier: Modifier = Modifier,
    singleLine: Boolean = true,
    keyboardOptions: androidx.compose.foundation.text.KeyboardOptions =
        androidx.compose.foundation.text.KeyboardOptions.Default
) {
    var buffer by rememberSaveable(stateSaver = TextFieldValue.Saver) {
        mutableStateOf(TextFieldValue(valueFromVm))
    }

    // Sync ONLY when VM value actually changes externally
    LaunchedEffect(valueFromVm) {
        if (buffer.text.isBlank()) {
            buffer = TextFieldValue(valueFromVm)
        }
    }

    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = buffer,
            onValueChange = { buffer = it },
            modifier = Modifier.fillMaxWidth(),
            singleLine = singleLine,
            label = { Text(label) },
            keyboardOptions = keyboardOptions
        )

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(
                onClick = { onApply(buffer.text.trim()) },
                modifier = Modifier.weight(1f)
            ) {
                Text("Apply")
            }

            OutlinedButton(
                onClick = { buffer = TextFieldValue(valueFromVm) },
                modifier = Modifier.weight(1f)
            ) {
                Text("Reset")
            }
        }
    }
}
