package com.ygocardscanner.data.catalog

import com.ygocardscanner.data.catalog.pokemon.PokemonTcgApiClient
import com.ygocardscanner.data.catalog.pokemon.PokemonTcgCardDto
import com.ygocardscanner.data.catalog.pokemon.PokemonTcgCardPageDto
import com.ygocardscanner.data.catalog.pokemon.PokemonTcgCatalogSource
import com.ygocardscanner.data.catalog.pokemon.PokemonTcgImagesDto
import com.ygocardscanner.data.catalog.pokemon.PokemonTcgSetDto
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PokemonTcgCatalogSourceTest {
    @Test
    fun `maps an English Pokemon card into a stable local catalog record`() = runBlocking {
        val source = PokemonTcgCatalogSource(
            apiClient = FakePokemonTcgApiClient,
            pageRequestDelayMillis = 0,
            now = { 42L },
        )

        val payload = source.loadCatalog()
        val card = payload.cards.single()
        val printing = card.printings.single()

        assertEquals("pokemon-tcg-v2", payload.sourceId)
        assertEquals("download-42", payload.catalogRevision)
        assertEquals("hgss4-1", card.providerCardId)
        assertEquals("Aggron", card.canonicalName)
        assertEquals(listOf("en"), card.texts.map { it.languageCode })
        assertEquals("hgss4-1", printing.providerPrintingId)
        assertEquals("hgss4-1", printing.setCode)
        assertEquals("en", printing.languageCode)
        assertEquals("https://images.pokemontcg.io/hgss4/1_hires.png", requireNotNull(card.artwork).imageUrl)
        assertTrue(requireNotNull(payload.contentHash).isNotBlank())
    }

    private object FakePokemonTcgApiClient : PokemonTcgApiClient {
        override suspend fun fetchCards(page: Int, pageSize: Int): PokemonTcgCardPageDto {
            assertEquals(1, page)
            assertEquals(250, pageSize)
            return PokemonTcgCardPageDto(
                data = listOf(
                    PokemonTcgCardDto(
                        id = "hgss4-1",
                        name = "Aggron",
                        set = PokemonTcgSetDto(id = "hgss4", name = "HS—Triumphant"),
                        number = "1",
                        rarity = "Rare Holo",
                        images = PokemonTcgImagesDto(
                            small = "https://images.pokemontcg.io/hgss4/1.png",
                            large = "https://images.pokemontcg.io/hgss4/1_hires.png",
                        ),
                    ),
                ),
                page = 1,
                pageSize = 250,
                count = 1,
                totalCount = 1,
            )
        }
    }
}
