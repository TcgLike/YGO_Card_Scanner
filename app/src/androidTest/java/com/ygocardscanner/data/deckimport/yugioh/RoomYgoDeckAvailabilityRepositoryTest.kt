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
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RoomYgoDeckAvailabilityRepositoryTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun checksOwnedQuantitiesAndKeepsUnavailableCardsUnowned() = runBlocking {
        val database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val payload = testCatalog()
        RoomCatalogRepository(database, StaticCatalogSource(payload), now = { 1L }).replaceCatalog(payload)
        val importer = RoomYgoDeckImportRepository(database, now = { 2L })
        val knownCard = requireNotNull(
            importer.preview(
                YgoDeckDocument("owned.ydk", mapOf(YgoDeckSection.MAIN to listOf("89631139"))),
                CardLanguage.ENGLISH,
            ).cards.single().cardId,
        )
        importer.importDeck(
            YgoDeckImportRequest(
                cards = listOf(YgoDeckImportSelection("89631139", knownCard, null, 2)),
                language = CardLanguage.ENGLISH,
                condition = CardCondition.NEAR_MINT,
                notes = "",
            ),
        )

        val repository = RoomYgoDeckAvailabilityRepository(database)
        val preview = repository.check(
            YgoDeckDocument(
                "check.ydk",
                mapOf(YgoDeckSection.MAIN to listOf("89631139", "89631139", "12345678")),
            ),
            CardLanguage.ENGLISH,
        )

        val owned = preview.cards.first { it.passcode == "89631139" }
        val unavailable = preview.cards.first { it.passcode == "12345678" }
        assertEquals(2, owned.requiredQuantity)
        assertEquals(2, owned.ownedQuantity)
        assertTrue(owned.hasEnough)
        assertFalse(unavailable.isInCatalog)
        assertFalse(unavailable.hasEnough)
        assertFalse(preview.canBuild)

        val insufficient = repository.check(
            YgoDeckDocument(
                "insufficient.ydk",
                mapOf(YgoDeckSection.MAIN to listOf("89631139", "89631139", "89631139")),
            ),
            CardLanguage.ENGLISH,
        ).cards.single()
        assertEquals(3, insufficient.requiredQuantity)
        assertEquals(2, insufficient.ownedQuantity)
        assertEquals(1, insufficient.missingQuantity)
        assertFalse(insufficient.hasEnough)
        database.close()
    }
}