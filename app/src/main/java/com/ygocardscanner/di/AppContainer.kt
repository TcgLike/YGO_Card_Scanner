package com.ygocardscanner.di

import android.content.Context
import androidx.work.WorkManager
import com.ygocardscanner.data.artwork.CardArtworkFileStore
import com.ygocardscanner.data.catalog.yugioh.HttpYgoJsonApiClient
import com.ygocardscanner.data.catalog.yugioh.HttpYgoProDeckApiClient
import com.ygocardscanner.data.catalog.yugioh.YgoJsonGermanPrintingSource
import com.ygocardscanner.data.catalog.yugioh.YgoProDeckCatalogSource
import com.ygocardscanner.data.deckimport.yugioh.RoomYgoDeckAvailabilityRepository
import com.ygocardscanner.data.deckimport.yugioh.RoomYgoDeckImportRepository
import com.ygocardscanner.data.local.AppDatabase
import com.ygocardscanner.data.repository.CardArtworkRepository
import com.ygocardscanner.data.repository.CatalogRepository
import com.ygocardscanner.data.repository.GermanPrintingEnrichmentRepository
import com.ygocardscanner.data.repository.RoomCardArtworkRepository
import com.ygocardscanner.data.repository.RoomCatalogRepository
import com.ygocardscanner.data.repository.RoomCatalogViewerRepository
import com.ygocardscanner.data.repository.RoomGermanPrintingEnrichmentRepository
import com.ygocardscanner.data.repository.RoomInventoryRepository
import com.ygocardscanner.data.scanner.RoomCardScannerRepository
import com.ygocardscanner.data.settings.AppLanguageSettings
import com.ygocardscanner.data.work.AppWorkerFactory
import com.ygocardscanner.data.work.CardArtworkUpdateScheduler
import com.ygocardscanner.data.work.CatalogUpdateScheduler
import com.ygocardscanner.data.work.FullArtworkDownloadScheduler
import com.ygocardscanner.data.work.GermanPrintingUpdateScheduler

/** Explicit dependency graph for the single app-private Yu-Gi-Oh! workspace. */
class AppContainer(context: Context) {
    private val applicationContext = context.applicationContext
    private val workManager by lazy { WorkManager.getInstance(applicationContext) }

    val languageSettings: AppLanguageSettings by lazy { AppLanguageSettings(applicationContext) }

    private val ygoDatabase by lazy { AppDatabase.create(applicationContext) }
    private val ygoCatalogSource by lazy { YgoProDeckCatalogSource(HttpYgoProDeckApiClient()) }
    private val germanPrintingSource by lazy { YgoJsonGermanPrintingSource(HttpYgoJsonApiClient()) }

    private val ygoCatalogRepository: CatalogRepository by lazy {
        RoomCatalogRepository(ygoDatabase, ygoCatalogSource)
    }
    private val ygoArtworkRepository: CardArtworkRepository by lazy {
        RoomCardArtworkRepository(ygoDatabase, CardArtworkFileStore(applicationContext))
    }
    private val ygoGermanPrintingRepository: GermanPrintingEnrichmentRepository by lazy {
        RoomGermanPrintingEnrichmentRepository(ygoDatabase, germanPrintingSource)
    }

    val ygoWorkspace: CardWorkspace by lazy {
        CardWorkspace(
            catalogRepository = ygoCatalogRepository,
            catalogViewerRepository = RoomCatalogViewerRepository(ygoDatabase),
            inventoryRepository = RoomInventoryRepository(ygoDatabase),
            artworkRepository = ygoArtworkRepository,
            catalogUpdateScheduler = CatalogUpdateScheduler(workManager, ygoCatalogRepository),
            artworkPackScheduler = FullArtworkDownloadScheduler(workManager, ygoArtworkRepository),
            artworkUpdateScheduler = CardArtworkUpdateScheduler(workManager, ygoArtworkRepository),
            scannerRepository = RoomCardScannerRepository(ygoDatabase),
            deckImportRepository = RoomYgoDeckImportRepository(ygoDatabase),
            deckAvailabilityRepository = RoomYgoDeckAvailabilityRepository(ygoDatabase),
        )
    }

    val germanPrintingRepository: GermanPrintingEnrichmentRepository get() = ygoGermanPrintingRepository
    val germanPrintingUpdateScheduler by lazy {
        GermanPrintingUpdateScheduler(workManager, ygoGermanPrintingRepository)
    }

    val workerFactory: AppWorkerFactory by lazy {
        AppWorkerFactory(
            catalogRepository = ygoCatalogRepository,
            artworkRepository = ygoArtworkRepository,
            germanPrintingRepository = ygoGermanPrintingRepository,
        )
    }
}