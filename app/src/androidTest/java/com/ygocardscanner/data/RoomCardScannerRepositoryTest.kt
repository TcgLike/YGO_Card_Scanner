package com.ygocardscanner.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ygocardscanner.data.catalog.network.CatalogCardTextDto
import com.ygocardscanner.data.local.AppDatabase
import com.ygocardscanner.data.repository.RoomCatalogRepository
import com.ygocardscanner.data.scanner.RoomCardScannerRepository
import com.ygocardscanner.data.scanner.ScanMatchKind
import com.ygocardscanner.data.scanner.ScanMatchResult
import com.ygocardscanner.data.scanner.ScanTextObservation
import com.ygocardscanner.model.CardLanguage
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RoomCardScannerRepositoryTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val databaseName = "scanner-${UUID.randomUUID()}.db"

    @After
    fun cleanUp() { context.deleteDatabase(databaseName) }

    @Test
    fun exactSetCodeWinsOverPasscodeAndName() = runBlocking {
        val database = openDatabase()
        RoomCatalogRepository(database, StaticCatalogSource(testCatalog())).replaceCatalog(testCatalog())

        val result = RoomCardScannerRepository(database).match(
            ScanTextObservation("Blue-Eyes White Dragon LOB-001 89631139", listOf("LOB001"), listOf("89631139"), listOf("Blue-Eyes White Dragon")),
            CardLanguage.ENGLISH,
        )

        assertTrue(result is ScanMatchResult.Candidates)
        result as ScanMatchResult.Candidates
        assertEquals(ScanMatchKind.EXACT_SET_CODE, result.kind)
        assertEquals(listOf("LOB001"), result.observedSetCodes)
        database.close()
    }

    @Test
    fun germanScannerModeReturnsGermanLocalizedName() = runBlocking {
        val germanName = "Blauäugiger weißer Drache"
        val catalog = testCatalog().copy(
            cards = testCatalog().cards.map { card ->
                card.copy(texts = listOf(CatalogCardTextDto("en", card.canonicalName), CatalogCardTextDto("de", germanName)))
            },
        )
        val database = openDatabase()
        RoomCatalogRepository(database, StaticCatalogSource(catalog)).replaceCatalog(catalog)

        val result = RoomCardScannerRepository(database).match(
            ScanTextObservation(germanName, emptyList(), emptyList(), listOf(germanName)),
            CardLanguage.GERMAN,
        )

        assertTrue(result is ScanMatchResult.Candidates)
        result as ScanMatchResult.Candidates
        assertEquals(ScanMatchKind.FUZZY_LOCALIZED_NAME, result.kind)
        assertEquals(germanName, result.candidates.single().printing.displayName)
        database.close()
    }

    private fun openDatabase(): AppDatabase = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
        .allowMainThreadQueries()
        .build()
}