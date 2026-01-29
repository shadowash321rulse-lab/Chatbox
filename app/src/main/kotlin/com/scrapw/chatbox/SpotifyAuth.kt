package com.scrapw.chatbox.spotify

import android.content.Context
import android.net.Uri
import android.util.Base64
import com.scrapw.chatbox.data.UserPreferencesRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Instant

object SpotifyAuth {
    // You will add this redirect URI in Spotify Developer Dashboard + AndroidManifest intent-filter
    const val REDIRECT_URI = "chatbox://spotify-callback"
    private const val AUTH_URL = "https://accounts.spotify.com/authorize"
    private const val TOKEN_URL = "https://accounts.spotify.com/api/token"

    private val scopes = listOf(
        "user-read-currently-playing",
        "user-read-playback-state"
    )

    data class AuthLaunch(val url: String)

    suspend fun buildAuthUrl(repo: UserPreferencesRepository): AuthLaunch {
        val clientId = repo.spotifyClientIdFirst()
        require(clientId.isNotBlank()) { "Spotify Client ID is empty" }

        val state = randomUrlSafe(16)
        val verifier = randomUrlSafe(64)
        val challenge = codeChallengeS256(verifier)

        repo.saveSpotifyState(state)
        repo.saveSpotifyCodeVerifier(verifier)

        val scopeStr = scopes.joinToString(" ")
        val url = AUTH_URL +
            "?client_id=" + enc(clientId) +
            "&response_type=code" +
            "&redirect_uri=" + enc(REDIRECT_URI) +
            "&code_challenge_method=S256" +
            "&code_challenge=" + enc(challenge) +
            "&state=" + enc(state) +
            "&scope=" + enc(scopeStr)

        return AuthLaunch(url)
    }

    suspend fun handleRedirect(
        context: Context,
        repo: UserPreferencesRepository,
        uri: Uri
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val code = uri.getQueryParameter("code") ?: error("Missing code")
            val returnedState = uri.getQueryParameter("state") ?: error("Missing state")

            val expectedState = repo.spotifyStateFirst()
            if (expectedState.isNotBlank() && expectedState != returnedState) {
                error("State mismatch")
            }

            val clientId = repo.spotifyClientIdFirst()
            val verifier = repo.spotifyCodeVerifierFirst()
            require(clientId.isNotBlank()) { "Spotify Client ID is empty" }
            require(verifier.isNotBlank()) { "Missing PKCE code_verifier" }

            val body = form(
                "client_id" to clientId,
                "grant_type" to "authorization_code",
                "code" to code,
                "redirect_uri" to REDIRECT_URI,
                "code_verifier" to verifier
            )

            val json = postForm(TOKEN_URL, body)
            val accessToken = json.getString("access_token")
            val refreshToken = json.optString("refresh_token", "")
            val expiresIn = json.getLong("expires_in") // seconds

            val expiresAt = Instant.now().epochSecond + expiresIn - 15 // small safety buffer
            repo.saveSpotifyAccessToken(accessToken)
            if (refreshToken.isNotBlank()) repo.saveSpotifyRefreshToken(refreshToken)
            repo.saveSpotifyExpiresAtEpochSec(expiresAt)

            // clear temporary PKCE values
            repo.saveSpotifyCodeVerifier("")
            repo.saveSpotifyState("")
        }
    }

    suspend fun ensureValidAccessToken(repo: UserPreferencesRepository): String = withContext(Dispatchers.IO) {
        val access = repo.spotifyAccessTokenFirst()
        val refresh = repo.spotifyRefreshTokenFirst()
        val expiresAt = repo.spotifyExpiresAtEpochSecFirst()
        val now = Instant.now().epochSecond

        if (access.isNotBlank() && expiresAt > now) return@withContext access
        if (refresh.isBlank()) return@withContext access // may be blank if not logged in

        val clientId = repo.spotifyClientIdFirst()
        require(clientId.isNotBlank()) { "Spotify Client ID is empty" }

        val body = form(
            "client_id" to clientId,
            "grant_type" to "refresh_token",
            "refresh_token" to refresh
        )
        val json = postForm(TOKEN_URL, body)
        val newAccess = json.getString("access_token")
        val expiresIn = json.getLong("expires_in")
        val newExpiresAt = Instant.now().epochSecond + expiresIn - 15

        repo.saveSpotifyAccessToken(newAccess)
        repo.saveSpotifyExpiresAtEpochSec(newExpiresAt)
        return@withContext newAccess
    }

    // ─────────────────────────────
    // HTTP helpers
    // ─────────────────────────────
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
        if (code !in 200..299) error("HTTP $code: $text")
        return JSONObject(text)
    }

    private fun form(vararg pairs: Pair<String, String>): String =
        pairs.joinToString("&") { (k, v) -> "${enc(k)}=${enc(v)}" }

    private fun enc(s: String): String = URLEncoder.encode(s, "UTF-8")

    private fun randomUrlSafe(bytes: Int): String {
        val b = ByteArray(bytes)
        SecureRandom().nextBytes(b)
        // Base64URL without padding
        return Base64.encodeToString(b, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
    }

    private fun codeChallengeS256(verifier: String): String {
        val md = MessageDigest.getInstance("SHA-256")
        val digest = md.digest(verifier.toByteArray(Charsets.US_ASCII))
        return Base64.encodeToString(digest, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
    }
}

// small “first()” helpers to avoid repeating in ViewModel
private suspend fun UserPreferencesRepository.spotifyClientIdFirst() = spotifyClientId.first()
private suspend fun UserPreferencesRepository.spotifyStateFirst() = spotifyState.first()
private suspend fun UserPreferencesRepository.spotifyCodeVerifierFirst() = spotifyCodeVerifier.first()
private suspend fun UserPreferencesRepository.spotifyAccessTokenFirst() = spotifyAccessToken.first()
private suspend fun UserPreferencesRepository.spotifyRefreshTokenFirst() = spotifyRefreshToken.first()
private suspend fun UserPreferencesRepository.spotifyExpiresAtEpochSecFirst() = spotifyExpiresAtEpochSec.first()

