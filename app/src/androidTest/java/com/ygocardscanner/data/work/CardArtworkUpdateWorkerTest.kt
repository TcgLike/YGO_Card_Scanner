package com.ygocardscanner.data.work

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.ListenableWorker
import androidx.work.testing.TestListenableWorkerBuilder
import androidx.work.workDataOf
import com.ygocardscanner.data.catalog.universal.CatalogPayload
import com.ygocardscanner.data.repository.CardArtworkRepository
import com.ygocardscanner.data.repository.CatalogRefreshResult
import com.ygocardscanner.data.repository.CatalogRepository
import com.ygocardscanner.data.repository.CatalogUpdateStatus
import com.ygocardscanner.model.CardLanguage
import com.ygocardscanner.model.CatalogPrintingSummary
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CardArtworkUpdateWorkerTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun downloadsOneArtworkIntoTheLocalCache() = runBlocking {
        val repository = RecordingArtworkRepository()

        val result = worker(repository).doWork()

        assertTrue(result is ListenableWorker.Result.Success)
        assertEquals(listOf("download:card-1"), repository.calls)
    }

    @Test
    fun transientArtworkFailureRetriesWithoutExposingProviderError() = runBlocking {
        val repository = RecordingArtworkRepository(downloadFailure = IOException("offline"))

        val result = worker(repository).doWork()

        assertTrue(result is ListenableWorker.Result.Retry)
        assertEquals(listOf("download:card-1", "retry:card-1"), repository.calls)
    }

    @Test
    fun invalidArtworkFailureIsTerminalAndSafe() = runBlocking {
        val repository = RecordingArtworkRepository(downloadFailure = IllegalArgumentException("invalid image"))

        val result = worker(repository).doWork()

        assertTrue(result is ListenableWorker.Result.Failure)
        assertEquals(listOf("download:card-1", "failed:card-1"), repository.calls)
    }

    private fun worker(repository: CardArtworkRepository): CardArtworkUpdateWorker =
        TestListenableWorkerBuilder
            .from(context, CardArtworkUpdateWorker::class.java)
            .setInputData(workDataOf(CardArtworkUpdateWorker.KEY_CARD_ID to "card-1"))
            .setWorkerFactory(AppWorkerFactory(NoOpCatalogRepository(), repository))
            .build() as CardArtworkUpdateWorker

    private class RecordingArtworkRepository(
        private val downloadFailure: Throwable? = null,
    ) : CardArtworkRepository {
        val calls = mutableListOf<String>()

        override suspend fun queueDownload(cardId: String): Boolean = true

        override suspend fun downloadArtwork(cardId: String) {
            calls += "download:$cardId"
            downloadFailure?.let { throw it }
        }

        override suspend fun markRetry(cardId: String) {
            calls += "retry:$cardId"
        }

        override suspend fun markFailed(cardId: String) {
            calls += "failed:$cardId"
        }
    }

    private class NoOpCatalogRepository : CatalogRepository {
        override fun observePrintings(
            query: String,
            language: CardLanguage,
        ): Flow<List<CatalogPrintingSummary>> = emptyFlow()

        override fun observeCatalogUpdateStatus(): Flow<CatalogUpdateStatus?> = emptyFlow()

        override suspend fun refreshCatalog(force: Boolean): CatalogRefreshResult = CatalogRefreshResult.UpToDate
        override suspend fun markCatalogUpdateQueued() = Unit
        override suspend fun markCatalogUpdateRunning() = Unit
        override suspend fun markCatalogUpdateRetry(message: String) = Unit
        override suspend fun markCatalogUpdateFailed(message: String) = Unit
        override suspend fun markCatalogUpdateSucceeded() = Unit
        override suspend fun replaceCatalog(payload: CatalogPayload) = Unit
    }
}

