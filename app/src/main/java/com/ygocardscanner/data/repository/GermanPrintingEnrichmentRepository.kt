package com.ygocardscanner.data.repository

import kotlinx.coroutines.flow.Flow

/** Isolated opt-in repository for community-sourced German physical printing metadata. */
interface GermanPrintingEnrichmentRepository {
    fun observeUpdateStatus(): Flow<CatalogUpdateStatus?>

    suspend fun refresh(force: Boolean): CatalogRefreshResult

    suspend fun setEnabled(enabled: Boolean)

    suspend fun markUpdateQueued()

    suspend fun markUpdateRunning()

    suspend fun markUpdateRetry(message: String)

    suspend fun markUpdateFailed(message: String)

    suspend fun markUpdateSucceeded()
}

