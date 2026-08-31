package com.ygocardscanner.ui.officialdecks

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.ygocardscanner.data.officialdecks.yugioh.OfficialDeckProductSummary
import com.ygocardscanner.data.officialdecks.yugioh.OfficialDeckType
import com.ygocardscanner.ui.localization.appText

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OfficialDeckLibraryScreen(viewModel: OfficialDeckLibraryViewModel, onBack: () -> Unit, onVariantSelected: (String) -> Unit) {
    val state by viewModel.uiState.collectAsState()
    Scaffold(topBar = { TopAppBar(title = { Text(appText("Official deck library", "Offizielle Deckbibliothek")) }, navigationIcon = { TextButton(onClick = onBack) { Text(appText("Back", "Zur\u00FCck")) } }) }) { padding ->
        when {
            state.isLoading -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { Text(appText("Loading official decks\u2026", "Offizielle Decks werden geladen\u2026")) }
            state.errorMessage != null -> { val message = requireNotNull(state.errorMessage); Column(Modifier.fillMaxSize().padding(padding).padding(16.dp)) { Text(message, color = MaterialTheme.colorScheme.error); Button(onClick = viewModel::retry, modifier = Modifier.padding(top = 12.dp)) { Text(appText("Try again", "Erneut versuchen")) } } }
            else -> LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                item { Text(appText("Curated offline lists for official products. Box art is a local generic illustration; no product images are downloaded.", "Kuratiere Offline-Listen f\u00FCr offizielle Produkte. Die Boxgrafik ist eine lokale, allgemeine Illustration; es werden keine Produktbilder heruntergeladen."), style = MaterialTheme.typography.bodySmall) }
                items(state.products, key = OfficialDeckProductSummary::productId) { product -> ProductCard(product, onVariantSelected) }
            }
        }
    }
}

@Composable private fun ProductCard(product: OfficialDeckProductSummary, onVariantSelected: (String) -> Unit) {
    Card(Modifier.fillMaxWidth()) { Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        DeckBoxArt(product.type, Modifier.size(82.dp, 112.dp))
        Column(Modifier.weight(1f)) {
            Text(product.title, style = MaterialTheme.typography.titleMedium)
            Text(appText("${product.type.localizedLabel()} \u00B7 ${product.releaseDate}", "${product.type.localizedLabel()} \u00B7 ${product.releaseDate}"), style = MaterialTheme.typography.bodySmall)
            if (product.variants.isEmpty()) Text(appText("Deck list is being verified.", "Die Deckliste wird noch gepr\u00FCft."), modifier = Modifier.padding(top = 8.dp), style = MaterialTheme.typography.bodySmall)
            product.variants.forEach { variant -> Text(variant.title, modifier = Modifier.padding(top = 8.dp)); Button(onClick = { onVariantSelected(variant.variantId) }, modifier = Modifier.padding(top = 4.dp)) { Text(appText("Review ${variant.totalCardCount} cards", "${variant.totalCardCount} Karten pr\u00FCfen")) } }
        }
    } }
}

@Composable private fun DeckBoxArt(type: OfficialDeckType, modifier: Modifier) {
    val color = when (type) { OfficialDeckType.STARTER -> Color(0xFF384C9A); OfficialDeckType.STRUCTURE -> Color(0xFF7B3F2E); OfficialDeckType.LEGENDARY -> Color(0xFF7F6A24) }
    Box(modifier.clip(MaterialTheme.shapes.medium).background(color), contentAlignment = Alignment.Center) { Text(type.localizedLabel(), color = Color.White, style = MaterialTheme.typography.labelLarge) }
}

@Composable private fun OfficialDeckType.localizedLabel(): String = when (this) { OfficialDeckType.STARTER -> appText("Starter", "Starter"); OfficialDeckType.STRUCTURE -> appText("Structure", "Structure"); OfficialDeckType.LEGENDARY -> appText("Legendary", "Legend\u00E4r") }