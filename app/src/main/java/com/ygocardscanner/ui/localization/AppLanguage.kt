package com.ygocardscanner.ui.localization

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import com.ygocardscanner.model.CardLanguage

val LocalAppLanguage = staticCompositionLocalOf { CardLanguage.ENGLISH }

@Composable
fun appText(english: String, german: String): String =
    if (LocalAppLanguage.current == CardLanguage.GERMAN) german else english