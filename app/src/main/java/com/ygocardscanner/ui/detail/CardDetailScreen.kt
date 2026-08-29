package com.ygocardscanner.ui.detail

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.Image
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

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            if (event is CardDetailEvent.EntryDeleted) onDeleted()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Card detail") },
                navigationIcon = { TextButton(onClick = onBack) { Text("Back") } },
            )
        },
    ) { innerPadding ->
        when {
            state.isLoading -> LoadingState()
            state.errorMessage != null && state.entry == null -> ErrorState(state.errorMessage.orEmpty(), viewModel::retry)
            state.entry == null -> EmptyState(
                title = "Entry not found",
                message = "It may have been removed from this device.",
                actionLabel = "Back to collection",
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
                onDelete = { showDeleteConfirmation = true },
            )
        }
    }

    if (showConditionPicker) {
        val entry = state.entry
        AlertDialog(
            onDismissRequest = { showConditionPicker = false },
            title = { Text("Card condition") },
            text = { Column { CardCondition.entries.forEach { condition -> TextButton(onClick = { showConditionPicker = false; viewModel.updateCondition(condition) }) { Text(condition.label) } } } },
            confirmButton = { TextButton(onClick = { showConditionPicker = false }) { Text("Cancel") } },
        )
    }
    if (showDeleteConfirmation) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmation = false },
            title = { Text("Remove card entry?") },
            text = { Text("This removes this inventory entry from this device. It does not alter the catalog.") },
            confirmButton = {
                TextButton(onClick = { showDeleteConfirmation = false; viewModel.deleteEntry() }) {
                    Text("Remove")
                }
            },
            dismissButton = { TextButton(onClick = { showDeleteConfirmation = false }) { Text("Cancel") } },
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
    onDelete: () -> Unit,
) {
    var quantityText by remember(entry.entryId, entry.quantity) { mutableStateOf(entry.quantity.toString()) }
    var quantityError by remember(entry.entryId) { mutableStateOf<String?>(null) }

    LazyColumn(modifier = modifier) {
        item {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(entry.cardName, style = MaterialTheme.typography.headlineSmall)
                ArtworkContent(
                    artwork = entry.artwork,
                    cardName = entry.cardName,
                    onRequestArtwork = onRequestArtwork,
                )
                if (entry.canonicalName != entry.cardName) {
                    Text(entry.canonicalName, style = MaterialTheme.typography.bodyMedium)
                }
                DetailLine("Set code", entry.setCode ?: "Not recorded")
                DetailLine("Set", entry.setName ?: "Unknown")
                DetailLine("Language", entry.language.label)
                DetailLine("Rarity", entry.rarity ?: "Not recorded")
                DetailLine("Edition", entry.edition.label)
                DetailLine("Condition", entry.condition.label)
                TextButton(onClick = onEditCondition, enabled = !isSaving) { Text("Edit condition") }
                entry.passcode?.let { DetailLine("Passcode", it) }
                if (entry.printingKind.code == "unknown") {
                    DetailLine("Printing", "Unknown printing")
                }
                OutlinedTextField(
                    value = quantityText,
                    onValueChange = { quantityText = it },
                    modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                    label = { Text("Quantity") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
                quantityError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                Row(modifier = Modifier.padding(top = 8.dp)) {
                    OutlinedButton(
                        onClick = { onQuantityChanged(entry.quantity - 1) },
                        enabled = !isSaving && entry.quantity > 1,
                    ) { Text("−1") }
                    OutlinedButton(
                        onClick = { onQuantityChanged(entry.quantity + 1) },
                        modifier = Modifier.padding(start = 8.dp),
                        enabled = !isSaving,
                    ) { Text("+1") }
                    Button(
                        onClick = {
                            val parsed = quantityText.toIntOrNull()
                            if (parsed == null || parsed <= 0) {
                                quantityError = "Quantity must be at least 1."
                            } else {
                                quantityError = null
                                onQuantityChanged(parsed)
                            }
                        },
                        modifier = Modifier.padding(start = 8.dp),
                        enabled = !isSaving,
                    ) { Text("Save") }
                }
                if (entry.notes.isNotBlank()) {
                    Text("Notes", modifier = Modifier.padding(top = 20.dp), style = MaterialTheme.typography.titleSmall)
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
                    Text("Remove from collection", color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
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
            contentDescription = "English artwork for $cardName",
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxWidth().height(260.dp).padding(top = 12.dp),
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
            ) "Retry image download" else "Download image")
        }
    }
}