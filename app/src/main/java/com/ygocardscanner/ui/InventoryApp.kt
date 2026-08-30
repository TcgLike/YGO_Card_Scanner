package com.ygocardscanner.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.ygocardscanner.di.AppContainer
import com.ygocardscanner.di.CardWorkspace
import com.ygocardscanner.model.CardGame
import com.ygocardscanner.ui.add.AddToCollectionScreen
import com.ygocardscanner.ui.add.AddToCollectionViewModel
import com.ygocardscanner.ui.catalog.CatalogViewerScreen
import com.ygocardscanner.ui.catalog.CatalogViewerViewModel
import com.ygocardscanner.ui.collection.CollectionListScreen
import com.ygocardscanner.ui.collection.CollectionListViewModel
import com.ygocardscanner.ui.detail.CardDetailScreen
import com.ygocardscanner.ui.detail.CardDetailViewModel
import com.ygocardscanner.ui.deckimport.YgoDeckImportScreen
import com.ygocardscanner.ui.deckimport.YgoDeckImportViewModel
import com.ygocardscanner.ui.localization.LocalAppLanguage
import com.ygocardscanner.ui.manual.ManualAddScreen
import com.ygocardscanner.ui.manual.ManualAddViewModel
import com.ygocardscanner.ui.navigation.Destinations
import com.ygocardscanner.ui.scanner.CardScannerScreen
import com.ygocardscanner.ui.scanner.ScannerViewModel
import com.ygocardscanner.ui.settings.SettingsScreen
import com.ygocardscanner.ui.settings.SettingsViewModel

@Composable
fun InventoryApp(container: AppContainer) {
    val navController = rememberNavController()
    val appLanguage by container.languageSettings.language.collectAsState()
    val selectedGame by container.languageSettings.selectedGame.collectAsState()
    val workspace = remember(selectedGame) { container.workspace(selectedGame) }

    fun switchWorkspace(game: CardGame) {
        if (game != selectedGame) {
            container.languageSettings.setSelectedGame(game)
            navController.popBackStack(Destinations.COLLECTION, inclusive = false)
        }
    }

    CompositionLocalProvider(LocalAppLanguage provides appLanguage) {
        WorkspaceNavigation(
            container = container,
            workspace = workspace,
            onGameSelected = ::switchWorkspace,
            navController = navController,
        )
    }
}

@Composable
private fun WorkspaceNavigation(
    container: AppContainer,
    workspace: CardWorkspace,
    onGameSelected: (CardGame) -> Unit,
    navController: androidx.navigation.NavHostController,
) {
    val workspaceKey = workspace.game.code
    val collectionFactory = remember(workspace) {
        viewModelFactory { CollectionListViewModel(workspace.inventoryRepository, container.languageSettings) }
    }
    val catalogViewerFactory = remember(workspace) {
        viewModelFactory { CatalogViewerViewModel(workspace.catalogViewerRepository, container.languageSettings) }
    }
    val addFactory = remember(workspace) {
        viewModelFactory {
            AddToCollectionViewModel(
                catalogRepository = workspace.catalogRepository,
                inventoryRepository = workspace.inventoryRepository,
                catalogUpdateScheduler = workspace.catalogUpdateScheduler,
                artworkRepository = workspace.artworkRepository,
                artworkUpdateScheduler = workspace.artworkUpdateScheduler,
                artworkPackScheduler = workspace.artworkPackScheduler,
                languageSettings = container.languageSettings,
            )
        }
    }
    val deckImportFactory = workspace.deckImportRepository?.let { repository ->
        remember(workspace) {
            viewModelFactory { YgoDeckImportViewModel(repository, container.languageSettings) }
        }
    }
    val manualFactory = remember(workspace) {
        viewModelFactory { ManualAddViewModel(workspace.inventoryRepository) }
    }
    val settingsFactory = remember(workspace) {
        viewModelFactory {
            SettingsViewModel(
                game = workspace.game,
                languageSettings = container.languageSettings,
                catalogRepository = workspace.catalogRepository,
                artworkRepository = workspace.artworkRepository,
                catalogScheduler = workspace.catalogUpdateScheduler,
                germanPrintingRepository = if (workspace.game == CardGame.YUGIOH) container.germanPrintingRepository else null,
                germanPrintingScheduler = if (workspace.game == CardGame.YUGIOH) container.germanPrintingUpdateScheduler else null,
                artworkScheduler = workspace.artworkPackScheduler,
            )
        }
    }

    NavHost(navController = navController, startDestination = Destinations.COLLECTION) {
        composable(Destinations.COLLECTION) {
            val viewModel: CollectionListViewModel = viewModel(key = "collection-$workspaceKey", factory = collectionFactory)
            CollectionListScreen(
                viewModel = viewModel,
                game = workspace.game,
                onGameSelected = onGameSelected,
                onAddCard = { navController.navigate(Destinations.ADD) },
                onCatalog = { navController.navigate(Destinations.CATALOG) },
                onSettings = { navController.navigate(Destinations.SETTINGS) },
                onEntrySelected = { entryId -> navController.navigate(Destinations.detail(entryId)) },
            )
        }
        composable(Destinations.CATALOG) {
            val viewModel: CatalogViewerViewModel = viewModel(key = "catalog-$workspaceKey", factory = catalogViewerFactory)
            CatalogViewerScreen(viewModel = viewModel, onBack = { navController.popBackStack() })
        }
        composable(Destinations.ADD) {
            val viewModel: AddToCollectionViewModel = viewModel(key = "add-$workspaceKey", factory = addFactory)
            AddToCollectionScreen(
                viewModel = viewModel,
                canScan = workspace.supportsScanner,
                canImportDeck = deckImportFactory != null,
                englishOnly = workspace.game == CardGame.POKEMON,
                onBack = { navController.popBackStack() },
                onManualUnknownPrinting = { navController.navigate(Destinations.MANUAL) },
                onImportDeck = { if (deckImportFactory != null) navController.navigate(Destinations.DECK_IMPORT) },
                onScanCard = { if (workspace.supportsScanner) navController.navigate(Destinations.SCANNER) },
                onAdded = { navController.popBackStack(Destinations.COLLECTION, inclusive = false) },
            )
        }
        if (deckImportFactory != null) {
            composable(Destinations.DECK_IMPORT) {
                val viewModel: YgoDeckImportViewModel = viewModel(key = "deck-import-$workspaceKey", factory = deckImportFactory)
                YgoDeckImportScreen(
                    viewModel = viewModel,
                    onBack = { navController.popBackStack() },
                    onImported = { navController.popBackStack(Destinations.COLLECTION, inclusive = false) },
                )
            }
        }
        if (workspace.supportsScanner) {
            composable(Destinations.SCANNER) {
                val scannerRepository = requireNotNull(workspace.scannerRepository)
                val scannerFactory = remember(workspace) {
                    viewModelFactory {
                        ScannerViewModel(
                            scannerRepository,
                            workspace.inventoryRepository,
                            workspace.artworkRepository,
                            container.languageSettings,
                        )
                    }
                }
                val viewModel: ScannerViewModel = viewModel(key = "scanner-$workspaceKey", factory = scannerFactory)
                CardScannerScreen(
                    viewModel = viewModel,
                    onBack = { navController.popBackStack() },
                    onManualAdd = { navController.navigate(Destinations.MANUAL) },
                )
            }
        }
        composable(Destinations.MANUAL) {
            val viewModel: ManualAddViewModel = viewModel(key = "manual-$workspaceKey", factory = manualFactory)
            ManualAddScreen(
                viewModel = viewModel,
                englishOnly = workspace.game == CardGame.POKEMON,
                onBack = { navController.popBackStack() },
                onAdded = { navController.popBackStack(Destinations.COLLECTION, inclusive = false) },
            )
        }
        composable(Destinations.SETTINGS) {
            val viewModel: SettingsViewModel = viewModel(key = "settings-$workspaceKey", factory = settingsFactory)
            SettingsScreen(viewModel = viewModel, onBack = { navController.popBackStack() })
        }
        composable(
            route = Destinations.DETAIL_PATTERN,
            arguments = listOf(navArgument("entryId") { type = NavType.StringType }),
        ) { backStackEntry ->
            val entryId = requireNotNull(backStackEntry.arguments?.getString("entryId"))
            val factory = remember(entryId, workspace) {
                viewModelFactory {
                    CardDetailViewModel(
                        entryId = entryId,
                        inventoryRepository = workspace.inventoryRepository,
                        artworkUpdateScheduler = workspace.artworkUpdateScheduler,
                    )
                }
            }
            val viewModel: CardDetailViewModel = viewModel(key = "detail-$workspaceKey-$entryId", factory = factory)
            CardDetailScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() },
                onDeleted = { navController.popBackStack() },
            )
        }
    }
}