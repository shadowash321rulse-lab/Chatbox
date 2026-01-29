package com.scrapw.chatbox.ui

import android.net.Uri
import android.util.Base64
import android.util.Log
import androidx.annotation.MainThread
import androidx.compose.runtime.*
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.scrapw.chatbox.ChatboxApplication
import com.scrapw.chatbox.UpdateInfo
import com.scrapw.chatbox.UpdateStatus
import com.scrapw.chatbox.checkUpdate
import com.scrapw.chatbox.data.UserPreferencesRepository
import com.scrapw.chatbox.osc.ChatboxOSC
import com.scrapw.chatbox.ui.mainScreen.ConversationUiState
import com.scrapw.chatbox.ui.mainScreen.Message
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Instant
import kotlin.math.roundToInt

class ChatboxViewModel(
    private val userPreferencesRepository: UserPreferencesRepository
) : ViewModel() {

    companion object {
        private lateinit var instance: ChatboxViewModel

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val application = (this[APPLICATION_KEY] as ChatboxApplication)
                instance = ChatboxViewModel(application.userPreferencesRepository)
                Log.d("ChatboxViewModel", "Init")
                instance
            }
        }

        @MainThread
        fun getInstance(): ChatboxViewModel {
            if (!isInstanceInitialized()) throw Exception("ChatboxViewModel is not initialized!")
            return instance
        }

        fun isInstanceInitialized(): Boolean = ::instance.isInitialized

        const val SPOTIFY_REDIRECT_URI = "chatbox://spotify-callback"
    }

    override fun onCleared() {
        stopCycle()
        super.onCleared()
    }

    // ============================
    // Core app state
    // ============================
    val conversationUiState = ConversationUiState()

    private val storedIpState: StateFlow<String> =
        userPreferencesRepository.ipAddress
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = ""
            )

    private val userInputIpState = MutableStateFlow("")
    var ipAddressLocked by mutableStateOf(false)

    private val ipFlow = listOf(storedIpState, userInputIpState).asFlow().flattenMerge()

    val messengerUiState: StateFlow<MessengerUiState> = combine(
        ipFlow,
        userPreferencesRepository.isRealtimeMsg,
        userPreferencesRepository.isTriggerSfx,
        userPreferencesRepository.isTypingIndicator,
        userPreferencesRepository.isSendImmediately
    ) { ipAddress, isRealtimeMsg, isTriggerSFX, isTypingIndicator, isSendImmediately ->
        MessengerUiState(
            ipAddress = ipAddress,
            isRealtimeMsg = isRealtimeMsg,
            isTriggerSFX = isTriggerSFX,
            isTypingIndicator = isTypingIndicator,
            isSendImmediately = isSendImmediately
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = MessengerUiState()
    )

    private val remoteChatboxOSC = ChatboxOSC(
        ipAddress = runBlocking { userPreferencesRepository.ipAddress.first() },
        port = 9000
    )

    private val localChatboxOSC = ChatboxOSC(
        ipAddress = "localhost",
        port = 9000
    )

    val messageText = mutableStateOf(TextFieldValue(""))

    fun onIpAddressChange(ip: String) {
        userInputIpState.value = ip
    }

    fun ipAddressApply(address: String) {
        CoroutineScope(Dispatchers.IO).launch {
            withContext(Dispatchers.IO) {
                remoteChatboxOSC.ipAddress = address
                isAddressResolvable.value = remoteChatboxOSC.addressResolvable
                if (!isAddressResolvable.value) ipAddressLocked = false
            }
        }
        viewModelScope.launch { userPreferencesRepository.saveIpAddress(address) }
    }

    fun portApply(port: Int) {
        remoteChatboxOSC.port = port
        viewModelScope.launch { userPreferencesRepository.savePort(port) }
    }

    val isAddressResolvable = mutableStateOf(true)

    // KEEP signature: overlay expects (TextFieldValue, Boolean)
    fun onMessageTextChange(message: TextFieldValue, local: Boolean = false) {
        val osc = if (!local) remoteChatboxOSC else localChatboxOSC
        messageText.value = message

        if (messengerUiState.value.isRealtimeMsg) {
            osc.sendRealtimeMessage(message.text)
        } else if (messengerUiState.value.isTypingIndicator) {
            osc.typing = message.text.isNotEmpty()
        }
    }

    fun sendMessage(local: Boolean = false) {
        val osc = if (!local) remoteChatboxOSC else localChatboxOSC
        osc.sendMessage(
            messageText.value.text,
            messengerUiState.value.isSendImmediately,
            messengerUiState.value.isTriggerSFX
        )
        osc.typing = false

        conversationUiState.addMessage(
            Message(messageText.value.text, false, Instant.now())
        )

        messageText.value = TextFieldValue("", TextRange.Zero)
    }

    fun stashMessage(local: Boolean = false) {
        val osc = if (!local) remoteChatboxOSC else localChatboxOSC
        osc.typing = false

        conversationUiState.addMessage(
            Message(messageText.value.text, true, Instant.now())
        )

        messageText.value = TextFieldValue("", TextRange.Zero)
    }

    fun onRealtimeMsgChanged(isChecked: Boolean) =
        viewModelScope.launch { userPreferencesRepository.saveIsRealtimeMsg(isChecked) }

    fun onTriggerSfxChanged(isChecked: Boolean) =
        viewModelScope.launch { userPreferencesRepository.saveIsTriggerSFX(isChecked) }

    fun onTypingIndicatorChanged(isChecked: Boolean) =
        viewModelScope.launch { userPreferencesRepository.saveTypingIndicator(isChecked) }

    fun onSendImmediatelyChanged(isChecked: Boolean) =
        viewModelScope.launch { userPreferencesRepository.saveIsSendImmediately(isChecked) }

    // ============================
    // Update checker
    // ============================
    private var updateChecked = false
    var updateInfo by mutableStateOf(UpdateInfo(UpdateStatus.NOT_CHECKED))

    fun checkUpdate() {
        if (updateChecked) return
        updateChecked = true
        viewModelScope.launch(Dispatchers.Main) {
            updateInfo = checkUpdate("ScrapW", "Chatbox")
        }
    }

    // ============================
    // Cycle
    // ============================
    var cycleEnabled by mutableStateOf(false)
    var cycleMessages by mutableStateOf("")
    var cycleIntervalSeconds by mutableStateOf(3)
    private var cycleJob: Job? = null

    fun startCycle(local: Boolean = false) {
        val msgs = cycleMessages.lines().map { it.trim() }.filter { it.isNotEmpty() }
        if (!cycleEnabled || msgs.isEmpty()) return

        cycleJob?.cancel()
        cycleJob = viewModelScope.launch {
            var i = 0
            while (cycleEnabled) {
                val outgoing = buildOutgoingMessage(msgs[i])
                messageText.value = TextFieldValue(outgoing, TextRange(outgoing.length))
                sendMessage(local)
                i = (i + 1) % msgs.size
                delay(cycleIntervalSeconds.coerceAtLeast(1).toLong() * 1000L)
            }
        }
    }

    fun stopCycle() {
        cycleJob?.cancel()
        cycleJob = null
    }

    // ============================
    // AFK
    // ============================
    var afkEnabled by mutableStateOf(false)
    var afkMessage by mutableStateOf("AFK 🌙 back soon")

    fun sendAfkNow(local: Boolean = false) {
        if (!afkEnabled) return
        val text = afkMessage.trim().ifEmpty { "AFK" }
        val outgoing = buildOutgoingMessage(text)
        messageText.value = TextFieldValue(outgoing, TextRange(outgoing.length))
        sendMessage(local)
    }

    // ============================
    // Simple text presets (kept)
    // ============================
    data class TextPreset(val name: String, val intervalSeconds: Int, val messages: String)

    private val builtInPresets = listOf(
        TextPreset("Cute intro 💕", 3, "hi hi 💗\nfollow me on vrchat"),
        TextPreset("Chill ✨", 5, "vibing ✨\nbe kind 🤍"),
        TextPreset("Minimal", 6, "…")
    )

    private val customPresets = mutableStateListOf<TextPreset>()
    val presets: List<TextPreset> get() = builtInPresets + customPresets

    var selectedPresetName by mutableStateOf(builtInPresets.first().name)

    fun applyPresetByName(name: String) {
        val p = presets.firstOrNull { it.name == name } ?: return
        selectedPresetName = p.name
        cycleEnabled = true
        cycleIntervalSeconds = p.intervalSeconds
        cycleMessages = p.messages
    }

    fun saveCurrentAsPreset(name: String) {
        val clean = name.trim()
        if (clean.isEmpty()) return
        val p = TextPreset(clean, cycleIntervalSeconds.coerceAtLeast(1), cycleMessages)
        val idx = customPresets.indexOfFirst { it.name == clean }
        if (idx >= 0) customPresets[idx] = p else customPresets.add(p)
        selectedPresetName = clean
    }

    fun deleteCustomPreset(name: String) {
        val idx = customPresets.indexOfFirst { it.name == name }
        if (idx >= 0) customPresets.removeAt(idx)
        if (selectedPresetName == name) selectedPresetName = builtInPresets.first().name
    }

    // ============================
    // Spotify (NO setter name clash)
    // ============================
    var spotifyEnabled by mutableStateOf(false)
    var spotifyClientId by mutableStateOf("")
    var spotifyPreset by mutableStateOf(1)

    private var spotifyAccessToken: String = ""
    private var spotifyRefreshToken: String = ""
    private var spotifyExpiresAtEpochSec: Long = 0L

    data class SpotifyNowPlaying(
        val isPlaying: Boolean,
        val artist: String,
        val track: String,
        val progressMs: Long,
        val durationMs: Long
    )

    var spotifyNowPlaying by mutableStateOf<SpotifyNowPlaying?>(null)
        private set

    var spotifyStatus by mutableStateOf("")
        private set

    // ✅ renamed to avoid JVM clash with property setter
    fun updateSpotifyEnabled(enabled: Boolean) {
        spotifyEnabled = enabled
        viewModelScope.launch { userPreferencesRepository.saveSpotifyEnabled(enabled) }
    }

    // ✅ renamed to avoid JVM clash with property setter
    fun updateSpotifyPreset(preset: Int) {
        spotifyPreset = preset.coerceIn(1, 5)
        viewModelScope.launch { userPreferencesRepository.saveSpotifyPreset(spotifyPreset) }
    }

    // ✅ renamed to avoid JVM clash with property setter
    fun updateSpotifyClientId(id: String) {
        spotifyClientId = id.trim()
        viewModelScope.launch { userPreferencesRepository.saveSpotifyClientId(spotifyClientId) }
    }

    fun disconnectSpotify() {
        viewModelScope.launch {
            userPreferencesRepository.clearSpotifyTokens()
            spotifyAccessToken = ""
            spotifyRefreshToken = ""
            spotifyExpiresAtEpochSec = 0L
            spotifyNowPlaying = null
            spotifyStatus = "Spotify disconnected"
        }
    }

    suspend fun buildSpotifyAuthUrl(): String = withContext(Dispatchers.IO) {
        if (spotifyClientId.isBlank()) throw IllegalStateException("Spotify Client ID is empty")

        val state = randomUrlSafe(16)
        val verifier = randomUrlSafe(64)
        val challenge = codeChallengeS256(verifier)

        userPreferencesRepository.saveSpotifyState(state)
        userPreferencesRepository.saveSpotifyCodeVerifier(verifier)

        val scopes = listOf(
            "user-read-currently-playing",
            "user-read-playback-state"
        ).joinToString(" ")

        "https://accounts.spotify.com/authorize" +
            "?client_id=" + enc(spotifyClientId) +
            "&response_type=code" +
            "&redirect_uri=" + enc(SPOTIFY_REDIRECT_URI) +
            "&code_challenge_method=S256" +
            "&code_challenge=" + enc(challenge) +
            "&state=" + enc(state) +
            "&scope=" + enc(scopes)
    }

    suspend fun handleSpotifyRedirectUri(uriString: String) {
        val uri = Uri.parse(uriString)
        val code = uri.getQueryParameter("code") ?: throw IllegalStateException("Missing code")
        val returnedState = uri.getQueryParameter("state") ?: throw IllegalStateException("Missing state")

        val expectedState = userPreferencesRepository.spotifyState.first()
        if (expectedState.isNotBlank() && expectedState != returnedState) {
            throw IllegalStateException("State mismatch")
        }

        val verifier = userPreferencesRepository.spotifyCodeVerifier.first()
        if (verifier.isBlank()) throw IllegalStateException("Missing PKCE code_verifier")

        val body = form(
            "client_id" to spotifyClientId,
            "grant_type" to "authorization_code",
            "code" to code,
            "redirect_uri" to SPOTIFY_REDIRECT_URI,
            "code_verifier" to verifier
        )

        val json = postForm("https://accounts.spotify.com/api/token", body)
        val access = json.getString("access_token")
        val refresh = json.optString("refresh_token", "")
        val expiresIn = json.getLong("expires_in")

        val expiresAt = Instant.now().epochSecond + expiresIn - 15
        saveSpotifyTokens(access, refresh, expiresAt)

        userPreferencesRepository.saveSpotifyCodeVerifier("")
        userPreferencesRepository.saveSpotifyState("")

        spotifyStatus = "Spotify connected"
    }

    suspend fun refreshSpotifyNowPlaying() {
        if (!spotifyEnabled) {
            spotifyNowPlaying = null
            return
        }
        if (spotifyClientId.isBlank()) {
            spotifyStatus = "Set Spotify Client ID"
            spotifyNowPlaying = null
            return
        }

        val token = ensureValidAccessToken()
        if (token.isBlank()) {
            spotifyStatus = "Not connected"
            spotifyNowPlaying = null
            return
        }

        spotifyNowPlaying = getNowPlaying(token)
    }

    private suspend fun ensureValidAccessToken(): String = withContext(Dispatchers.IO) {
        val now = Instant.now().epochSecond
        if (spotifyAccessToken.isNotBlank() && spotifyExpiresAtEpochSec > now) return@withContext spotifyAccessToken
        if (spotifyRefreshToken.isBlank()) return@withContext ""

        val body = form(
            "client_id" to spotifyClientId,
            "grant_type" to "refresh_token",
            "refresh_token" to spotifyRefreshToken
        )

        return@withContext try {
            val json = postForm("https://accounts.spotify.com/api/token", body)
            val newAccess = json.getString("access_token")
            val expiresIn = json.getLong("expires_in")
            val newExpiresAt = Instant.now().epochSecond + expiresIn - 15
            saveSpotifyTokens(newAccess, spotifyRefreshToken, newExpiresAt)
            newAccess
        } catch (_: Throwable) {
            ""
        }
    }

    private suspend fun saveSpotifyTokens(access: String, refresh: String, expiresAt: Long) {
        spotifyAccessToken = access
        if (refresh.isNotBlank()) spotifyRefreshToken = refresh
        spotifyExpiresAtEpochSec = expiresAt

        userPreferencesRepository.saveSpotifyAccessToken(access)
        if (refresh.isNotBlank()) userPreferencesRepository.saveSpotifyRefreshToken(refresh)
        userPreferencesRepository.saveSpotifyExpiresAtEpochSec(expiresAt)
    }

    private suspend fun getNowPlaying(accessToken: String): SpotifyNowPlaying? = withContext(Dispatchers.IO) {
        val url = URL("https://api.spotify.com/v1/me/player/currently-playing")
        val conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            setRequestProperty("Authorization", "Bearer $accessToken")
        }
        val code = conn.responseCode
        if (code == 204) return@withContext null
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

        SpotifyNowPlaying(isPlaying, artist, track, progressMs, durationMs)
    }

    // ============================
    // Output composition
    // ============================
    private fun buildOutgoingMessage(cycleLine: String): String {
        val cycle = cycleLine.trim()
        val spotifyBlock = buildSpotifyBlockOrEmpty()

        val lines = mutableListOf<String>()
        if (cycle.isNotEmpty()) lines.add(cycle)
        if (spotifyBlock.isNotEmpty()) lines.addAll(spotifyBlock.split("\n"))

        val joined = lines.joinToString("\n")
        return if (joined.length <= 144) joined else joined.take(144)
    }

    fun buildSpotifyBlockOrEmpty(): String {
        if (!spotifyEnabled) return ""
        val np = spotifyNowPlaying ?: return ""

        val progressLine = formatProgressLine(np.progressMs, np.durationMs, spotifyPreset)
        val budgetForTitle = (144 - progressLine.length - 1).coerceAtLeast(0)
        val title = clampTitleToBudget(np.artist, np.track, budgetForTitle)

        val combined = "$title\n$progressLine"
        return if (combined.length <= 144) combined else combined.take(144)
    }

    private fun clampTitleToBudget(artist: String, track: String, budget: Int): String {
        if (budget <= 0) return ""
        val full = "🎧 $artist — $track"
        if (full.length <= budget) return full

        val noArtist = "🎧 $track"
        if (noArtist.length <= budget) return noArtist

        val prefix = "🎧 "
        val available = (budget - prefix.length).coerceAtLeast(0)
        val t = ellipsize(track, available)
        return prefix + t
    }

    private fun ellipsize(text: String, maxLen: Int): String {
        if (maxLen <= 0) return ""
        if (text.length <= maxLen) return text
        if (maxLen == 1) return "…"
        return text.take(maxLen - 1) + "…"
    }

    private fun formatProgressLine(progressMs: Long, durationMs: Long, preset: Int): String {
        val dur = durationMs.coerceAtLeast(1L)
        val prog = progressMs.coerceIn(0L, dur)
        val leftTime = formatTime(prog)
        val rightTime = formatTime(dur)

        return when (preset.coerceIn(1, 5)) {
            1 -> {
                val inner = buildBar(10, prog.toFloat() / dur.toFloat(), "━", "─", "◉")
                "♡$inner♡ $leftTime / $rightTime"
            }
            2 -> {
                val bar = buildBar(12, prog.toFloat() / dur.toFloat(), "━", "─", "◉")
                "$bar $leftTime/$rightTime"
            }
            3 -> {
                val bar = buildBar(9, prog.toFloat() / dur.toFloat(), "⟡", "⟡", "◉")
                "$bar $leftTime / $rightTime"
            }
            4 -> {
                val wave = buildWaveBar(prog.toFloat() / dur.toFloat())
                "$wave $leftTime / $rightTime"
            }
            else -> {
                val bar = buildBar(11, prog.toFloat() / dur.toFloat(), "▣", "▢", "◉")
                "$bar $leftTime / $rightTime"
            }
        }
    }

    private fun buildBar(width: Int, progress: Float, filled: String, empty: String, marker: String): String {
        val w = width.coerceAtLeast(2)
        val idx = (progress.coerceIn(0f, 1f) * (w - 1)).roundToInt()
        val sb = StringBuilder()
        for (i in 0 until w) {
            sb.append(
                when {
                    i == idx -> marker
                    i < idx -> filled
                    else -> empty
                }
            )
        }
        return sb.toString()
    }

    private fun buildWaveBar(progress: Float): String {
        val wave = listOf("▁", "▂", "▃", "▄", "▅", "▅", "▄", "▃", "▂", "▁", "▁")
        val w = wave.size
        val idx = (progress.coerceIn(0f, 1f) * (w - 1)).roundToInt()
        val sb = StringBuilder()
        for (i in 0 until w) sb.append(if (i == idx) "●" else wave[i])
        return sb.toString()
    }

    private fun formatTime(ms: Long): String {
        val totalSec = (ms / 1000L).coerceAtLeast(0L)
        val m = totalSec / 60L
        val s = totalSec % 60L
        return if (s < 10) "${m}:0${s}" else "${m}:${s}"
    }

    // ============================
    // OAuth helpers
    // ============================
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
        if (code !in 200..299) error("HTTP $code: $text")
        return JSONObject(text)
    }

    private fun randomUrlSafe(bytes: Int): String {
        val b = ByteArray(bytes)
        SecureRandom().nextBytes(b)
        return Base64.encodeToString(b, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
    }

    private fun codeChallengeS256(verifier: String): String {
        val md = MessageDigest.getInstance("SHA-256")
        val digest = md.digest(verifier.toByteArray(Charsets.US_ASCII))
        return Base64.encodeToString(digest, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
    }

    // ============================
    // Persistence wiring
    // ============================
    init {
        viewModelScope.launch {
            cycleEnabled = userPreferencesRepository.cycleEnabled.first()
            cycleMessages = userPreferencesRepository.cycleMessages.first()
            cycleIntervalSeconds = userPreferencesRepository.cycleInterval.first()

            afkEnabled = userPreferencesRepository.afkEnabled.first()
            afkMessage = userPreferencesRepository.afkMessage.first()

            selectedPresetName = userPreferencesRepository.selectedPreset.first().ifBlank { builtInPresets.first().name }

            val json = userPreferencesRepository.presetsJson.first()
            customPresets.clear()
            customPresets.addAll(decodePresetsJson(json))

            spotifyEnabled = userPreferencesRepository.spotifyEnabled.first()
            spotifyClientId = userPreferencesRepository.spotifyClientId.first()
            spotifyPreset = userPreferencesRepository.spotifyPreset.first().coerceIn(1, 5)

            spotifyAccessToken = userPreferencesRepository.spotifyAccessToken.first()
            spotifyRefreshToken = userPreferencesRepository.spotifyRefreshToken.first()
            spotifyExpiresAtEpochSec = userPreferencesRepository.spotifyExpiresAtEpochSec.first()
        }

        viewModelScope.launch { snapshotFlow { cycleEnabled }.collect { userPreferencesRepository.saveCycleEnabled(it) } }
        viewModelScope.launch { snapshotFlow { cycleMessages }.collect { userPreferencesRepository.saveCycleMessages(it) } }
        viewModelScope.launch { snapshotFlow { cycleIntervalSeconds }.collect { userPreferencesRepository.saveCycleInterval(it) } }
        viewModelScope.launch { snapshotFlow { afkEnabled }.collect { userPreferencesRepository.saveAfkEnabled(it) } }
        viewModelScope.launch { snapshotFlow { afkMessage }.collect { userPreferencesRepository.saveAfkMessage(it) } }
        viewModelScope.launch { snapshotFlow { selectedPresetName }.collect { userPreferencesRepository.saveSelectedPreset(it) } }
        viewModelScope.launch {
            snapshotFlow { customPresets.toList() }.collect {
                userPreferencesRepository.savePresetsJson(encodePresetsJson(it))
            }
        }
        // Spotify vars persist via direct flow saves already in updateSpotify* calls,
        // but also keep them safe if UI assigns the vars directly:
        viewModelScope.launch { snapshotFlow { spotifyEnabled }.collect { userPreferencesRepository.saveSpotifyEnabled(it) } }
        viewModelScope.launch { snapshotFlow { spotifyClientId }.collect { userPreferencesRepository.saveSpotifyClientId(it) } }
        viewModelScope.launch { snapshotFlow { spotifyPreset }.collect { userPreferencesRepository.saveSpotifyPreset(it) } }
    }

    private fun encodePresetsJson(list: List<TextPreset>): String {
        val arr = JSONArray()
        list.forEach {
            arr.put(JSONObject().apply {
                put("name", it.name)
                put("interval", it.intervalSeconds)
                put("messages", it.messages)
            })
        }
        return arr.toString()
    }

    private fun decodePresetsJson(json: String): List<TextPreset> {
        if (json.isBlank()) return emptyList()
        return try {
            val arr = JSONArray(json)
            List(arr.length()) { idx ->
                val o = arr.getJSONObject(idx)
                TextPreset(
                    name = o.getString("name"),
                    intervalSeconds = o.getInt("interval"),
                    messages = o.getString("messages")
                )
            }
        } catch (_: Throwable) {
            emptyList()
        }
    }
}

data class MessengerUiState(
    val ipAddress: String = "127.0.0.1",
    val isRealtimeMsg: Boolean = false,
    val isTriggerSFX: Boolean = true,
    val isTypingIndicator: Boolean = true,
    val isSendImmediately: Boolean = true
)

