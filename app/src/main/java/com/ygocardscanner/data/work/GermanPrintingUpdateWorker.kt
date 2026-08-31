package com.ygocardscanner.data.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.ygocardscanner.data.repository.GermanPrintingEnrichmentRepository
import java.io.IOException
import kotlinx.coroutines.CancellationException

/** Durable, opt-in download for community-sourced German physical printing metadata. */
class GermanPrintingUpdateWorker(
    appContext: Context,
    params: WorkerParameters,
    private val repository: GermanPrintingEnrichmentRepository,
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        repository.markUpdateRunning()
        return try {
            repository.refresh(force = inputData.getBoolean(KEY_FORCE, false))
            repository.markUpdateSucceeded()
            Result.success()
        } catch (error: CancellationException) {
            throw error
        } catch (error: IOException) {
            if (runAttemptCount >= MAX_TRANSIENT_ATTEMPTS) {
                repository.markUpdateFailed(FINAL_NETWORK_FAILURE_MESSAGE)
                Result.failure()
            } else {
                repository.markUpdateRetry(RETRY_NETWORK_FAILURE_MESSAGE)
                Result.retry()
            }
        } catch (_: Exception) {
            repository.markUpdateFailed(VALIDATION_FAILURE_MESSAGE)
            Result.failure()
        }
    }

    companion object {
        const val UNIQUE_WORK_NAME = "ygojson-german-printing-update"
        const val KEY_FORCE = "force"

        private const val MAX_TRANSIENT_ATTEMPTS = 5
        private const val RETRY_NETWORK_FAILURE_MESSAGE =
            "German printing update will retry when a connection is available. Installed catalog data is unchanged."
        private const val FINAL_NETWORK_FAILURE_MESSAGE =
            "German printing update could not reach the optional source. Installed catalog data is unchanged."
        private const val VALIDATION_FAILURE_MESSAGE =
            "German printing update could not be validated. Installed catalog data is unchanged."
    }
}

