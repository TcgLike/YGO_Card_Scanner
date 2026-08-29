package com.ygocardscanner.data.work

import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.ygocardscanner.data.repository.CatalogRepository
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException

/** Schedules one durable, constrained public-catalog update at a time. */
class CatalogUpdateScheduler(
    private val workManager: WorkManager,
    private val catalogRepository: CatalogRepository,
) {
    suspend fun enqueue(force: Boolean = false) {
        // State is persisted before scheduling so an offline launch can explain why no catalog is
        // immediately available. The worker becomes the sole caller that performs network I/O.
        catalogRepository.markCatalogUpdateQueued()
        try {
            val request = OneTimeWorkRequestBuilder<CatalogUpdateWorker>()
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .setRequiresStorageNotLow(true)
                        .build(),
                )
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .setInputData(workDataOf(CatalogUpdateWorker.KEY_FORCE to force))
                .build()
            workManager.enqueueUniqueWork(
                CatalogUpdateWorker.UNIQUE_WORK_NAME,
                ExistingWorkPolicy.KEEP,
                request,
            )
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            catalogRepository.markCatalogUpdateFailed(SCHEDULING_FAILURE_MESSAGE)
        }
    }

    private companion object {
        const val SCHEDULING_FAILURE_MESSAGE =
            "Catalog update could not be scheduled. Your installed catalog is unchanged."
    }
}
