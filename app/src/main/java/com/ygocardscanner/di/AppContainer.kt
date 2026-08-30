package com.ygocardscanner.di

import android.content.Context
import androidx.work.WorkManager
import com.ygocardscanner.data.artwork.CardArtworkFileStore
import com.ygocardscanner.data.catalog.yugioh.HttpYgoJsonApiClient
import com.ygocardscanner.data.catalog.pokemon.PokemonTcgCatalogSource
import com.ygocardscanner.data.catalog.yugioh.YgoJsonGermanPrintingSource
import com.ygocardscanner.data.catalog.yugioh.YgoProDeckCatalogSource
import com.ygocardscanner.data.catalog.pokemon.HttpPokemonTcgApiClient
import com.ygocardscanner.data.catalog.yugioh.HttpYgoProDeckApiClient
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
import com.ygocardscanner.model.CardGame

/** Explicit graph with physically separate private workspaces for Yu-Gi-Oh! and Pokémon. */
class AppContainer(context: Context) {
    private val applicationContext = context.applicationContext
    private val workManager by lazy { WorkManager.getInstance(applicationContext) }

    val languageSettings: AppLanguageSettings by lazy { AppLanguageSettings(applicationContext) }

    private val ygoDatabase by lazy { AppDatabase.create(applicationContext) }
    private val pokemonDatabase by lazy {
        AppDatabase.create(applicationContext, POKEMON_DATABASE_NAME)
    }

    private val ygoCatalogSource by lazy { YgoProDeckCatalogSource(HttpYgoProDeckApiClient()) }
    private val pokemonCatalogSource by lazy { PokemonTcgCatalogSource(HttpPokemonTcgApiClient()) }
    private val germanPrintingSource by lazy { YgoJsonGermanPrintingSource(HttpYgoJsonApiClient()) }

    private val ygoCatalogRepository: CatalogRepository by lazy {
        RoomCatalogRepository(ygoDatabase, ygoCatalogSource)
    }
    private val pokemonCatalogRepository: CatalogRepository by lazy {
        RoomCatalogRepository(pokemonDatabase, pokemonCatalogSource)
    }
    private val ygoArtworkRepository: CardArtworkRepository by lazy {
        RoomCardArtworkRepository(ygoDatabase, CardArtworkFileStore(applicationContext))
    }
    private val pokemonArtworkRepository: CardArtworkRepository by lazy {
        RoomCardArtworkRepository(
            pokemonDatabase,
            CardArtworkFileStore(
                applicationContext,
                directoryName = POKEMON_ARTWORK_DIRECTORY,
                providerImageHost = POKEMON_IMAGE_HOST,
            ),
        )
    }
    private val ygoGermanPrintingRepository: GermanPrintingEnrichmentRepository by lazy {
        RoomGermanPrintingEnrichmentRepository(ygoDatabase, germanPrintingSource)
    }

    val ygoWorkspace: CardWorkspace by lazy {
        CardWorkspace(
            game = CardGame.YUGIOH,
            catalogRepository = ygoCatalogRepository,
            catalogViewerRepository = RoomCatalogViewerRepository(ygoDatabase),
            inventoryRepository = RoomInventoryRepository(ygoDatabase),
            artworkRepository = ygoArtworkRepository,
            catalogUpdateScheduler = CatalogUpdateScheduler(workManager, ygoCatalogRepository, CardGame.YUGIOH),
            artworkPackScheduler = FullArtworkDownloadScheduler(workManager, ygoArtworkRepository, CardGame.YUGIOH),
            artworkUpdateScheduler = CardArtworkUpdateScheduler(workManager, ygoArtworkRepository, CardGame.YUGIOH),
            scannerRepository = RoomCardScannerRepository(ygoDatabase),
        )
    }
    val pokemonWorkspace: CardWorkspace by lazy {
        CardWorkspace(
            game = CardGame.POKEMON,
            catalogRepository = pokemonCatalogRepository,
            catalogViewerRepository = RoomCatalogViewerRepository(pokemonDatabase),
            inventoryRepository = RoomInventoryRepository(pokemonDatabase),
            artworkRepository = pokemonArtworkRepository,
            catalogUpdateScheduler = CatalogUpdateScheduler(workManager, pokemonCatalogRepository, CardGame.POKEMON),
            artworkPackScheduler = FullArtworkDownloadScheduler(workManager, pokemonArtworkRepository, CardGame.POKEMON),
            artworkUpdateScheduler = CardArtworkUpdateScheduler(workManager, pokemonArtworkRepository, CardGame.POKEMON),
        )
    }

    fun workspace(game: CardGame): CardWorkspace = when (game) {
        CardGame.YUGIOH -> ygoWorkspace
        CardGame.POKEMON -> pokemonWorkspace
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
            pokemonCatalogRepository = pokemonCatalogRepository,
            pokemonArtworkRepository = pokemonArtworkRepository,
        )
    }

    private companion object {
        const val POKEMON_DATABASE_NAME = "pokemon-card-scanner.db"
        const val POKEMON_ARTWORK_DIRECTORY = "pokemon_card_artwork"
        const val POKEMON_IMAGE_HOST = "images.pokemontcg.io"
    }
}