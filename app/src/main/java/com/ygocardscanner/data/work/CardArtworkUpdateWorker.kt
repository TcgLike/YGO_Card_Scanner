package com.ygocardscanner.data.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.ygocardscanner.data.repository.CardArtworkRepository
import java.io.IOException
import kotlinx.coroutines.CancellationException

/** Downloads one English catalog image to app-private storage; it never exposes a remote image URL to UI. */
class CardArtworkUpdateWorker(
    appContext: Context,
    params: WorkerParameters,
    private val artworkRepository: CardArtworkRepository,
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val cardId = inputData.getString(KEY_CARD_ID) ?: return Result.failure()
        return try {
            artworkRepository.downloadArtwork(cardId)
            Result.success()
        } catch (error: CancellationException) {
            throw error
        } catch (_: IOException) {
            if (runAttemptCount >= MAX_TRANSIENT_ATTEMPTS) {
                artworkRepository.markFailed(cardId)
                Result.failure()
            } else {
                artworkRepository.markRetry(cardId)
                Result.retry()
            }
        } catch (_: Exception) {
            artworkRepository.markFailed(cardId)
            Result.failure()
        }
    }

    companion object {
        const val KEY_CARD_ID = "card_id"
        const val UNIQUE_WORK_PREFIX = "public-card-artwork-"
        private const val MAX_TRANSIENT_ATTEMPTS = 3
    }
}
