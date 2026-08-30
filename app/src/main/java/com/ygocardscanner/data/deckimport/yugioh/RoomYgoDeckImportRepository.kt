package com.ygocardscanner.data.deckimport.yugioh

import androidx.room.withTransaction
import com.ygocardscanner.data.local.AppDatabase
import com.ygocardscanner.data.local.entity.InventoryEntry
import com.ygocardscanner.model.CardEdition
import com.ygocardscanner.model.PrintingKind
import java.util.UUID

/** Yu-Gi-Oh!-only import implementation. It never reads another game's Room database. */
class RoomYgoDeckImportRepository(
    private val database: AppDatabase,
    private val now: () -> Long = System::currentTimeMillis,
) : YgoDeckImportRepository {
    private val catalogDao = database.catalogDao()
    private val inventoryDao = database.inventoryDao()

    override suspend fun preview(document: YgoDeckDocument, language: com.ygocardscanner.model.CardLanguage): YgoDeckImportPreview {
        val quantities = document.cardsBySection.values
            .flatten()
            .groupingBy { it }
            .eachCount()
        val main = document.cardsBySection[ YgoDeckSection.MAIN ].orEmpty().groupingBy { it }.eachCount()
        val extra = document.cardsBySection[ YgoDeckSection.EXTRA ].orEmpty().groupingBy { it }.eachCount()
        val side = document.cardsBySection[ YgoDeckSection.SIDE ].orEmpty().groupingBy { it }.eachCount()

        val cards = quantities.keys.sorted().map { passcode ->
            val card = catalogDao.getActiveCardForDeckImport(passcode, language.code)
            val printings = if (card == null) emptyList() else catalogDao.getActivePrintingsByPasscode(passcode, language.code)
            YgoDeckImportCard(
                passcode = passcode,
                cardId = card?.cardId,
                displayName = card?.displayName,
                mainQuantity = main[passcode] ?: 0,
                extraQuantity = extra[passcode] ?: 0,
                sideQuantity = side[passcode] ?: 0,
                printingChoices = printings.map { row ->
                    YgoDeckPrintingChoice(
                        printingId = row.printing.printingId,
                        label = listOfNotNull(
                            row.printing.setCode,
                            row.printing.rarityCode,
                            CardEdition.fromCode(row.printing.editionCode).label,
                        ).joinToString(" · "),
                    )
                }.distinctBy(YgoDeckPrintingChoice::printingId),
            )
        }
        return YgoDeckImportPreview(document.sourceLabel, document.totalCardCount, cards)
    }

    override suspend fun importDeck(request: YgoDeckImportRequest): YgoDeckImportResult {
        require(request.cards.isNotEmpty()) { "There are no resolved cards to import." }
        require(request.cards.all { it.quantity > 0 }) { "Every imported card must have a positive quantity." }
        require(request.cards.map(YgoDeckImportSelection::passcode).distinct().size == request.cards.size) {
            "A deck import may contain each passcode only once."
        }

        return database.withTransaction {
            val timestamp = now()
            request.cards.forEach { selection ->
                val card = requireNotNull(catalogDao.getActiveCardForDeckImport(selection.passcode, request.language.code)) {
                    "Passcode ${selection.passcode} is no longer available in the local catalog. Review the deck again."
                }
                require(card.cardId == selection.cardId) { "Card selection changed. Review the deck again." }

                val attributes = selectedAttributes(selection, request.language.code)
                val existing = inventoryDao.findMatchingDeckImportEntry(
                    cardId = attributes.cardId,
                    printingId = attributes.printingId,
                    languageCode = request.language.code,
                    rarityCode = attributes.rarity,
                    editionCode = attributes.edition.code,
                    conditionCode = request.condition.code,
                    notes = request.notes.trim(),
                )
                if (existing != null) {
                    check(inventoryDao.incrementQuantity(existing.entryId, selection.quantity, timestamp) == 1)
                } else {
                    inventoryDao.insert(
                        InventoryEntry(
                            entryId = UUID.randomUUID().toString(),
                            cardId = attributes.cardId,
                            printingId = attributes.printingId,
                            printingKind = if (attributes.printingId == null) PrintingKind.UNKNOWN.code else PrintingKind.KNOWN.code,
                            setCodeSnapshot = attributes.setCode,
                            normalizedSetCodeSnapshot = attributes.normalizedSetCode,
                            languageCode = request.language.code,
                            rarityCode = attributes.rarity,
                            editionCode = attributes.edition.code,
                            conditionCode = request.condition.code,
                            quantity = selection.quantity,
                            notes = request.notes.trim(),
                            createdAtEpochMillis = timestamp,
                            updatedAtEpochMillis = timestamp,
                        ),
                    )
                }
            }
            YgoDeckImportResult(
                addedEntryCount = request.cards.size,
                addedCardCount = request.cards.sumOf(YgoDeckImportSelection::quantity),
            )
        }
    }

    private suspend fun selectedAttributes(
        selection: YgoDeckImportSelection,
        languageCode: String,
    ): ResolvedDeckImportAttributes {
        if (selection.printingId == null) {
            return ResolvedDeckImportAttributes(
                cardId = selection.cardId,
                printingId = null,
                setCode = null,
                normalizedSetCode = null,
                rarity = null,
                edition = CardEdition.UNKNOWN,
            )
        }
        val printing = catalogDao.getActivePrintingsByPasscode(selection.passcode, languageCode)
            .firstOrNull { it.printing.printingId == selection.printingId }
            ?.printing
            ?: throw IllegalArgumentException("The selected physical printing is unavailable. Review the deck again.")
        require(printing.cardId == selection.cardId) { "Selected printing does not belong to the imported card." }
        return ResolvedDeckImportAttributes(
            cardId = printing.cardId,
            printingId = printing.printingId,
            setCode = printing.setCode,
            normalizedSetCode = printing.normalizedSetCode,
            rarity = printing.rarityCode,
            edition = CardEdition.fromCode(printing.editionCode),
        )
    }
}
