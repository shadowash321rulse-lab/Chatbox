package com.scrapw.chatbox.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.material3.Text

@Composable
fun MusicPresetPreview(
    previewTextProvider: (Float) -> String
) {
    val progress by rememberInfiniteTransition(label = "musicPreview")
        .animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(2200, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "progress"
        )

    Text(
        text = previewTextProvider(progress),
        fontFamily = FontFamily.Monospace
    )
}
