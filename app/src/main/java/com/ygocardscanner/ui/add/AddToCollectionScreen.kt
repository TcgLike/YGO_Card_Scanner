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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.FileOpen
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import com.ygocardscanner.data.artwork.CardArtworkFileStore
import com.ygocardscanner.model.CardArtworkDetail
import com.ygocardscanner.model.CardArtworkDownloadState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.ygocardscanner.model.CardCondition
import com.ygocardscanner.model.CardEdition
import com.ygocardscanner.model.CardLanguage
import com.ygocardscanner.model.CatalogPrintingSummary
import com.ygocardscanner.model.KnownPrintingDraft
import com.ygocardscanner.ui.components.EmptyState
import com.ygocardscanner.ui.components.ErrorState
import com.ygocardscanner.ui.components.LoadingState
import com.ygocardscanner.ui.localization.UiText
import com.ygocardscanner.ui.localization.appText
import com.ygocardscanner.ui.localization.localizedLabel
import com.ygocardscanner.ui.localization.formattedAmount

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddToCollectionScreen(
    viewModel: AddToCollectionViewModel,
    canScan: Boolean,
    canImportDeck: Boolean,
    englishOnly: Boolean,
    onBack: () -> Unit,
    onManualUnknownPrinting: () -> Unit,
    onImportDeck: () -> Unit,
    onScanCard: () -> Unit,
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
    val quantityRequiredMessage = appText("Quantity must be at least 1.", "Die Menge muss mindestens 1 sein.")

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            if (event is AddToCollectionEvent.EntryAdded) onAdded()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (selected == null) appText("Add to collection", "Zur Sammlung hinzufügen") else appText("Confirm card", "Karte bestätigen")) },
                navigationIcon = { TextButton(onClick = { if (selected == null) onBack() else { selected = null; viewModel.clearSelectedArtwork() } }) { Text(appText("Back", "Zurück")) } },
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
                canImportDeck = canImportDeck,
                onImportDeck = onImportDeck,
                canScan = canScan,
                onScanCard = onScanCard,
                onRetry = viewModel::retry,
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
                        allowGermanLanguage = !englishOnly,
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
                                validationMessage = quantityRequiredMessage
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
                        Text(if (state.isSaving) appText("Adding…", "Wird hinzugefügt…") else appText("Add to collection", "Zur Sammlung hinzufügen"))
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
    canScan: Boolean,
    canImportDeck: Boolean,
    onScanCard: () -> Unit,
    onImportDeck: () -> Unit,
    onRetry: () -> Unit,
) {
    Column(modifier = modifier) {
        OutlinedTextField(
            value = state.query,
            onValueChange = onQueryChange,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            label = { Text(appText("Search card catalog", "Kartenkatalog durchsuchen")) },
            supportingText = { Text(appText("Search name, passcode, or set code", "Name, Passcode oder Set-Code suchen")) },
            singleLine = true,
        )
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            if (canScan) {
                IconButton(onClick = onScanCard) {
                    Icon(Icons.Outlined.CameraAlt, contentDescription = appText(UiText.ScanCard))
                }
            }
            if (canImportDeck) {
                IconButton(onClick = onImportDeck) {
                    Icon(Icons.Outlined.FileOpen, contentDescription = appText(UiText.ImportDeck))
                }
            }
            IconButton(onClick = onManualUnknownPrinting) {
                Icon(Icons.Outlined.EditNote, contentDescription = appText(UiText.AddUnknownPrinting))
            }
        }
        when {
            state.isLoading -> LoadingState(appText("Searching your local card catalog…", "Lokaler Kartenkatalog wird durchsucht…"))
            state.errorMessage != null -> ErrorState(state.errorMessage, onRetry)
            state.query.isBlank() -> EmptyState(
                title = appText("Search the card catalog", "Kartenkatalog durchsuchen"),
                message = appText("Enter a card name, passcode, or set code. Download the catalog if it is not available yet.", "Gib einen Kartennamen, Passcode oder Set-Code ein. Lade den Katalog herunter, falls er noch nicht verfügbar ist."),
            )
            state.printings.isEmpty() -> EmptyState(
                title = appText("No catalog cards found", "Keine Katalogkarten gefunden"),
                message = appText("Try another local search, download an update, or add an unknown printing manually.", "Versuche eine andere lokale Suche, lade ein Update herunter oder füge einen unbekannten Druck manuell hinzu."),
                actionLabel = appText("Add unknown printing", "Unbekannten Druck hinzufügen"),
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
                            printing.referencePrice?.let { price ->
                                Text(
                                    if (price.isPrintingSpecific) {
                                        appText(
                                            "Set price: ${price.formattedAmount()}",
                                            "Set-Preis: ${price.formattedAmount()}",
                                        )
                                    } else {
                                        appText(
                                            "Cardmarket reference (card): ${price.formattedAmount()}",
                                            "Cardmarket-Referenz (Karte): ${price.formattedAmount()}",
                                        )
                                    },
                                    modifier = Modifier.padding(top = 4.dp),
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                            }                        }
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
    allowGermanLanguage: Boolean,
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
        label = { Text(appText("Quantity", "Menge")) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
    )
    if (allowGermanLanguage) {
        ChoiceRow(appText("Language", "Sprache"), language.localizedLabel()) {
            onLanguageChange(if (language == CardLanguage.ENGLISH) CardLanguage.GERMAN else CardLanguage.ENGLISH)
        }
    } else {
        ChoiceRow(appText("Language", "Sprache"), appText("English", "Englisch")) {}
    }
    OutlinedTextField(
        value = rarity,
        onValueChange = onRarityChange,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        label = { Text(appText("Rarity (optional)", "Seltenheit (optional)")) },
        singleLine = true,
    )
    ChoiceRow(appText("Edition", "Edition"), edition.localizedLabel()) {
        val options = CardEdition.entries
        onEditionChange(options[(options.indexOf(edition) + 1) % options.size])
    }
    ChoiceRow(appText("Condition", "Zustand"), condition.localizedLabel()) {
        val options = CardCondition.entries
        onConditionChange(options[(options.indexOf(condition) + 1) % options.size])
    }
    OutlinedTextField(
        value = notes,
        onValueChange = onNotesChange,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        label = { Text(appText("Notes (optional)", "Notizen (optional)")) },
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
            contentDescription = appText("English artwork for $cardName", "Englisches Kartenbild für $cardName"),
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxWidth().height(260.dp).padding(horizontal = 16.dp, vertical = 8.dp),
        )
    } else {
        val message = when (artwork.downloadState) {
            CardArtworkDownloadState.NOT_DOWNLOADED -> appText("Preparing the local English card image…", "Lokales englisches Kartenbild wird vorbereitet…")
            CardArtworkDownloadState.QUEUED -> appText("Card image download is queued.", "Kartenbild-Download ist vorgemerkt.")
            CardArtworkDownloadState.DOWNLOADING -> appText("Downloading the card image to this device…", "Kartenbild wird auf dieses Gerät heruntergeladen…")
            CardArtworkDownloadState.AVAILABLE -> appText("The saved card image is unavailable.", "Das gespeicherte Kartenbild ist nicht verfügbar.")
            CardArtworkDownloadState.FAILED -> artwork.message ?: appText("The card image could not be downloaded.", "Das Kartenbild konnte nicht heruntergeladen werden.")
        }
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            Text(message, style = MaterialTheme.typography.bodyMedium)
            TextButton(onClick = onRefresh) { Text(appText("Refresh image", "Bild aktualisieren")) }
        }
    }
}

