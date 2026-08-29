package com.ygocardscanner.ui.add

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import com.ygocardscanner.data.artwork.CardArtworkFileStore
import com.ygocardscanner.model.ArtworkPackPhase
import com.ygocardscanner.model.ArtworkPackStatus
import com.ygocardscanner.model.CardArtworkDetail
import com.ygocardscanner.model.CardArtworkDownloadState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.ygocardscanner.data.repository.CatalogUpdatePhase
import com.ygocardscanner.data.repository.CatalogUpdateStatus
import com.ygocardscanner.model.CardCondition
import com.ygocardscanner.model.CardEdition
import com.ygocardscanner.model.CardLanguage
import com.ygocardscanner.model.CatalogPrintingSummary
import com.ygocardscanner.model.KnownPrintingDraft
import com.ygocardscanner.ui.components.EmptyState
import com.ygocardscanner.ui.components.ErrorState
import com.ygocardscanner.ui.components.LoadingState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddToCollectionScreen(
    viewModel: AddToCollectionViewModel,
    onBack: () -> Unit,
    onManualUnknownPrinting: () -> Unit,
    onAdded: () -> Unit,
) {
    val state by viewModel.uiState.collectAsState()
    var selected by remember { mutableStateOf<CatalogPrintingSummary?>(null) }
    var quantity by rememberSaveable { mutableStateOf("1") }
    var language by rememberSaveable { mutableStateOf(CardLanguage.ENGLISH) }
    var rarity by rememberSaveable { mutableStateOf("") }
    var edition by rememberSaveable { mutableStateOf(CardEdition.UNKNOWN) }
    var condition by rememberSaveable { mutableStateOf(CardCondition.NEAR_MINT) }
    var notes by rememberSaveable { mutableStateOf("") }
    var validationMessage by rememberSaveable { mutableStateOf<String?>(null) }

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            if (event is AddToCollectionEvent.EntryAdded) onAdded()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (selected == null) "Add to collection" else "Confirm card") },
                navigationIcon = { TextButton(onClick = { if (selected == null) onBack() else { selected = null; viewModel.clearSelectedArtwork() } }) { Text("Back") } },
            )
        },
    ) { innerPadding ->
        if (selected == null) {
            CatalogPicker(
                state = state,
                modifier = Modifier.fillMaxSize().padding(innerPadding),
                onQueryChange = viewModel::updateQuery,
                onSelect = { printing ->
                    selected = printing
                    language = printing.language
                    rarity = printing.rarity.orEmpty()
                    edition = printing.edition
                    validationMessage = null
                },
                onManualUnknownPrinting = onManualUnknownPrinting,
                onRetry = viewModel::retry,
                onDisplayLanguageChange = viewModel::updateDisplayLanguage,
                onRequestCatalogUpdate = viewModel::requestCatalogUpdate,
                artworkPackStatus = state.artworkPackStatus,
                isRequestingArtworkPack = state.isRequestingArtworkPack,
                onRequestArtworkPack = viewModel::requestArtworkPack,
            )
        } else {
            val printing = selected ?: return@Scaffold
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(innerPadding),
            ) {
                item {
                    Text(
                        printing.displayName,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        style = MaterialTheme.typography.headlineSmall,
                    )
                    Text(
                        listOfNotNull(printing.setCode, printing.setName).joinToString(" · "),
                        modifier = Modifier.padding(horizontal = 16.dp),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    AddArtworkPreview(
                        artwork = state.selectedArtwork,
                        cardName = printing.displayName,
                        onRefresh = { viewModel.refreshSelectedArtwork(printing.cardId) },
                    )
                    InventoryFields(
                        quantity = quantity,
                        onQuantityChange = { quantity = it },
                        language = language,
                        onLanguageChange = { language = it },
                        rarity = rarity,
                        onRarityChange = { rarity = it },
                        edition = edition,
                        onEditionChange = { edition = it },
                        condition = condition,
                        onConditionChange = { condition = it },
                        notes = notes,
                        onNotesChange = { notes = it },
                    )
                    validationMessage?.let { message ->
                        Text(
                            message,
                            modifier = Modifier.padding(horizontal = 16.dp),
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                    state.errorMessage?.let { message ->
                        Text(
                            message,
                            modifier = Modifier.padding(horizontal = 16.dp),
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                    Button(
                        onClick = {
                            val parsedQuantity = quantity.toIntOrNull()
                            if (parsedQuantity == null || parsedQuantity <= 0) {
                                validationMessage = "Quantity must be at least 1."
                            } else {
                                viewModel.addKnownPrinting(
                                    KnownPrintingDraft(
                                        printingId = printing.printingId,
                                        language = language,
                                        rarity = rarity.trim().ifBlank { null },
                                        edition = edition,
                                        condition = condition,
                                        quantity = parsedQuantity,
                                        notes = notes.trim(),
                                    ),
                                )
                            }
                        },
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        enabled = !state.isSaving,
                    ) {
                        Text(if (state.isSaving) "Adding…" else "Add to collection")
                    }
                }
            }
        }
    }
}

@Composable
private fun CatalogPicker(
    state: AddToCollectionUiState,
    modifier: Modifier,
    onQueryChange: (String) -> Unit,
    onSelect: (CatalogPrintingSummary) -> Unit,
    onManualUnknownPrinting: () -> Unit,
    onRetry: () -> Unit,
    onDisplayLanguageChange: (CardLanguage) -> Unit,
    onRequestCatalogUpdate: () -> Unit,
    artworkPackStatus: ArtworkPackStatus?,
    isRequestingArtworkPack: Boolean,
    onRequestArtworkPack: () -> Unit,
) {
    Column(modifier = modifier) {
        OutlinedTextField(
            value = state.query,
            onValueChange = onQueryChange,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            label = { Text("Search card catalog") },
            supportingText = { Text("Search name, passcode, or set code") },
            singleLine = true,
        )
        CatalogDisplayLanguageSelector(
            selectedLanguage = state.displayLanguage,
            onLanguageChange = onDisplayLanguageChange,
        )
        CatalogUpdateControls(
            status = state.catalogUpdateStatus,
            isRequestingUpdate = state.isRequestingCatalogUpdate,
            onRequestUpdate = onRequestCatalogUpdate,
        )
        ArtworkPackControls(artworkPackStatus, isRequestingArtworkPack, onRequestArtworkPack)
        TextButton(onClick = onManualUnknownPrinting, modifier = Modifier.padding(horizontal = 8.dp)) {
            Text("Add an unknown printing manually")
        }
        when {
            state.isLoading -> LoadingState("Searching your local card catalog...")
            state.errorMessage != null -> ErrorState(state.errorMessage, onRetry)
            state.query.isBlank() -> EmptyState(
                title = "Search the card catalog",
                message = "Enter a card name, passcode, or set code. Download the catalog if it is not available yet.",
            )
            state.printings.isEmpty() -> EmptyState(
                title = "No catalog cards found",
                message = "Try another local search, download an update, or add an unknown printing manually.",
                actionLabel = "Add unknown printing",
                onAction = onManualUnknownPrinting,
            )
            else -> LazyColumn {
                items(state.printings, key = CatalogPrintingSummary::printingId) { printing ->
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)
                            .clickable { onSelect(printing) },
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(printing.displayName, style = MaterialTheme.typography.titleMedium)
                            Text(
                                listOfNotNull(printing.setCode, printing.setName, printing.rarity).joinToString(" · "),
                                modifier = Modifier.padding(top = 4.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun InventoryFields(
    quantity: String,
    onQuantityChange: (String) -> Unit,
    language: CardLanguage,
    onLanguageChange: (CardLanguage) -> Unit,
    rarity: String,
    onRarityChange: (String) -> Unit,
    edition: CardEdition,
    onEditionChange: (CardEdition) -> Unit,
    condition: CardCondition,
    onConditionChange: (CardCondition) -> Unit,
    notes: String,
    onNotesChange: (String) -> Unit,
) {
    OutlinedTextField(
        value = quantity,
        onValueChange = onQuantityChange,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        label = { Text("Quantity") },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
    )
    ChoiceRow("Language", language.label) {
        onLanguageChange(if (language == CardLanguage.ENGLISH) CardLanguage.GERMAN else CardLanguage.ENGLISH)
    }
    OutlinedTextField(
        value = rarity,
        onValueChange = onRarityChange,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        label = { Text("Rarity (optional)") },
        singleLine = true,
    )
    ChoiceRow("Edition", edition.label) {
        val options = CardEdition.entries
        onEditionChange(options[(options.indexOf(edition) + 1) % options.size])
    }
    ChoiceRow("Condition", condition.label) {
        val options = CardCondition.entries
        onConditionChange(options[(options.indexOf(condition) + 1) % options.size])
    }
    OutlinedTextField(
        value = notes,
        onValueChange = onNotesChange,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        label = { Text("Notes (optional)") },
        minLines = 3,
    )
}

@Composable
private fun ChoiceRow(label: String, value: String, onClick: () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 2.dp)) {
        Text(label, modifier = Modifier.weight(1f).padding(top = 12.dp))
        TextButton(onClick = onClick) { Text(value) }
    }
}

@Composable
private fun CatalogDisplayLanguageSelector(
    selectedLanguage: CardLanguage,
    onLanguageChange: (CardLanguage) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            "Display language",
            modifier = Modifier.weight(1f).padding(top = 12.dp),
            style = MaterialTheme.typography.bodyMedium,
        )
        TextButton(
            onClick = { onLanguageChange(CardLanguage.ENGLISH) },
            enabled = selectedLanguage != CardLanguage.ENGLISH,
        ) {
            Text("English")
        }
        TextButton(
            onClick = { onLanguageChange(CardLanguage.GERMAN) },
            enabled = selectedLanguage != CardLanguage.GERMAN,
        ) {
            Text("Deutsch")
        }
    }
}

@Composable
private fun CatalogUpdateControls(
    status: CatalogUpdateStatus?,
    isRequestingUpdate: Boolean,
    onRequestUpdate: () -> Unit,
) {
    val updateInProgress = status?.phase?.isInProgress == true
    val actionLabel = when (status?.phase) {
        null -> "Download catalog"
        CatalogUpdatePhase.FAILED -> "Retry catalog update"
        else -> "Check for updates"
    }
    val statusMessage = when (status?.phase) {
        null -> "The public card catalog has not been downloaded yet."
        CatalogUpdatePhase.QUEUED -> "Catalog update is queued."
        CatalogUpdatePhase.RUNNING -> "Updating the local card catalog..."
        CatalogUpdatePhase.RETRYING -> "Catalog update will retry when possible."
        CatalogUpdatePhase.SUCCEEDED -> "The local card catalog is ready."
        CatalogUpdatePhase.FAILED -> status?.message ?: "The last catalog update failed."
    }

    Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(statusMessage, style = MaterialTheme.typography.bodyMedium)
            Button(
                onClick = onRequestUpdate,
                modifier = Modifier.padding(top = 8.dp),
                enabled = !isRequestingUpdate && !updateInProgress,
            ) {
                Text(if (isRequestingUpdate) "Scheduling..." else actionLabel)
            }
        }
    }
}

@Composable
private fun AddArtworkPreview(
    artwork: CardArtworkDetail?,
    cardName: String,
    onRefresh: () -> Unit,
) {
    if (artwork == null) return
    val context = LocalContext.current
    val fileStore = remember(context) { CardArtworkFileStore(context) }
    val image = remember(artwork.localFileName) { fileStore.resolve(artwork.localFileName) }
    val bitmap by androidx.compose.runtime.produceState<Bitmap?>(initialValue = null, image) {
        value = withContext(Dispatchers.IO) { image?.let { BitmapFactory.decodeFile(it.absolutePath) } }
    }
    if (bitmap != null) {
        Image(
            bitmap = requireNotNull(bitmap).asImageBitmap(),
            contentDescription = "English artwork for $cardName",
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxWidth().height(260.dp).padding(horizontal = 16.dp, vertical = 8.dp),
        )
    } else {
        val message = when (artwork.downloadState) {
            CardArtworkDownloadState.NOT_DOWNLOADED -> "Preparing the local English card image..."
            CardArtworkDownloadState.QUEUED -> "Card image download is queued."
            CardArtworkDownloadState.DOWNLOADING -> "Downloading the card image to this device..."
            CardArtworkDownloadState.AVAILABLE -> "The saved card image is unavailable."
            CardArtworkDownloadState.FAILED -> artwork.message ?: "The card image could not be downloaded."
        }
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            Text(message, style = MaterialTheme.typography.bodyMedium)
            TextButton(onClick = onRefresh) { Text("Refresh image") }
        }
    }
}

@Composable
private fun ArtworkPackControls(
    status: ArtworkPackStatus?,
    isRequesting: Boolean,
    onRequest: () -> Unit,
) {
    val inProgress = status?.phase?.isInProgress == true
    val message = when (status?.phase) {
        null -> "Optional: download one primary English image for every catalog card to this device. Requires 3.5 GiB free space; the cache is capped at 4 GiB."
        ArtworkPackPhase.QUEUED, ArtworkPackPhase.RUNNING -> "Downloading offline card images: ${status.completedArtworkCount} / ${status.totalArtworkCount}."
        ArtworkPackPhase.RETRYING -> "Offline image download will retry when connected."
        ArtworkPackPhase.SUCCEEDED -> "Offline English card images are ready: ${status.completedArtworkCount} cards."
        ArtworkPackPhase.QUOTA_REACHED, ArtworkPackPhase.FAILED -> status.message ?: "Offline image download stopped."
    }
    Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(message, style = MaterialTheme.typography.bodyMedium)
            Button(
                onClick = onRequest,
                modifier = Modifier.padding(top = 8.dp),
                enabled = !isRequesting && !inProgress,
            ) { Text(if (isRequesting) "Scheduling..." else if (status?.phase == ArtworkPackPhase.FAILED) "Retry image download" else "Download offline card images") }
        }
    }
}