package com.ygocardscanner.data.repository

import com.ygocardscanner.model.CollectionEntrySummary
import com.ygocardscanner.model.CardLanguage
import com.ygocardscanner.model.InventoryEntryDetail
import com.ygocardscanner.model.KnownPrintingDraft
import com.ygocardscanner.model.UnknownPrintingDraft
import kotlinx.coroutines.flow.Flow

interface InventoryRepository {
    fun observeCollection(query: String, displayLanguage: CardLanguage = CardLanguage.ENGLISH): Flow<List<CollectionEntrySummary>>

    fun observeEntry(entryId: String): Flow<InventoryEntryDetail?>

    suspend fun addKnownPrinting(draft: KnownPrintingDraft): String

    /** Creates a local Card/CardText and an inventory entry with no Printing foreign key. */
    suspend fun addUnknownPrinting(draft: UnknownPrintingDraft): String

    /** Sets a positive quantity; zero removes the entry and negative quantities are rejected. */
    suspend fun setCondition(entryId: String, condition: com.ygocardscanner.model.CardCondition)

    suspend fun setQuantity(entryId: String, quantity: Int)

    suspend fun deleteEntry(entryId: String)
}

