package com.ygocardscanner.data.work

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.ListenableWorker
import androidx.work.testing.TestListenableWorkerBuilder
import com.ygocardscanner.data.catalog.universal.CatalogPayload
import com.ygocardscanner.data.repository.ArtworkPackBatchResult
import com.ygocardscanner.data.repository.CardArtworkRepository
import com.ygocardscanner.data.repository.CatalogRefreshResult
import com.ygocardscanner.data.repository.CatalogRepository
import com.ygocardscanner.data.repository.CatalogUpdateStatus
import com.ygocardscanner.model.CardLanguage
import com.ygocardscanner.model.CatalogPrintingSummary
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FullArtworkDownloadWorkerTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun finishesWhenRoomBackedPackHasNoRemainingBatch() = runBlocking {
        val repository = PackRepository(ArtworkPackBatchResult.Complete)

        val result = worker(repository).doWork()

        assertTrue(result is ListenableWorker.Result.Success)
        assertEquals(listOf("process"), repository.calls)
    }

    @Test
    fun stopsSafelyWhenTheCacheQuotaIsReached() = runBlocking {
        val repository = PackRepository(ArtworkPackBatchResult.QuotaReached)

        val result = worker(repository).doWork()

        assertTrue(result is ListenableWorker.Result.Failure)
        assertEquals(listOf("process"), repository.calls)
    }

    private fun worker(repository: CardArtworkRepository): FullArtworkDownloadWorker =
        TestListenableWorkerBuilder
            .from(context, FullArtworkDownloadWorker::class.java)
            .setWorkerFactory(AppWorkerFactory(NoOpCatalogRepository(), repository))
            .build() as FullArtworkDownloadWorker

    private class PackRepository(private val result: ArtworkPackBatchResult) : CardArtworkRepository {
        val calls = mutableListOf<String>()
        override suspend fun queueDownload(cardId: String) = false
        override suspend fun downloadArtwork(cardId: String) = Unit
        override suspend fun markRetry(cardId: String) = Unit
        override suspend fun markFailed(cardId: String) = Unit
        override suspend fun processNextFullPackBatch(): ArtworkPackBatchResult {
            calls += "process"
            return result
        }
    }

    private class NoOpCatalogRepository : CatalogRepository {
        override fun observePrintings(query: String, language: CardLanguage): Flow<List<CatalogPrintingSummary>> = emptyFlow()
        override fun observeCatalogUpdateStatus(): Flow<CatalogUpdateStatus?> = emptyFlow()
        override suspend fun refreshCatalog(force: Boolean) = CatalogRefreshResult.UpToDate
        override suspend fun markCatalogUpdateQueued() = Unit
        override suspend fun markCatalogUpdateRunning() = Unit
        override suspend fun markCatalogUpdateRetry(message: String) = Unit
        override suspend fun markCatalogUpdateFailed(message: String) = Unit
        override suspend fun markCatalogUpdateSucceeded() = Unit
        override suspend fun replaceCatalog(payload: CatalogPayload) = Unit
    }
}
