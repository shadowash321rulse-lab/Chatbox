package com.scrapw.chatbox.spotify

import android.net.Uri
import android.util.Base64
import com.scrapw.chatbox.data.UserPreferencesRepository
import kotlinx.coroutines.flow.first
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.security.MessageDigest
import java.time.Instant

object SpotifyTokenExchange {

    private const val TOKEN_URL = "https://accounts.spotify.com/api/token"
    private const val REDIRECT_URI = "chatbox://spotify-callback"

    suspend fun exchangeAndStore(repo: UserPreferencesRepository, uri: Uri) {
        val code = uri.getQueryParameter("code") ?: return
        val returnedState = uri.getQueryParameter("state") ?: return

        val expectedState = repo.spotifyState.first()
        if (expectedState.isNotBlank() && expectedState != returnedState) return

        val clientId = repo.spotifyClientId.first()
        val verifier = repo.spotifyCodeVerifier.first()
        if (clientId.isBlank() || verifier.isBlank()) return

        val body = form(
            "client_id" to clientId,
            "grant_type" to "authorization_code",
            "code" to code,
            "redirect_uri" to REDIRECT_URI,
            "code_verifier" to verifier
        )

        val json = postForm(TOKEN_URL, body)

        val accessToken = json.optString("access_token", "")
        val refreshToken = json.optString("refresh_token", "")
        val expiresIn = json.optLong("expires_in", 0L)

        if (accessToken.isBlank() || expiresIn <= 0L) return

        val expiresAt = Instant.now().epochSecond + expiresIn - 15

        repo.saveSpotifyAccessToken(accessToken)
        if (refreshToken.isNotBlank()) repo.saveSpotifyRefreshToken(refreshToken)
        repo.saveSpotifyExpiresAtEpochSec(expiresAt)

        // Clear PKCE temp
        repo.saveSpotifyCodeVerifier("")
        repo.saveSpotifyState("")
    }

    // ───────── helpers ─────────
    private fun enc(s: String): String = URLEncoder.encode(s, "UTF-8")

    private fun form(vararg pairs: Pair<String, String>): String =
        pairs.joinToString("&") { (k, v) -> "${enc(k)}=${enc(v)}" }

    private fun postForm(urlStr: String, body: String): JSONObject {
        val url = URL(urlStr)
        val conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            doOutput = true
            setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
        }
        conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
        val code = conn.responseCode
        val stream = if (code in 200..299) conn.inputStream else conn.errorStream
        val text = stream.bufferedReader().use { it.readText() }
        if (code !in 200..299) return JSONObject("{}")
        return JSONObject(text)
    }

    // Not strictly needed here (ViewModel generates the challenge),
    // but kept in case you want to move generation into this object later.
    fun codeChallengeS256(verifier: String): String {
        val md = MessageDigest.getInstance("SHA-256")
        val digest = md.digest(verifier.toByteArray(Charsets.US_ASCII))
        return Base64.encodeToString(digest, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
    }
}
