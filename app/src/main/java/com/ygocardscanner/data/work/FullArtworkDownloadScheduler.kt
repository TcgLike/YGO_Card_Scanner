package com.ygocardscanner.data.work

import androidx.work.ExistingWorkPolicy
import androidx.work.WorkManager
import com.ygocardscanner.data.repository.CardArtworkRepository
import kotlinx.coroutines.CancellationException

/** Starts the explicit, opt-in English artwork pack. It never runs automatically. */
class FullArtworkDownloadScheduler(
    private val workManager: WorkManager,
    private val artworkRepository: CardArtworkRepository,
) {
    suspend fun enqueue() {
        if (!artworkRepository.prepareFullPack()) return
        try {
            workManager.enqueueUniqueWork(
                FullArtworkDownloadWorker.UNIQUE_WORK_NAME,
                ExistingWorkPolicy.REPLACE,
                FullArtworkDownloadWorker.request(),
            )
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            artworkRepository.markFullPackFailed()
        }
    }
}
