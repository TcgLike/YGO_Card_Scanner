package com.ygocardscanner.data.work

import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.ygocardscanner.data.repository.CardArtworkRepository
import com.ygocardscanner.model.CardGame
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException

/** Queues one local artwork download in the selected game workspace. */
class CardArtworkUpdateScheduler(
    private val workManager: WorkManager,
    private val artworkRepository: CardArtworkRepository,
    private val game: CardGame = CardGame.YUGIOH,
) {
    suspend fun enqueue(cardId: String) {
        if (!artworkRepository.queueDownload(cardId)) return
        try {
            val request = OneTimeWorkRequestBuilder<CardArtworkUpdateWorker>()
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .setRequiresStorageNotLow(true)
                        .build(),
                )
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .setInputData(workDataOf(
                    CardArtworkUpdateWorker.KEY_CARD_ID to cardId,
                    CatalogUpdateWorker.KEY_GAME to game.code,
                ))
                .build()
            workManager.enqueueUniqueWork(workName(cardId), ExistingWorkPolicy.KEEP, request)
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            artworkRepository.markFailed(cardId)
        }
    }

    private fun workName(cardId: String): String = when (game) {
        CardGame.YUGIOH -> CardArtworkUpdateWorker.UNIQUE_WORK_PREFIX + cardId
        CardGame.POKEMON -> "pokemon-${CardArtworkUpdateWorker.UNIQUE_WORK_PREFIX}$cardId"
    }
}