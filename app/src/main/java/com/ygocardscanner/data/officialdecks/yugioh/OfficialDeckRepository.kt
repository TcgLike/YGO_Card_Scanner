package com.ygocardscanner.data.officialdecks.yugioh

import android.content.Context
import androidx.room.withTransaction
import com.ygocardscanner.data.deckimport.yugioh.YgoDeckDocument
import com.ygocardscanner.data.deckimport.yugioh.YgoDeckSection
import com.ygocardscanner.data.local.AppDatabase
import com.ygocardscanner.data.local.entity.OfficialDeckCard
import com.ygocardscanner.data.local.entity.OfficialDeckCatalogState
import com.ygocardscanner.data.local.entity.OfficialDeckProduct
import com.ygocardscanner.data.local.entity.OfficialDeckVariant
import com.ygocardscanner.data.util.CatalogNormalizers
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.util.Locale

const val OFFICIAL_DECK_SOURCE_ID = "bundled-official-decks"

enum class OfficialDeckType(val code: String) { STARTER("starter"), STRUCTURE("structure"), LEGENDARY("legendary") }

data class OfficialDeckProductSummary(val productId: String, val title: String, val type: OfficialDeckType, val releaseDate: String, val coverStyle: String, val variants: List<OfficialDeckVariantSummary>)
data class OfficialDeckVariantSummary(val variantId: String, val title: String, val totalCardCount: Int, val isCompleteBoxContents: Boolean)
data class OfficialDeckBonusCandidate(val passcode: String, val setCode: String, val displayName: String)
data class OfficialDeckBonusGroup(val id: String, val label: String, val candidates: List<OfficialDeckBonusCandidate>)

data class OfficialDeckImportRecipe(
    val sourceLabel: String,
    val declaredTotalCardCount: Int,
    val fixedCardsBySection: Map<YgoDeckSection, List<String>>,
    val bonusGroups: List<OfficialDeckBonusGroup> = emptyList(),
) {
    fun document(selectedBonusPasscodes: Map<String, String?> = emptyMap()): YgoDeckDocument {
        require(selectedBonusPasscodes.keys.all { selected -> bonusGroups.any { it.id == selected } }) { "Unknown official-deck bonus group." }
        val cards = fixedCardsBySection.mapValues { (_, values) -> values.toMutableList() }.toMutableMap()
        bonusGroups.forEach { group ->
            val selected = selectedBonusPasscodes[group.id] ?: return@forEach
            val candidate = group.candidates.firstOrNull { it.passcode == selected }
                ?: error("Selected bonus card is not part of this official deck.")
            val section = cards.entries.firstOrNull { candidate.passcode in it.value }?.key
                ?: error("Selected bonus card is missing from the verified deck base.")
            cards.getValue(section).add(candidate.passcode)
        }
        return YgoDeckDocument(sourceLabel, cards)
    }
}

interface OfficialDeckRepository {
    suspend fun loadLibrary(): List<OfficialDeckProductSummary>
    suspend fun recipe(variantId: String): OfficialDeckImportRecipe
}

interface OfficialDeckSeedSource { fun read(): OfficialDeckPayload }
interface VerifiedOfficialDeckRecipeSource { fun read(): VerifiedOfficialDeckRecipePayloadList }

class RoomOfficialDeckRepository(
    context: Context,
    private val database: AppDatabase,
    private val now: () -> Long = System::currentTimeMillis,
    private val seedSource: OfficialDeckSeedSource = OfficialDeckAssetSource(context),
    private val verifiedRecipeSource: VerifiedOfficialDeckRecipeSource = VerifiedOfficialDeckRecipeAssetSource(context),
) : OfficialDeckRepository {
    override suspend fun loadLibrary(): List<OfficialDeckProductSummary> {
        ensureSeeded()
        val stored = database.officialDeckDao().products().map { product ->
            OfficialDeckProductSummary(product.productId, product.title, OfficialDeckType.entries.first { it.code == product.productType }, product.releaseDate, product.coverStyle,
                database.officialDeckDao().variants(product.productId).map { variant -> OfficialDeckVariantSummary(variant.variantId, variant.title, variant.totalCardCount, variant.isCompleteBoxContents) })
        }
        return (stored + verifiedRecipeSource.read().recipes.map(VerifiedOfficialDeckRecipePayload::toSummary))
            .sortedWith(compareBy(OfficialDeckProductSummary::type).thenByDescending(OfficialDeckProductSummary::releaseDate).thenBy(OfficialDeckProductSummary::title))
    }

    override suspend fun recipe(variantId: String): OfficialDeckImportRecipe {
        ensureSeeded()
        verifiedRecipeSource.read().recipes.firstOrNull { it.variant.id == variantId }?.let { return it.resolve(database) }
        val variant = requireNotNull(database.officialDeckDao().variant(variantId)) { "Official deck is unavailable." }
        val cards = database.officialDeckDao().cards(variantId)
        require(cards.isNotEmpty()) { "Official deck has no verified fixed card list." }
        require(cards.all { it.quantity > 0 }) { "Official deck contains an invalid quantity." }
        require(cards.sumOf(OfficialDeckCard::quantity) == variant.totalCardCount) { "Official deck card total does not match its definition." }
        return OfficialDeckImportRecipe(variant.title, variant.totalCardCount, cards.groupBy { row ->
            requireNotNull(YgoDeckSection.entries.firstOrNull { it.name == row.sectionCode }) { "Official deck contains an unknown section." }
        }.mapValues { (_, rows) -> rows.flatMap { row -> List(row.quantity) { row.passcode } } })
    }

    private suspend fun ensureSeeded() {
        val payload = seedSource.read()
        if (database.officialDeckDao().state(OFFICIAL_DECK_SOURCE_ID)?.catalogRevision == payload.revision) return
        database.withTransaction {
            val dao = database.officialDeckDao()
            if (dao.state(OFFICIAL_DECK_SOURCE_ID)?.catalogRevision == payload.revision) return@withTransaction
            val products = payload.products.map { it.toEntity() }
            val variants = payload.products.flatMap { product -> product.variants.map { it.toEntity(product.id) } }
            val cards = payload.products.flatMap { product -> product.variants.flatMap { variant -> variant.cards.map { it.toEntity(variant.id) } } }
            validatePack(products, variants, cards)
            dao.deleteCards(); dao.deleteVariants(); dao.deleteProducts()
            dao.upsertProducts(products); dao.upsertVariants(variants); dao.upsertCards(cards)
            dao.upsertState(OfficialDeckCatalogState(OFFICIAL_DECK_SOURCE_ID, payload.revision, now()))
        }
    }
}

internal fun validatePack(products: List<OfficialDeckProduct>, variants: List<OfficialDeckVariant>, cards: List<OfficialDeckCard>) {
    require(products.map(OfficialDeckProduct::productId).distinct().size == products.size) { "Duplicate official deck product ID." }
    require(variants.map(OfficialDeckVariant::variantId).distinct().size == variants.size) { "Duplicate official deck variant ID." }
    require(variants.all { it.productId in products.map(OfficialDeckProduct::productId) }) { "Official deck variant references an unknown product." }
    require(cards.all { it.quantity > 0 && it.passcode.length == 8 && it.passcode.all(Char::isDigit) && it.sectionCode in YgoDeckSection.entries.map(YgoDeckSection::name) }) { "Official deck pack has an invalid card slot." }
    require(cards.all { it.variantId in variants.map(OfficialDeckVariant::variantId) }) { "Official deck card references an unknown variant." }
    require(cards.map { listOf(it.variantId, it.passcode, it.sectionCode, it.optionGroupId) }.distinct().size == cards.size) { "Duplicate official deck card slot." }
    require(variants.all { variant -> cards.filter { it.variantId == variant.variantId }.sumOf(OfficialDeckCard::quantity) == variant.totalCardCount }) { "Official deck card total does not match its definition." }
}

internal class OfficialDeckAssetSource(context: Context) : OfficialDeckSeedSource {
    private val assets = context.assets
    private val json = Json { ignoreUnknownKeys = false }
    override fun read(): OfficialDeckPayload = assets.open("official-decks/official-decks-v1.json").bufferedReader().use { json.decodeFromString(it.readText()) }
}
internal class VerifiedOfficialDeckRecipeAssetSource(context: Context) : VerifiedOfficialDeckRecipeSource {
    private val assets = context.assets
    private val json = Json { ignoreUnknownKeys = false }
    override fun read(): VerifiedOfficialDeckRecipePayloadList = assets.open("official-decks/verified-official-deck-recipes-v1.json").bufferedReader().use { json.decodeFromString(it.readText()) }
}

@Serializable data class OfficialDeckPayload(val revision: String, val products: List<OfficialDeckProductPayload>)
@Serializable data class OfficialDeckProductPayload(val id: String, val title: String, val type: String, val releaseDate: String, val officialProductUrl: String, val coverStyle: String, val sourceNote: String, val variants: List<OfficialDeckVariantPayload>) { fun toEntity() = OfficialDeckProduct(id, title, type, releaseDate, officialProductUrl, coverStyle, sourceNote) }
@Serializable data class OfficialDeckVariantPayload(val id: String, val title: String, val totalCardCount: Int, val completeBoxContents: Boolean = false, val cards: List<OfficialDeckCardPayload>) { fun toEntity(productId: String) = OfficialDeckVariant(id, productId, title, totalCardCount, completeBoxContents) }
@Serializable data class OfficialDeckCardPayload(val passcode: String, val section: String, val quantity: Int, val optionGroupId: String = "") { fun toEntity(variantId: String) = OfficialDeckCard(variantId, passcode, section, quantity, optionGroupId) }

@Serializable data class VerifiedOfficialDeckRecipePayloadList(val revision: String, val recipes: List<VerifiedOfficialDeckRecipePayload>)
@Serializable data class VerifiedOfficialDeckRecipePayload(val product: VerifiedOfficialDeckProductPayload, val variant: VerifiedOfficialDeckVariantPayload, val baseRanges: List<VerifiedOfficialDeckRangePayload>, val quantityOverrides: Map<String, Int> = emptyMap(), val bonusGroups: List<VerifiedOfficialDeckBonusGroupPayload> = emptyList()) {
    fun toSummary() = OfficialDeckProductSummary(product.id, product.title, OfficialDeckType.entries.first { it.code == product.type }, product.releaseDate, product.coverStyle, listOf(OfficialDeckVariantSummary(variant.id, variant.title, variant.totalCardCount, variant.completeBoxContents)))
    suspend fun resolve(database: AppDatabase): OfficialDeckImportRecipe {
        val expanded = baseRanges.flatMap { it.expand() }
        require(expanded.map(VerifiedOfficialDeckRangeCard::setCode).distinct().size == expanded.size) { "Verified official deck repeats a set code." }
        val resolved = expanded.associate { card ->
            val passcodes = database.catalogDao().getActivePasscodesByNormalizedSetCode(requireNotNull(CatalogNormalizers.setCode(card.setCode))).filterNotNull().distinct()
            require(passcodes.size == 1) { "${card.setCode} is unavailable in the local catalog. Download or refresh the English + German catalog, then try again." }
            card.setCode to ResolvedVerifiedDeckCard(passcodes.single(), card.section, quantityOverrides[card.setCode] ?: 1)
        }
        require(resolved.values.sumOf(ResolvedVerifiedDeckCard::quantity) == variant.fixedCardCount) { "Verified official deck fixed-card total does not match its definition." }
        val groups = bonusGroups.map { group -> OfficialDeckBonusGroup(group.id, group.label, group.candidates.map { candidate ->
            val base = requireNotNull(resolved[candidate.setCode]) { "Official deck bonus is not part of the fixed base." }
            OfficialDeckBonusCandidate(base.passcode, candidate.setCode, candidate.displayName)
        }) }
        require(variant.fixedCardCount + groups.size == variant.totalCardCount) { "Verified official deck total does not match its optional bonus groups." }
        return OfficialDeckImportRecipe(variant.title, variant.totalCardCount, resolved.values.groupBy(ResolvedVerifiedDeckCard::section).mapValues { (_, cards) -> cards.flatMap { card -> List(card.quantity) { card.passcode } } }, groups)
    }
}
private data class ResolvedVerifiedDeckCard(val passcode: String, val section: YgoDeckSection, val quantity: Int)
@Serializable data class VerifiedOfficialDeckProductPayload(val id: String, val title: String, val type: String, val releaseDate: String, val coverStyle: String)
@Serializable data class VerifiedOfficialDeckVariantPayload(val id: String, val title: String, val fixedCardCount: Int, val totalCardCount: Int, val completeBoxContents: Boolean)
@Serializable data class VerifiedOfficialDeckRangePayload(val prefix: String, val start: Int, val end: Int, val section: String) {
    fun expand(): List<VerifiedOfficialDeckRangeCard> {
        require(start in 1..999 && end in start..999) { "Verified official deck has an invalid set-code range." }
        val deckSection = requireNotNull(YgoDeckSection.entries.firstOrNull { it.name == section }) { "Verified official deck has an invalid section." }
        return (start..end).map { number -> VerifiedOfficialDeckRangeCard("$prefix${String.format(Locale.ROOT, "%03d", number)}", deckSection) }
    }
}
data class VerifiedOfficialDeckRangeCard(val setCode: String, val section: YgoDeckSection)
@Serializable data class VerifiedOfficialDeckBonusGroupPayload(val id: String, val label: String, val candidates: List<VerifiedOfficialDeckBonusCandidatePayload>)
@Serializable data class VerifiedOfficialDeckBonusCandidatePayload(val setCode: String, val displayName: String)
