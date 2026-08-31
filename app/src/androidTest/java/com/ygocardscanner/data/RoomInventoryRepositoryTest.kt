package com.ygocardscanner.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ygocardscanner.data.catalog.universal.CatalogMapper
import com.ygocardscanner.data.catalog.yugioh.DevelopmentCatalogSource
import com.ygocardscanner.data.local.AppDatabase
import com.ygocardscanner.data.local.AppDatabaseMigrations
import com.ygocardscanner.data.repository.RoomCatalogRepository
import com.ygocardscanner.data.repository.RoomInventoryRepository
import com.ygocardscanner.model.CardCondition
import com.ygocardscanner.model.CardEdition
import com.ygocardscanner.model.CardLanguage
import com.ygocardscanner.model.KnownPrintingDraft
import java.util.UUID
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RoomInventoryRepositoryTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val databaseName = "inventory-repository-${UUID.randomUUID()}.db"

    @After
    fun cleanUp() {
        context.deleteDatabase(databaseName)
    }

    @Test
    fun quantityAndNotesSurviveDatabaseRecreation() = runBlocking {
        val catalog = testCatalog()
        val printingId = CatalogMapper.map(catalog, 1L).printings.single().printingId
        val firstDatabase = openDatabase()
        val catalogRepository = RoomCatalogRepository(firstDatabase, StaticCatalogSource(catalog), now = { 1L })
        val firstInventoryRepository = RoomInventoryRepository(firstDatabase, now = { 2L })

        catalogRepository.replaceCatalog(catalog)
        val entryId = firstInventoryRepository.addKnownPrinting(draft(printingId, notes = "binder page 4"))
        firstInventoryRepository.setQuantity(entryId, 4)
        firstDatabase.close()

        val reopenedDatabase = openDatabase()
        val reloadedRepository = RoomInventoryRepository(reopenedDatabase, now = { 3L })
        val reloaded = requireNotNull(reloadedRepository.observeEntry(entryId).first())

        assertEquals(4, reloaded.quantity)
        assertEquals("binder page 4", reloaded.notes)
        reopenedDatabase.close()
    }

    @Test
    fun replacingCatalogCannotDeleteExistingInventory() = runBlocking {
        val firstCatalog = testCatalog()
        val firstPrintingId = CatalogMapper.map(firstCatalog, 1L).printings.single().printingId
        val database = openDatabase()
        val catalogRepository = RoomCatalogRepository(database, StaticCatalogSource(firstCatalog), now = { 1L })
        val inventoryRepository = RoomInventoryRepository(database, now = { 2L })

        catalogRepository.replaceCatalog(firstCatalog)
        val entryId = inventoryRepository.addKnownPrinting(draft(firstPrintingId, notes = "keep this entry"))
        inventoryRepository.setQuantity(entryId, 3)

        val replacementCatalog = testCatalog(
            providerCardId = "dark-magician",
            providerPrintingId = "lob-en-005",
            cardName = "Dark Magician",
            setCode = "LOB-005",
            revision = "2",
        )
        catalogRepository.replaceCatalog(replacementCatalog)

        val preserved = requireNotNull(inventoryRepository.observeEntry(entryId).first())
        assertEquals(3, preserved.quantity)
        assertEquals("LOB-001", preserved.setCode)
        assertEquals("keep this entry", preserved.notes)
        assertNotNull(database.catalogDao().getPrintingRow(firstPrintingId, "en"))

        inventoryRepository.setQuantity(entryId, 0)
        assertNull(inventoryRepository.observeEntry(entryId).first())
        database.close()
    }

    @Test
    fun publicCatalogReplacementRetiresSeedButKeepsSeedInventory() = runBlocking {
        val seedCatalog = testCatalog(
            sourceId = DevelopmentCatalogSource.SOURCE_ID,
            revision = "seed",
            providerCardId = "blue-eyes",
            providerPrintingId = "lob-en-001",
            cardName = "Blue-Eyes White Dragon",
            setCode = "LOB-001",
        )
        val publicCatalog = testCatalog(
            sourceId = "ygoprodeck-v7",
            revision = "public-1",
            providerCardId = "dark-magician",
            providerPrintingId = "lob-en-005",
            cardName = "Dark Magician",
            setCode = "LOB-005",
        )
        val source = VersionedStaticCatalogSource(
            payload = publicCatalog,
            supersededSourceIds = setOf(DevelopmentCatalogSource.SOURCE_ID),
        )
        val seedPrintingId = CatalogMapper.map(seedCatalog, 1L).printings.single().printingId
        val database = openDatabase()
        val catalogRepository = RoomCatalogRepository(database, source, now = { 10L })
        val inventoryRepository = RoomInventoryRepository(database, now = { 11L })

        catalogRepository.replaceCatalog(seedCatalog)
        val entryId = inventoryRepository.addKnownPrinting(draft(seedPrintingId, notes = "seed binder"))
        inventoryRepository.setQuantity(entryId, 2)
        catalogRepository.replaceCatalog(publicCatalog)

        val preserved = requireNotNull(inventoryRepository.observeEntry(entryId).first())
        assertEquals(2, preserved.quantity)
        assertEquals("LOB-001", preserved.setCode)
        assertEquals("seed binder", preserved.notes)
        assertTrue(catalogRepository.observePrintings("blue", CardLanguage.ENGLISH).first().isEmpty())
        assertNotNull(database.catalogDao().getPrintingRow(seedPrintingId, "en"))
        database.close()
    }

    @Test
    fun matchingPublicRevisionSkipsFullCatalogReload() = runBlocking {
        val publicCatalog = testCatalog(sourceId = "ygoprodeck-v7", revision = "public-1")
        val source = VersionedStaticCatalogSource(publicCatalog)
        val database = openDatabase()
        val catalogRepository = RoomCatalogRepository(database, source, now = { 10L })

        catalogRepository.refreshCatalog(force = false)
        catalogRepository.refreshCatalog(force = false)

        assertEquals(1, source.loadCalls)
        database.close()
    }
    @Test
    fun blankCatalogSearchDoesNotLoadTheWholeCatalog() = runBlocking {
        val catalog = testCatalog()
        val database = openDatabase()
        val catalogRepository = RoomCatalogRepository(database, StaticCatalogSource(catalog), now = { 1L })

        catalogRepository.replaceCatalog(catalog)

        assertTrue(catalogRepository.observePrintings("", CardLanguage.ENGLISH).first().isEmpty())
        database.close()
    }
    @Test
    fun catalogSearchMatchesGermanLocalizedNames() = runBlocking {
        val catalog = testCatalog()
        val database = openDatabase()
        val catalogRepository = RoomCatalogRepository(database, StaticCatalogSource(catalog), now = { 1L })

        catalogRepository.replaceCatalog(catalog)
        val results = catalogRepository.observePrintings(
            query = "blau",
            language = CardLanguage.ENGLISH,
        ).first()

        assertEquals(1, results.size)
        assertEquals("Blue-Eyes White Dragon", results.single().displayName)
        database.close()
    }

    private fun openDatabase(): AppDatabase = Room.databaseBuilder(
        context,
        AppDatabase::class.java,
        databaseName,
    ).addMigrations(
        AppDatabaseMigrations.MIGRATION_1_2,
        AppDatabaseMigrations.MIGRATION_2_3,
        AppDatabaseMigrations.MIGRATION_3_4,
    )
        .allowMainThreadQueries()
        .build()

    private fun draft(printingId: String, notes: String) = KnownPrintingDraft(
        printingId = printingId,
        language = CardLanguage.ENGLISH,
        rarity = "ultra_rare",
        edition = CardEdition.FIRST_EDITION,
        condition = CardCondition.NEAR_MINT,
        quantity = 1,
        notes = notes,
    )
}

