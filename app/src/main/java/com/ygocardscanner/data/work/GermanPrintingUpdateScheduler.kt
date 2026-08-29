package com.ygocardscanner.data.work

import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.ygocardscanner.data.repository.GermanPrintingEnrichmentRepository
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException

/** Schedules only the user-enabled German printing enrichment source. */
class GermanPrintingUpdateScheduler(
    private val workManager: WorkManager,
    private val repository: GermanPrintingEnrichmentRepository,
) {
    suspend fun enqueue(force: Boolean = false) {
        repository.markUpdateQueued()
        try {
            val request = OneTimeWorkRequestBuilder<GermanPrintingUpdateWorker>()
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .setRequiresStorageNotLow(true)
                        .build(),
                )
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .setInputData(workDataOf(GermanPrintingUpdateWorker.KEY_FORCE to force))
                .build()
            workManager.enqueueUniqueWork(
                GermanPrintingUpdateWorker.UNIQUE_WORK_NAME,
                ExistingWorkPolicy.KEEP,
                request,
            )
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            repository.markUpdateFailed(SCHEDULING_FAILURE_MESSAGE)
        }
    }

    private companion object {
        const val SCHEDULING_FAILURE_MESSAGE =
            "German printing update could not be scheduled. Installed catalog data is unchanged."
    }
}
