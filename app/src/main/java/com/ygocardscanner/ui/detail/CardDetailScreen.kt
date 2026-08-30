package com.ygocardscanner.ui.detail

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.ygocardscanner.data.artwork.CardArtworkFileStore
import com.ygocardscanner.model.CardArtworkDetail
import com.ygocardscanner.model.CardArtworkDownloadState
import com.ygocardscanner.model.InventoryEntryDetail
import com.ygocardscanner.model.CardCondition
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.ygocardscanner.ui.components.EmptyState
import com.ygocardscanner.ui.components.ErrorState
import com.ygocardscanner.ui.components.LoadingState
import com.ygocardscanner.ui.components.LocalArtworkViewer
import com.ygocardscanner.ui.localization.appText
import com.ygocardscanner.ui.localization.localizedLabel
import com.ygocardscanner.ui.localization.formattedAmount
import com.ygocardscanner.ui.localization.formattedObservedAt
import com.ygocardscanner.ui.localization.providerLabel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CardDetailScreen(
    viewModel: CardDetailViewModel,
    onBack: () -> Unit,
    onDeleted: () -> Unit,
) {
    val state by viewModel.uiState.collectAsState()
    var showDeleteConfirmation by remember { mutableStateOf(false) }
    var showConditionPicker by remember { mutableStateOf(false) }
    var fullscreenArtworkFileName by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            if (event is CardDetailEvent.EntryDeleted) onDeleted()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(appText("Card detail", "Kartendetails")) },
                navigationIcon = { TextButton(onClick = onBack) { Text(appText("Back", "Zurück")) } },
            )
        },
    ) { innerPadding ->
        when {
            state.isLoading -> LoadingState()
            state.errorMessage != null && state.entry == null -> ErrorState(state.errorMessage.orEmpty(), viewModel::retry)
            state.entry == null -> EmptyState(
                title = appText("Entry not found", "Eintrag nicht gefunden"),
                message = appText("It may have been removed from this device.", "Er wurde möglicherweise von diesem Gerät entfernt."),
                actionLabel = appText("Back to collection", "Zurück zur Sammlung"),
                onAction = onBack,
            )
            else -> EntryDetailContent(
                entry = requireNotNull(state.entry),
                isSaving = state.isSaving,
                errorMessage = state.errorMessage,
                modifier = Modifier.fillMaxSize().padding(innerPadding),
                onQuantityChanged = viewModel::updateQuantity,
                onEditCondition = { showConditionPicker = true },
                onRequestArtwork = viewModel::requestArtwork,
                onOpenArtwork = { fileName -> fullscreenArtworkFileName = fileName },
                onDelete = { showDeleteConfirmation = true },
            )
        }
    }

    fullscreenArtworkFileName?.let { fileName ->
        state.entry?.let { entry -> LocalArtworkViewer(fileName, entry.cardName, onDismiss = { fullscreenArtworkFileName = null }) }
    }

    if (showConditionPicker) {
        val entry = state.entry
        AlertDialog(
            onDismissRequest = { showConditionPicker = false },
            title = { Text(appText("Card condition", "Kartenzustand")) },
            text = { Column { CardCondition.entries.forEach { condition -> TextButton(onClick = { showConditionPicker = false; viewModel.updateCondition(condition) }) { Text(condition.localizedLabel()) } } } },
            confirmButton = { TextButton(onClick = { showConditionPicker = false }) { Text(appText("Cancel", "Abbrechen")) } },
        )
    }
    if (showDeleteConfirmation) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmation = false },
            title = { Text(appText("Remove card entry?", "Karteneintrag entfernen?")) },
            text = { Text(appText("This removes this inventory entry from this device. It does not alter the catalog.", "Dies entfernt diesen Sammlungseintrag von diesem Gerät. Der Katalog bleibt unverändert.")) },
            confirmButton = {
                TextButton(onClick = { showDeleteConfirmation = false; viewModel.deleteEntry() }) {
                    Text(appText("Remove", "Entfernen"))
                }
            },
            dismissButton = { TextButton(onClick = { showDeleteConfirmation = false }) { Text(appText("Cancel", "Abbrechen")) } },
        )
    }
}

@Composable
private fun EntryDetailContent(
    entry: InventoryEntryDetail,
    isSaving: Boolean,
    errorMessage: String?,
    modifier: Modifier,
    onQuantityChanged: (Int) -> Unit,
    onEditCondition: () -> Unit,
    onRequestArtwork: () -> Unit,
    onOpenArtwork: (String) -> Unit,
    onDelete: () -> Unit,
) {
    var quantityText by remember(entry.entryId, entry.quantity) { mutableStateOf(entry.quantity.toString()) }
    var quantityError by remember(entry.entryId) { mutableStateOf<String?>(null) }
    val quantityRequiredMessage = appText("Quantity must be at least 1.", "Die Menge muss mindestens 1 sein.")

    LazyColumn(modifier = modifier) {
        item {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(entry.cardName, style = MaterialTheme.typography.headlineSmall)
                ArtworkContent(
                    artwork = entry.artwork,
                    cardName = entry.cardName,
                    onRequestArtwork = onRequestArtwork,
                    onOpenArtwork = onOpenArtwork,
                )
                if (entry.canonicalName != entry.cardName) {
                    Text(entry.canonicalName, style = MaterialTheme.typography.bodyMedium)
                }
                DetailLine(appText("Set code", "Set-Code"), entry.setCode ?: appText("Not recorded", "Nicht erfasst"))
                DetailLine(appText("Set", "Set"), entry.setName ?: appText("Unknown", "Unbekannt"))
                DetailLine(appText("Language", "Sprache"), entry.language.localizedLabel())
                DetailLine(appText("Rarity", "Seltenheit"), entry.rarity ?: appText("Not recorded", "Nicht erfasst"))
                DetailLine(appText("Edition", "Auflage"), entry.edition.localizedLabel())
                DetailLine(appText("Condition", "Zustand"), entry.condition.localizedLabel())
                TextButton(onClick = onEditCondition, enabled = !isSaving) { Text(appText("Edit condition", "Zustand bearbeiten")) }
                entry.passcode?.let { DetailLine(appText("Passcode", "Passcode"), it) }
                if (entry.printingKind.code == "unknown") {
                    DetailLine(appText("Printing", "Druck"), appText("Unknown printing", "Unbekannter Druck"))
                }
                PriceOverview(entry.prices)
                OutlinedTextField(
                    value = quantityText,
                    onValueChange = { quantityText = it },
                    modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                    label = { Text(appText("Quantity", "Anzahl")) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
                quantityError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                Row(modifier = Modifier.padding(top = 8.dp)) {
                    OutlinedButton(
                        onClick = { onQuantityChanged(entry.quantity - 1) },
                        enabled = !isSaving && entry.quantity > 1,
                    ) { Text(appText("−1", "−1")) }
                    OutlinedButton(
                        onClick = { onQuantityChanged(entry.quantity + 1) },
                        modifier = Modifier.padding(start = 8.dp),
                        enabled = !isSaving,
                    ) { Text(appText("+1", "+1")) }
                    Button(
                        onClick = {
                            val parsed = quantityText.toIntOrNull()
                            if (parsed == null || parsed <= 0) {
                                quantityError = quantityRequiredMessage
                            } else {
                                quantityError = null
                                onQuantityChanged(parsed)
                            }
                        },
                        modifier = Modifier.padding(start = 8.dp),
                        enabled = !isSaving,
                    ) { Text(appText("Save", "Speichern")) }
                }
                if (entry.notes.isNotBlank()) {
                    Text(appText("Notes", "Notizen"), modifier = Modifier.padding(top = 20.dp), style = MaterialTheme.typography.titleSmall)
                    Text(entry.notes, modifier = Modifier.padding(top = 4.dp))
                }
                errorMessage?.let {
                    Text(it, modifier = Modifier.padding(top = 12.dp), color = MaterialTheme.colorScheme.error)
                }
                TextButton(
                    onClick = onDelete,
                    modifier = Modifier.padding(top = 20.dp),
                    enabled = !isSaving,
                ) {
                    Text(appText("Remove from collection", "Aus Sammlung entfernen"), color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

@Composable
private fun PriceOverview(prices: List<com.ygocardscanner.model.PriceQuote>) {
    Text(
        appText("Price references", "Preisreferenzen"),
        modifier = Modifier.padding(top = 20.dp),
        style = MaterialTheme.typography.titleSmall,
    )
    if (prices.isEmpty()) {
        Text(
            appText(
                "No local price data yet. Refresh the catalog to update public reference prices.",
                "Noch keine lokalen Preisdaten. Aktualisiere den Katalog für öffentliche Preisreferenzen.",
            ),
            modifier = Modifier.padding(top = 4.dp),
            style = MaterialTheme.typography.bodyMedium,
        )
        return
    }

    prices.forEach { price ->
        val label = if (price.isPrintingSpecific) {
            appText("${price.providerLabel()} (this set code)", "${price.providerLabel()} (dieser Set-Code)")
        } else {
            appText("${price.providerLabel()} (card-level)", "${price.providerLabel()} (kartenweit)")
        }
        DetailLine(label, price.formattedAmount())
        Text(
            appText(
                "Observed ${price.formattedObservedAt()}",
                "Abgerufen ${price.formattedObservedAt()}",
            ),
            style = MaterialTheme.typography.bodySmall,
        )
    }
    Text(
        appText(
            "Public reference prices are approximate. They do not account for the card's condition, edition, language, or a specific listing unless marked for this set code.",
            "Öffentliche Preisreferenzen sind nur Näherungswerte. Sie berücksichtigen Zustand, Auflage, Sprache oder ein bestimmtes Angebot nicht, außer sie sind für diesen Set-Code markiert.",
        ),
        modifier = Modifier.padding(top = 8.dp),
        style = MaterialTheme.typography.bodySmall,
    )
}
@Composable
private fun DetailLine(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
        Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.labelLarge)
        Text(value, modifier = Modifier.weight(1.5f), style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun ArtworkContent(
    artwork: CardArtworkDetail?,
    cardName: String,
    onRequestArtwork: () -> Unit,
    onOpenArtwork: (String) -> Unit,
) {
    if (artwork == null) return

    val context = LocalContext.current
    val fileStore = remember(context) { CardArtworkFileStore(context) }
    val localFile = remember(artwork.localFileName) { fileStore.resolve(artwork.localFileName) }
    val bitmap by produceState<Bitmap?>(initialValue = null, localFile) {
        value = withContext(Dispatchers.IO) {
            localFile?.let { file -> BitmapFactory.decodeFile(file.absolutePath) }
        }
    }

    if (bitmap != null) {
        Image(
            bitmap = requireNotNull(bitmap).asImageBitmap(),
            contentDescription = appText("English artwork for $cardName", "Englisches Kartenbild für $cardName"),
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxWidth().height(260.dp).padding(top = 12.dp).clickable { artwork.localFileName?.let(onOpenArtwork) },
        )
        return
    }

    val status = when (artwork.downloadState) {
        CardArtworkDownloadState.NOT_DOWNLOADED -> "Preparing the local English card image..."
        CardArtworkDownloadState.QUEUED -> "Card image download is queued."
        CardArtworkDownloadState.DOWNLOADING -> "Downloading the card image to this device..."
        CardArtworkDownloadState.AVAILABLE -> "The saved card image is unavailable."
        CardArtworkDownloadState.FAILED -> artwork.message ?: "The card image could not be downloaded."
    }
    Text(
        status,
        modifier = Modifier.padding(top = 12.dp),
        style = MaterialTheme.typography.bodyMedium,
    )
    if (artwork.downloadState !in setOf(
            CardArtworkDownloadState.QUEUED,
            CardArtworkDownloadState.DOWNLOADING,
        )
    ) {
        OutlinedButton(
            onClick = onRequestArtwork,
            modifier = Modifier.padding(top = 4.dp),
        ) {
            Text(if (artwork.downloadState == CardArtworkDownloadState.FAILED ||
                artwork.downloadState == CardArtworkDownloadState.AVAILABLE
            ) appText("Retry image download", "Bilddownload erneut versuchen") else appText("Download image", "Bild herunterladen"))
        }
    }
}
