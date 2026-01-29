package com.scrapw.chatbox.spotify

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import com.scrapw.chatbox.ChatboxApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Receives: chatbox://spotify-callback?code=...&state=...
 * Exchanges code -> tokens and stores them in DataStore.
 * Then returns user to MainActivity.
 */
class SpotifyCallbackActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val uri: Uri? = intent?.data
        if (uri != null) {
            val repo = (application as ChatboxApplication).userPreferencesRepository
            CoroutineScope(Dispatchers.IO).launch {
                SpotifyTokenExchange.exchangeAndStore(repo, uri)
            }
        }

        // Return to the app
        val launch = packageManager.getLaunchIntentForPackage(packageName)
        launch?.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        if (launch != null) startActivity(launch)

        finish()
    }
}
