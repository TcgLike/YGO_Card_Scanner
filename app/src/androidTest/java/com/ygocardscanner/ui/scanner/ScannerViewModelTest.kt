package com.ygocardscanner.ui.scanner

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ygocardscanner.data.repository.CardArtworkRepository
import com.ygocardscanner.data.repository.InventoryRepository
import com.ygocardscanner.data.scanner.CardScannerRepository
import com.ygocardscanner.data.scanner.ScanCandidate
import com.ygocardscanner.data.scanner.ScanMatchKind
import com.ygocardscanner.data.scanner.ScanMatchResult
import com.ygocardscanner.data.scanner.ScanTextObservation
import com.ygocardscanner.data.settings.AppLanguageSettings
import com.ygocardscanner.model.CardEdition
import com.ygocardscanner.model.CardLanguage
import com.ygocardscanner.model.CatalogPrintingSummary
import com.ygocardscanner.model.CollectionEntrySummary
import com.ygocardscanner.model.InventoryEntryDetail
import com.ygocardscanner.model.KnownPrintingDraft
import com.ygocardscanner.model.UnknownPrintingDraft
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ScannerViewModelTest {
    @Test
    fun confirmedLiveScanStaysLiveUntilTheFrameClearsThenRearms() = runBlocking {
        val viewModel = ScannerViewModel(
            scannerRepository = MatchingScannerRepository(),
            inventoryRepository = RecordingInventoryRepository(),
            artworkRepository = NoArtworkRepository(),
            languageSettings = AppLanguageSettings(ApplicationProvider.getApplicationContext<Context>()),
        )

        viewModel.onRecognizedText("Blue-Eyes White Dragon")
        withTimeout(5_000) { viewModel.uiState.first { it.match != null } }

        val event = async(start = CoroutineStart.UNDISPATCHED) {
            withTimeout(5_000) { viewModel.events.first() }
        }
        viewModel.confirmSelected()
        val afterAdd = withTimeout(5_000) { viewModel.uiState.first { it.awaitingClearAfterAccepted } }

        assertEquals(com.ygocardscanner.data.scanner.ScannerMode.LIVE, afterAdd.mode)
        assertNull(afterAdd.match)
        assertTrue(afterAdd.awaitingClearAfterAccepted)
        assertEquals(1, afterAdd.liveAcceptedCount)
        assertTrue(event.await() is ScannerEvent.LiveCardAdded)

        viewModel.onRecognizedText("")
        val rearmed = withTimeout(5_000) { viewModel.uiState.first { !it.awaitingClearAfterAccepted } }
        assertFalse(rearmed.awaitingClearAfterAccepted)

        viewModel.onRecognizedText("Blue-Eyes White Dragon")
        assertTrue(withTimeout(5_000) { viewModel.uiState.first { it.match != null } }.match != null)
    }

    private class MatchingScannerRepository : CardScannerRepository {
        override suspend fun match(
            observation: ScanTextObservation,
            displayLanguage: CardLanguage,
        ): ScanMatchResult = ScanMatchResult.Candidates(
            kind = ScanMatchKind.FUZZY_LOCALIZED_NAME,
            candidates = listOf(
                ScanCandidate(
                    printing = CatalogPrintingSummary(
                        printingId = "test:printing",
                        cardId = "test:card",
                        displayName = "Blue-Eyes White Dragon",
                        setCode = "LOB-EN001",
                        setName = "Legend of Blue Eyes White Dragon",
                        language = CardLanguage.ENGLISH,
                        rarity = "Ultra Rare",
                        edition = CardEdition.UNKNOWN,
                    ),
                    kind = ScanMatchKind.FUZZY_LOCALIZED_NAME,
                    score = 90,
                ),
            ),
            observedFingerprint = "blueeyes",
        )
    }

    private class RecordingInventoryRepository : InventoryRepository {
        override fun observeCollection(query: String, displayLanguage: CardLanguage): Flow<List<CollectionEntrySummary>> = flowOf(emptyList())
        override fun observeEntry(entryId: String): Flow<InventoryEntryDetail?> = flowOf(null)
        override suspend fun addKnownPrinting(draft: KnownPrintingDraft): String = "test:entry"
        override suspend fun addUnknownPrinting(draft: UnknownPrintingDraft): String = "unused"
        override suspend fun setCondition(entryId: String, condition: com.ygocardscanner.model.CardCondition) = Unit
        override suspend fun setQuantity(entryId: String, quantity: Int) = Unit
        override suspend fun deleteEntry(entryId: String) = Unit
    }

    private class NoArtworkRepository : CardArtworkRepository {
        override suspend fun queueDownload(cardId: String): Boolean = false
        override suspend fun downloadArtwork(cardId: String) = Unit
        override suspend fun markRetry(cardId: String) = Unit
        override suspend fun markFailed(cardId: String) = Unit
    }
}

