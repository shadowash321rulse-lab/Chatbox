package com.scrapw.chatbox

import android.app.Activity
import android.net.Uri
import androidx.browser.customtabs.CustomTabsIntent
import com.scrapw.chatbox.data.UserPreferencesRepository
import kotlinx.coroutines.flow.first
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import kotlin.math.max

class SpotifyAuth(
    private val repo: UserPreferencesRepository
) {
    private val http = OkHttpClient()

    private val redirectUri = "chatbox://spotify-callback"
    private val scopes = "user-read-playback-state user-read-currently-playing"

    fun beginLogin(activity: Activity, clientId: String) {
        val verifier = randomUrlSafe(64)
        val challenge = sha256Base64Url(verifier)
        val state = randomUrlSafe(24)

        kotlinx.coroutines.runBlocking {
            repo.saveSpotifyCodeVerifier(verifier)
            repo.saveSpotifyState(state)
        }

        val url = Uri.Builder()
            .scheme("https")
            .authority("accounts.spotify.com")
            .appendPath("authorize")
            .appendQueryParameter("response_type", "code")
            .appendQueryParameter("client_id", clientId)
            .appendQueryParameter("redirect_uri", redirectUri)
            .appendQueryParameter("code_challenge_method", "S256")
            .appendQueryParameter("code_challenge", challenge)
            .appendQueryParameter("state", state)
            .appendQueryParameter("scope", scopes)
            .build()

        CustomTabsIntent.Builder().build().launchUrl(activity, url)
    }

    suspend fun exchangeCodeForTokens(code: String, state: String, clientId: String) {
        val expectedState = repo.spotifyState.first().trim()
        if (expectedState.isBlank() || expectedState != state) return

        val verifier = repo.spotifyCodeVerifier.first().trim()
        if (verifier.isBlank() || clientId.isBlank()) return

        val body = FormBody.Builder()
            .add("client_id", clientId)
            .add("grant_type", "authorization_code")
            .add("code", code)
            .add("redirect_uri", redirectUri)
            .add("code_verifier", verifier)
            .build()

        val req = Request.Builder()
            .url("https://accounts.spotify.com/api/token")
            .post(body)
            .build()

        http.newCall(req).execute().use { res ->
            if (!res.isSuccessful) return
            val json = JSONObject(res.body?.string().orEmpty())
            val access = json.optString("access_token", "")
            val refresh = json.optString("refresh_token", "")
            val expiresIn = max(1, json.optInt("expires_in", 3600))
            val expiresAt = (System.currentTimeMillis() / 1000L) + expiresIn.toLong() - 30L

            repo.saveSpotifyAccessToken(access)
            if (refresh.isNotBlank()) repo.saveSpotifyRefreshToken(refresh)
            repo.saveSpotifyExpiresAtEpochSec(expiresAt)

            repo.saveSpotifyCodeVerifier("")
            repo.saveSpotifyState("")
        }
    }

    private fun randomUrlSafe(len: Int): String {
        val bytes = ByteArray(len)
        SecureRandom().nextBytes(bytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }

    private fun sha256Base64Url(input: String): String {
        val md = MessageDigest.getInstance("SHA-256")
        val digest = md.digest(input.toByteArray(Charsets.UTF_8))
        return Base64.getUrlEncoder().withoutPadding().encodeToString(digest)
    }
}
