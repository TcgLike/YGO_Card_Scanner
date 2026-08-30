package com.ygocardscanner.data.repository

import com.ygocardscanner.data.local.AppDatabase
import com.ygocardscanner.data.util.CatalogNormalizers
import com.ygocardscanner.model.CardArtworkDetail
import com.ygocardscanner.model.CardArtworkDownloadState
import com.ygocardscanner.model.CardLanguage
import com.ygocardscanner.model.CatalogCardSummary
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Read-only local catalog browser. It never requests catalog or artwork data from the network. */
interface CatalogViewerRepository {
    fun observeCards(query: String, displayLanguage: CardLanguage): Flow<List<CatalogCardSummary>>
}

class RoomCatalogViewerRepository(database: AppDatabase) : CatalogViewerRepository {
    private val catalogDao = database.catalogDao()

    override fun observeCards(
        query: String,
        displayLanguage: CardLanguage,
    ): Flow<List<CatalogCardSummary>> {
        val normalizedName = CatalogNormalizers.name(query)
        val normalizedSetCode = CatalogNormalizers.setCode(query).orEmpty()
        return catalogDao.observeActiveCards(
            nameQuery = normalizedName,
            compactQuery = normalizedSetCode,
            languageCode = displayLanguage.code,
            hasQuery = normalizedName.isNotBlank() || normalizedSetCode.isNotBlank(),
        ).map { rows ->
            rows.map { row ->
                CatalogCardSummary(
                    cardId = row.cardId,
                    displayName = row.displayName,
                    passcode = row.passcode,
                    artwork = row.artworkRemoteUrl?.let {
                        CardArtworkDetail(
                            localFileName = row.artworkLocalFileName,
                            downloadState = CardArtworkDownloadState.fromCode(row.artworkDownloadState),
                            message = row.artworkMessage,
                        )
                    },
                    isOwned = row.isOwned,
                )
            }
        }
    }
}
