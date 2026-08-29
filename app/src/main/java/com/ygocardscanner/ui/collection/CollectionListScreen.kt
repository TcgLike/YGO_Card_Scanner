package com.ygocardscanner.ui.collection

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.ygocardscanner.data.artwork.CardArtworkFileStore
import com.ygocardscanner.model.CardArtworkDetail
import com.ygocardscanner.model.CardArtworkDownloadState
import com.ygocardscanner.model.CollectionEntrySummary
import com.ygocardscanner.ui.components.EmptyState
import com.ygocardscanner.ui.components.ErrorState
import com.ygocardscanner.ui.components.LoadingState
import com.ygocardscanner.ui.localization.appText
import com.ygocardscanner.ui.localization.localizedLabel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CollectionListScreen(
    viewModel: CollectionListViewModel,
    onAddCard: () -> Unit,
    onSettings: () -> Unit,
    onEntrySelected: (String) -> Unit,
) {
    val state by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(appText("Collection", "Sammlung")) },
                actions = { TextButton(onClick = onSettings) { Text(appText("Settings", "Einstellungen")) } },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onAddCard) { Text(appText("Add", "Hinzufügen")) }
        },
    ) { innerPadding ->
        Column(Modifier.fillMaxSize().padding(innerPadding)) {
            OutlinedTextField(
                value = state.query,
                onValueChange = viewModel::updateQuery,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                label = { Text(appText("Search your collection", "Sammlung durchsuchen")) },
                singleLine = true,
            )
            when {
                state.isLoading -> LoadingState(appText("Loading your local collection…", "Lokale Sammlung wird geladen…"))
                state.errorMessage != null -> ErrorState(state.errorMessage.orEmpty(), viewModel::retry)
                state.entries.isEmpty() -> EmptyState(
                    title = if (state.query.isBlank()) appText("Your collection is empty", "Deine Sammlung ist leer") else appText("No cards found", "Keine Karten gefunden"),
                    message = if (state.query.isBlank()) appText("Add a catalog card or record an unknown printing manually.", "Füge eine Katalogkarte hinzu oder erfasse einen unbekannten Druck manuell.") else appText("Try a card name, passcode, set code, or note.", "Versuche einen Kartennamen, Passcode, Set-Code oder eine Notiz."),
                    actionLabel = if (state.query.isBlank()) appText("Add a card", "Karte hinzufügen") else null,
                    onAction = if (state.query.isBlank()) onAddCard else null,
                )
                else -> LazyColumn(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)) {
                    items(state.entries, key = CollectionEntrySummary::entryId) { entry ->
                        CollectionEntryRow(entry, onClick = { onEntrySelected(entry.entryId) })
                    }
                }
            }
        }
    }
}

@Composable
private fun CollectionEntryRow(entry: CollectionEntrySummary, onClick: () -> Unit) {
    Card(Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable(onClick = onClick)) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(entry.cardName, style = MaterialTheme.typography.titleMedium)
                Text(listOfNotNull(entry.setCode, entry.rarity, entry.edition.localizedLabel()).joinToString(" · "), modifier = Modifier.padding(top = 4.dp), style = MaterialTheme.typography.bodyMedium)
                Text("${entry.quantity} × ${entry.condition.localizedLabel()}" + if (entry.isUnknownPrinting) " · ${appText("Unknown printing", "Unbekannter Druck")}" else "", modifier = Modifier.padding(top = 4.dp), style = MaterialTheme.typography.labelLarge)
            }
            CollectionEntryArtwork(entry.artwork, entry.cardName, Modifier.padding(start = 12.dp))
        }
    }
}

@Composable
private fun CollectionEntryArtwork(artwork: CardArtworkDetail?, cardName: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val fileStore = remember(context) { CardArtworkFileStore(context) }
    val image = remember(artwork?.localFileName) { fileStore.resolve(artwork?.localFileName) }
    val bitmap by produceState<Bitmap?>(initialValue = null, image) {
        value = withContext(Dispatchers.IO) { image?.let { BitmapFactory.decodeFile(it.absolutePath) } }
    }
    val shape = RoundedCornerShape(6.dp)
    Box(modifier = modifier.width(64.dp).height(92.dp).clip(shape).background(MaterialTheme.colorScheme.surfaceVariant).border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape), contentAlignment = Alignment.Center) {
        if (bitmap != null) {
            Image(bitmap = requireNotNull(bitmap).asImageBitmap(), contentDescription = appText("English artwork for $cardName", "Englisches Kartenbild für $cardName"), contentScale = ContentScale.Crop, modifier = Modifier.matchParentSize())
        } else {
            val label = when (artwork?.downloadState) {
                CardArtworkDownloadState.QUEUED, CardArtworkDownloadState.DOWNLOADING -> appText("Loading\nimage", "Bild wird\ngeladen")
                else -> appText("No saved\nimage", "Kein gespeichertes\nBild")
            }
            Text(label, textAlign = TextAlign.Center, style = MaterialTheme.typography.labelSmall)
        }
    }
}