package com.scrapw.chatbox.ui

import com.scrapw.chatbox.data.UserPreferencesRepository
import kotlinx.coroutines.flow.first
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import kotlin.math.max

class SpotifyApi(
    private val repo: UserPreferencesRepository
) {
    private val http = OkHttpClient()

    suspend fun fetchNowPlaying(): ChatboxViewModel.SpotifyNowPlaying? {
        val token = ensureValidAccessToken() ?: return null

        val req = Request.Builder()
            .url("https://api.spotify.com/v1/me/player/currently-playing")
            .header("Authorization", "Bearer $token")
            .build()

        http.newCall(req).execute().use { res ->
            if (res.code == 204) return null
            if (res.code == 401) {
                refreshToken()
                return null
            }
            if (!res.isSuccessful) return null

            val body = res.body?.string().orEmpty()
            val json = JSONObject(body)

            val isPlaying = json.optBoolean("is_playing", false)
            val progressMs = json.optLong("progress_ms", 0L)

            val item = json.optJSONObject("item") ?: return null
            val durationMs = item.optLong("duration_ms", 1L)
            val track = item.optString("name", "")

            val artists = item.optJSONArray("artists")
            val artist = if (artists != null && artists.length() > 0) {
                artists.optJSONObject(0)?.optString("name", "") ?: ""
            } else ""

            return ChatboxViewModel.SpotifyNowPlaying(
                isPlaying = isPlaying,
                artist = artist,
                track = track,
                progressMs = progressMs,
                durationMs = durationMs
            )
        }
    }

    private suspend fun ensureValidAccessToken(): String? {
        val access = repo.spotifyAccessToken.first().trim()
        if (access.isBlank()) return null

        val expiresAt = repo.spotifyExpiresAtEpochSec.first()
        val now = System.currentTimeMillis() / 1000L
        return if (expiresAt > now) access else {
            refreshToken()
            repo.spotifyAccessToken.first().trim().ifBlank { null }
        }
    }

    private suspend fun refreshToken() {
        val refresh = repo.spotifyRefreshToken.first().trim()
        val clientId = repo.spotifyClientId.first().trim()
        if (refresh.isBlank() || clientId.isBlank()) return

        val body = FormBody.Builder()
            .add("client_id", clientId)
            .add("grant_type", "refresh_token")
            .add("refresh_token", refresh)
            .build()

        val req = Request.Builder()
            .url("https://accounts.spotify.com/api/token")
            .post(body)
            .build()

        http.newCall(req).execute().use { res ->
            if (!res.isSuccessful) return
            val json = JSONObject(res.body?.string().orEmpty())
            val access = json.optString("access_token", "")
            val expiresIn = max(1, json.optInt("expires_in", 3600))
            val expiresAt = (System.currentTimeMillis() / 1000L) + expiresIn.toLong() - 30L

            if (access.isNotBlank()) repo.saveSpotifyAccessToken(access)
            repo.saveSpotifyExpiresAtEpochSec(expiresAt)
        }
    }
}
