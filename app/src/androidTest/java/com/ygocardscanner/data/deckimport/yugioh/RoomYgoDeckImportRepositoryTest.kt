package com.ygocardscanner.data.deckimport.yugioh

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ygocardscanner.data.StaticCatalogSource
import com.ygocardscanner.data.testCatalog
import com.ygocardscanner.data.local.AppDatabase
import com.ygocardscanner.data.repository.RoomCatalogRepository
import com.ygocardscanner.model.CardCondition
import com.ygocardscanner.model.CardLanguage
import java.util.UUID
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RoomYgoDeckImportRepositoryTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun importsKnownCardAsUnknownPrintingAndMergesARepeatedImport() = runBlocking {
        val database = openDatabase()
        val payload = testCatalog()
        RoomCatalogRepository(database, StaticCatalogSource(payload), now = { 1L }).replaceCatalog(payload)
        val repository = RoomYgoDeckImportRepository(database, now = { 2L })
        val preview = repository.preview(
            YgoDeckDocument("starter.ydk", mapOf(YgoDeckSection.MAIN to listOf("89631139", "89631139"))),
            CardLanguage.ENGLISH,
        )
        val card = preview.cards.single()

        assertTrue(card.isResolved)
        assertEquals(2, card.quantity)
        repository.importDeck(request(card.cardId.orEmpty(), quantity = 2))
        repository.importDeck(request(card.cardId.orEmpty(), quantity = 2))

        val entries = database.inventoryDao().observeCollection("", "", "", "en").first()
        assertEquals(1, entries.size)
        assertEquals(4, entries.single().entry.quantity)
        assertEquals("unknown", entries.single().entry.printingKind)
        database.close()
    }

    @Test
    fun rejectsTheWholeBatchWhenAnySelectionNoLongerMatchesTheCatalog() = runBlocking {
        val database = openDatabase()
        val payload = testCatalog()
        RoomCatalogRepository(database, StaticCatalogSource(payload), now = { 1L }).replaceCatalog(payload)
        val repository = RoomYgoDeckImportRepository(database, now = { 2L })
        val cardId = requireNotNull(repository.preview(
            YgoDeckDocument("starter.ydk", mapOf(YgoDeckSection.MAIN to listOf("89631139"))),
            CardLanguage.ENGLISH,
        ).cards.single().cardId)

        runCatching {
            repository.importDeck(
                YgoDeckImportRequest(
                    cards = listOf(
                        YgoDeckImportSelection("89631139", cardId, null, 1),
                        YgoDeckImportSelection("12345678", "missing", null, 1),
                    ),
                    language = CardLanguage.ENGLISH,
                    condition = CardCondition.NEAR_MINT,
                    notes = "",
                ),
            )
        }.onSuccess { error("Expected an invalid selection to fail.") }

        assertTrue(database.inventoryDao().observeCollection("", "", "", "en").first().isEmpty())
        database.close()
    }

    private fun request(cardId: String, quantity: Int) = YgoDeckImportRequest(
        cards = listOf(YgoDeckImportSelection("89631139", cardId, null, quantity)),
        language = CardLanguage.ENGLISH,
        condition = CardCondition.NEAR_MINT,
        notes = "",
    )

    private fun openDatabase(): AppDatabase = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
        .allowMainThreadQueries()
        .build()
}
