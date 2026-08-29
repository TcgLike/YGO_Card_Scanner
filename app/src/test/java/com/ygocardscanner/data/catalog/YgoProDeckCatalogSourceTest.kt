package com.ygocardscanner.data.catalog

import com.ygocardscanner.data.catalog.network.YgoProDeckApiClient
import com.ygocardscanner.data.catalog.network.YgoProDeckCardDto
import com.ygocardscanner.data.catalog.network.YgoProDeckCardImageDto
import com.ygocardscanner.data.catalog.network.YgoProDeckCardPageDto
import com.ygocardscanner.data.catalog.network.YgoProDeckCardSetDto
import com.ygocardscanner.data.catalog.network.YgoProDeckDatabaseVersionDto
import com.ygocardscanner.data.catalog.network.YgoProDeckLanguage
import com.ygocardscanner.data.catalog.network.YgoProDeckPageMetaDto
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class YgoProDeckCatalogSourceTest {
    @Test
    fun `loads all English and German pages and joins localized text by padded passcode`() = runBlocking {
        val client = FakeYgoProDeckApiClient(
            pages = mapOf(
                YgoProDeckLanguage.ENGLISH to mapOf(
                    0 to page(
                        cards = listOf(card(id = 42, name = "English Forty-Two")),
                        nextOffset = 2,
                    ),
                    2 to page(cards = listOf(card(id = 7, name = "English Seven"))),
                ),
                YgoProDeckLanguage.GERMAN to mapOf(
                    0 to page(
                        cards = listOf(card(id = 42, name = "Deutsch Zweiundvierzig")),
                        nextOffset = 2,
                    ),
                    2 to page(cards = listOf(card(id = 7, name = "Deutsch Sieben"))),
                ),
            ),
        )
        val source = YgoProDeckCatalogSource(
            apiClient = client,
            pageSize = 2,
            pageRequestDelayMillis = 0,
        )

        val payload = source.loadCatalog()

        assertEquals("ygoprodeck-v7", payload.sourceId)
        assertEquals(listOf("00000007", "00000042"), payload.cards.map { it.passcode })
        assertEquals(
            listOf("English Forty-Two", "Deutsch Zweiundvierzig"),
            payload.cards.single { it.passcode == "00000042" }.texts.map { it.name },
        )
        assertEquals(
            listOf(
                YgoProDeckLanguage.ENGLISH to 0,
                YgoProDeckLanguage.ENGLISH to 2,
                YgoProDeckLanguage.GERMAN to 0,
                YgoProDeckLanguage.GERMAN to 2,
            ),
            client.cardRequests,
        )
    }

    @Test
    fun `uses only English card sets and leaves all provider editions unknown`() = runBlocking {
        val client = FakeYgoProDeckApiClient(
            pages = mapOf(
                YgoProDeckLanguage.ENGLISH to mapOf(
                    0 to page(
                        cards = listOf(
                            card(
                                id = 89631139,
                                name = "Blue-Eyes White Dragon",
                                sets = listOf(
                                    YgoProDeckCardSetDto(
                                        setName = "Legend of Blue Eyes White Dragon",
                                        setCode = "LOB-EN001",
                                        setRarity = "Ultra Rare",
                                        setRarityCode = "(UR)",
                                    ),
                                ),
                            ),
                        ),
                    ),
                ),
                YgoProDeckLanguage.GERMAN to mapOf(
                    0 to page(
                        cards = listOf(
                            card(
                                id = 89631139,
                                name = "Blauäugiger w. Drache",
                                sets = listOf(
                                    YgoProDeckCardSetDto(
                                        setName = "Deutscher Druck, nicht übernehmen",
                                        setCode = "LOB-DE001",
                                        setRarity = "Ultra Rare",
                                    ),
                                ),
                            ),
                        ),
                    ),
                ),
            ),
        )
        val source = YgoProDeckCatalogSource(
            apiClient = client,
            pageRequestDelayMillis = 0,
        )

        val payload = source.loadCatalog()
        val card = payload.cards.single()

        assertEquals(listOf("LOB-EN001"), card.printings.map { it.setCode })
        assertEquals(listOf("en"), card.printings.map { it.languageCode })
        assertEquals(listOf("unknown"), card.printings.map { it.editionCode })
        assertTrue(card.texts.any { it.languageCode == "de" && it.name == "Blauäugiger w. Drache" })
        assertTrue(card.printings.none { it.setCode == "LOB-DE001" })
    }

    @Test
    fun `uses only the English primary artwork`() = runBlocking {
        val client = FakeYgoProDeckApiClient(
            pages = mapOf(
                YgoProDeckLanguage.ENGLISH to mapOf(
                    0 to page(
                        cards = listOf(
                            YgoProDeckCardDto(
                                id = 42,
                                name = "English card",
                                cardImages = listOf(
                                    com.ygocardscanner.data.catalog.network.YgoProDeckCardImageDto(
                                        id = 42,
                                        imageUrl = "https://images.ygoprodeck.com/images/cards/42.jpg",
                                    ),
                                ),
                            ),
                        ),
                    ),
                ),
                YgoProDeckLanguage.GERMAN to mapOf(
                    0 to page(
                        cards = listOf(
                            YgoProDeckCardDto(
                                id = 42,
                                name = "German card",
                                cardImages = listOf(
                                    com.ygocardscanner.data.catalog.network.YgoProDeckCardImageDto(
                                        id = 99,
                                        imageUrl = "https://images.ygoprodeck.com/images/cards/99.jpg",
                                    ),
                                ),
                            ),
                        ),
                    ),
                ),
            ),
        )

        val artwork = YgoProDeckCatalogSource(client, pageRequestDelayMillis = 0)
            .loadCatalog()
            .cards
            .single()
            .artwork

        assertEquals("00000042:42", artwork?.providerArtworkId)
        assertEquals("https://images.ygoprodeck.com/images/cards/42.jpg", artwork?.imageUrl)
    }

    @Test
    fun `exposes the provider database version as a lightweight revision`() = runBlocking {
        val client = FakeYgoProDeckApiClient(
            pages = emptyMap(),
            databaseVersion = YgoProDeckDatabaseVersionDto(
                databaseVersion = JsonPrimitive(42),
                date = "2026-08-29T00:00:00+00:00",
            ),
        )
        val source = YgoProDeckCatalogSource(client, pageRequestDelayMillis = 0)

        val revision = source.fetchRevision()

        assertEquals("ygoprodeck-v7", revision.sourceId)
        assertEquals("42|2026-08-29T00:00:00+00:00", revision.revision)
        assertNull(revision.contentHash)
        assertEquals(setOf(DevelopmentCatalogSource.SOURCE_ID), source.supersededSourceIds)
    }

    @Test
    fun `provider database version array DTO parses the documented response shape`() {
        val records = json
            .decodeFromString<List<YgoProDeckDatabaseVersionDto>>(
                """[{"database_version":"146.68","last_update":"2026-08-21 00:00:28"}]""",
            )

        assertEquals(1, records.size)
        assertEquals("146.68", records.single().databaseVersion?.content)
        assertEquals("2026-08-21 00:00:28", records.single().lastUpdate)
    }
    private class FakeYgoProDeckApiClient(
        private val pages: Map<YgoProDeckLanguage, Map<Int, YgoProDeckCardPageDto>>,
        private val databaseVersion: YgoProDeckDatabaseVersionDto = YgoProDeckDatabaseVersionDto(
            databaseVersion = JsonPrimitive(1),
            date = "2026-01-01",
        ),
    ) : YgoProDeckApiClient {
        val cardRequests = mutableListOf<Pair<YgoProDeckLanguage, Int>>()

        override suspend fun fetchCards(
            language: YgoProDeckLanguage,
            pageSize: Int,
            offset: Int,
        ): YgoProDeckCardPageDto {
            cardRequests += language to offset
            return requireNotNull(pages[language]?.get(offset)) {
                "No fake page for $language at offset $offset."
            }
        }

        override suspend fun fetchDatabaseVersion(): YgoProDeckDatabaseVersionDto = databaseVersion
    }

    private companion object {
        val json = Json { ignoreUnknownKeys = true }
        fun card(
            id: Long,
            name: String,
            sets: List<YgoProDeckCardSetDto> = emptyList(),
            images: List<YgoProDeckCardImageDto> = emptyList(),
        ) = YgoProDeckCardDto(
            id = id,
            name = name,
            cardSets = sets,
            cardImages = images,
        )

        fun page(
            cards: List<YgoProDeckCardDto>,
            nextOffset: Int? = null,
        ) = YgoProDeckCardPageDto(
            data = cards,
            meta = YgoProDeckPageMetaDto(nextPageOffset = nextOffset),
        )
    }
}
