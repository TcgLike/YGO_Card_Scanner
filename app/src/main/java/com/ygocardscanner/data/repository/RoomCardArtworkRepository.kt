package com.ygocardscanner.data.repository

import com.ygocardscanner.data.artwork.CardArtworkFileStore
import com.ygocardscanner.data.local.AppDatabase
import com.ygocardscanner.data.local.entity.ArtworkPackState
import com.ygocardscanner.data.local.entity.CardArtwork
import com.ygocardscanner.data.local.entity.CardArtworkCache
import com.ygocardscanner.model.ArtworkPackPhase
import com.ygocardscanner.model.ArtworkPackStatus
import com.ygocardscanner.model.CardArtworkDetail
import com.ygocardscanner.model.CardArtworkDownloadState
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class RoomCardArtworkRepository(
    private val database: AppDatabase,
    private val fileStore: CardArtworkFileStore,
    private val now: () -> Long = System::currentTimeMillis,
) : CardArtworkRepository {
    private val artworkDao = database.artworkDao()
    private val downloadMutex = Mutex()

    override suspend fun queueDownload(cardId: String): Boolean {
        val artwork = artworkDao.getActiveArtwork(cardId) ?: return false
        val existing = artworkDao.getCache(cardId)
        if (isAvailableFor(artwork, existing)) return false
        if (existing?.remoteUrlSnapshot == artwork.remoteUrl &&
            existing.downloadState in setOf(CardArtworkDownloadState.QUEUED.code, CardArtworkDownloadState.DOWNLOADING.code)
        ) return false
        artworkDao.upsertCache(cacheFor(artwork, CardArtworkDownloadState.QUEUED))
        return true
    }

    override suspend fun downloadArtwork(cardId: String) = downloadMutex.withLock {
        val artwork = artworkDao.getActiveArtwork(cardId) ?: return
        downloadArtwork(artwork)
    }

    override suspend fun markRetry(cardId: String) {
        updateFailureState(cardId, CardArtworkDownloadState.QUEUED, RETRY_MESSAGE)
    }

    override suspend fun markFailed(cardId: String) {
        updateFailureState(cardId, CardArtworkDownloadState.FAILED, FAILURE_MESSAGE)
    }

    override suspend fun getArtwork(cardId: String): CardArtworkDetail? {
        val artwork = artworkDao.getActiveArtwork(cardId) ?: return null
        val cache = artworkDao.getCache(cardId)
        return CardArtworkDetail(
            localFileName = cache?.localFileName,
            downloadState = CardArtworkDownloadState.fromCode(cache?.downloadState),
            message = cache?.safeErrorText,
        )
    }

    override fun observePackStatus(): Flow<ArtworkPackStatus?> =
        artworkDao.observePackState(PUBLIC_ARTWORK_SOURCE_ID).map { state -> state?.toStatus() }

    override suspend fun prepareFullPack(): Boolean {
        val total = artworkDao.countActiveArtworks(PUBLIC_ARTWORK_SOURCE_ID)
        val cacheBytes = fileStore.cacheSizeBytes()
        val existing = artworkDao.getPackState(PUBLIC_ARTWORK_SOURCE_ID)
        val resumeState = existing?.takeIf { state ->
            val phase = ArtworkPackPhase.fromCode(state.phase)
            phase !in setOf(ArtworkPackPhase.SUCCEEDED, ArtworkPackPhase.QUOTA_REACHED) &&
                state.nextOffset in 0 until total
        }
        val completed = resumeState?.completedArtworkCount ?: 0
        val failed = resumeState?.failedArtworkCount ?: 0
        val nextOffset = resumeState?.nextOffset ?: 0
        val message = when {
            total == 0 -> "Download the card catalog before downloading card images."
            cacheBytes >= CardArtworkFileStore.MAX_CACHE_BYTES -> "The 4 GiB card-image cache limit has been reached."
            resumeState == null && fileStore.availableBytes() < CardArtworkFileStore.MINIMUM_FREE_BYTES_FOR_FULL_PACK ->
                "At least 3.5 GiB of free device storage is required before starting the full image download."
            else -> null
        }
        if (message != null) {
            savePackState(
                phase = if (total == 0) ArtworkPackPhase.FAILED else ArtworkPackPhase.QUOTA_REACHED,
                total = total,
                completed = completed,
                failed = failed,
                nextOffset = nextOffset,
                cacheBytes = cacheBytes,
                message = message,
            )
            return false
        }
        savePackState(ArtworkPackPhase.QUEUED, total, completed, failed, nextOffset, cacheBytes, null)
        return true
    }
    override suspend fun processNextFullPackBatch(): ArtworkPackBatchResult = downloadMutex.withLock {
        val existing = artworkDao.getPackState(PUBLIC_ARTWORK_SOURCE_ID) ?: return ArtworkPackBatchResult.Complete
        val phase = ArtworkPackPhase.fromCode(existing.phase)
        if (!phase.isInProgress) return ArtworkPackBatchResult.Complete
        savePackState(
            phase = ArtworkPackPhase.RUNNING,
            total = existing.totalArtworkCount,
            completed = existing.completedArtworkCount,
            failed = existing.failedArtworkCount,
            nextOffset = existing.nextOffset,
            cacheBytes = fileStore.cacheSizeBytes(),
            message = null,
        )
        val artworks = artworkDao.getActiveArtworkPage(PUBLIC_ARTWORK_SOURCE_ID, PACK_BATCH_SIZE, existing.nextOffset)
        if (artworks.isEmpty()) {
            savePackState(ArtworkPackPhase.SUCCEEDED, existing.totalArtworkCount, existing.completedArtworkCount, existing.failedArtworkCount, existing.nextOffset, fileStore.cacheSizeBytes(), null)
            return ArtworkPackBatchResult.Complete
        }

        var failures = existing.failedArtworkCount
        var completed = existing.completedArtworkCount
        try {
            artworks.forEach { artwork ->
                try {
                    downloadArtwork(artwork)
                } catch (_: IllegalArgumentException) {
                    artworkDao.upsertCache(cacheFor(artwork, CardArtworkDownloadState.FAILED, message = FAILURE_MESSAGE))
                    failures += 1
                }
                completed += 1
            }
        } catch (_: CardArtworkFileStore.ArtworkStorageQuotaExceededException) {
            savePackState(ArtworkPackPhase.QUOTA_REACHED, existing.totalArtworkCount, completed, failures, existing.nextOffset + (completed - existing.completedArtworkCount), fileStore.cacheSizeBytes(), "The 4 GiB card-image cache limit was reached. Existing local images remain available.")
            return ArtworkPackBatchResult.QuotaReached
        }
        savePackState(ArtworkPackPhase.QUEUED, existing.totalArtworkCount, completed, failures, existing.nextOffset + artworks.size, fileStore.cacheSizeBytes(), null)
        ArtworkPackBatchResult.Continue
    }

    override suspend fun markFullPackRetry() {
        val state = artworkDao.getPackState(PUBLIC_ARTWORK_SOURCE_ID) ?: return
        savePackState(ArtworkPackPhase.RETRYING, state.totalArtworkCount, state.completedArtworkCount, state.failedArtworkCount, state.nextOffset, fileStore.cacheSizeBytes(), RETRY_MESSAGE)
    }

    override suspend fun markFullPackFailed() {
        val state = artworkDao.getPackState(PUBLIC_ARTWORK_SOURCE_ID) ?: return
        savePackState(ArtworkPackPhase.FAILED, state.totalArtworkCount, state.completedArtworkCount, state.failedArtworkCount, state.nextOffset, fileStore.cacheSizeBytes(), FAILURE_MESSAGE)
    }

    private suspend fun downloadArtwork(artwork: CardArtwork) {
        val existing = artworkDao.getCache(artwork.cardId)
        if (isAvailableFor(artwork, existing)) return
        artworkDao.upsertCache(cacheFor(artwork, CardArtworkDownloadState.DOWNLOADING))
        val stored = fileStore.download(artwork.artworkId, artwork.remoteUrl)
        artworkDao.upsertCache(cacheFor(artwork, CardArtworkDownloadState.AVAILABLE, fileName = stored.fileName))
    }

    private suspend fun updateFailureState(cardId: String, state: CardArtworkDownloadState, message: String) {
        val artwork = artworkDao.getActiveArtwork(cardId) ?: return
        artworkDao.upsertCache(cacheFor(artwork, state, message = message))
    }

    private suspend fun savePackState(phase: ArtworkPackPhase, total: Int, completed: Int, failed: Int, nextOffset: Int, cacheBytes: Long, message: String?) {
        artworkDao.upsertPackState(ArtworkPackState(PUBLIC_ARTWORK_SOURCE_ID, phase.code, total, completed, failed, nextOffset, cacheBytes, now(), message?.take(ArtworkPackState.MAX_SAFE_ERROR_TEXT_LENGTH)))
    }

    private fun ArtworkPackState.toStatus() = ArtworkPackStatus(
        phase = ArtworkPackPhase.fromCode(phase), totalArtworkCount = totalArtworkCount,
        completedArtworkCount = completedArtworkCount, failedArtworkCount = failedArtworkCount,
        cachedBytes = cachedBytes, message = safeErrorText,
    )

    private fun isAvailableFor(artwork: CardArtwork, cache: CardArtworkCache?): Boolean =
        cache?.remoteUrlSnapshot == artwork.remoteUrl && cache.downloadState == CardArtworkDownloadState.AVAILABLE.code && fileStore.resolve(cache.localFileName) != null

    private fun cacheFor(artwork: CardArtwork, state: CardArtworkDownloadState, fileName: String? = null, message: String? = null): CardArtworkCache {
        val timestamp = now()
        return CardArtworkCache(
            cardId = artwork.cardId, remoteUrlSnapshot = artwork.remoteUrl, localFileName = fileName,
            downloadState = state.code,
            lastAttemptAtEpochMillis = if (state in setOf(CardArtworkDownloadState.QUEUED, CardArtworkDownloadState.DOWNLOADING, CardArtworkDownloadState.FAILED)) timestamp else null,
            lastSuccessAtEpochMillis = if (state == CardArtworkDownloadState.AVAILABLE) timestamp else null,
            safeErrorText = message?.take(CardArtworkCache.MAX_SAFE_ERROR_TEXT_LENGTH),
        )
    }

    companion object {
        const val PUBLIC_ARTWORK_SOURCE_ID = "ygoprodeck-v7"
        private const val PACK_BATCH_SIZE = 25
        private const val RETRY_MESSAGE = "Card image download will retry when a connection is available."
        private const val FAILURE_MESSAGE = "Card image could not be downloaded. You can retry it later."
    }
}