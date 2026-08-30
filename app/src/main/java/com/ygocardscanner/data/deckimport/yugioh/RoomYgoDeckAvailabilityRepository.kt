package com.ygocardscanner.data.deckimport.yugioh

import com.ygocardscanner.data.local.AppDatabase
import com.ygocardscanner.model.CardLanguage

/** Uses only the app-private Room catalog and collection; it never uploads deck data. */
class RoomYgoDeckAvailabilityRepository(
    private val database: AppDatabase,
) : YgoDeckAvailabilityRepository {
    private val catalogDao = database.catalogDao()
    private val inventoryDao = database.inventoryDao()

    override suspend fun check(
        document: YgoDeckDocument,
        language: CardLanguage,
    ): YgoDeckAvailabilityPreview {
        val main = document.cardsBySection[YgoDeckSection.MAIN].orEmpty().groupingBy { it }.eachCount()
        val extra = document.cardsBySection[YgoDeckSection.EXTRA].orEmpty().groupingBy { it }.eachCount()
        val side = document.cardsBySection[YgoDeckSection.SIDE].orEmpty().groupingBy { it }.eachCount()
        val passcodes = (main.keys + extra.keys + side.keys).distinct()
        val ownedQuantities = passcodes.flatMap { passcode -> inventoryDao.getOwnedQuantitiesByPasscode(passcode) }
            .groupingBy { it.passcode }
            .fold(0) { total, entry -> total + entry.quantity }

        val cards = passcodes.map { passcode ->
            val catalogCard = catalogDao.getActiveCardForDeckImport(passcode, language.code)
            YgoDeckAvailabilityCard(
                passcode = passcode,
                displayName = catalogCard?.displayName,
                mainQuantity = main[passcode] ?: 0,
                extraQuantity = extra[passcode] ?: 0,
                sideQuantity = side[passcode] ?: 0,
                ownedQuantity = ownedQuantities[passcode] ?: 0,
            )
        }.sortedWith(
            compareBy<YgoDeckAvailabilityCard> { !it.hasEnough }
                .thenBy { it.displayName ?: it.passcode },
        )
        return YgoDeckAvailabilityPreview(document.sourceLabel, document.totalCardCount, cards)
    }
}