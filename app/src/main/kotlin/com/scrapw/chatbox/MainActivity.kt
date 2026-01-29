package com.scrapw.chatbox

import android.content.Context
import android.content.ContextWrapper
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.scrapw.chatbox.overlay.OverlayDaemon
import com.scrapw.chatbox.ui.theme.ChatboxTheme
import com.scrapw.chatbox.ui.theme.ThemeMode

fun Context.getActivity(): ComponentActivity? = when (this) {
    is ComponentActivity -> this
    is ContextWrapper -> baseContext.getActivity()
    else -> null
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)

        // Get repository from Application (no manual wiring elsewhere)
        val repo = (application as ChatboxApplication).userPreferencesRepository

        setContent {
            val themeModeString by repo.themeMode.collectAsState(initial = "System")
            val themeMode = runCatching { ThemeMode.valueOf(themeModeString) }
                .getOrDefault(ThemeMode.System)

            // Turn dynamicColor off for a consistent “space” design.
            // If you want Android 12+ dynamic colors later, set dynamicColor = true.
            ChatboxTheme(themeMode = themeMode, dynamicColor = false) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    ChatboxApp()
                }
            }

            // Keep existing overlay daemon call
            OverlayDaemon(this)
        }
    }
}
