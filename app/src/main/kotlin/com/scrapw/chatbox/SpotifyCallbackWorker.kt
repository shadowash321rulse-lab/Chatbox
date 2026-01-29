package com.scrapw.chatbox.spotify

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.ContextCompat
import com.scrapw.chatbox.ChatboxApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

object SpotifyCallbackWorker {
    fun start(context: Context, uriString: String) {
        // lightweight “fire and forget”
        CoroutineScope(Dispatchers.IO).launch {
            val repo = (context.applicationContext as ChatboxApplication).userPreferencesRepository
            SpotifyAuth.handleRedirect(context, repo, Uri.parse(uriString))
        }
    }
}

