package com.ygocardscanner.di

import com.ygocardscanner.data.deckimport.yugioh.YgoDeckImportRepository
import com.ygocardscanner.data.deckimport.yugioh.YgoDeckAvailabilityRepository
import com.ygocardscanner.data.repository.CardArtworkRepository
import com.ygocardscanner.data.repository.CatalogRepository
import com.ygocardscanner.data.repository.CatalogViewerRepository
import com.ygocardscanner.data.repository.InventoryRepository
import com.ygocardscanner.data.scanner.CardScannerRepository
import com.ygocardscanner.data.work.CardArtworkUpdateScheduler
import com.ygocardscanner.data.work.CatalogUpdateScheduler
import com.ygocardscanner.data.work.FullArtworkDownloadScheduler
import com.ygocardscanner.model.CardGame

/** All dependencies and persistent data for one card game. Workspaces never share a Room database. */
data class CardWorkspace(
    val game: CardGame,
    val catalogRepository: CatalogRepository,
    val catalogViewerRepository: CatalogViewerRepository,
    val inventoryRepository: InventoryRepository,
    val artworkRepository: CardArtworkRepository,
    val catalogUpdateScheduler: CatalogUpdateScheduler,
    val artworkPackScheduler: FullArtworkDownloadScheduler,
    val artworkUpdateScheduler: CardArtworkUpdateScheduler,
    val scannerRepository: CardScannerRepository? = null,
    val deckImportRepository: YgoDeckImportRepository? = null,
    val deckAvailabilityRepository: YgoDeckAvailabilityRepository? = null,
) {
    val supportsScanner: Boolean get() = scannerRepository != null
}
