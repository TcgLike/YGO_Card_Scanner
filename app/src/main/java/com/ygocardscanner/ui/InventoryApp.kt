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
import com.ygocardscanner.ui.add.AddToCollectionScreen
import com.ygocardscanner.ui.add.AddToCollectionViewModel
import com.ygocardscanner.ui.collection.CollectionListScreen
import com.ygocardscanner.ui.collection.CollectionListViewModel
import com.ygocardscanner.ui.detail.CardDetailScreen
import com.ygocardscanner.ui.detail.CardDetailViewModel
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
    val collectionFactory = remember(container) {
        viewModelFactory { CollectionListViewModel(container.inventoryRepository) }
    }
    val addFactory = remember(container) {
        viewModelFactory {
            AddToCollectionViewModel(
                catalogRepository = container.catalogRepository,
                inventoryRepository = container.inventoryRepository,
                catalogUpdateScheduler = container.catalogUpdateScheduler,
                artworkRepository = container.artworkRepository,
                artworkUpdateScheduler = container.artworkUpdateScheduler,
                artworkPackScheduler = container.artworkPackScheduler,
                languageSettings = container.languageSettings,
            )
        }
    }
    val scannerFactory = remember(container) {
        viewModelFactory { ScannerViewModel(container.scannerRepository, container.inventoryRepository) }
    }
    val manualFactory = remember(container) {
        viewModelFactory { ManualAddViewModel(container.inventoryRepository) }
    }
    val settingsFactory = remember(container) {
        viewModelFactory {
            SettingsViewModel(
                languageSettings = container.languageSettings,
                catalogRepository = container.catalogRepository,
                artworkRepository = container.artworkRepository,
                catalogScheduler = container.catalogUpdateScheduler,
                artworkScheduler = container.artworkPackScheduler,
            )
        }
    }

    CompositionLocalProvider(LocalAppLanguage provides appLanguage) {
        NavHost(navController = navController, startDestination = Destinations.COLLECTION) {
            composable(Destinations.COLLECTION) {
                val viewModel: CollectionListViewModel = viewModel(factory = collectionFactory)
                CollectionListScreen(
                    viewModel = viewModel,
                    onAddCard = { navController.navigate(Destinations.ADD) },
                    onSettings = { navController.navigate(Destinations.SETTINGS) },
                    onEntrySelected = { entryId -> navController.navigate(Destinations.detail(entryId)) },
                )
            }
            composable(Destinations.ADD) {
                val viewModel: AddToCollectionViewModel = viewModel(factory = addFactory)
                AddToCollectionScreen(
                    viewModel = viewModel,
                    onBack = { navController.popBackStack() },
                    onManualUnknownPrinting = { navController.navigate(Destinations.MANUAL) },
                    onScanCard = { navController.navigate(Destinations.SCANNER) },
                    onAdded = { navController.popBackStack(Destinations.COLLECTION, inclusive = false) },
                )
            }
            composable(Destinations.SCANNER) {
                val viewModel: ScannerViewModel = viewModel(factory = scannerFactory)
                CardScannerScreen(
                    viewModel = viewModel,
                    onBack = { navController.popBackStack() },
                    onManualAdd = { navController.navigate(Destinations.MANUAL) },
                    onAdded = { navController.popBackStack(Destinations.COLLECTION, inclusive = false) },
                )
            }
            composable(Destinations.MANUAL) {
                val viewModel: ManualAddViewModel = viewModel(factory = manualFactory)
                ManualAddScreen(
                    viewModel = viewModel,
                    onBack = { navController.popBackStack() },
                    onAdded = { navController.popBackStack(Destinations.COLLECTION, inclusive = false) },
                )
            }
            composable(Destinations.SETTINGS) {
                val viewModel: SettingsViewModel = viewModel(factory = settingsFactory)
                SettingsScreen(viewModel = viewModel, onBack = { navController.popBackStack() })
            }
            composable(
                route = Destinations.DETAIL_PATTERN,
                arguments = listOf(navArgument("entryId") { type = NavType.StringType }),
            ) { backStackEntry ->
                val entryId = requireNotNull(backStackEntry.arguments?.getString("entryId"))
                val factory = remember(entryId, container) {
                    viewModelFactory {
                        CardDetailViewModel(
                            entryId = entryId,
                            inventoryRepository = container.inventoryRepository,
                            artworkUpdateScheduler = container.artworkUpdateScheduler,
                        )
                    }
                }
                val viewModel: CardDetailViewModel = viewModel(factory = factory)
                CardDetailScreen(
                    viewModel = viewModel,
                    onBack = { navController.popBackStack() },
                    onDeleted = { navController.popBackStack() },
                )
            }
        }
    }
}