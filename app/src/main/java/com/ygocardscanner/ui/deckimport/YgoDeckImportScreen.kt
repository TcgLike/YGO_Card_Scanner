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
import com.ygocardscanner.ui.localization.appText
import com.ygocardscanner.ui.localization.localizedLabel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun YgoDeckImportScreen(viewModel: YgoDeckImportViewModel, onBack: () -> Unit, onImported: () -> Unit) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var pastedInput by remember { mutableStateOf("") }
    val selectedFileError = appText("The selected deck file could not be opened.", "Die ausgewählte Deckdatei konnte nicht geöffnet werden.")
    var baseCodeInput by remember { mutableStateOf("") }
    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        scope.launch {
            runCatching { readDocument(context, uri) }
                .onSuccess { contents -> viewModel.preview(uri.lastPathSegment ?: "deck.ydk", contents, baseCodeInput) }
                .onFailure { error -> viewModel.reportReadError(error.message?.takeIf { it.isNotBlank() } ?: selectedFileError) }
        }
    }
    LaunchedEffect(viewModel) { viewModel.events.collect { if (it is YgoDeckImportEvent.Imported) onImported() } }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(appText("Import Yu-Gi-Oh! deck", "Yu-Gi-Oh!-Deck importieren")) },
                navigationIcon = { TextButton(onClick = onBack) { Text(appText("Back", "Zurück")) } },
            )
        },
    ) { padding ->
        if (state.preview == null) {
            Column(Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
                Text(appText("Import a local .ydk file or paste a ydke:// deck code. The file stays on this device.", "Importiere eine lokale .ydk-Datei oder füge einen ydke://-Deckcode ein. Die Datei bleibt auf diesem Gerät."))
                OutlinedTextField(value = baseCodeInput, onValueChange = { baseCodeInput = it }, modifier = Modifier.fillMaxWidth().padding(top = 12.dp), label = { Text(appText("Deck printing base code (optional)", "Basiscode des Deckdrucks (optional)")) }, supportingText = { Text(appText("Example: CH02-DE or CH02-DEXXX. Matching local printings are placed first.", "Beispiel: CH02-DE oder CH02-DEXXX. Passende lokale Drucke werden zuerst angezeigt.")) }, singleLine = true)
                Button(onClick = { filePicker.launch("*/*") }, modifier = Modifier.padding(top = 12.dp)) { Text(appText("Choose .ydk file", ".ydk-Datei auswählen")) }
                OutlinedTextField(value = pastedInput, onValueChange = { pastedInput = it }, modifier = Modifier.fillMaxWidth().padding(top = 12.dp), label = { Text(appText("Paste .ydk contents or ydke:// code", ".ydk-Inhalt oder ydke://-Code einfügen")) }, minLines = 6)
                Button(onClick = { viewModel.preview("Pasted deck", pastedInput, baseCodeInput) }, modifier = Modifier.padding(top = 8.dp), enabled = pastedInput.isNotBlank() && !state.isLoading) {
                    Text(if (state.isLoading) appText("Reading…", "Wird gelesen…") else appText("Review deck", "Deck prüfen"))
                }
                state.errorMessage?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 12.dp)) }
            }
        } else {
            DeckReview(state, viewModel, Modifier.fillMaxSize().padding(padding))
        }
    }
}

@Composable
private fun DeckReview(state: YgoDeckImportUiState, viewModel: YgoDeckImportViewModel, modifier: Modifier) {
    val preview = requireNotNull(state.preview)
    val matchedCards = preview.cards.count(YgoDeckImportCard::hasBaseCodeMatch)
    LazyColumn(modifier = modifier.padding(horizontal = 16.dp)) {
        item {
            Row(Modifier.fillMaxWidth().padding(top = 12.dp)) {
                Column(Modifier.weight(1f)) {
                    Text(preview.sourceLabel, style = MaterialTheme.typography.titleMedium)
                    Text(appText("${preview.totalCardCount} cards · ${preview.cards.size} unique passcodes", "${preview.totalCardCount} Karten · ${preview.cards.size} eindeutige Passcodes"))
                }
                TextButton(onClick = viewModel::clearPreview) { Text(appText("Change code", "Code ändern")) }
            }
            Text(appText("Deck files identify cards by passcode, not physical printings. Unknown printing remains the safe default unless one local match is unambiguous.", "Deckdateien identifizieren Karten über den Passcode, nicht über den physischen Druck. Unbekannter Druck bleibt die sichere Vorgabe, sofern nicht genau ein lokaler Treffer eindeutig ist."), modifier = Modifier.padding(top = 8.dp), style = MaterialTheme.typography.bodySmall)
            if (preview.baseCodePrefix != null) {
                Text(appText("$matchedCards of ${preview.cards.size} local cards match ${state.baseCodeInput.trim()}. Matching cards are first and ordered by the code number.", "$matchedCards von ${preview.cards.size} lokalen Karten passen zu ${state.baseCodeInput.trim()}. Passende Karten stehen zuerst und sind nach der Codenummer sortiert."), modifier = Modifier.padding(top = 8.dp))
                if (state.cardsWithoutBaseCode.isNotEmpty()) {
                    Text(appText("No local printing with that base code was found for: ${state.cardsWithoutBaseCode.joinToString { it.displayName ?: it.passcode }}.", "Kein lokaler Druck mit diesem Basiscode wurde gefunden für: ${state.cardsWithoutBaseCode.joinToString { it.displayName ?: it.passcode }}."), color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 8.dp))
                    Text(appText("For German print codes, enable and update the optional German printing backup in Settings before reviewing the deck again.", "Für deutsche Druckcodes aktiviere und aktualisiere vor der erneuten Deckprüfung die optionale Sicherungsquelle für deutsche Drucke in den Einstellungen."), style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp))
                }
            }
            ConditionPicker(state.condition, viewModel::setCondition)
            OutlinedTextField(value = state.notes, onValueChange = viewModel::setNotes, modifier = Modifier.fillMaxWidth().padding(top = 8.dp), label = { Text(appText("Notes for imported entries (optional)", "Notizen für importierte Einträge (optional)")) })
            if (state.unresolvedCards.isNotEmpty()) {
                Text(appText("${state.unresolvedCards.size} passcode(s) are not in the local catalog. Download/update the Yu-Gi-Oh! catalog or remove them before importing.", "${state.unresolvedCards.size} Passcode(s) sind nicht im lokalen Katalog. Lade den Yu-Gi-Oh!-Katalog herunter/aktualisiere ihn oder entferne sie vor dem Import."), color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 12.dp))
            }
        }
        items(preview.cards, key = YgoDeckImportCard::passcode) { card -> DeckCardRow(card, state.selectedPrintingIds[card.passcode]) { viewModel.selectPrinting(card.passcode, it) } }
        item {
            state.errorMessage?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 12.dp)) }
            Button(onClick = viewModel::importDeck, modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp), enabled = state.unresolvedCards.isEmpty() && !state.isSaving) {
                Text(if (state.isSaving) appText("Adding deck…", "Deck wird hinzugefügt…") else appText("Add deck to collection", "Deck zur Sammlung hinzufügen"))
            }
        }
    }
}

@Composable
private fun DeckCardRow(card: YgoDeckImportCard, selectedPrintingId: String?, onPrintingSelected: (String?) -> Unit) {
    var expanded by remember(card.passcode) { mutableStateOf(false) }
    Card(Modifier.fillMaxWidth().padding(top = 8.dp)) {
        Column(Modifier.padding(12.dp)) {
            Text(card.displayName ?: appText("Unknown passcode ${card.passcode}", "Unbekannter Passcode ${card.passcode}"), style = MaterialTheme.typography.titleSmall)
            Text(appText("${card.passcode} · Main ${card.mainQuantity} · Extra ${card.extraQuantity} · Side ${card.sideQuantity} · Total ${card.quantity}", "${card.passcode} · Hauptdeck ${card.mainQuantity} · Extra Deck ${card.extraQuantity} · Side Deck ${card.sideQuantity} · Gesamt ${card.quantity}"))
            if (card.hasBaseCodeMatch) Text(appText("Matches the selected base code", "Passt zum gewählten Basiscode"), color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall)
            if (card.isResolved) {
                val label = card.printingChoices.firstOrNull { it.printingId == selectedPrintingId }?.label ?: appText("Unknown printing (safe default)", "Unbekannter Druck (sichere Vorgabe)")
                Row {
                    TextButton(onClick = { expanded = true }) { Text(label) }
                    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                        DropdownMenuItem(text = { Text(appText("Unknown printing", "Unbekannter Druck")) }, onClick = { onPrintingSelected(null); expanded = false })
                        card.printingChoices.forEach { choice -> DropdownMenuItem(text = { Text(choice.label) }, onClick = { onPrintingSelected(choice.printingId); expanded = false }) }
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
        Text(appText("Condition", "Zustand"), modifier = Modifier.weight(1f).padding(top = 12.dp))
        TextButton(onClick = { expanded = true }) { Text(selected.localizedLabel()) }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            CardCondition.entries.forEach { condition -> DropdownMenuItem(text = { Text(condition.localizedLabel()) }, onClick = { onSelected(condition); expanded = false }) }
        }
    }
}

private suspend fun readDocument(context: Context, uri: Uri): String = withContext(Dispatchers.IO) {
    context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
        ?: error("The selected deck file could not be opened.")
}
