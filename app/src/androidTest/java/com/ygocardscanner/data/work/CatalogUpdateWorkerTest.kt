package com.ygocardscanner.data.work

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.ListenableWorker
import androidx.work.testing.TestListenableWorkerBuilder
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
class CatalogUpdateWorkerTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun successfulUpdateMarksDurableSuccess() = runBlocking {
        val repository = RecordingCatalogRepository()

        val result = worker(repository).doWork()

        assertTrue(result is ListenableWorker.Result.Success)
        assertEquals(listOf("running", "refresh:false", "succeeded"), repository.calls)
    }

    @Test
    fun transientNetworkFailureRequestsRetryWithoutReplacingCatalog() = runBlocking {
        val repository = RecordingCatalogRepository(refreshFailure = IOException("offline"))

        val result = worker(repository).doWork()

        assertTrue(result is ListenableWorker.Result.Retry)
        assertEquals(listOf("running", "refresh:false", "retry"), repository.calls)
    }

    @Test
    fun malformedCatalogFailureMarksSafeTerminalState() = runBlocking {
        val repository = RecordingCatalogRepository(refreshFailure = IllegalStateException("bad payload"))

        val result = worker(repository).doWork()

        assertTrue(result is ListenableWorker.Result.Failure)
        assertEquals(listOf("running", "refresh:false", "failed"), repository.calls)
    }

    private fun worker(repository: CatalogRepository): CatalogUpdateWorker =
        TestListenableWorkerBuilder
            .from(context, CatalogUpdateWorker::class.java)
            .setWorkerFactory(AppWorkerFactory(repository, NoOpArtworkRepository()))
            .build() as CatalogUpdateWorker

    private class NoOpArtworkRepository : CardArtworkRepository {
        override suspend fun queueDownload(cardId: String): Boolean = false
        override suspend fun downloadArtwork(cardId: String) = Unit
        override suspend fun markRetry(cardId: String) = Unit
        override suspend fun markFailed(cardId: String) = Unit
    }
    private class RecordingCatalogRepository(
        private val refreshFailure: Throwable? = null,
    ) : CatalogRepository {
        val calls = mutableListOf<String>()

        override fun observePrintings(
            query: String,
            language: CardLanguage,
        ): Flow<List<CatalogPrintingSummary>> = emptyFlow()

        override fun observeCatalogUpdateStatus(): Flow<CatalogUpdateStatus?> = emptyFlow()

        override suspend fun refreshCatalog(force: Boolean): CatalogRefreshResult {
            calls += "refresh:$force"
            refreshFailure?.let { throw it }
            return CatalogRefreshResult.Updated
        }

        override suspend fun markCatalogUpdateQueued() {
            calls += "queued"
        }

        override suspend fun markCatalogUpdateRunning() {
            calls += "running"
        }

        override suspend fun markCatalogUpdateRetry(message: String) {
            calls += "retry"
        }

        override suspend fun markCatalogUpdateFailed(message: String) {
            calls += "failed"
        }

        override suspend fun markCatalogUpdateSucceeded() {
            calls += "succeeded"
        }

        override suspend fun replaceCatalog(payload: CatalogPayload) = Unit
    }
}

