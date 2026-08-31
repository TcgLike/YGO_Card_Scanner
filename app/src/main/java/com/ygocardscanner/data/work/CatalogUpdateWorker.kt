package com.ygocardscanner.data.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.ygocardscanner.data.repository.CatalogRepository
import java.io.IOException
import kotlinx.coroutines.CancellationException

/**
 * Downloads only public catalog data. The catalog repository validates and maps everything before
 * one Room transaction, so a failed worker cannot partially replace an installed catalog or touch
 * any inventory entry.
 */
class CatalogUpdateWorker(
    appContext: Context,
    params: WorkerParameters,
    private val catalogRepository: CatalogRepository,
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        catalogRepository.markCatalogUpdateRunning()
        return try {
            catalogRepository.refreshCatalog(force = inputData.getBoolean(KEY_FORCE, false))
            catalogRepository.markCatalogUpdateSucceeded()
            Result.success()
        } catch (error: CancellationException) {
            throw error
        } catch (error: IOException) {
            if (runAttemptCount >= MAX_TRANSIENT_ATTEMPTS) {
                catalogRepository.markCatalogUpdateFailed(FINAL_NETWORK_FAILURE_MESSAGE)
                Result.failure()
            } else {
                catalogRepository.markCatalogUpdateRetry(RETRY_NETWORK_FAILURE_MESSAGE)
                Result.retry()
            }
        } catch (_: Exception) {
            catalogRepository.markCatalogUpdateFailed(VALIDATION_FAILURE_MESSAGE)
            Result.failure()
        }
    }

    companion object {
        const val UNIQUE_WORK_NAME = "public-catalog-update"
        const val KEY_FORCE = "force"

        private const val MAX_TRANSIENT_ATTEMPTS = 5
        private const val RETRY_NETWORK_FAILURE_MESSAGE =
            "Catalog update will retry when a connection is available. Your installed catalog is unchanged."
        private const val FINAL_NETWORK_FAILURE_MESSAGE =
            "Catalog update could not reach the public source. Your installed catalog is unchanged."
        private const val VALIDATION_FAILURE_MESSAGE =
            "Catalog update could not be validated. Your installed catalog is unchanged."
    }
}

