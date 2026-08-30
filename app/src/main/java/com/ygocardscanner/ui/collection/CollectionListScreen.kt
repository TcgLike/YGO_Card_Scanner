package com.ygocardscanner.ui.collection

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.Canvas
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ygocardscanner.data.artwork.CardArtworkFileStore
import com.ygocardscanner.model.CardArtworkDetail
import com.ygocardscanner.model.CardArtworkDownloadState
import com.ygocardscanner.model.CollectionEntrySummary
import com.ygocardscanner.model.CollectionLayout
import com.ygocardscanner.ui.components.EmptyState
import com.ygocardscanner.ui.components.ErrorState
import com.ygocardscanner.ui.components.LoadingState
import com.ygocardscanner.ui.components.LocalArtworkViewer
import com.ygocardscanner.ui.localization.UiText
import com.ygocardscanner.ui.localization.appText
import com.ygocardscanner.ui.localization.localizedLabel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CollectionListScreen(
    viewModel: CollectionListViewModel,
    onAddCard: () -> Unit,
    onCheckDeck: () -> Unit,
    onCatalog: () -> Unit,
    onSettings: () -> Unit,
    onEntrySelected: (String) -> Unit,
) {
    val state by viewModel.uiState.collectAsState()
    var fullscreenArtwork by remember { mutableStateOf<Pair<String, String>?>(null) }
    var layoutMenuExpanded by remember { mutableStateOf(false) }
    val canBuildDescription = appText(UiText.CanBuildIt)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(appText(UiText.AppName)) },
                actions = {
                    TextButton(onClick = onCatalog) { Text(appText("Catalog", "Katalog")) }
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
                                    trailingIcon = {
                                        if (state.layout == layout) Text(appText("✓", "✓"))
                                    },
                                )
                            }
                        }
                    }
                    TextButton(onClick = onSettings) { Text(appText("Settings", "Einstellungen")) }
                },
            )
        },
        floatingActionButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                FloatingActionButton(
                    onClick = onCheckDeck,
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    modifier = Modifier.semantics { contentDescription = canBuildDescription },
                ) {
                    DeckStackGlyph()
                }
                FloatingActionButton(onClick = onAddCard) { Text(appText(UiText.Add)) }
            }
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
                else -> CollectionEntries(
                    entries = state.entries,
                    layout = state.layout,
                    onEntrySelected = onEntrySelected,
                    onArtworkClick = { fileName, cardName -> fullscreenArtwork = fileName to cardName },
                )
            }
        }
    }

    fullscreenArtwork?.let { (fileName, cardName) ->
        LocalArtworkViewer(fileName, cardName, onDismiss = { fullscreenArtwork = null })
    }
}

@Composable
private fun DeckStackGlyph() {
    val colors = MaterialTheme.colorScheme
    Canvas(Modifier.width(28.dp).height(30.dp)) {
        val cardWidth = size.width * 0.6f
        val cardHeight = size.height * 0.7f
        val radius = CornerRadius(size.width * 0.08f)
        listOf(Offset(size.width * 0.08f, size.height * 0.06f), Offset(size.width * 0.19f, size.height * 0.16f), Offset(size.width * 0.31f, size.height * 0.26f)).forEachIndexed { index, topLeft ->
            drawRoundRect(
                color = if (index == 2) colors.primary else colors.primary.copy(alpha = 0.45f),
                topLeft = topLeft,
                size = Size(cardWidth, cardHeight),
                cornerRadius = radius,
            )
            drawRoundRect(
                color = colors.onPrimary,
                topLeft = topLeft,
                size = Size(cardWidth, cardHeight),
                cornerRadius = radius,
                style = Stroke(width = size.width * 0.035f),
            )
        }
    }
}

@Composable
private fun CollectionEntries(entries: List<CollectionEntrySummary>, layout: CollectionLayout, onEntrySelected: (String) -> Unit, onArtworkClick: (String, String) -> Unit) {
    when (layout) {
        CollectionLayout.DETAILED -> LazyColumn(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)) {
            items(entries, key = CollectionEntrySummary::entryId) { entry -> CollectionEntryRow(entry, { onEntrySelected(entry.entryId) }, { fileName -> onArtworkClick(fileName, entry.cardName) }) }
        }
        CollectionLayout.COMPACT -> LazyColumn(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)) {
            items(entries, key = CollectionEntrySummary::entryId) { entry -> CompactCollectionEntryRow(entry, onClick = { onEntrySelected(entry.entryId) }) }
        }
        CollectionLayout.ARTWORK_TILES -> LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 112.dp),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) { gridItems(entries, key = CollectionEntrySummary::entryId) { entry -> CollectionArtworkTile(entry, onClick = { onEntrySelected(entry.entryId) }) } }
    }
}

@Composable
private fun CollectionEntryRow(entry: CollectionEntrySummary, onClick: () -> Unit, onArtworkClick: (String) -> Unit) {
    Card(Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable(onClick = onClick)) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(entry.cardName, style = MaterialTheme.typography.titleMedium)
                Text(listOfNotNull(entry.setCode, entry.rarity, entry.edition.localizedLabel()).joinToString(" · "), modifier = Modifier.padding(top = 4.dp), style = MaterialTheme.typography.bodyMedium)
                val quantityLabel = if (entry.isUnknownPrinting) {
                    appText(
                        "${entry.quantity} × ${entry.condition.localizedLabel()} · Unknown printing",
                        "${entry.quantity} × ${entry.condition.localizedLabel()} · Unbekannter Druck",
                    )
                } else {
                    appText(
                        "${entry.quantity} × ${entry.condition.localizedLabel()}",
                        "${entry.quantity} × ${entry.condition.localizedLabel()}",
                    )
                }
                Text(
                    quantityLabel,
                    modifier = Modifier.padding(top = 4.dp),
                    style = MaterialTheme.typography.labelLarge,
                )
            }
            CollectionEntryArtwork(entry.artwork, entry.cardName, Modifier.padding(start = 12.dp), onArtworkClick)
        }
    }
}

@Composable
private fun CompactCollectionEntryRow(entry: CollectionEntrySummary, onClick: () -> Unit) {
    Card(Modifier.fillMaxWidth().padding(vertical = 3.dp).clickable(onClick = onClick)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(entry.cardName, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(listOfNotNull(entry.setCode, entry.rarity).joinToString(" · "), style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Text(appText("${entry.quantity} × ${entry.condition.localizedLabel()}", "${entry.quantity} × ${entry.condition.localizedLabel()}"), modifier = Modifier.padding(start = 8.dp), style = MaterialTheme.typography.labelMedium, maxLines = 1)
        }
    }
}

@Composable
private fun CollectionEntryArtwork(artwork: CardArtworkDetail?, cardName: String, modifier: Modifier = Modifier, onArtworkClick: (String) -> Unit) {
    val bitmap = collectionArtworkBitmap(artwork?.localFileName)
    val shape = RoundedCornerShape(6.dp)
    Box(modifier = modifier.width(64.dp).height(92.dp).clip(shape).background(MaterialTheme.colorScheme.surfaceVariant).border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape), contentAlignment = Alignment.Center) {
        if (bitmap != null) {
            Image(bitmap = bitmap.asImageBitmap(), contentDescription = appText("English artwork for $cardName", "Englisches Kartenbild für $cardName"), contentScale = ContentScale.Crop, modifier = Modifier.matchParentSize().clickable { artwork?.localFileName?.let(onArtworkClick) })
        } else {
            val label = when (artwork?.downloadState) {
                CardArtworkDownloadState.QUEUED, CardArtworkDownloadState.DOWNLOADING -> appText("Loading\nimage", "Bild wird\ngeladen")
                else -> appText("No saved\nimage", "Kein gespeichertes\nBild")
            }
            Text(label, textAlign = TextAlign.Center, style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun CollectionArtworkTile(entry: CollectionEntrySummary, onClick: () -> Unit) {
    val bitmap = collectionArtworkBitmap(entry.artwork?.localFileName)
    val shape = RoundedCornerShape(8.dp)
    Box(modifier = Modifier.fillMaxWidth().aspectRatio(0.68f).clip(shape).background(MaterialTheme.colorScheme.surfaceVariant).border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        bitmap?.let { Image(bitmap = it.asImageBitmap(), contentDescription = appText("Open ${entry.cardName}", "${entry.cardName} öffnen"), contentScale = ContentScale.Fit, modifier = Modifier.matchParentSize()) }
    }
}

@Composable
private fun collectionArtworkBitmap(localFileName: String?): Bitmap? {
    val context = LocalContext.current
    val fileStore = remember(context) { CardArtworkFileStore(context) }
    val image = remember(localFileName) { fileStore.resolve(localFileName) }
    val bitmap by produceState<Bitmap?>(initialValue = null, image) { value = withContext(Dispatchers.IO) { image?.let { BitmapFactory.decodeFile(it.absolutePath) } } }
    return bitmap
}

@Composable
private fun layoutLabel(layout: CollectionLayout): String = when (layout) {
    CollectionLayout.DETAILED -> appText("Detailed list", "Detaillierte Liste")
    CollectionLayout.COMPACT -> appText("Compact list", "Kompakte Liste")
    CollectionLayout.ARTWORK_TILES -> appText("Artwork tiles", "Bildkacheln")
}
