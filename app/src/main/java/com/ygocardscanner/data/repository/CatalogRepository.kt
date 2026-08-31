package com.ygocardscanner.data.repository

import com.ygocardscanner.data.catalog.universal.CatalogPayload
import com.ygocardscanner.model.CardLanguage
import com.ygocardscanner.model.CatalogPrintingSummary
import kotlinx.coroutines.flow.Flow

interface CatalogRepository {
    fun observePrintings(
        query: String,
        language: CardLanguage,
    ): Flow<List<CatalogPrintingSummary>>

    /**
     * The update worker records this state in Room so screens remain offline-first and survive a
     * process recreation while a catalog download is queued, retried, or fails.
     */
    fun observeCatalogUpdateStatus(): Flow<CatalogUpdateStatus?>

    /** Fetches and validates the public catalog before replacing its Room rows atomically. */
    suspend fun refreshCatalog(force: Boolean): CatalogRefreshResult

    suspend fun markCatalogUpdateQueued()

    suspend fun markCatalogUpdateRunning()

    suspend fun markCatalogUpdateRetry(message: String)

    suspend fun markCatalogUpdateFailed(message: String)

    suspend fun markCatalogUpdateSucceeded()

    /**
     * Atomically replaces catalog content for one source. Missing catalog records are retained and
     * marked inactive so existing inventory foreign keys and snapshots stay valid.
     */
    suspend fun replaceCatalog(payload: CatalogPayload)
}

