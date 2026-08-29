package com.ygocardscanner.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val LightColors = lightColorScheme(
    primary = YuGiBlue,
    onPrimary = androidx.compose.ui.graphics.Color.White,
    secondary = YuGiGold,
    onSecondary = Ink,
)

private val DarkColors = darkColorScheme(
    primary = YuGiGold,
    secondary = YuGiBlue,
)

@Composable
fun YgoCardScannerTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (androidx.compose.foundation.isSystemInDarkTheme()) DarkColors else LightColors,
        content = content,
    )
}
