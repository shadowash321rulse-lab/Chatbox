package com.scrapw.chatbox

import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import com.scrapw.chatbox.ui.ChatboxViewModel

@Composable
fun ChatboxApp(
    navController: NavHostController? = null,
    chatboxViewModel: ChatboxViewModel = viewModel(factory = ChatboxViewModel.Factory)
) {
    ChatboxScreen(
        navController = navController,
        chatboxViewModel = chatboxViewModel
    )
}
