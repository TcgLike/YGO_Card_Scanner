package com.ygocardscanner.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ygocardscanner.data.catalog.universal.CatalogRevision
import com.ygocardscanner.data.catalog.yugioh.GermanPrintingPayload
import com.ygocardscanner.data.catalog.yugioh.GermanPrintingRecord
import com.ygocardscanner.data.catalog.yugioh.GermanPrintingSource
import com.ygocardscanner.data.repository.RoomCatalogRepository
import com.ygocardscanner.data.repository.RoomGermanPrintingEnrichmentRepository
import com.ygocardscanner.data.repository.RoomInventoryRepository
import com.ygocardscanner.model.CardCondition
import com.ygocardscanner.model.CardEdition
import com.ygocardscanner.model.CardLanguage
import com.ygocardscanner.model.KnownPrintingDraft
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RoomGermanPrintingEnrichmentRepositoryTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun replacementAndDisableKeepInventoryWhileRemovingOptionalSearchRows() = runBlocking {
        val database = Room.inMemoryDatabaseBuilder(context, com.ygocardscanner.data.local.AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val primary = testCatalog(sourceId = "ygoprodeck-v7")
        val source = FakeGermanPrintingSource()
        val catalogRepository = RoomCatalogRepository(database, StaticCatalogSource(primary), now = { 1L })
        val enrichment = RoomGermanPrintingEnrichmentRepository(database, source, now = { 2L })
        val inventory = RoomInventoryRepository(database, now = { 3L })

        catalogRepository.replaceCatalog(primary)
        enrichment.refresh(force = true)
        val germanPrinting = catalogRepository.observePrintings("LOB-DE001", CardLanguage.GERMAN).first().single()
        assertEquals("LOB-DE001", germanPrinting.setCode)

        val entryId = inventory.addKnownPrinting(
            KnownPrintingDraft(
                printingId = germanPrinting.printingId,
                language = CardLanguage.GERMAN,
                rarity = "Ultra Rare",
                edition = CardEdition.FIRST_EDITION,
                condition = CardCondition.NEAR_MINT,
                quantity = 2,
                notes = "German copy",
            ),
        )
        enrichment.setEnabled(false)

        assertTrue(catalogRepository.observePrintings("LOB-DE001", CardLanguage.GERMAN).first().isEmpty())
        val preserved = requireNotNull(inventory.observeEntry(entryId).first())
        assertEquals(2, preserved.quantity)
        assertEquals("LOB-DE001", preserved.setCode)
        database.close()
    }

    private class FakeGermanPrintingSource : GermanPrintingSource {
        override val sourceId = "ygojson-german-printings-v1"

        override suspend fun fetchRevision() = CatalogRevision(sourceId, "test-revision", "test-hash")

        override suspend fun loadGermanPrintings() = GermanPrintingPayload(
            sourceId = sourceId,
            catalogRevision = "test-revision",
            contentHash = "test-hash",
            printings = listOf(
                GermanPrintingRecord(
                    providerPrintingId = "test-printing",
                    passcode = "89631139",
                    setCode = "LOB-DE001",
                    setName = "Legend of Blue Eyes White Dragon",
                    rarityCode = "Ultra Rare",
                    editionCode = "first_edition",
                ),
            ),
        )
    }
}
