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
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.ygocardscanner.data.deckimport.yugioh.YgoDeckAvailabilityCard
import com.ygocardscanner.ui.localization.UiText
import com.ygocardscanner.ui.localization.appText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun YgoDeckAvailabilityScreen(viewModel: YgoDeckAvailabilityViewModel, onBack: () -> Unit) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var pastedInput by remember { mutableStateOf("") }
    val selectedFileError = appText("The selected deck file could not be opened.", "Die ausgewählte Deckdatei konnte nicht geöffnet werden.")
    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        scope.launch {
            runCatching { readDeckDocument(context, uri) }
                .onSuccess { contents -> viewModel.check(uri.lastPathSegment ?: "deck.ydk", contents) }
                .onFailure { error -> viewModel.reportReadError(error.message?.takeIf { it.isNotBlank() } ?: selectedFileError) }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(appText(UiText.CanBuildIt)) },
                navigationIcon = { TextButton(onClick = onBack) { Text(appText("Back", "Zurück")) } },
            )
        },
    ) { padding ->
        val preview = state.preview
        if (preview == null) {
            Column(Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
                Text(appText("Choose a local .ydk file or paste a ydke:// deck code. The deck is checked only against this device's collection.", "Wähle eine lokale .ydk-Datei oder füge einen ydke://-Deckcode ein. Das Deck wird nur mit der Sammlung auf diesem Gerät abgeglichen."))
                Button(onClick = { filePicker.launch("*/*") }, modifier = Modifier.padding(top = 12.dp)) { Text(appText("Choose .ydk file", ".ydk-Datei auswählen")) }
                OutlinedTextField(value = pastedInput, onValueChange = { pastedInput = it }, modifier = Modifier.fillMaxWidth().padding(top = 12.dp), label = { Text(appText("Paste .ydk contents or ydke:// code", ".ydk-Inhalt oder ydke://-Code einfügen")) }, minLines = 6)
                Button(onClick = { viewModel.check("Pasted deck", pastedInput) }, modifier = Modifier.padding(top = 8.dp), enabled = pastedInput.isNotBlank() && !state.isLoading) {
                    Text(if (state.isLoading) appText("Checking…", "Wird geprüft…") else appText("Check collection", "Sammlung prüfen"))
                }
                state.errorMessage?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 12.dp)) }
            }
        } else {
            AvailabilityResults(preview, state.errorMessage, viewModel::clear, Modifier.fillMaxSize().padding(padding))
        }
    }
}

@Composable
private fun AvailabilityResults(preview: com.ygocardscanner.data.deckimport.yugioh.YgoDeckAvailabilityPreview, errorMessage: String?, onCheckAnother: () -> Unit, modifier: Modifier) {
    LazyColumn(modifier = modifier.padding(horizontal = 16.dp)) {
        item {
            Row(Modifier.fillMaxWidth().padding(top = 12.dp)) {
                Column(Modifier.weight(1f)) {
                    Text(preview.sourceLabel, style = MaterialTheme.typography.titleMedium)
                    Text(appText("${preview.totalCardCount} cards · ${preview.cards.size} unique passcodes", "${preview.totalCardCount} Karten · ${preview.cards.size} eindeutige Passcodes"))
                }
                TextButton(onClick = onCheckAnother) { Text(appText("Check another", "Anderes Deck prüfen")) }
            }
            Text(
                if (preview.canBuild) appText("You have enough copies of every catalog card in this deck.", "Du besitzt genügend Exemplare jeder Katalogkarte in diesem Deck.") else appText("${preview.ownedCardCount} of ${preview.cards.size} Karteneinträge sind ausreichend vorhanden. Grüne Karten sind verfügbar.", "${preview.ownedCardCount} von ${preview.cards.size} Karteneinträgen sind ausreichend vorhanden. Grüne Karten sind verfügbar."),
                color = if (preview.canBuild) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
            )
        }
        items(preview.cards, key = YgoDeckAvailabilityCard::passcode) { card -> AvailabilityCardRow(card) }
        item { errorMessage?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(vertical = 16.dp)) } }
    }
}

@Composable
private fun AvailabilityCardRow(card: YgoDeckAvailabilityCard) {
    val colors = if (card.hasEnough) CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer) else CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    Card(modifier = Modifier.fillMaxWidth().padding(top = 8.dp), colors = colors) {
        Column(Modifier.padding(12.dp)) {
            Text(card.displayName ?: appText("Unknown passcode ${card.passcode}", "Unbekannter Passcode ${card.passcode}"), style = MaterialTheme.typography.titleSmall)
            Text(appText("${card.passcode} · Main ${card.mainQuantity} · Extra ${card.extraQuantity} · Side ${card.sideQuantity}", "${card.passcode} · Hauptdeck ${card.mainQuantity} · Extra Deck ${card.extraQuantity} · Side Deck ${card.sideQuantity}"))
            when {
                !card.isInCatalog -> Text(appText("Not in the local catalog. Download or update the catalog to check it.", "Nicht im lokalen Katalog. Lade den Katalog herunter oder aktualisiere ihn, um die Karte zu prüfen."), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                card.hasEnough -> Text(appText("Owned ${card.ownedQuantity} / Need ${card.requiredQuantity}", "Vorhanden ${card.ownedQuantity} / Benötigt ${card.requiredQuantity}"), color = MaterialTheme.colorScheme.primary)
                else -> Text(appText("Owned ${card.ownedQuantity} / Need ${card.requiredQuantity} · missing ${card.missingQuantity}", "Vorhanden ${card.ownedQuantity} / Benötigt ${card.requiredQuantity} · fehlen ${card.missingQuantity}"))
            }
        }
    }
}

private suspend fun readDeckDocument(context: Context, uri: Uri): String = withContext(Dispatchers.IO) {
    context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
        ?: error("The selected deck file could not be opened.")
}
