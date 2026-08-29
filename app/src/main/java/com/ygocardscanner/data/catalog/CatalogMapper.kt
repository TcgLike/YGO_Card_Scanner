package com.ygocardscanner.data.catalog

import com.ygocardscanner.data.catalog.network.CatalogPayload
import com.ygocardscanner.data.local.entity.Card
import com.ygocardscanner.data.local.entity.CardArtwork
import com.ygocardscanner.data.local.entity.CardText
import com.ygocardscanner.data.local.entity.CatalogMetadata
import com.ygocardscanner.data.local.entity.Printing
import com.ygocardscanner.data.util.CatalogNormalizers

/** Database rows produced from one complete, validated catalog payload. */
data class MappedCatalog(
    val cards: List<Card>,
    val cardTexts: List<CardText>,
    val artworks: List<CardArtwork>,
    val printings: List<Printing>,
    val metadata: CatalogMetadata,
)

/**
 * Pure mapping boundary between transport DTOs and Room entities.
 *
 * Provider IDs are kept as their own fields and converted into deterministic local primary keys.
 * That makes catalog replacement idempotent while preserving inventory foreign keys.
 */
object CatalogMapper {
    fun map(
        payload: CatalogPayload,
        updatedAtEpochMillis: Long = System.currentTimeMillis(),
    ): MappedCatalog {
        val sourceId = payload.sourceId.required("Catalog source ID")
        val revision = payload.catalogRevision.required("Catalog revision")
        require(payload.cards.isNotEmpty()) { "Catalog must contain at least one card." }

        val cards = ArrayList<Card>(payload.cards.size)
        val cardTexts = mutableListOf<CardText>()
        val artworks = mutableListOf<CardArtwork>()
        val printings = mutableListOf<Printing>()
        val providerCardIds = mutableSetOf<String>()
        val providerPrintingIds = mutableSetOf<String>()
        val textKeys = mutableSetOf<Pair<String, String>>()

        payload.cards.forEach { networkCard ->
            val providerCardId = networkCard.providerCardId.required("Provider card ID")
            require(providerCardIds.add(providerCardId)) {
                "Duplicate provider card ID '$providerCardId' for source '$sourceId'."
            }
            val cardId = stableId(sourceId, "card", providerCardId)
            val canonicalName = networkCard.canonicalName.required("Canonical name")

            cards += Card(
                cardId = cardId,
                sourceId = sourceId,
                providerCardId = providerCardId,
                passcode = networkCard.passcode?.trim()?.takeIf(String::isNotEmpty),
                canonicalName = canonicalName,
                isActive = true,
                catalogRevision = revision,
                updatedAtEpochMillis = updatedAtEpochMillis,
            )

            require(networkCard.texts.isNotEmpty()) {
                "Card '$providerCardId' must contain at least one localized text."
            }
            networkCard.texts.forEach { networkText ->
                val languageCode = networkText.languageCode.required("Card text language")
                require(textKeys.add(cardId to languageCode)) {
                    "Duplicate '$languageCode' text for card '$providerCardId'."
                }
                val name = networkText.name.required("Card text name")
                cardTexts += CardText(
                    cardId = cardId,
                    languageCode = languageCode,
                    name = name,
                    normalizedName = CatalogNormalizers.name(name),
                    description = networkText.description?.trim()?.takeIf(String::isNotEmpty),
                    isActive = true,
                    catalogRevision = revision,
                )
            }

            networkCard.artwork?.let { networkArtwork ->
                val providerArtworkId = networkArtwork.providerArtworkId.required("Provider artwork ID")
                val remoteUrl = networkArtwork.imageUrl.required("Artwork URL")
                artworks += CardArtwork(
                    // One selected English artwork per canonical card keeps cache IDs stable even
                    // if the provider changes the alternate-artwork identifier.
                    artworkId = stableId(sourceId, "artwork", providerCardId),
                    cardId = cardId,
                    sourceId = sourceId,
                    providerArtworkId = providerArtworkId,
                    remoteUrl = remoteUrl,
                    isActive = true,
                    catalogRevision = revision,
                    updatedAtEpochMillis = updatedAtEpochMillis,
                )
            }

            networkCard.printings.forEach { networkPrinting ->
                val providerPrintingId = networkPrinting.providerPrintingId.required("Provider printing ID")
                require(providerPrintingIds.add(providerPrintingId)) {
                    "Duplicate provider printing ID '$providerPrintingId' for source '$sourceId'."
                }
                val setCode = networkPrinting.setCode.required("Set code")
                val normalizedSetCode = requireNotNull(CatalogNormalizers.setCode(setCode)) {
                    "Set code '$setCode' cannot be normalized."
                }
                val languageCode = networkPrinting.languageCode.required("Printing language")
                val editionCode = networkPrinting.editionCode.required("Printing edition")

                printings += Printing(
                    printingId = stableId(sourceId, "printing", providerPrintingId),
                    cardId = cardId,
                    sourceId = sourceId,
                    providerPrintingId = providerPrintingId,
                    setCode = setCode,
                    normalizedSetCode = normalizedSetCode,
                    setName = networkPrinting.setName?.trim()?.takeIf(String::isNotEmpty),
                    languageCode = languageCode,
                    rarityCode = networkPrinting.rarityCode?.trim()?.takeIf(String::isNotEmpty),
                    editionCode = editionCode,
                    isActive = true,
                    catalogRevision = revision,
                    updatedAtEpochMillis = updatedAtEpochMillis,
                )
            }
        }

        return MappedCatalog(
            cards = cards,
            cardTexts = cardTexts,
            artworks = artworks,
            printings = printings,
            metadata = CatalogMetadata(
                sourceId = sourceId,
                catalogRevision = revision,
                contentHash = payload.contentHash?.trim()?.takeIf(String::isNotEmpty),
                updatedAtEpochMillis = updatedAtEpochMillis,
                lastError = null,
            ),
        )
    }

    private fun stableId(sourceId: String, recordType: String, providerId: String): String =
        "$sourceId:$recordType:$providerId"

    private fun String.required(label: String): String =
        trim().also { require(it.isNotEmpty()) { "$label must not be blank." } }
}