package com.ygocardscanner.di

import android.content.Context
import androidx.work.WorkManager
import com.ygocardscanner.data.artwork.CardArtworkFileStore
import com.ygocardscanner.data.settings.AppLanguageSettings
import com.ygocardscanner.data.catalog.YgoProDeckCatalogSource
import com.ygocardscanner.data.catalog.HttpYgoJsonApiClient
import com.ygocardscanner.data.catalog.YgoJsonGermanPrintingSource
import com.ygocardscanner.data.catalog.network.HttpYgoProDeckApiClient
import com.ygocardscanner.data.local.AppDatabase
import com.ygocardscanner.data.repository.CardArtworkRepository
import com.ygocardscanner.data.repository.CatalogRepository
import com.ygocardscanner.data.repository.InventoryRepository
import com.ygocardscanner.data.repository.GermanPrintingEnrichmentRepository
import com.ygocardscanner.data.repository.RoomCardArtworkRepository
import com.ygocardscanner.data.repository.RoomCatalogRepository
import com.ygocardscanner.data.repository.RoomInventoryRepository
import com.ygocardscanner.data.repository.RoomGermanPrintingEnrichmentRepository
import com.ygocardscanner.data.scanner.CardScannerRepository
import com.ygocardscanner.data.scanner.RoomCardScannerRepository
import com.ygocardscanner.data.work.AppWorkerFactory
import com.ygocardscanner.data.work.CardArtworkUpdateScheduler
import com.ygocardscanner.data.work.FullArtworkDownloadScheduler
import com.ygocardscanner.data.work.CatalogUpdateScheduler
import com.ygocardscanner.data.work.GermanPrintingUpdateScheduler

/** Small, explicit dependency graph for local inventory and public catalog updates. */
class AppContainer(context: Context) {
    private val applicationContext = context.applicationContext

    val languageSettings: AppLanguageSettings by lazy { AppLanguageSettings(applicationContext) }

    val database: AppDatabase by lazy {
        AppDatabase.create(applicationContext)
    }

    private val publicCatalogSource by lazy {
        YgoProDeckCatalogSource(HttpYgoProDeckApiClient())
    }
    private val germanPrintingSource by lazy {
        YgoJsonGermanPrintingSource(HttpYgoJsonApiClient())
    }


    val catalogRepository: CatalogRepository by lazy {
        RoomCatalogRepository(
            database = database,
            catalogSource = publicCatalogSource,
        )
    }

    val germanPrintingRepository: GermanPrintingEnrichmentRepository by lazy {
        RoomGermanPrintingEnrichmentRepository(
            database = database,
            source = germanPrintingSource,
        )
    }

    val inventoryRepository: InventoryRepository by lazy {
        RoomInventoryRepository(database)
    }

    val artworkRepository: CardArtworkRepository by lazy {
        RoomCardArtworkRepository(
            database = database,
            fileStore = CardArtworkFileStore(applicationContext),
        )
    }

    val scannerRepository: CardScannerRepository by lazy {
        RoomCardScannerRepository(database)
    }

    val workerFactory: AppWorkerFactory by lazy {
        AppWorkerFactory(catalogRepository, artworkRepository, germanPrintingRepository)
    }

    val catalogUpdateScheduler: CatalogUpdateScheduler by lazy {
        CatalogUpdateScheduler(
            workManager = WorkManager.getInstance(applicationContext),
            catalogRepository = catalogRepository,
        )
    }

    val germanPrintingUpdateScheduler: GermanPrintingUpdateScheduler by lazy {
        GermanPrintingUpdateScheduler(
            workManager = WorkManager.getInstance(applicationContext),
            repository = germanPrintingRepository,
        )
    }

    val artworkPackScheduler: FullArtworkDownloadScheduler by lazy {
        FullArtworkDownloadScheduler(
            workManager = WorkManager.getInstance(applicationContext),
            artworkRepository = artworkRepository,
        )
    }

    val artworkUpdateScheduler: CardArtworkUpdateScheduler by lazy {
        CardArtworkUpdateScheduler(
            workManager = WorkManager.getInstance(applicationContext),
            artworkRepository = artworkRepository,
        )
    }
}
