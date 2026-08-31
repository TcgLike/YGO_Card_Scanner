package com.ygocardscanner.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ygocardscanner.data.catalog.universal.CatalogMapper
import com.ygocardscanner.data.local.AppDatabase
import com.ygocardscanner.data.repository.RoomCatalogRepository
import com.ygocardscanner.data.repository.RoomCatalogViewerRepository
import com.ygocardscanner.data.repository.RoomInventoryRepository
import com.ygocardscanner.model.CardCondition
import com.ygocardscanner.model.CardEdition
import com.ygocardscanner.model.CardLanguage
import com.ygocardscanner.model.KnownPrintingDraft
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RoomCatalogViewerRepositoryTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun catalogViewerShowsEveryActiveCardAndMarksCardsOwnedByAnyInventoryEntry() = runBlocking {
        val catalog = testCatalog()
        val printingId = CatalogMapper.map(catalog, 1L).printings.single().printingId
        val database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        try {
            RoomCatalogRepository(database, StaticCatalogSource(catalog), now = { 1L }).replaceCatalog(catalog)
            val viewer = RoomCatalogViewerRepository(database)
            val inventory = RoomInventoryRepository(database, now = { 2L })

            val beforeAdding = viewer.observeCards("", CardLanguage.GERMAN).first()
            assertEquals(1, beforeAdding.size)
            assertEquals("Blau?ugiger w. Drache", beforeAdding.single().displayName)
            assertFalse(beforeAdding.single().isOwned)

            inventory.addKnownPrinting(
                KnownPrintingDraft(
                    printingId = printingId,
                    language = CardLanguage.ENGLISH,
                    rarity = null,
                    edition = CardEdition.FIRST_EDITION,
                    condition = CardCondition.NEAR_MINT,
                    quantity = 2,
                    notes = "",
                ),
            )

            assertTrue(viewer.observeCards("89631139", CardLanguage.ENGLISH).first().single().isOwned)
        } finally {
            database.close()
        }
    }
}

