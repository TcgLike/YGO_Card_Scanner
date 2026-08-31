package com.ygocardscanner.data.repository

import androidx.room.withTransaction
import com.ygocardscanner.data.catalog.yugioh.GermanPrintingPayload
import com.ygocardscanner.data.catalog.yugioh.GermanPrintingSource
import com.ygocardscanner.data.local.AppDatabase
import com.ygocardscanner.data.local.entity.CatalogMetadata
import com.ygocardscanner.data.local.entity.CatalogUpdateState
import com.ygocardscanner.data.local.entity.Printing
import com.ygocardscanner.data.util.CatalogNormalizers
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/**
 * Keeps the optional source separate from the primary catalog replacement path. It may only add
 * German printing rows that resolve to an already installed primary card passcode.
 */
class RoomGermanPrintingEnrichmentRepository(
    private val database: AppDatabase,
    private val source: GermanPrintingSource,
    private val primaryCatalogSourceId: String = PRIMARY_CATALOG_SOURCE_ID,
    private val now: () -> Long = System::currentTimeMillis,
) : GermanPrintingEnrichmentRepository {
    private val catalogDao = database.catalogDao()
    private val updateStateDao = database.catalogUpdateStateDao()

    override fun observeUpdateStatus(): Flow<CatalogUpdateStatus?> =
        updateStateDao.observe(source.sourceId).map { state ->
            state?.let {
                CatalogUpdateStatus(
                    sourceId = it.sourceId,
                    phase = CatalogUpdatePhase.fromCode(it.phase),
                    lastAttemptAtEpochMillis = it.lastAttemptAtEpochMillis,
                    lastSuccessAtEpochMillis = it.lastSuccessAtEpochMillis,
                    lastFailureAtEpochMillis = it.lastFailureAtEpochMillis,
                    message = it.safeErrorText,
                )
            }
        }

    override suspend fun refresh(force: Boolean): CatalogRefreshResult {
        val availableRevision = source.fetchRevision()
        require(availableRevision.sourceId == source.sourceId) {
            "German printing source revision did not match the configured source."
        }
        val installed = catalogDao.getMetadata(source.sourceId)
        if (!force && installed?.catalogRevision == availableRevision.revision &&
            installed.contentHash == availableRevision.contentHash
        ) {
            return CatalogRefreshResult.UpToDate
        }
        replaceGermanPrintings(source.loadGermanPrintings())
        return CatalogRefreshResult.Updated
    }

    override suspend fun setEnabled(enabled: Boolean) {
        database.withTransaction {
            if (enabled) {
                catalogDao.activatePrintings(source.sourceId)
            } else {
                // Records remain retained for any inventory row that references them. Deactivation
                // only removes this optional source from new search and scanner candidates.
                catalogDao.deactivatePrintings(source.sourceId)
            }
        }
    }

    override suspend fun markUpdateQueued() = persistUpdateState(CatalogUpdatePhase.QUEUED)

    override suspend fun markUpdateRunning() = persistUpdateState(CatalogUpdatePhase.RUNNING)

    override suspend fun markUpdateRetry(message: String) =
        persistUpdateState(CatalogUpdatePhase.RETRYING, message)

    override suspend fun markUpdateFailed(message: String) =
        persistUpdateState(CatalogUpdatePhase.FAILED, message)

    override suspend fun markUpdateSucceeded() = persistUpdateState(CatalogUpdatePhase.SUCCEEDED)

    private suspend fun replaceGermanPrintings(payload: GermanPrintingPayload) {
        require(payload.sourceId == source.sourceId) {
            "German printing payload did not match the configured source."
        }
        require(payload.printings.isNotEmpty()) { "German printing payload is empty." }
        val timestamp = now()
        val mapped = withContext(Dispatchers.Default) {
            val primaryCards = catalogDao.getActiveCardPasscodes(primaryCatalogSourceId)
                .associate { it.passcode to it.cardId }
            payload.printings.mapNotNull { record ->
                val cardId = primaryCards[record.passcode] ?: return@mapNotNull null
                val normalizedSetCode = CatalogNormalizers.setCode(record.setCode) ?: return@mapNotNull null
                Printing(
                    printingId = "${source.sourceId}:printing:${record.providerPrintingId}",
                    cardId = cardId,
                    sourceId = source.sourceId,
                    providerPrintingId = record.providerPrintingId,
                    setCode = record.setCode,
                    normalizedSetCode = normalizedSetCode,
                    setName = record.setName,
                    languageCode = GERMAN_LANGUAGE_CODE,
                    rarityCode = record.rarityCode,
                    editionCode = record.editionCode,
                    isActive = true,
                    catalogRevision = payload.catalogRevision,
                    updatedAtEpochMillis = timestamp,
                )
            }.distinctBy { it.providerPrintingId }
        }
        require(mapped.isNotEmpty()) {
            "German printing source did not match any installed primary catalog card."
        }
        database.withTransaction {
            // This source has no cards, texts, or artwork of its own. Inventory is intentionally
            // untouched and removed source records remain inactive instead of being deleted.
            catalogDao.deactivatePrintings(source.sourceId)
            catalogDao.upsertPrintings(mapped)
            catalogDao.upsertMetadata(
                CatalogMetadata(
                    sourceId = source.sourceId,
                    catalogRevision = payload.catalogRevision,
                    contentHash = payload.contentHash,
                    updatedAtEpochMillis = timestamp,
                    lastError = null,
                ),
            )
        }
    }

    private suspend fun persistUpdateState(
        phase: CatalogUpdatePhase,
        message: String? = null,
    ) {
        val previous = updateStateDao.get(source.sourceId)
        val timestamp = now()
        val safeMessage = message?.trim()?.take(CatalogUpdateState.MAX_SAFE_ERROR_TEXT_LENGTH)
            ?.takeIf(String::isNotEmpty)
        updateStateDao.upsert(
            CatalogUpdateState(
                sourceId = source.sourceId,
                phase = phase.code,
                lastAttemptAtEpochMillis = when (phase) {
                    CatalogUpdatePhase.RUNNING,
                    CatalogUpdatePhase.RETRYING,
                    CatalogUpdatePhase.FAILED,
                    -> timestamp

                    CatalogUpdatePhase.QUEUED,
                    CatalogUpdatePhase.SUCCEEDED,
                    -> previous?.lastAttemptAtEpochMillis ?: timestamp
                },
                lastSuccessAtEpochMillis = if (phase == CatalogUpdatePhase.SUCCEEDED) timestamp
                else previous?.lastSuccessAtEpochMillis,
                lastFailureAtEpochMillis = when (phase) {
                    CatalogUpdatePhase.RETRYING,
                    CatalogUpdatePhase.FAILED,
                    -> timestamp

                    CatalogUpdatePhase.QUEUED,
                    CatalogUpdatePhase.RUNNING,
                    CatalogUpdatePhase.SUCCEEDED,
                    -> previous?.lastFailureAtEpochMillis
                },
                safeErrorText = if (phase == CatalogUpdatePhase.RETRYING || phase == CatalogUpdatePhase.FAILED) {
                    safeMessage
                } else {
                    null
                },
            ),
        )
    }

    private companion object {
        const val PRIMARY_CATALOG_SOURCE_ID = "ygoprodeck-v7"
        const val GERMAN_LANGUAGE_CODE = "de"
    }
}

