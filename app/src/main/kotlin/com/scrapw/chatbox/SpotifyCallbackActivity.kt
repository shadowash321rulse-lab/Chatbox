package com.scrapw.chatbox

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import com.scrapw.chatbox.ui.ChatboxViewModel

class SpotifyCallbackActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val data: Uri? = intent?.data
        val code = data?.getQueryParameter("code")
        val state = data?.getQueryParameter("state")

        if (!code.isNullOrBlank() && !state.isNullOrBlank()) {
            try {
                ChatboxViewModel.getInstance().onSpotifyAuthCodeReceived(code, state)
            } catch (_: Throwable) { }
        }

        startActivity(
            Intent(this, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        )
        finish()
    }
}
