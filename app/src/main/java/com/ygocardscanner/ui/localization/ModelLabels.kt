package com.ygocardscanner.ui.localization

import androidx.compose.runtime.Composable
import com.ygocardscanner.model.CardCondition
import com.ygocardscanner.model.CardEdition
import com.ygocardscanner.model.CardLanguage

@Composable
fun CardCondition.localizedLabel(): String = when (this) {
    CardCondition.MINT -> appText("Mint", "Mint")
    CardCondition.NEAR_MINT -> appText("Near mint", "Nahezu mint")
    CardCondition.LIGHTLY_PLAYED -> appText("Lightly played", "Leicht bespielt")
    CardCondition.MODERATELY_PLAYED -> appText("Moderately played", "Mäßig bespielt")
    CardCondition.HEAVILY_PLAYED -> appText("Heavily played", "Stark bespielt")
    CardCondition.DAMAGED -> appText("Damaged", "Beschädigt")
}

@Composable
fun CardEdition.localizedLabel(): String = when (this) {
    CardEdition.FIRST_EDITION -> appText("1st Edition", "1. Auflage")
    CardEdition.UNLIMITED -> appText("Unlimited", "Unlimitiert")
    CardEdition.LIMITED -> appText("Limited", "Limitiert")
    CardEdition.UNKNOWN -> appText("Unknown", "Unbekannt")
}

@Composable
fun CardLanguage.localizedLabel(): String = when (this) {
    CardLanguage.ENGLISH -> appText("English", "Englisch")
    CardLanguage.GERMAN -> appText("German", "Deutsch")
}
