package com.localdex.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Primary = Color(0xFF2962FF)
private val Secondary = Color(0xFF00B8D4)

private val LightColors = lightColorScheme(primary = Primary, secondary = Secondary)
private val DarkColors = darkColorScheme(primary = Color(0xFF82A4FF), secondary = Secondary)

@Composable
fun LocalDexTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        content = content,
    )
}
