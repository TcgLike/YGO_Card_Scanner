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
import androidx.compose.material3.RadioButton
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
fun YgoDeckImportScreen(
    viewModel: YgoDeckImportViewModel,
    onBack: () -> Unit,
    onImported: () -> Unit,
    officialVariantId: String?,
    onBrowseOfficialDecks: () -> Unit,
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var pastedInput by remember { mutableStateOf("") }
    val selectedFileError = appText("The selected deck file could not be opened.", "Die ausgew\u00E4hlte Deckdatei konnte nicht ge\u00F6ffnet werden.")
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
    LaunchedEffect(officialVariantId) { officialVariantId?.let(viewModel::previewOfficialDeck) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(appText("Import Yu-Gi-Oh! deck", "Yu-Gi-Oh!-Deck importieren")) },
                navigationIcon = { TextButton(onClick = onBack) { Text(appText("Back", "Zur\u00FCck")) } },
            )
        },
    ) { padding ->
        if (state.officialRecipe != null) {
            OfficialDeckBonusReview(state, viewModel, Modifier.fillMaxSize().padding(padding))
        } else if (state.preview == null) {
            Column(Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
                TextButton(onClick = onBrowseOfficialDecks) { Text(appText("Browse official decks", "Offizielle Decks durchsuchen")) }
                if (officialVariantId != null && state.isLoading) Text(appText("Loading official deck\u2026", "Offizielles Deck wird geladen\u2026"))
                Text(appText("Import a local .ydk file or paste a ydke:// deck code. The file stays on this device.", "Importiere eine lokale .ydk-Datei oder f\u00FCge einen ydke://-Deckcode ein. Die Datei bleibt auf diesem Ger\u00E4t."))
                OutlinedTextField(value = baseCodeInput, onValueChange = { baseCodeInput = it }, modifier = Modifier.fillMaxWidth().padding(top = 12.dp), label = { Text(appText("Deck printing base code (optional)", "Basiscode des Deckdrucks (optional)")) }, supportingText = { Text(appText("Example: CH02-DE or CH02-DEXXX. Matching local printings are placed first.", "Beispiel: CH02-DE oder CH02-DEXXX. Passende lokale Drucke werden zuerst angezeigt.")) }, singleLine = true)
                Button(onClick = { filePicker.launch("*/*") }, modifier = Modifier.padding(top = 12.dp)) { Text(appText("Choose .ydk file", ".ydk-Datei ausw\u00E4hlen")) }
                OutlinedTextField(value = pastedInput, onValueChange = { pastedInput = it }, modifier = Modifier.fillMaxWidth().padding(top = 12.dp), label = { Text(appText("Paste .ydk contents or ydke:// code", ".ydk-Inhalt oder ydke://-Code einf\u00FCgen")) }, minLines = 6)
                Button(onClick = { viewModel.preview("Pasted deck", pastedInput, baseCodeInput) }, modifier = Modifier.padding(top = 8.dp), enabled = pastedInput.isNotBlank() && !state.isLoading) {
                    Text(if (state.isLoading) appText("Reading\u2026", "Wird gelesen\u2026") else appText("Review deck", "Deck pr\u00FCfen"))
                }
                state.errorMessage?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 12.dp)) }
            }
        } else {
            DeckReview(state, viewModel, Modifier.fillMaxSize().padding(padding))
        }
    }
}

@Composable
private fun OfficialDeckBonusReview(state: YgoDeckImportUiState, viewModel: YgoDeckImportViewModel, modifier: Modifier) {
    val recipe = requireNotNull(state.officialRecipe)
    LazyColumn(modifier = modifier.padding(16.dp)) {
        item {
            Text(recipe.sourceLabel, style = MaterialTheme.typography.titleLarge)
            Text(appText("This product has ${recipe.declaredTotalCardCount} physical cards. The fixed base is verified; the final card is a random bonus. Select it only if you know which card is in your box.", "Dieses Produkt hat ${recipe.declaredTotalCardCount} physische Karten. Die feste Basis ist verifiziert; die letzte Karte ist ein zufälliger Bonus. Wähle sie nur aus, wenn du weißt, welche Karte in deiner Box ist."), modifier = Modifier.padding(top = 8.dp))
        }
        recipe.bonusGroups.forEach { group ->
            item {
                Card(Modifier.fillMaxWidth().padding(top = 16.dp)) {
                    Column(Modifier.padding(12.dp)) {
                        Text(appText(group.label, "Zufällige Bonuskarte (optional)"), style = MaterialTheme.typography.titleMedium)
                        Text(appText("Leave this unselected if you are unsure. The app will import only the fixed cards.", "Lass dies unausgewählt, wenn du unsicher bist. Die App importiert dann nur die festen Karten."), style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp))
                        Row(Modifier.fillMaxWidth().padding(top = 8.dp)) {
                            RadioButton(selected = state.selectedOfficialBonusPasscodes[group.id] == null, onClick = { viewModel.selectOfficialBonus(group.id, null) })
                            Text(appText("Do not add a bonus card", "Keine Bonuskarte hinzufügen"), modifier = Modifier.padding(top = 12.dp))
                        }
                        group.candidates.forEach { candidate ->
                            Row(Modifier.fillMaxWidth()) {
                                RadioButton(selected = state.selectedOfficialBonusPasscodes[group.id] == candidate.passcode, onClick = { viewModel.selectOfficialBonus(group.id, candidate.passcode) })
                                Column(Modifier.padding(top = 8.dp)) {
                                    Text(candidate.displayName)
                                    Text(candidate.setCode, style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                    }
                }
            }
        }
        item {
            state.errorMessage?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 12.dp)) }
            Button(onClick = viewModel::reviewOfficialRecipe, modifier = Modifier.fillMaxWidth().padding(top = 16.dp), enabled = !state.isLoading) {
                Text(appText("Review deck", "Deck prüfen"))
            }
            TextButton(onClick = viewModel::clearPreview, modifier = Modifier.fillMaxWidth()) { Text(appText("Cancel", "Abbrechen")) }
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
                    Text(appText("${preview.totalCardCount} cards \u00B7 ${preview.cards.size} unique passcodes", "${preview.totalCardCount} Karten \u00B7 ${preview.cards.size} eindeutige Passcodes"))
                }
                TextButton(onClick = viewModel::clearPreview) { Text(appText("Change code", "Code \u00E4ndern")) }
            }
            Text(appText("Deck files identify cards by passcode, not physical printings. Unknown printing remains the safe default unless one local match is unambiguous.", "Deckdateien identifizieren Karten \u00FCber den Passcode, nicht \u00FCber den physischen Druck. Unbekannter Druck bleibt die sichere Vorgabe, sofern nicht genau ein lokaler Treffer eindeutig ist."), modifier = Modifier.padding(top = 8.dp), style = MaterialTheme.typography.bodySmall)
            if (preview.baseCodePrefix != null) {
                Text(appText("$matchedCards of ${preview.cards.size} local cards match ${state.baseCodeInput.trim()}. Matching cards are first and ordered by the code number.", "$matchedCards von ${preview.cards.size} lokalen Karten passen zu ${state.baseCodeInput.trim()}. Passende Karten stehen zuerst und sind nach der Codenummer sortiert."), modifier = Modifier.padding(top = 8.dp))
                if (state.cardsWithoutBaseCode.isNotEmpty()) {
                    Text(appText("No local printing with that base code was found for: ${state.cardsWithoutBaseCode.joinToString { it.displayName ?: it.passcode }}.", "Kein lokaler Druck mit diesem Basiscode wurde gefunden f\u00FCr: ${state.cardsWithoutBaseCode.joinToString { it.displayName ?: it.passcode }}."), color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 8.dp))
                    Text(appText("For German print codes, enable and update the optional German printing backup in Settings before reviewing the deck again.", "F\u00FCr deutsche Druckcodes aktiviere und aktualisiere vor der erneuten Deckpr\u00FCfung die optionale Sicherungsquelle f\u00FCr deutsche Drucke in den Einstellungen."), style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp))
                }
            }
            ConditionPicker(state.condition, viewModel::setCondition)
            OutlinedTextField(value = state.notes, onValueChange = viewModel::setNotes, modifier = Modifier.fillMaxWidth().padding(top = 8.dp), label = { Text(appText("Notes for imported entries (optional)", "Notizen f\u00FCr importierte Eintr\u00E4ge (optional)")) })
            if (state.unresolvedCards.isNotEmpty()) {
                Text(appText("${state.unresolvedCards.size} passcode(s) are not in the local catalog. Download/update the Yu-Gi-Oh! catalog or remove them before importing.", "${state.unresolvedCards.size} Passcode(s) sind nicht im lokalen Katalog. Lade den Yu-Gi-Oh!-Katalog herunter/aktualisiere ihn oder entferne sie vor dem Import."), color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 12.dp))
            }
        }
        items(preview.cards, key = YgoDeckImportCard::passcode) { card -> DeckCardRow(card, state.selectedPrintingIds[card.passcode]) { viewModel.selectPrinting(card.passcode, it) } }
        item {
            state.errorMessage?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 12.dp)) }
            Button(onClick = viewModel::importDeck, modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp), enabled = state.unresolvedCards.isEmpty() && !state.isSaving) {
                Text(if (state.isSaving) appText("Adding deck\u2026", "Deck wird hinzugef\u00FCgt\u2026") else appText("Add deck to collection", "Deck zur Sammlung hinzuf\u00FCgen"))
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
            Text(appText("${card.passcode} \u00B7 Main ${card.mainQuantity} \u00B7 Extra ${card.extraQuantity} \u00B7 Side ${card.sideQuantity} \u00B7 Total ${card.quantity}", "${card.passcode} \u00B7 Hauptdeck ${card.mainQuantity} \u00B7 Extra Deck ${card.extraQuantity} \u00B7 Side Deck ${card.sideQuantity} \u00B7 Gesamt ${card.quantity}"))
            if (card.hasBaseCodeMatch) Text(appText("Matches the selected base code", "Passt zum gew\u00E4hlten Basiscode"), color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall)
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

