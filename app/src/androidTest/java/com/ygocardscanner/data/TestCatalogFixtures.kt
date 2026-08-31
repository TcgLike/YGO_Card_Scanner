package com.ygocardscanner.data

import com.ygocardscanner.data.catalog.universal.CatalogSource
import com.ygocardscanner.data.catalog.universal.CatalogCardDto
import com.ygocardscanner.data.catalog.universal.CatalogCardTextDto
import com.ygocardscanner.data.catalog.universal.CatalogPayload
import com.ygocardscanner.data.catalog.universal.CatalogPrintingDto

internal fun testCatalog(
    sourceId: String = "test-source",
    revision: String = "1",
    providerCardId: String = "blue-eyes",
    providerPrintingId: String = "lob-en-001",
    cardName: String = "Blue-Eyes White Dragon",
    setCode: String = "LOB-001",
): CatalogPayload = CatalogPayload(
    sourceId = sourceId,
    catalogRevision = revision,
    contentHash = "$sourceId-$revision",
    cards = listOf(
        CatalogCardDto(
            providerCardId = providerCardId,
            passcode = "89631139",
            canonicalName = cardName,
            texts = listOf(
                CatalogCardTextDto("en", cardName),
                CatalogCardTextDto("de", "Blauäugiger w. Drache"),
            ),
            printings = listOf(
                CatalogPrintingDto(
                    providerPrintingId = providerPrintingId,
                    setCode = setCode,
                    setName = "Test set",
                    languageCode = "en",
                    rarityCode = "ultra_rare",
                    editionCode = "first_edition",
                ),
            ),
        ),
    ),
)

internal class StaticCatalogSource(
    private val payload: CatalogPayload,
) : CatalogSource {
    override val sourceId: String = payload.sourceId

    override suspend fun loadCatalog(): CatalogPayload = payload
}

