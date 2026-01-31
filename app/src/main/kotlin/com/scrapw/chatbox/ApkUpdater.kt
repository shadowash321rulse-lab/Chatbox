package com.scrapw.chatbox.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

object ApkUpdater {

    data class LatestRelease(
        val tag: String,
        val name: String,
        val body: String,
        val apkUrl: String
    )

    /**
     * Fetches GitHub "latest release" JSON and extracts:
     * - tag_name
     * - name
     * - body
     * - first asset ending with .apk -> browser_download_url
     */
    suspend fun fetchLatestRelease(owner: String, repo: String): LatestRelease? = withContext(Dispatchers.IO) {
        val api = "https://api.github.com/repos/$owner/$repo/releases/latest"
        val conn = (URL(api).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 12_000
            readTimeout = 12_000
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("User-Agent", "VRC-A")
        }

        try {
            val code = conn.responseCode
            if (code !in 200..299) return@withContext null
            val json = conn.inputStream.bufferedReader().readText()

            // Very small JSON extraction (no extra dependencies)
            fun pick(key: String): String {
                val pattern = "\"$key\"\\s*:\\s*\""
                val start = json.indexOf(pattern)
                if (start < 0) return ""
                val from = start + pattern.length
                val end = json.indexOf('"', from)
                if (end < 0) return ""
                return json.substring(from, end)
                    .replace("\\n", "\n")
                    .replace("\\r", "\r")
                    .replace("\\t", "\t")
                    .replace("\\\"", "\"")
            }

            val tag = pick("tag_name")
            val name = pick("name")

            // body can include quotes/newlines; use a safer extraction
            val bodyKey = "\"body\""
            val bodyIdx = json.indexOf(bodyKey)
            val body = if (bodyIdx >= 0) {
                val colon = json.indexOf(':', bodyIdx)
                val firstQuote = json.indexOf('"', colon + 1)
                if (firstQuote >= 0) {
                    var i = firstQuote + 1
                    val sb = StringBuilder()
                    var escaping = false
                    while (i < json.length) {
                        val c = json[i]
                        if (escaping) {
                            sb.append(
                                when (c) {
                                    'n' -> '\n'
                                    'r' -> '\r'
                                    't' -> '\t'
                                    '"' -> '"'
                                    '\\' -> '\\'
                                    else -> c
                                }
                            )
                            escaping = false
                        } else {
                            if (c == '\\') escaping = true
                            else if (c == '"') break
                            else sb.append(c)
                        }
                        i++
                    }
                    sb.toString()
                } else ""
            } else ""

            // Find first .apk asset browser_download_url
            val apkUrl = run {
                val needle = "\"browser_download_url\""
                var idx = 0
                var found = ""
                while (true) {
                    val p = json.indexOf(needle, idx)
                    if (p < 0) break
                    val colon = json.indexOf(':', p)
                    val q1 = json.indexOf('"', colon + 1)
                    val q2 = json.indexOf('"', q1 + 1)
                    if (q1 > 0 && q2 > q1) {
                        val url = json.substring(q1 + 1, q2)
                        if (url.endsWith(".apk", ignoreCase = true)) {
                            found = url
                            break
                        }
                    }
                    idx = p + needle.length
                }
                found
            }

            if (apkUrl.isBlank()) return@withContext null
            LatestRelease(tag = tag, name = name, body = body, apkUrl = apkUrl)
        } finally {
            conn.disconnect()
        }
    }

    suspend fun downloadApk(context: Context, url: String): File? = withContext(Dispatchers.IO) {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 20_000
            readTimeout = 20_000
            setRequestProperty("User-Agent", "VRC-A")
        }

        try {
            val code = conn.responseCode
            if (code !in 200..299) return@withContext null

            val outFile = File(context.cacheDir, "vrca_update.apk")
            conn.inputStream.use { input ->
                outFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            outFile
        } finally {
            conn.disconnect()
        }
    }

    /**
     * Launches the installer UI.
     * NOTE: Android forces user confirmation. That is normal and unavoidable.
     */
    fun promptInstall(context: Context, apkFile: File) {
        val uri: Uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            apkFile
        )

        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(intent)
    }

    fun openUnknownSourcesSettings(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                data = Uri.parse("package:${context.packageName}")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } else {
            val intent = Intent(Settings.ACTION_SECURITY_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        }
    }
}
