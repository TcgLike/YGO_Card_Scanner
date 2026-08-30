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

/** Schedules the durable, constrained Yu-Gi-Oh! public-catalog update. */
class CatalogUpdateScheduler(
    private val workManager: WorkManager,
    private val catalogRepository: CatalogRepository,
) {
    suspend fun enqueue(force: Boolean = false) {
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
            workManager.enqueueUniqueWork(CatalogUpdateWorker.UNIQUE_WORK_NAME, ExistingWorkPolicy.KEEP, request)
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