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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun YgoDeckAvailabilityScreen(
    viewModel: YgoDeckAvailabilityViewModel,
    onBack: () -> Unit,
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var pastedInput by remember { mutableStateOf("") }
    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        scope.launch {
            runCatching { readDeckDocument(context, uri) }
                .onSuccess { contents -> viewModel.check(uri.lastPathSegment ?: "deck.ydk", contents) }
                .onFailure { error -> viewModel.reportReadError(error.message ?: "The selected deck file could not be opened.") }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Can I build it?") },
                navigationIcon = { TextButton(onClick = onBack) { Text("Back") } },
            )
        },
    ) { padding ->
        val preview = state.preview
        if (preview == null) {
            Column(Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
                Text("Choose a local .ydk file or paste a ydke:// deck code. The deck is checked only against this device's collection.")
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
                    onClick = { viewModel.check("Pasted deck", pastedInput) },
                    modifier = Modifier.padding(top = 8.dp),
                    enabled = pastedInput.isNotBlank() && !state.isLoading,
                ) { Text(if (state.isLoading) "Checking..." else "Check collection") }
                state.errorMessage?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 12.dp)) }
            }
        } else {
            AvailabilityResults(
                preview = preview,
                errorMessage = state.errorMessage,
                onCheckAnother = viewModel::clear,
                modifier = Modifier.fillMaxSize().padding(padding),
            )
        }
    }
}

@Composable
private fun AvailabilityResults(
    preview: com.ygocardscanner.data.deckimport.yugioh.YgoDeckAvailabilityPreview,
    errorMessage: String?,
    onCheckAnother: () -> Unit,
    modifier: Modifier,
) {
    LazyColumn(modifier = modifier.padding(horizontal = 16.dp)) {
        item {
            Row(Modifier.fillMaxWidth().padding(top = 12.dp)) {
                Column(Modifier.weight(1f)) {
                    Text(preview.sourceLabel, style = MaterialTheme.typography.titleMedium)
                    Text("${preview.totalCardCount} cards - ${preview.cards.size} unique passcodes")
                }
                TextButton(onClick = onCheckAnother) { Text("Check another") }
            }
            Text(
                if (preview.canBuild) {
                    "You have enough copies of every catalog card in this deck."
                } else {
                    "${preview.ownedCardCount} of ${preview.cards.size} card entries have enough copies. Green cards are available."
                },
                color = if (preview.canBuild) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
            )
        }
        items(preview.cards, key = YgoDeckAvailabilityCard::passcode) { card ->
            AvailabilityCardRow(card)
        }
        item {
            errorMessage?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(vertical = 16.dp)) }
        }
    }
}

@Composable
private fun AvailabilityCardRow(card: YgoDeckAvailabilityCard) {
    val colors = if (card.hasEnough) {
        CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
    } else {
        CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    }
    Card(
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        colors = colors,
    ) {
        Column(Modifier.padding(12.dp)) {
            Text(card.displayName ?: "Unknown passcode ${card.passcode}", style = MaterialTheme.typography.titleSmall)
            Text("${card.passcode} - Main ${card.mainQuantity} - Extra ${card.extraQuantity} - Side ${card.sideQuantity}")
            when {
                !card.isInCatalog -> Text(
                    "Not in the local catalog. Download or update the catalog to check it.",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
                card.hasEnough -> Text(
                    "Owned ${card.ownedQuantity} / Need ${card.requiredQuantity}",
                    color = MaterialTheme.colorScheme.primary,
                )
                else -> Text("Owned ${card.ownedQuantity} / Need ${card.requiredQuantity} - missing ${card.missingQuantity}")
            }
        }
    }
}

private suspend fun readDeckDocument(context: Context, uri: Uri): String = withContext(Dispatchers.IO) {
    context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
        ?: error("The selected deck file could not be opened.")
}