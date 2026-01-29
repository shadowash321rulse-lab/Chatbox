package com.scrapw.chatbox.ui.mainScreen

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.scrapw.chatbox.data.SettingsStates
import com.scrapw.chatbox.ui.ChatboxViewModel
import com.scrapw.chatbox.ui.MessengerUiState

@Preview
@Composable
fun MainScreen(
    modifier: Modifier = Modifier,
    chatboxViewModel: ChatboxViewModel = viewModel(factory = ChatboxViewModel.Factory),
    uiState: MessengerUiState = MessengerUiState()
) {
    val displayIpState = SettingsStates.displayIpState()
    val displayMessageOptionsState = SettingsStates.displayMessageOptionsState()

    // “Spacey” layout: content sits in a padded column,
    // conversation gets breathing room,
    // bottom controls feel like a floating dock.
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        if (displayIpState.value) {
            ElevatedCard(
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp)
            ) {
                Box(Modifier.padding(10.dp)) {
                    IpField(chatboxViewModel, uiState)
                }
            }
        }

        // Conversation area (kept simple; your Conversation composable handles its own UI)
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            tonalElevation = 0.dp,
            shape = RoundedCornerShape(18.dp)
        ) {
            // Slight inner padding to reduce edge-clinging
            Box(Modifier.padding(6.dp)) {
                Conversation(
                    uiState = chatboxViewModel.conversationUiState,
                    onCopyPressed = chatboxViewModel::onMessageTextChange,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }

        // Bottom “dock”
        Surface(
            modifier = Modifier.fillMaxWidth(),
            tonalElevation = 2.dp,
            shadowElevation = 6.dp,
            shape = RoundedCornerShape(22.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (displayMessageOptionsState.value) {
                    // Keep your existing options panel, but inside the dock for a cleaner look
                    MessageOptions(chatboxViewModel, uiState, true)
                }

                // Your redesigned MessageField already has its own spacing/cards
                MessageField(chatboxViewModel)
            }
        }
    }
}
