package com.houneteam.airodump.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val DarkColors = darkColorScheme(
    primary = md_theme_dark_primary,
    onPrimary = md_theme_dark_onPrimary,
    background = md_theme_dark_background,
    onBackground = md_theme_dark_onBackground
)

@Composable
fun AirodumpTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColors,
        typography = TypographyDefaults,
        shapes = ShapesDefaults,
        content = content
    )
}
