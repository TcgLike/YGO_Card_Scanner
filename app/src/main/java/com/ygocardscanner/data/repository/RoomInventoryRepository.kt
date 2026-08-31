package com.ygocardscanner.data.repository

import androidx.room.withTransaction
import com.ygocardscanner.data.local.AppDatabase
import com.ygocardscanner.data.local.entity.Card
import com.ygocardscanner.data.local.entity.CardText
import com.ygocardscanner.data.local.entity.InventoryEntry
import com.ygocardscanner.data.local.entity.PriceSnapshot
import com.ygocardscanner.data.util.CatalogNormalizers
import com.ygocardscanner.model.CardArtworkDetail
import com.ygocardscanner.model.CardArtworkDownloadState
import com.ygocardscanner.model.CardCondition
import com.ygocardscanner.model.CardEdition
import com.ygocardscanner.model.CardLanguage
import com.ygocardscanner.model.CollectionEntrySummary
import com.ygocardscanner.model.InventoryEntryDetail
import com.ygocardscanner.model.KnownPrintingDraft
import com.ygocardscanner.model.PrintingKind
import com.ygocardscanner.model.PriceQuote
import com.ygocardscanner.model.UnknownPrintingDraft
import java.util.UUID
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

class RoomInventoryRepository(
    private val database: AppDatabase,
    private val now: () -> Long = System::currentTimeMillis,
) : InventoryRepository {
    private val catalogDao = database.catalogDao()
    private val inventoryDao = database.inventoryDao()

    override fun observeCollection(query: String, displayLanguage: CardLanguage): Flow<List<CollectionEntrySummary>> =
        inventoryDao.observeCollection(
            rawQuery = query.trim(),
            nameQuery = CatalogNormalizers.name(query),
            compactQuery = CatalogNormalizers.setCode(query).orEmpty(),
            displayLanguageCode = displayLanguage.code,
        ).map { rows ->
            rows.map { row ->
                CollectionEntrySummary(
                    entryId = row.entry.entryId,
                    cardName = row.displayName,
                    setCode = row.entry.setCodeSnapshot,
                    language = CardLanguage.fromCode(row.entry.languageCode),
                    rarity = row.entry.rarityCode,
                    edition = CardEdition.fromCode(row.entry.editionCode),
                    condition = CardCondition.fromCode(row.entry.conditionCode),
                    quantity = row.entry.quantity,
                    isUnknownPrinting = PrintingKind.fromCode(row.entry.printingKind) == PrintingKind.UNKNOWN,
                    artwork = if (row.artworkRemoteUrl != null) {
                        CardArtworkDetail(
                            localFileName = row.artworkLocalFileName,
                            downloadState = CardArtworkDownloadState.fromCode(row.artworkDownloadState),
                            message = row.artworkMessage,
                        )
                    } else {
                        null
                    },
                )
            }
        }

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun observeEntry(entryId: String): Flow<InventoryEntryDetail?> =
        inventoryDao.observeEntry(entryId).flatMapLatest { row ->
            if (row == null) {
                flowOf(null)
            } else {
                database.priceDao().observeForCardAndPrinting(row.entry.cardId, row.entry.printingId)
                    .map { prices ->
                        InventoryEntryDetail(
                            entryId = row.entry.entryId,
                            cardId = row.entry.cardId,
                            cardName = row.displayName,
                            canonicalName = row.canonicalName,
                            passcode = row.passcode,
                            setCode = row.entry.setCodeSnapshot,
                            setName = row.catalogSetName,
                            language = CardLanguage.fromCode(row.entry.languageCode),
                            rarity = row.entry.rarityCode,
                            edition = CardEdition.fromCode(row.entry.editionCode),
                            condition = CardCondition.fromCode(row.entry.conditionCode),
                            quantity = row.entry.quantity,
                            notes = row.entry.notes,
                            printingKind = PrintingKind.fromCode(row.entry.printingKind),
                            artwork = if (row.artworkRemoteUrl != null) {
                                CardArtworkDetail(
                                    localFileName = row.artworkLocalFileName,
                                    downloadState = CardArtworkDownloadState.fromCode(row.artworkDownloadState),
                                    message = row.artworkMessage,
                                )
                            } else {
                                null
                            },
                            prices = prices.sortedWith(
                                compareBy<PriceSnapshot> {
                                    if (it.printingId == row.entry.printingId) 0 else 1
                                }.thenBy { priceProviderOrder(it.providerId) },
                            ).map { it.toQuote() },
                        )
                    }
            }
        }
    override suspend fun addKnownPrinting(draft: KnownPrintingDraft): String {
        require(draft.quantity > 0) { "Quantity must be greater than zero when adding a card." }

        return database.withTransaction {
            val row = requireNotNull(
                catalogDao.getPrintingRow(
                    printingId = draft.printingId,
                    languageCode = draft.language.code,
                ),
            ) { "The selected printing is unavailable." }
            val printing = row.printing

            val timestamp = now()
            val entryId = UUID.randomUUID().toString()
            inventoryDao.insert(
                InventoryEntry(
                    entryId = entryId,
                    cardId = printing.cardId,
                    printingId = printing.printingId,
                    printingKind = PrintingKind.KNOWN.code,
                    setCodeSnapshot = printing.setCode,
                    normalizedSetCodeSnapshot = printing.normalizedSetCode,
                    languageCode = draft.language.code,
                    rarityCode = draft.rarity?.trim()?.takeIf(String::isNotEmpty) ?: printing.rarityCode,
                    editionCode = draft.edition.code,
                    conditionCode = draft.condition.code,
                    quantity = draft.quantity,
                    notes = draft.notes.trim(),
                    createdAtEpochMillis = timestamp,
                    updatedAtEpochMillis = timestamp,
                ),
            )
            entryId
        }
    }

    override suspend fun addUnknownPrinting(draft: UnknownPrintingDraft): String {
        require(draft.cardName.isNotBlank()) { "A card name is required for an unknown printing." }
        require(draft.quantity > 0) { "Quantity must be greater than zero when adding a card." }

        return database.withTransaction {
            val timestamp = now()
            val localProviderCardId = UUID.randomUUID().toString()
            val cardId = "local:card:$localProviderCardId"
            val displayName = draft.cardName.trim()

            // Unknown printings deliberately have no Printing record. Their manually supplied
            // attributes live on the inventory snapshot and remain independent of catalog data.
            catalogDao.upsertCards(
                listOf(
                    Card(
                        cardId = cardId,
                        sourceId = LOCAL_SOURCE_ID,
                        providerCardId = localProviderCardId,
                        passcode = null,
                        canonicalName = displayName,
                        isActive = true,
                        catalogRevision = LOCAL_CATALOG_REVISION,
                        updatedAtEpochMillis = timestamp,
                    ),
                ),
            )
            catalogDao.upsertCardTexts(
                listOf(
                    CardText(
                        cardId = cardId,
                        languageCode = draft.language.code,
                        name = displayName,
                        normalizedName = CatalogNormalizers.name(displayName),
                        description = null,
                        isActive = true,
                        catalogRevision = LOCAL_CATALOG_REVISION,
                    ),
                ),
            )

            val entryId = UUID.randomUUID().toString()
            inventoryDao.insert(
                InventoryEntry(
                    entryId = entryId,
                    cardId = cardId,
                    printingId = null,
                    printingKind = PrintingKind.UNKNOWN.code,
                    setCodeSnapshot = draft.setCode?.trim()?.takeIf(String::isNotEmpty),
                    normalizedSetCodeSnapshot = CatalogNormalizers.setCode(draft.setCode),
                    languageCode = draft.language.code,
                    rarityCode = draft.rarity?.trim()?.takeIf(String::isNotEmpty),
                    editionCode = draft.edition.code,
                    conditionCode = draft.condition.code,
                    quantity = draft.quantity,
                    notes = draft.notes.trim(),
                    createdAtEpochMillis = timestamp,
                    updatedAtEpochMillis = timestamp,
                ),
            )
            entryId
        }
    }

    override suspend fun setCondition(entryId: String, condition: CardCondition) {
        require(inventoryDao.updateCondition(entryId, condition.code, now()) == 1) {
            "Inventory entry '$entryId' does not exist."
        }
    }
    override suspend fun setQuantity(entryId: String, quantity: Int) {
        require(quantity >= 0) { "Quantity cannot be negative." }
        if (quantity == 0) {
            deleteEntry(entryId)
            return
        }

        val updatedRows = inventoryDao.updateQuantity(
            entryId = entryId,
            quantity = quantity,
            updatedAtEpochMillis = now(),
        )
        require(updatedRows == 1) { "Inventory entry '$entryId' does not exist." }
    }

    override suspend fun deleteEntry(entryId: String) {
        inventoryDao.deleteById(entryId)
    }

    private fun priceProviderOrder(providerId: String): Int = when (providerId) {
        "set_price" -> 0
        "cardmarket" -> 1
        "tcgplayer" -> 2
        "ebay" -> 3
        "amazon" -> 4
        "coolstuffinc" -> 5
        else -> 99
    }
    private fun PriceSnapshot.toQuote(): PriceQuote = PriceQuote(
        providerId = providerId,
        currencyCode = currencyCode,
        amountMinor = amountMinor,
        observedAtEpochMillis = observedAtEpochMillis,
        isPrintingSpecific = printingId != null,
    )
    private companion object {
        const val LOCAL_SOURCE_ID = "local"
        const val LOCAL_CATALOG_REVISION = "local"
    }
}

