package com.ygocardscanner.data.catalog.yugioh
import com.ygocardscanner.data.catalog.universal.CatalogPayload
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

/** Pure parser for the small bundled development catalog. */
object DevelopmentCatalogParser {
    private val json = Json {
        ignoreUnknownKeys = false
        explicitNulls = false
    }

    fun parse(rawJson: String): CatalogPayload =
        json.decodeFromString<CatalogPayload>(rawJson).also(::validatePayload)

    private fun validatePayload(payload: CatalogPayload) {
        require(payload.sourceId.isNotBlank()) { "Catalog source ID must not be blank." }
        require(payload.catalogRevision.isNotBlank()) { "Catalog revision must not be blank." }
        require(payload.cards.isNotEmpty()) { "Catalog must contain at least one card." }

        val cardIds = mutableSetOf<String>()
        val printingIds = mutableSetOf<String>()
        payload.cards.forEach { card ->
            require(card.providerCardId.isNotBlank()) { "A catalog card is missing its provider ID." }
            require(cardIds.add(card.providerCardId)) {
                "Duplicate provider card ID: ${card.providerCardId}"
            }
            require(card.canonicalName.isNotBlank()) {
                "Card ${card.providerCardId} is missing a canonical name."
            }
            require(card.texts.isNotEmpty()) {
                "Card ${card.providerCardId} must contain at least one localized text."
            }
            card.texts.forEach { text ->
                require(text.languageCode.isNotBlank()) {
                    "Card ${card.providerCardId} contains a text without a language."
                }
                require(text.name.isNotBlank()) {
                    "Card ${card.providerCardId} contains a text without a name."
                }
            }
            card.printings.forEach { printing ->
                require(printing.providerPrintingId.isNotBlank()) {
                    "Card ${card.providerCardId} contains a printing without a provider ID."
                }
                require(printingIds.add(printing.providerPrintingId)) {
                    "Duplicate provider printing ID: ${printing.providerPrintingId}"
                }
                require(printing.setCode.isNotBlank()) {
                    "Printing ${printing.providerPrintingId} is missing a set code."
                }
                require(printing.languageCode.isNotBlank()) {
                    "Printing ${printing.providerPrintingId} is missing a language."
                }
                require(printing.editionCode.isNotBlank()) {
                    "Printing ${printing.providerPrintingId} is missing an edition."
                }
            }
        }
    }
}
