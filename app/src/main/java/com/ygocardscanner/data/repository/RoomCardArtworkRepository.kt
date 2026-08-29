package com.ygocardscanner.data.repository

import com.ygocardscanner.data.artwork.CardArtworkFileStore
import com.ygocardscanner.data.local.AppDatabase
import com.ygocardscanner.data.local.entity.CardArtwork
import com.ygocardscanner.data.local.entity.CardArtworkCache
import com.ygocardscanner.model.CardArtworkDownloadState

class RoomCardArtworkRepository(
    private val database: AppDatabase,
    private val fileStore: CardArtworkFileStore,
    private val now: () -> Long = System::currentTimeMillis,
) : CardArtworkRepository {
    private val artworkDao = database.artworkDao()

    override suspend fun queueDownload(cardId: String): Boolean {
        val artwork = artworkDao.getActiveArtwork(cardId) ?: return false
        val existing = artworkDao.getCache(cardId)
        if (isAvailableFor(artwork, existing)) return false
        if (existing?.remoteUrlSnapshot == artwork.remoteUrl &&
            existing.downloadState in setOf(
                CardArtworkDownloadState.QUEUED.code,
                CardArtworkDownloadState.DOWNLOADING.code,
            )
        ) {
            return false
        }
        artworkDao.upsertCache(cacheFor(artwork, CardArtworkDownloadState.QUEUED))
        return true
    }

    override suspend fun downloadArtwork(cardId: String) {
        val artwork = artworkDao.getActiveArtwork(cardId) ?: return
        val existing = artworkDao.getCache(cardId)
        if (isAvailableFor(artwork, existing)) return

        artworkDao.upsertCache(cacheFor(artwork, CardArtworkDownloadState.DOWNLOADING))
        val fileName = fileStore.download(artwork.artworkId, artwork.remoteUrl)
        artworkDao.upsertCache(
            cacheFor(
                artwork = artwork,
                state = CardArtworkDownloadState.AVAILABLE,
                fileName = fileName,
            ),
        )
    }

    override suspend fun markRetry(cardId: String) {
        updateFailureState(cardId, CardArtworkDownloadState.QUEUED, RETRY_MESSAGE)
    }

    override suspend fun markFailed(cardId: String) {
        updateFailureState(cardId, CardArtworkDownloadState.FAILED, FAILURE_MESSAGE)
    }

    private suspend fun updateFailureState(
        cardId: String,
        state: CardArtworkDownloadState,
        message: String,
    ) {
        val artwork = artworkDao.getActiveArtwork(cardId) ?: return
        artworkDao.upsertCache(cacheFor(artwork, state, message = message))
    }

    private fun isAvailableFor(artwork: CardArtwork, cache: CardArtworkCache?): Boolean =
        cache?.remoteUrlSnapshot == artwork.remoteUrl &&
            cache.downloadState == CardArtworkDownloadState.AVAILABLE.code &&
            fileStore.resolve(cache.localFileName) != null

    private fun cacheFor(
        artwork: CardArtwork,
        state: CardArtworkDownloadState,
        fileName: String? = null,
        message: String? = null,
    ): CardArtworkCache {
        val timestamp = now()
        return CardArtworkCache(
            cardId = artwork.cardId,
            remoteUrlSnapshot = artwork.remoteUrl,
            localFileName = fileName,
            downloadState = state.code,
            lastAttemptAtEpochMillis = when (state) {
                CardArtworkDownloadState.QUEUED,
                CardArtworkDownloadState.DOWNLOADING,
                CardArtworkDownloadState.FAILED,
                -> timestamp

                CardArtworkDownloadState.NOT_DOWNLOADED,
                CardArtworkDownloadState.AVAILABLE,
                -> null
            },
            lastSuccessAtEpochMillis = if (state == CardArtworkDownloadState.AVAILABLE) timestamp else null,
            safeErrorText = message?.take(CardArtworkCache.MAX_SAFE_ERROR_TEXT_LENGTH),
        )
    }

    private companion object {
        const val RETRY_MESSAGE = "Card image download will retry when a connection is available."
        const val FAILURE_MESSAGE = "Card image could not be downloaded. You can retry it later."
    }
}
