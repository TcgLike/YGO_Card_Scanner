package com.ygocardscanner.data.work

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.ygocardscanner.data.repository.ArtworkPackBatchResult
import com.ygocardscanner.data.repository.CardArtworkRepository
import com.ygocardscanner.model.CardGame
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException

/** Processes a small durable batch so the full offline English image pack can resume safely. */
class FullArtworkDownloadWorker(
    appContext: Context,
    params: WorkerParameters,
    private val artworkRepository: CardArtworkRepository,
) : CoroutineWorker(appContext, params) {
    override suspend fun getForegroundInfo(): ForegroundInfo = foregroundInfo()
    override suspend fun doWork(): Result = try {
        setForeground(foregroundInfo())
        when (artworkRepository.processNextFullPackBatch()) {
            ArtworkPackBatchResult.Continue -> {
                enqueueContinuation(applicationContext, CardGame.fromCode(inputData.getString(CatalogUpdateWorker.KEY_GAME)))
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

    private fun foregroundInfo(): ForegroundInfo {
        val manager = applicationContext.getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(NotificationChannel(CHANNEL_ID, "Card image download", NotificationManager.IMPORTANCE_LOW))
        }
        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(com.ygocardscanner.R.drawable.ic_launcher)
            .setContentTitle("Downloading card images")
            .setContentText("Keeping the offline English card image pack up to date.")
            .setOngoing(true)
            .build()
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) ForegroundInfo(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC) else ForegroundInfo(NOTIFICATION_ID, notification)
    }
    companion object {
        const val UNIQUE_WORK_NAME = "public-card-artwork-full-pack"
        private const val CHANNEL_ID = "offline_card_images"
        private const val NOTIFICATION_ID = 4101
        private const val MAX_TRANSIENT_ATTEMPTS = 3

        fun request(game: CardGame = CardGame.YUGIOH) = OneTimeWorkRequestBuilder<FullArtworkDownloadWorker>()
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .setRequiresStorageNotLow(true)
                    .build(),
            )
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .setInputData(androidx.work.workDataOf(CatalogUpdateWorker.KEY_GAME to game.code))
            .build()

        fun uniqueWorkName(game: CardGame): String = when (game) {
            CardGame.YUGIOH -> UNIQUE_WORK_NAME
            CardGame.POKEMON -> "pokemon-$UNIQUE_WORK_NAME"
        }

        fun enqueueContinuation(context: Context, game: CardGame = CardGame.YUGIOH) {
            WorkManager.getInstance(context.applicationContext).enqueueUniqueWork(
                uniqueWorkName(game),
                ExistingWorkPolicy.APPEND_OR_REPLACE,
                request(game),
            )
        }
    }
}
