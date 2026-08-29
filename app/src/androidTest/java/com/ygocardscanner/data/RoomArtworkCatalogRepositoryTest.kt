package com.ygocardscanner.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ygocardscanner.data.catalog.CatalogMapper
import com.ygocardscanner.data.catalog.network.CatalogCardArtworkDto
import com.ygocardscanner.data.local.AppDatabase
import com.ygocardscanner.data.local.AppDatabaseMigrations
import com.ygocardscanner.data.local.entity.CardArtworkCache
import com.ygocardscanner.data.repository.RoomCatalogRepository
import com.ygocardscanner.data.repository.RoomInventoryRepository
import com.ygocardscanner.model.CardArtworkDownloadState
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
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RoomArtworkCatalogRepositoryTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val databaseName = "artwork-catalog-${UUID.randomUUID()}.db"

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @After
    fun cleanUp() {
        context.deleteDatabase(databaseName)
    }

    @Test
    fun catalogRefreshRetainsInventoryAndMakesChangedArtworkEligibleForNewLocalDownload() = runBlocking {
        val firstCatalog = catalogWithArtwork(revision = "1", imageUrl = firstImageUrl)
        val firstPrintingId = CatalogMapper.map(firstCatalog, 1L).printings.single().printingId
        val firstCardId = CatalogMapper.map(firstCatalog, 1L).cards.single().cardId
        val database = openDatabase()
        val catalogRepository = RoomCatalogRepository(database, StaticCatalogSource(firstCatalog), now = { 1L })
        val inventoryRepository = RoomInventoryRepository(database, now = { 2L })

        catalogRepository.replaceCatalog(firstCatalog)
        val entryId = inventoryRepository.addKnownPrinting(
            KnownPrintingDraft(
                printingId = firstPrintingId,
                language = CardLanguage.ENGLISH,
                rarity = null,
                edition = CardEdition.UNKNOWN,
                condition = CardCondition.NEAR_MINT,
                quantity = 2,
                notes = "preserve this card",
            ),
        )
        database.artworkDao().upsertCache(
            CardArtworkCache(
                cardId = firstCardId,
                remoteUrlSnapshot = firstImageUrl,
                localFileName = temporaryFolder.newFile("cached.img").name,
                downloadState = CardArtworkDownloadState.AVAILABLE.code,
                lastAttemptAtEpochMillis = 2L,
                lastSuccessAtEpochMillis = 2L,
                safeErrorText = null,
            ),
        )

        catalogRepository.replaceCatalog(catalogWithArtwork(revision = "2", imageUrl = secondImageUrl))

        val entry = requireNotNull(inventoryRepository.observeEntry(entryId).first())
        assertEquals(2, entry.quantity)
        assertEquals("preserve this card", entry.notes)
        assertNotNull(entry.artwork)
        assertEquals(CardArtworkDownloadState.NOT_DOWNLOADED, entry.artwork?.downloadState)
        assertEquals(null, entry.artwork?.localFileName)
        database.close()
    }

    private fun catalogWithArtwork(revision: String, imageUrl: String) = testCatalog(revision = revision).let { catalog ->
        catalog.copy(
            cards = catalog.cards.map { card ->
                card.copy(
                    artwork = CatalogCardArtworkDto(
                        providerArtworkId = "89631139",
                        imageUrl = imageUrl,
                    ),
                )
            },
        )
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

    private companion object {
        const val firstImageUrl = "https://images.ygoprodeck.com/images/cards/89631139.jpg"
        const val secondImageUrl = "https://images.ygoprodeck.com/images/cards/89631139-new.jpg"
    }
}
