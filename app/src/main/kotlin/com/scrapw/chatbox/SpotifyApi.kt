package com.scrapw.chatbox.spotify

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class SpotifyNowPlaying(
    val isPlaying: Boolean,
    val artist: String,
    val track: String,
    val progressMs: Long,
    val durationMs: Long
)

object SpotifyApi {
    private const val NOW_PLAYING_URL = "https://api.spotify.com/v1/me/player/currently-playing"

    suspend fun getNowPlaying(accessToken: String): SpotifyNowPlaying? = withContext(Dispatchers.IO) {
        val url = URL(NOW_PLAYING_URL)
        val conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            setRequestProperty("Authorization", "Bearer $accessToken")
        }

        val code = conn.responseCode
        if (code == 204) return@withContext null // no content
        val stream = if (code in 200..299) conn.inputStream else conn.errorStream
        val text = stream.bufferedReader().use { it.readText() }
        if (code !in 200..299) return@withContext null

        val json = JSONObject(text)
        val isPlaying = json.optBoolean("is_playing", false)
        val progressMs = json.optLong("progress_ms", 0L)

        val item = json.optJSONObject("item") ?: return@withContext null
        val track = item.optString("name", "")
        val durationMs = item.optLong("duration_ms", 0L)

        val artistsArr = item.optJSONArray("artists")
        val artist = if (artistsArr != null && artistsArr.length() > 0) {
            artistsArr.getJSONObject(0).optString("name", "")
        } else ""

        return@withContext SpotifyNowPlaying(
            isPlaying = isPlaying,
            artist = artist,
            track = track,
            progressMs = progressMs,
            durationMs = durationMs
        )
    }
}

