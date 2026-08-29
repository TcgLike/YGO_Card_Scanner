package com.ygocardscanner.data.catalog

import com.ygocardscanner.data.catalog.network.CatalogCardDto
import com.ygocardscanner.data.catalog.network.CatalogCardTextDto
import com.ygocardscanner.data.catalog.network.CatalogPayload
import com.ygocardscanner.data.catalog.network.CatalogPrintingDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class CatalogMapperTest {
    @Test
    fun mapsStableProviderIdsAndNormalizedSetCode() {
        val mapped = CatalogMapper.map(samplePayload(), updatedAtEpochMillis = 42L)

        assertEquals("test:card:blue-eyes", mapped.cards.single().cardId)
        assertEquals("test:printing:lob-en-001", mapped.printings.single().printingId)
        assertEquals("LOB001", mapped.printings.single().normalizedSetCode)
        assertEquals(42L, mapped.metadata.updatedAtEpochMillis)
    }

    @Test
    fun rejectsDuplicateProviderCardIds() {
        val card = samplePayload().cards.single()
        val duplicate = samplePayload().copy(cards = listOf(card, card.copy(canonicalName = "Duplicate")))

        assertThrows(IllegalArgumentException::class.java) {
            CatalogMapper.map(duplicate, updatedAtEpochMillis = 42L)
        }
    }

    @Test
    fun mapsEnglishArtworkSeparatelyFromCardTextAndPrinting() {
        val originalCard = samplePayload().cards.single()
        val payload = samplePayload().copy(
            cards = listOf(
                originalCard.copy(
                    artwork = com.ygocardscanner.data.catalog.network.CatalogCardArtworkDto(
                        providerArtworkId = "89631139",
                        imageUrl = "https://images.ygoprodeck.com/images/cards/89631139.jpg",
                    ),
                ),
            ),
        )

        val artwork = CatalogMapper.map(payload, updatedAtEpochMillis = 42L).artworks.single()

        assertEquals("test:artwork:blue-eyes", artwork.artworkId)
        assertEquals("89631139", artwork.providerArtworkId)
        assertEquals("https://images.ygoprodeck.com/images/cards/89631139.jpg", artwork.remoteUrl)
    }
    private fun samplePayload() = CatalogPayload(
        sourceId = "test",
        catalogRevision = "1",
        contentHash = "hash",
        cards = listOf(
            CatalogCardDto(
                providerCardId = "blue-eyes",
                passcode = "89631139",
                canonicalName = "Blue-Eyes White Dragon",
                texts = listOf(
                    CatalogCardTextDto("en", "Blue-Eyes White Dragon"),
                    CatalogCardTextDto("de", "Blauäugiger w. Drache"),
                ),
                printings = listOf(
                    CatalogPrintingDto(
                        providerPrintingId = "lob-en-001",
                        setCode = "LOB-001",
                        setName = "Legend of Blue Eyes White Dragon",
                        languageCode = "en",
                        rarityCode = "ultra_rare",
                        editionCode = "first_edition",
                    ),
                ),
            ),
        ),
    )
}
