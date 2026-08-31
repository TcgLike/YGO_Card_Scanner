package com.ygocardscanner.data.officialdecks.yugioh

import com.ygocardscanner.data.local.entity.OfficialDeckCard
import com.ygocardscanner.data.local.entity.OfficialDeckProduct
import com.ygocardscanner.data.local.entity.OfficialDeckVariant
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import com.ygocardscanner.data.deckimport.yugioh.YgoDeckSection
import org.junit.Test
import java.io.File

class OfficialDeckPackTest {
    private val modernProductIds = setOf(
        "structure-sr14", "structure-sdck", "structure-sdbt", "structure-sr13", "structure-sdcb",
        "structure-sdaz", "structure-sdcs", "structure-sdfc", "structure-sdch", "structure-sdsa",
        "structure-sr10", "structure-sdsh", "structure-sdrr", "structure-sr08", "structure-sr07",
        "structure-sdpl", "structure-sr06", "structure-sr05", "structure-sdcl", "structure-sr03",
    )
    @Test
    fun bundledPackHasValidUniqueImportableCardLists() {
        val file = listOf(
            File("src/main/assets/official-decks/official-decks-v1.json"),
            File("app/src/main/assets/official-decks/official-decks-v1.json"),
        ).first(File::exists)
        val payload = Json.decodeFromString<OfficialDeckPayload>(file.readText())
        val products = payload.products.map(OfficialDeckProductPayload::toEntity)
        val variants = payload.products.flatMap { product -> product.variants.map { it.toEntity(product.id) } }
        val cards = payload.products.flatMap { product -> product.variants.flatMap { variant -> variant.cards.map { it.toEntity(variant.id) } } }

        validatePack(products, variants, cards)
assertEquals(23, variants.size)
        assertEquals(1_025, cards.sumOf(OfficialDeckCard::quantity))
        val modernProducts = payload.products.filter { it.id in modernProductIds }
        assertEquals(20, modernProducts.size)
        assertEquals(891, modernProducts.sumOf { product -> product.variants.sumOf { variant -> variant.cards.sumOf(OfficialDeckCardPayload::quantity) } })
    }

    @Test
    fun verifiedRecipesDescribeAllFourExcludedProductsAndCorrectTotals() {
        val file = listOf(
            File("src/main/assets/official-decks/verified-official-deck-recipes-v1.json"),
            File("app/src/main/assets/official-decks/verified-official-deck-recipes-v1.json"),
        ).first(File::exists)
        val recipes = Json.decodeFromString<VerifiedOfficialDeckRecipePayloadList>(file.readText()).recipes
        assertEquals(
            setOf(
                "structure-blue-eyes-white-destiny-english",
                "structure-soulburner-english",
                "chronicles-fallen-virtuous-english",
                "chronicles-spirit-charmers-english",
            ),
            recipes.map { it.variant.id }.toSet(),
        )
        assertEquals(46, recipes.single { it.variant.id == "structure-soulburner-english" }.variant.totalCardCount)
        recipes.filter { it.variant.totalCardCount == 51 }.forEach { recipe ->
            assertEquals(50, recipe.variant.fixedCardCount)
            assertEquals(1, recipe.bonusGroups.size)
            assertEquals(3.takeIf { recipe.variant.id.contains("blue-eyes") } ?: 6, recipe.bonusGroups.single().candidates.size)
        }
    }

    @Test
    fun optionalBonusIsNotImportedUntilTheUserSelectsIt() {
        val recipe = OfficialDeckImportRecipe(
            sourceLabel = "Test deck",
            declaredTotalCardCount = 2,
            fixedCardsBySection = mapOf(YgoDeckSection.MAIN to listOf("12345678")),
            bonusGroups = listOf(OfficialDeckBonusGroup("bonus", "Bonus", listOf(OfficialDeckBonusCandidate("12345678", "TEST-EN001", "Test card")))),
        )
        assertEquals(1, recipe.document().totalCardCount)
        assertEquals(2, recipe.document(mapOf("bonus" to "12345678")).totalCardCount)
    }

    @Test
    fun rejectsADeclaredTotalThatDoesNotMatchCardSlots() {
        assertThrows(IllegalArgumentException::class.java) {
            validatePack(
                products = listOf(OfficialDeckProduct("product", "Deck", "starter", "2026-01-01", "https://example.invalid", "starter", "test")),
                variants = listOf(OfficialDeckVariant("variant", "product", "Deck", 2, false)),
                cards = listOf(OfficialDeckCard("variant", "12345678", "MAIN", 1)),
            )
        }
    }
}