package com.ygocardscanner.di

import com.ygocardscanner.data.deckimport.yugioh.YgoDeckAvailabilityRepository
import com.ygocardscanner.data.deckimport.yugioh.YgoDeckImportRepository
import com.ygocardscanner.data.officialdecks.yugioh.OfficialDeckRepository
import com.ygocardscanner.data.repository.CardArtworkRepository
import com.ygocardscanner.data.repository.CatalogRepository
import com.ygocardscanner.data.repository.CatalogViewerRepository
import com.ygocardscanner.data.repository.InventoryRepository
import com.ygocardscanner.data.scanner.CardScannerRepository
import com.ygocardscanner.data.work.CardArtworkUpdateScheduler
import com.ygocardscanner.data.work.CatalogUpdateScheduler
import com.ygocardscanner.data.work.FullArtworkDownloadScheduler

/** Dependencies for the single app-private Yu-Gi-Oh! workspace. */
data class CardWorkspace(
    val catalogRepository: CatalogRepository,
    val catalogViewerRepository: CatalogViewerRepository,
    val inventoryRepository: InventoryRepository,
    val artworkRepository: CardArtworkRepository,
    val catalogUpdateScheduler: CatalogUpdateScheduler,
    val artworkPackScheduler: FullArtworkDownloadScheduler,
    val artworkUpdateScheduler: CardArtworkUpdateScheduler,
    val scannerRepository: CardScannerRepository,
    val deckImportRepository: YgoDeckImportRepository,
    val deckAvailabilityRepository: YgoDeckAvailabilityRepository,
    val officialDeckRepository: OfficialDeckRepository,
)