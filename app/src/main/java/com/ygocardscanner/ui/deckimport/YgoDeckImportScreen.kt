package com.ygocardscanner.ui.deckimport

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.ygocardscanner.data.deckimport.yugioh.YgoDeckImportCard
import com.ygocardscanner.model.CardCondition
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun YgoDeckImportScreen(
    viewModel: YgoDeckImportViewModel,
    onBack: () -> Unit,
    onImported: () -> Unit,
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var pastedInput by remember { mutableStateOf("") }
    var baseCodeInput by remember { mutableStateOf("") }
    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        scope.launch {
            runCatching { readDocument(context, uri) }
                .onSuccess { contents ->
                    viewModel.preview(uri.lastPathSegment ?: "deck.ydk", contents, baseCodeInput)
                }
                .onFailure { error -> viewModel.reportReadError(error.message ?: "The selected deck file could not be opened.") }
        }
    }

    LaunchedEffect(viewModel) {
        viewModel.events.collect { if (it is YgoDeckImportEvent.Imported) onImported() }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Import Yu-Gi-Oh! deck") },
                navigationIcon = { TextButton(onClick = onBack) { Text("Back") } },
            )
        },
    ) { padding ->
        if (state.preview == null) {
            Column(Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
                Text("Import a local .ydk file or paste a ydke:// deck code. The file stays on this device.")
                OutlinedTextField(
                    value = baseCodeInput,
                    onValueChange = { baseCodeInput = it },
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                    label = { Text("Deck printing base code (optional)") },
                    supportingText = { Text("Example: CH02-DE or CH02-DEXXX. Matching local printings are placed first.") },
                    singleLine = true,
                )
                Button(onClick = { filePicker.launch("*/*") }, modifier = Modifier.padding(top = 12.dp)) {
                    Text("Choose .ydk file")
                }
                OutlinedTextField(
                    value = pastedInput,
                    onValueChange = { pastedInput = it },
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                    label = { Text("Paste .ydk contents or ydke:// code") },
                    minLines = 6,
                )
                Button(
                    onClick = { viewModel.preview("Pasted deck", pastedInput, baseCodeInput) },
                    modifier = Modifier.padding(top = 8.dp),
                    enabled = pastedInput.isNotBlank() && !state.isLoading,
                ) { Text(if (state.isLoading) "Reading..." else "Review deck") }
                state.errorMessage?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 12.dp)) }
            }
        } else {
            DeckReview(state, viewModel, Modifier.fillMaxSize().padding(padding))
        }
    }
}

@Composable
private fun DeckReview(
    state: YgoDeckImportUiState,
    viewModel: YgoDeckImportViewModel,
    modifier: Modifier,
) {
    val preview = requireNotNull(state.preview)
    val matchedCards = preview.cards.count(YgoDeckImportCard::hasBaseCodeMatch)
    LazyColumn(modifier = modifier.padding(horizontal = 16.dp)) {
        item {
            Row(Modifier.fillMaxWidth().padding(top = 12.dp)) {
                Column(Modifier.weight(1f)) {
                    Text(preview.sourceLabel, style = MaterialTheme.typography.titleMedium)
                    Text("${preview.totalCardCount} cards - ${preview.cards.size} unique passcodes")
                }
                TextButton(onClick = viewModel::clearPreview) { Text("Change code") }
            }
            Text(
                "Deck files identify cards by passcode, not physical printings. Unknown printing remains the safe default unless one local match is unambiguous.",
                modifier = Modifier.padding(top = 8.dp),
                style = MaterialTheme.typography.bodySmall,
            )
            if (preview.baseCodePrefix != null) {
                Text(
                    "$matchedCards of ${preview.cards.size} local cards match ${state.baseCodeInput.trim()}. Matching cards are first and ordered by the code number.",
                    modifier = Modifier.padding(top = 8.dp),
                )
                if (state.cardsWithoutBaseCode.isNotEmpty()) {
                    Text(
                        "No local printing with that base code was found for: ${state.cardsWithoutBaseCode.joinToString { it.displayName ?: it.passcode }}.",
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                    Text(
                        "For German print codes, enable and update the optional German printing backup in Settings before reviewing the deck again.",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
            ConditionPicker(state.condition, viewModel::setCondition)
            OutlinedTextField(
                value = state.notes,
                onValueChange = viewModel::setNotes,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                label = { Text("Notes for imported entries (optional)") },
            )
            if (state.unresolvedCards.isNotEmpty()) {
                Text(
                    "${state.unresolvedCards.size} passcode(s) are not in the local catalog. Download/update the Yu-Gi-Oh! catalog or remove them before importing.",
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = 12.dp),
                )
            }
        }
        items(preview.cards, key = YgoDeckImportCard::passcode) { card ->
            DeckCardRow(
                card = card,
                selectedPrintingId = state.selectedPrintingIds[card.passcode],
                onPrintingSelected = { viewModel.selectPrinting(card.passcode, it) },
            )
        }
        item {
            state.errorMessage?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 12.dp)) }
            Button(
                onClick = viewModel::importDeck,
                modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
                enabled = state.unresolvedCards.isEmpty() && !state.isSaving,
            ) { Text(if (state.isSaving) "Adding deck..." else "Add deck to collection") }
        }
    }
}

@Composable
private fun DeckCardRow(
    card: YgoDeckImportCard,
    selectedPrintingId: String?,
    onPrintingSelected: (String?) -> Unit,
) {
    var expanded by remember(card.passcode) { mutableStateOf(false) }
    Card(Modifier.fillMaxWidth().padding(top = 8.dp)) {
        Column(Modifier.padding(12.dp)) {
            Text(card.displayName ?: "Unknown passcode ${card.passcode}", style = MaterialTheme.typography.titleSmall)
            Text("${card.passcode} - Main ${card.mainQuantity} - Extra ${card.extraQuantity} - Side ${card.sideQuantity} - Total ${card.quantity}")
            if (card.hasBaseCodeMatch) {
                Text("Matches the selected base code", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall)
            }
            if (card.isResolved) {
                val label = card.printingChoices.firstOrNull { it.printingId == selectedPrintingId }?.label ?: "Unknown printing (safe default)"
                Row {
                    TextButton(onClick = { expanded = true }) { Text(label) }
                    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                        DropdownMenuItem(
                            text = { Text("Unknown printing") },
                            onClick = { onPrintingSelected(null); expanded = false },
                        )
                        card.printingChoices.forEach { choice ->
                            DropdownMenuItem(
                                text = { Text(choice.label) },
                                onClick = { onPrintingSelected(choice.printingId); expanded = false },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ConditionPicker(selected: CardCondition, onSelected: (CardCondition) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth().padding(top = 8.dp)) {
        Text("Condition", modifier = Modifier.weight(1f).padding(top = 12.dp))
        TextButton(onClick = { expanded = true }) { Text(selected.label) }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            CardCondition.entries.forEach { condition ->
                DropdownMenuItem(
                    text = { Text(condition.label) },
                    onClick = { onSelected(condition); expanded = false },
                )
            }
        }
    }
}

private suspend fun readDocument(context: Context, uri: Uri): String = withContext(Dispatchers.IO) {
    context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
        ?: error("The selected deck file could not be opened.")
}
