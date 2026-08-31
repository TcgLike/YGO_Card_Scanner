package com.ygocardscanner.data.repository

import androidx.room.withTransaction
import com.ygocardscanner.data.catalog.universal.CatalogMapper
import com.ygocardscanner.data.catalog.universal.CatalogSource
import com.ygocardscanner.data.catalog.universal.VersionedCatalogSource
import com.ygocardscanner.data.catalog.universal.CatalogPayload
import com.ygocardscanner.data.local.AppDatabase
import com.ygocardscanner.data.local.entity.CatalogUpdateState
import com.ygocardscanner.data.util.CatalogNormalizers
import com.ygocardscanner.model.CardEdition
import com.ygocardscanner.model.CardLanguage
import com.ygocardscanner.model.CatalogPrintingSummary
import com.ygocardscanner.model.PriceQuote
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class RoomCatalogRepository(
    private val database: AppDatabase,
    private val catalogSource: CatalogSource,
    private val now: () -> Long = System::currentTimeMillis,
) : CatalogRepository {
    private val catalogDao = database.catalogDao()
    private val artworkDao = database.artworkDao()
    private val priceDao = database.priceDao()
    private val updateStateDao = database.catalogUpdateStateDao()

    override fun observePrintings(
        query: String,
        language: CardLanguage,
    ): Flow<List<CatalogPrintingSummary>> {
        val nameQuery = CatalogNormalizers.name(query)
        val compactSetCodeQuery = CatalogNormalizers.setCode(query).orEmpty()
        return catalogDao.observeActivePrintings(
            nameQuery = nameQuery,
            compactQuery = compactSetCodeQuery,
            languageCode = language.code,
            hasQuery = nameQuery.isNotBlank() || compactSetCodeQuery.isNotBlank(),
            resultLimit = SEARCH_RESULT_LIMIT,
        ).map { rows ->
            rows.map { row ->
                CatalogPrintingSummary(
                    printingId = row.printing.printingId,
                    cardId = row.printing.cardId,
                    displayName = row.displayName,
                    setCode = row.printing.setCode,
                    setName = row.printing.setName,
                    language = CardLanguage.fromCode(row.printing.languageCode),
                    rarity = row.printing.rarityCode,
                    edition = CardEdition.fromCode(row.printing.editionCode),
                    referencePrice = row.toReferencePrice(),
                )
            }
        }
    }

    override fun observeCatalogUpdateStatus(): Flow<CatalogUpdateStatus?> =
        updateStateDao.observe(catalogSource.sourceId).map { state ->
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

    override suspend fun refreshCatalog(force: Boolean): CatalogRefreshResult {
        val versionedSource = catalogSource as? VersionedCatalogSource
        val availableRevision = versionedSource?.fetchRevision()
        if (availableRevision != null) {
            require(availableRevision.sourceId == catalogSource.sourceId) {
                "Catalog source revision did not match the configured source."
            }
            val installed = catalogDao.getMetadata(catalogSource.sourceId)
            if (!force && installed?.catalogRevision == availableRevision.revision) {
                return CatalogRefreshResult.UpToDate
            }
        }

        val payload = catalogSource.loadCatalog()
        require(payload.sourceId == catalogSource.sourceId) {
            "Catalog payload source did not match the configured source."
        }
        replaceCatalog(payload)
        return CatalogRefreshResult.Updated
    }

    override suspend fun markCatalogUpdateQueued() {
        persistUpdateState(CatalogUpdatePhase.QUEUED)
    }

    override suspend fun markCatalogUpdateRunning() {
        persistUpdateState(CatalogUpdatePhase.RUNNING)
    }

    override suspend fun markCatalogUpdateRetry(message: String) {
        persistUpdateState(CatalogUpdatePhase.RETRYING, message)
    }

    override suspend fun markCatalogUpdateFailed(message: String) {
        persistUpdateState(CatalogUpdatePhase.FAILED, message)
    }

    override suspend fun markCatalogUpdateSucceeded() {
        persistUpdateState(CatalogUpdatePhase.SUCCEEDED)
    }

    override suspend fun replaceCatalog(payload: CatalogPayload) {
        // Full payload validation and DTO-to-entity mapping are deliberately off the main thread.
        // Only a complete mapped catalog enters the transaction below.
        val mappedCatalog = withContext(Dispatchers.Default) {
            CatalogMapper.map(payload, now())
        }
        database.withTransaction {
            // No inventory row is ever deleted or updated here. Removed provider records remain in
            // the database as inactive rows so existing foreign keys and inventory snapshots live on.
            catalogDao.deactivateCards(payload.sourceId)
            catalogDao.deactivateCardTexts(payload.sourceId)
            artworkDao.deactivateArtworks(payload.sourceId)
            catalogDao.deactivatePrintings(payload.sourceId)
            catalogDao.upsertCards(mappedCatalog.cards)
            catalogDao.upsertCardTexts(mappedCatalog.cardTexts)
            artworkDao.upsertArtworks(mappedCatalog.artworks)
            catalogDao.upsertPrintings(mappedCatalog.printings)
            priceDao.upsertSnapshots(mappedCatalog.priceSnapshots)
            catalogDao.upsertMetadata(mappedCatalog.metadata)

            // The old tiny seed is retired only as part of a successful public import. It remains
            // stored (and any inventory linked to it remains valid), but it no longer duplicates
            // search results with public provider records.
            (catalogSource as? VersionedCatalogSource)
                ?.takeIf { payload.sourceId == catalogSource.sourceId }
                ?.supersededSourceIds
                ?.asSequence()
                ?.filter { it != payload.sourceId }
                ?.distinct()
                ?.forEach { retiredSourceId ->
                    catalogDao.deactivateCards(retiredSourceId)
                    catalogDao.deactivateCardTexts(retiredSourceId)
                    artworkDao.deactivateArtworks(retiredSourceId)
                    catalogDao.deactivatePrintings(retiredSourceId)
                }
        }
    }

    private suspend fun persistUpdateState(
        phase: CatalogUpdatePhase,
        message: String? = null,
    ) {
        val previous = updateStateDao.get(catalogSource.sourceId)
        val timestamp = now()
        val safeMessage = message?.trim()?.take(CatalogUpdateState.MAX_SAFE_ERROR_TEXT_LENGTH)
            ?.takeIf(String::isNotEmpty)
        updateStateDao.upsert(
            CatalogUpdateState(
                sourceId = catalogSource.sourceId,
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
                lastSuccessAtEpochMillis = if (phase == CatalogUpdatePhase.SUCCEEDED) {
                    timestamp
                } else {
                    previous?.lastSuccessAtEpochMillis
                },
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

    private fun com.ygocardscanner.data.local.query.CatalogPrintingRow.toReferencePrice(): PriceQuote? =
        if (
            printingPriceAmountMinor != null &&
            printingPriceCurrencyCode != null &&
            printingPriceObservedAtEpochMillis != null
        ) {
            PriceQuote(
                providerId = "set_price",
                currencyCode = printingPriceCurrencyCode,
                amountMinor = printingPriceAmountMinor,
                observedAtEpochMillis = printingPriceObservedAtEpochMillis,
                isPrintingSpecific = true,
            )
        } else if (
            fallbackPriceAmountMinor != null &&
            fallbackPriceCurrencyCode != null &&
            fallbackPriceObservedAtEpochMillis != null
        ) {
            PriceQuote(
                providerId = "cardmarket",
                currencyCode = fallbackPriceCurrencyCode,
                amountMinor = fallbackPriceAmountMinor,
                observedAtEpochMillis = fallbackPriceObservedAtEpochMillis,
                isPrintingSpecific = false,
            )
        } else {
            null
        }
    private companion object {
        const val SEARCH_RESULT_LIMIT = 100
    }
}

