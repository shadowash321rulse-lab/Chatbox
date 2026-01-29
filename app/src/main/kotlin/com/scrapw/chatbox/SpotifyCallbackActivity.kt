package com.scrapw.chatbox.spotify

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import com.scrapw.chatbox.ChatboxApplication

class SpotifyCallbackActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val uri: Uri? = intent?.data
        if (uri != null) {
            val repo = (application as ChatboxApplication).userPreferencesRepository
            // Kick token exchange off in background
            // (no UI, so just start and finish)
            SpotifyCallbackWorker.start(this, uri.toString())
        }

        // Return to app
        val launch = packageManager.getLaunchIntentForPackage(packageName)
        launch?.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        if (launch != null) startActivity(launch)

        finish()
    }
}

