package dev.splitwise.offline.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// A simple teal-led palette echoing Splitwise's friendly green, tuned for M3.
private val LightColors = lightColorScheme(
    primary = Color(0xFF1B7A5A),
    onPrimary = Color.White,
    secondary = Color(0xFF2D9B7A),
    background = Color(0xFFF7F9F8),
    surface = Color.White,
)
private val DarkColors = darkColorScheme(
    primary = Color(0xFF4FBF9A),
    onPrimary = Color(0xFF00301F),
    secondary = Color(0xFF6FD4B0),
    background = Color(0xFF101814),
    surface = Color(0xFF172420),
)

@Composable
fun SplitwiseOfflineTheme(
    darkTheme: Boolean = androidx.compose.foundation.isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = androidx.compose.material3.Typography(),
        content = content,
    )
}