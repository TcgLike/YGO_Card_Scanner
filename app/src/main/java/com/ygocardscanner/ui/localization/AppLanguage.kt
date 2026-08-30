package com.ygocardscanner.ui.localization

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import com.ygocardscanner.model.CardLanguage

val LocalAppLanguage = staticCompositionLocalOf { CardLanguage.ENGLISH }

/** A visible UI string with its English and German variants. */
data class UiTextToken(
    val english: String,
    val german: String,
) {
    fun resolve(language: CardLanguage): String =
        if (language == CardLanguage.GERMAN) german else english
}

@Composable
fun appText(token: UiTextToken): String = token.resolve(LocalAppLanguage.current)

/** Compatibility overload for localized values that are only used once. */
@Composable
fun appText(english: String, german: String): String = appText(UiTextToken(english, german))

object UiText {
    val AppName = UiTextToken("Yu-Gi-Oh!", "Yu-Gi-Oh!")
    val Add = UiTextToken("Add", "Hinzufügen")
    val CanBuildIt = UiTextToken("Can I build it?", "Kann ich es bauen?")
    val ScanCard = UiTextToken("Scan a card", "Karte scannen")
    val ImportDeck = UiTextToken("Import a deck", "Deck importieren")
    val AddUnknownPrinting = UiTextToken("Add an unknown printing manually", "Unbekannten Druck manuell hinzufügen")
}
