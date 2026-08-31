package com.ygocardscanner.data.repository

import com.ygocardscanner.model.ArtworkPackStatus
import com.ygocardscanner.model.CardArtworkDetail
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/** Coordinates app-private English artwork caching; only workers make network calls. */
interface CardArtworkRepository {
    suspend fun queueDownload(cardId: String): Boolean
    suspend fun downloadArtwork(cardId: String)
    suspend fun markRetry(cardId: String)
    suspend fun markFailed(cardId: String)

    /** Room-backed local artwork state for the add and detail flows. */
    suspend fun getArtwork(cardId: String): CardArtworkDetail? = null

    fun observePackStatus(): Flow<ArtworkPackStatus?> = flowOf(null)
    suspend fun prepareFullPack(): Boolean = false
    suspend fun processNextFullPackBatch(): ArtworkPackBatchResult = ArtworkPackBatchResult.Complete
    suspend fun markFullPackRetry() = Unit
    suspend fun markFullPackFailed() = Unit
}

sealed interface ArtworkPackBatchResult {
    data object Continue : ArtworkPackBatchResult
    data object Complete : ArtworkPackBatchResult
    data object QuotaReached : ArtworkPackBatchResult
}