package com.ygocardscanner.data.work

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.ygocardscanner.data.repository.ArtworkPackBatchResult
import com.ygocardscanner.data.repository.CardArtworkRepository
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException

/** Processes a small durable batch so the full offline English image pack can resume safely. */
class FullArtworkDownloadWorker(
    appContext: Context,
    params: WorkerParameters,
    private val artworkRepository: CardArtworkRepository,
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result = try {
        when (artworkRepository.processNextFullPackBatch()) {
            ArtworkPackBatchResult.Continue -> {
                enqueueContinuation(applicationContext)
                Result.success()
            }
            ArtworkPackBatchResult.Complete -> Result.success()
            ArtworkPackBatchResult.QuotaReached -> Result.failure()
        }
    } catch (error: CancellationException) {
        throw error
    } catch (_: IOException) {
        if (runAttemptCount >= MAX_TRANSIENT_ATTEMPTS) {
            artworkRepository.markFullPackFailed()
            Result.failure()
        } else {
            artworkRepository.markFullPackRetry()
            Result.retry()
        }
    } catch (_: Exception) {
        artworkRepository.markFullPackFailed()
        Result.failure()
    }

    companion object {
        const val UNIQUE_WORK_NAME = "public-card-artwork-full-pack"
        private const val MAX_TRANSIENT_ATTEMPTS = 3

        fun request() = OneTimeWorkRequestBuilder<FullArtworkDownloadWorker>()
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .setRequiresStorageNotLow(true)
                    .build(),
            )
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()

        fun enqueueContinuation(context: Context) {
            WorkManager.getInstance(context.applicationContext).enqueueUniqueWork(
                UNIQUE_WORK_NAME,
                ExistingWorkPolicy.APPEND_OR_REPLACE,
                request(),
            )
        }
    }
}
