package com.ygocardscanner.ui.catalog

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ygocardscanner.data.artwork.CardArtworkFileStore
import com.ygocardscanner.model.CardArtworkDownloadState
import com.ygocardscanner.model.CatalogCardSummary
import com.ygocardscanner.model.CollectionLayout
import com.ygocardscanner.ui.components.EmptyState
import com.ygocardscanner.ui.components.ErrorState
import com.ygocardscanner.ui.components.LoadingState
import com.ygocardscanner.ui.components.LocalArtworkViewer
import com.ygocardscanner.ui.localization.appText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CatalogViewerScreen(viewModel: CatalogViewerViewModel, onBack: () -> Unit) {
    val state by viewModel.uiState.collectAsState()
    var layoutMenuExpanded by remember { mutableStateOf(false) }
    var fullscreenArtwork by remember { mutableStateOf<Pair<String, String>?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(appText("Card catalog", "Kartenkatalog")) },
                navigationIcon = {
                    TextButton(onClick = onBack) { Text(appText("Back", "Zurück")) }
                },
                actions = {
                    Box {
                        TextButton(onClick = { layoutMenuExpanded = true }) {
                            Text(appText("View", "Ansicht"))
                        }
                        DropdownMenu(
                            expanded = layoutMenuExpanded,
                            onDismissRequest = { layoutMenuExpanded = false },
                        ) {
                            CollectionLayout.entries.forEach { layout ->
                                DropdownMenuItem(
                                    text = { Text(layoutLabel(layout)) },
                                    onClick = {
                                        viewModel.setLayout(layout)
                                        layoutMenuExpanded = false
                                    },
                                    trailingIcon = { if (state.layout == layout) Text("✓") },
                                )
                            }
                        }
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(Modifier.fillMaxSize().padding(innerPadding)) {
            OutlinedTextField(
                value = state.query,
                onValueChange = viewModel::updateQuery,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                label = { Text(appText("Search all local cards", "Alle lokalen Karten durchsuchen")) },
                singleLine = true,
            )
            when {
                state.isLoading -> LoadingState(appText("Loading the local card catalog…", "Lokaler Kartenkatalog wird geladen…"))
                state.errorMessage != null -> ErrorState(state.errorMessage.orEmpty(), viewModel::retry)
                state.cards.isEmpty() -> EmptyState(
                    title = appText("No catalog cards found", "Keine Katalogkarten gefunden"),
                    message = if (state.query.isBlank()) {
                        appText(
                            "Download the English and German catalog in Settings to browse cards here.",
                            "Lade den englischen und deutschen Katalog in den Einstellungen herunter, um hier Karten zu durchsuchen.",
                        )
                    } else {
                        appText("Try a card name, passcode, or set code.", "Versuche einen Kartennamen, Passcode oder Set-Code.")
                    },
                )
                else -> CatalogCards(
                    cards = state.cards,
                    layout = state.layout,
                    onOpenArtwork = { fileName, cardName -> fullscreenArtwork = fileName to cardName },
                )
            }
        }
    }

    fullscreenArtwork?.let { (fileName, cardName) ->
        LocalArtworkViewer(fileName, cardName, onDismiss = { fullscreenArtwork = null })
    }
}

@Composable
private fun CatalogCards(
    cards: List<CatalogCardSummary>,
    layout: CollectionLayout,
    onOpenArtwork: (String, String) -> Unit,
) {
    when (layout) {
        CollectionLayout.DETAILED -> LazyColumn(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)) {
            items(cards, key = CatalogCardSummary::cardId) { card ->
                CatalogDetailedRow(card, onOpenArtwork)
            }
        }

        CollectionLayout.COMPACT -> LazyColumn(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)) {
            items(cards, key = CatalogCardSummary::cardId) { card ->
                CatalogCompactRow(card, onOpenArtwork)
            }
        }

        CollectionLayout.ARTWORK_TILES -> LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 112.dp),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            gridItems(cards, key = CatalogCardSummary::cardId) { card ->
                CatalogArtworkTile(card, onOpenArtwork)
            }
        }
    }
}

@Composable
private fun CatalogDetailedRow(card: CatalogCardSummary, onOpenArtwork: (String, String) -> Unit) {
    Card(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(card.displayName, style = MaterialTheme.typography.titleMedium)
                card.passcode?.let { passcode ->
                    Text(
                        appText("Passcode: $passcode", "Passcode: $passcode"),
                        modifier = Modifier.padding(top = 4.dp),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                if (card.isOwned) {
                    Text(
                        appText("✓ In your collection", "✓ In deiner Sammlung"),
                        modifier = Modifier.padding(top = 4.dp),
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
            }
            CatalogArtwork(card, Modifier.padding(start = 12.dp), onOpenArtwork)
        }
    }
}

@Composable
private fun CatalogCompactRow(card: CatalogCardSummary, onOpenArtwork: (String, String) -> Unit) {
    val openImage = card.artwork?.localFileName
    Card(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)
            .then(if (openImage != null) Modifier.clickable { onOpenArtwork(openImage, card.displayName) } else Modifier),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(card.displayName, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                card.passcode?.let { passcode ->
                    Text(passcode, style = MaterialTheme.typography.bodySmall)
                }
            }
            if (card.isOwned) {
                Text("✓", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}

@Composable
private fun CatalogArtwork(
    card: CatalogCardSummary,
    modifier: Modifier,
    onOpenArtwork: (String, String) -> Unit,
) {
    val bitmap = catalogArtworkBitmap(card.artwork?.localFileName)
    val shape = RoundedCornerShape(6.dp)
    Box(
        modifier = modifier
            .width(64.dp)
            .height(92.dp)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape),
        contentAlignment = Alignment.Center,
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = appText("Open ${card.displayName}", "${card.displayName} öffnen"),
                contentScale = ContentScale.Crop,
                modifier = Modifier.matchParentSize().clickable {
                    card.artwork?.localFileName?.let { onOpenArtwork(it, card.displayName) }
                },
            )
        } else {
            val label = when (card.artwork?.downloadState) {
                CardArtworkDownloadState.QUEUED, CardArtworkDownloadState.DOWNLOADING -> appText("Loading\nimage", "Bild wird\ngeladen")
                else -> appText("No saved\nimage", "Kein gespeichertes\nBild")
            }
            Text(label, textAlign = TextAlign.Center, style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun CatalogArtworkTile(card: CatalogCardSummary, onOpenArtwork: (String, String) -> Unit) {
    val bitmap = catalogArtworkBitmap(card.artwork?.localFileName)
    val shape = RoundedCornerShape(8.dp)
    val openImage = card.artwork?.localFileName
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(0.68f)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape)
            .then(if (openImage != null) Modifier.clickable { onOpenArtwork(openImage, card.displayName) } else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        bitmap?.let {
            Image(
                bitmap = it.asImageBitmap(),
                contentDescription = appText("Open ${card.displayName}", "${card.displayName} öffnen"),
                contentScale = ContentScale.Fit,
                modifier = Modifier.matchParentSize(),
            )
        }
        if (card.isOwned) {
            Text(
                "✓",
                color = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(bottomStart = 8.dp))
                    .padding(horizontal = 7.dp, vertical = 3.dp),
            )
        }
    }
}

@Composable
private fun catalogArtworkBitmap(localFileName: String?): Bitmap? {
    val context = LocalContext.current
    val fileStore = remember(context) { CardArtworkFileStore(context) }
    val file = remember(localFileName) { fileStore.resolve(localFileName) }
    val bitmap by produceState<Bitmap?>(initialValue = null, file) {
        value = withContext(Dispatchers.IO) { file?.let { BitmapFactory.decodeFile(it.absolutePath) } }
    }
    return bitmap
}

@Composable
private fun layoutLabel(layout: CollectionLayout): String = when (layout) {
    CollectionLayout.DETAILED -> appText("Detailed list", "Detaillierte Liste")
    CollectionLayout.COMPACT -> appText("Compact list", "Kompakte Liste")
    CollectionLayout.ARTWORK_TILES -> appText("Artwork tiles", "Bildkacheln")
}
