package com.ygocardscanner.data.work

import androidx.work.ExistingWorkPolicy
import androidx.work.WorkManager
import com.ygocardscanner.data.repository.CardArtworkRepository
import com.ygocardscanner.model.CardGame
import kotlinx.coroutines.CancellationException

/** Starts the explicit, opt-in English artwork pack for one isolated game workspace. */
class FullArtworkDownloadScheduler(
    private val workManager: WorkManager,
    private val artworkRepository: CardArtworkRepository,
    private val game: CardGame = CardGame.YUGIOH,
) {
    suspend fun enqueue() {
        if (!artworkRepository.prepareFullPack()) return
        try {
            workManager.enqueueUniqueWork(
                FullArtworkDownloadWorker.uniqueWorkName(game),
                ExistingWorkPolicy.REPLACE,
                FullArtworkDownloadWorker.request(game),
            )
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            artworkRepository.markFullPackFailed()
        }
    }
}