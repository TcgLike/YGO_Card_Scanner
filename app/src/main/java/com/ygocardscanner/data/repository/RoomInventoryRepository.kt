package com.ygocardscanner.data.repository

import androidx.room.withTransaction
import com.ygocardscanner.data.local.AppDatabase
import com.ygocardscanner.data.local.entity.Card
import com.ygocardscanner.data.local.entity.CardText
import com.ygocardscanner.data.local.entity.InventoryEntry
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
import com.ygocardscanner.model.UnknownPrintingDraft
import java.util.UUID
import kotlinx.coroutines.flow.Flow
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

    override fun observeEntry(entryId: String): Flow<InventoryEntryDetail?> =
        inventoryDao.observeEntry(entryId).map { row ->
            row?.let {
                InventoryEntryDetail(
                    entryId = it.entry.entryId,
                    cardId = it.entry.cardId,
                    cardName = it.displayName,
                    canonicalName = it.canonicalName,
                    passcode = it.passcode,
                    setCode = it.entry.setCodeSnapshot,
                    setName = it.catalogSetName,
                    language = CardLanguage.fromCode(it.entry.languageCode),
                    rarity = it.entry.rarityCode,
                    edition = CardEdition.fromCode(it.entry.editionCode),
                    condition = CardCondition.fromCode(it.entry.conditionCode),
                    quantity = it.entry.quantity,
                    notes = it.entry.notes,
                    printingKind = PrintingKind.fromCode(it.entry.printingKind),
                    artwork = if (it.artworkRemoteUrl != null) {
                        CardArtworkDetail(
                            localFileName = it.artworkLocalFileName,
                            downloadState = CardArtworkDownloadState.fromCode(it.artworkDownloadState),
                            message = it.artworkMessage,
                        )
                    } else {
                        null
                    },
                )
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

    private companion object {
        const val LOCAL_SOURCE_ID = "local"
        const val LOCAL_CATALOG_REVISION = "local"
    }
}
