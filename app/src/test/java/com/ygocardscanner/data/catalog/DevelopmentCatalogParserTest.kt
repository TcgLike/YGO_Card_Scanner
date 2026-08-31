package com.ygocardscanner.data.catalog

import com.ygocardscanner.data.catalog.yugioh.DevelopmentCatalogParser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DevelopmentCatalogParserTest {
    @Test
    fun parsesEnglishAndGermanLocalizedTexts() {
        val payload = DevelopmentCatalogParser.parse(
            """
            {
              "source_id": "test-source",
              "catalog_revision": "1",
              "cards": [
                {
                  "provider_card_id": "blue-eyes",
                  "passcode": "89631139",
                  "canonical_name": "Blue-Eyes White Dragon",
                  "texts": [
                    {"language_code": "en", "name": "Blue-Eyes White Dragon"},
                    {"language_code": "de", "name": "Blauäugiger w. Drache"}
                  ],
                  "printings": [
                    {
                      "provider_printing_id": "lob-en-001",
                      "set_code": "LOB-001",
                      "language_code": "en",
                      "edition_code": "first_edition"
                    }
                  ]
                }
              ]
            }
            """.trimIndent(),
        )

        assertEquals("test-source", payload.sourceId)
        assertEquals("89631139", payload.cards.single().passcode)
        assertTrue(payload.cards.single().texts.any { it.languageCode == "de" && it.name.contains("Blau") })
    }
}

