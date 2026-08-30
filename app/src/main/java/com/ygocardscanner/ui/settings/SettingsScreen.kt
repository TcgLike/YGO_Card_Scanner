package com.ygocardscanner.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ygocardscanner.data.repository.CatalogUpdatePhase
import com.ygocardscanner.model.ArtworkPackPhase
import com.ygocardscanner.model.CardLanguage
import com.ygocardscanner.ui.localization.appText

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(viewModel: SettingsViewModel, onBack: () -> Unit) {
    val state by viewModel.uiState.collectAsState()
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(appText("Settings", "Einstellungen")) },
                navigationIcon = { TextButton(onClick = onBack) { Text(appText("Back", "ZurÃ¼ck")) } },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
        ) {
            SettingsCard(title = appText("App language", "App-Sprache")) {
                Text(appText("Changes the language throughout the app. Card catalog downloads always include English and German.", "Ã„ndert die Sprache in der gesamten App. Katalogdownloads enthalten immer Englisch und Deutsch."))
                TextButton(onClick = { viewModel.setLanguage(CardLanguage.ENGLISH) }, enabled = state.language != CardLanguage.ENGLISH) { Text(appText("English", "Englisch")) }
                TextButton(onClick = { viewModel.setLanguage(CardLanguage.GERMAN) }, enabled = state.language != CardLanguage.GERMAN) { Text(appText("German", "Deutsch")) }
            }
            SettingsCard(title = appText("Live scan feedback", "Live-Scan-RÃ¼ckmeldung")) {
                Text(
                    appText(
                        "Show a short card-to-deck animation after a live scan is added. It uses only artwork already stored on this device and does not pause scanning.",
                        "Zeigt nach einem hinzugefÃ¼gten Live-Scan eine kurze Karten-zu-Deck-Animation. Sie verwendet nur bereits auf diesem GerÃ¤t gespeicherte Bilder und pausiert den Scan nicht.",
                    ),
                )
                Switch(
                    checked = state.scanSuccessAnimationEnabled,
                    onCheckedChange = viewModel::setScanSuccessAnimationEnabled,
                )
            }
            SettingsCard(title = appText("Card catalog", "Kartenkatalog")) {
                Text(catalogMessage(state.catalogStatus?.phase, state.catalogStatus?.message))
                if (state.catalogStatus?.phase?.isInProgress == true) {
                    Spacer(Modifier.height(12.dp))
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    Text(
                        appText(
                            "The download is still working. This may take several minutes for both languages.",
                            "Der Download lÃ¤uft weiter. FÃ¼r beide Sprachen kann das einige Minuten dauern.",
                        ),
                        modifier = Modifier.padding(top = 6.dp),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Button(
                    onClick = viewModel::refreshCatalog,
                    enabled = !state.isSchedulingCatalog && state.catalogStatus?.phase?.isInProgress != true,
                ) {
                    Text(
                        if (state.isSchedulingCatalog) {
                            appText("Scheduling...", "Wird geplant...")
                        } else {
                            appText("Download / refresh English + German catalog", "Englischen + deutschen Katalog herunterladen / aktualisieren")
                        },
                    )
                }
            }
            if (state.supportsGermanPrintingBackup) {
            SettingsCard(title = appText("German printing backup", "Deutsche Drucke (Zusatzquelle)")) {
                Text(appText(
                    "Optional community source for German physical set codes. It downloads about 35 MB and only runs when enabled.",
                    "Optionale Community-Quelle fÃ¼r deutsche physische Set-Codes. Der Download umfasst etwa 35 MB und lÃ¤uft nur wenn aktiviert.",
                ))
                Switch(
                    checked = state.germanPrintingSourceEnabled,
                    onCheckedChange = viewModel::setGermanPrintingSourceEnabled,
                    enabled = !state.isSchedulingGermanPrintings,
                )
                if (state.germanPrintingSourceEnabled) {
                    Text(germanPrintingMessage(state.germanPrintingStatus?.phase, state.germanPrintingStatus?.message))
                    if (state.germanPrintingStatus?.phase?.isInProgress == true) {
                        Spacer(Modifier.height(12.dp))
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }
                    Button(
                        onClick = viewModel::refreshGermanPrintings,
                        enabled = !state.isSchedulingGermanPrintings && state.germanPrintingStatus?.phase?.isInProgress != true,
                    ) {
                        Text(if (state.isSchedulingGermanPrintings) appText("Schedulingâ€¦", "Wird geplantâ€¦") else appText("Download / refresh German codes", "Deutsche Codes herunterladen / aktualisieren"))
                    }
                } else {
                    Text(
                        appText(
                            "Disabled: this source contributes no search or scanner results. Stored catalog and inventory records are retained.",
                            "Deaktiviert: Diese Quelle liefert keine Such- oder Scanner-Ergebnisse. Gespeicherte Katalog- und InventareintrÃ¤ge bleiben erhalten.",
                        ),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
            }
            SettingsCard(title = appText("Offline card images", "Offline-Kartenbilder")) {
                Text(artworkMessage(state.artworkStatus?.phase, state.artworkStatus?.completedArtworkCount, state.artworkStatus?.totalArtworkCount, state.artworkStatus?.message))
                Button(onClick = viewModel::resumeArtworkDownload, enabled = !state.isSchedulingArtwork) {
                    Text(if (state.isSchedulingArtwork) appText("Schedulingâ€¦", "Wird geplantâ€¦") else artworkAction(state.artworkStatus?.phase))
                }
            }
            state.errorMessage?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 12.dp)) }
        }
    }
}

@Composable
private fun SettingsCard(title: String, content: @Composable () -> Unit) {
    Card(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Column(Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            content()
        }
    }
}

@Composable
private fun catalogMessage(phase: CatalogUpdatePhase?, message: String?): String = when (phase) {
    null -> appText("Download the complete local catalog in English and German.", "LÃ¤dt den vollstÃ¤ndigen lokalen Katalog auf Englisch und Deutsch herunter.")
    CatalogUpdatePhase.QUEUED, CatalogUpdatePhase.RUNNING -> appText("Catalog download is running.", "Katalogdownload lÃ¤uft.")
    CatalogUpdatePhase.RETRYING -> appText("Catalog download will resume when connected.", "Katalogdownload wird bei Verbindung fortgesetzt.")
    CatalogUpdatePhase.SUCCEEDED -> appText("Catalog is up to date in English and German.", "Katalog ist auf Englisch und Deutsch aktuell.")
    CatalogUpdatePhase.FAILED -> message ?: appText("Catalog download stopped.", "Katalogdownload wurde angehalten.")
}

@Composable
private fun germanPrintingMessage(phase: CatalogUpdatePhase?, message: String?): String = when (phase) {
    null -> appText("Enabled. Download the optional German physical printing catalog when you are ready.", "Aktiviert. Lade den optionalen Katalog deutscher physischer Drucke herunter, wenn du bereit bist.")
    CatalogUpdatePhase.QUEUED, CatalogUpdatePhase.RUNNING -> appText("German printing update is running.", "Update der deutschen Drucke lÃ¤uft.")
    CatalogUpdatePhase.RETRYING -> appText("German printing update will resume when connected.", "Update der deutschen Drucke wird bei Verbindung fortgesetzt.")
    CatalogUpdatePhase.SUCCEEDED -> appText("German physical printing codes are ready.", "Deutsche physische Druckcodes sind bereit.")
    CatalogUpdatePhase.FAILED -> message ?: appText("German printing update stopped.", "Update der deutschen Drucke wurde angehalten.")
}
@Composable
private fun artworkMessage(phase: ArtworkPackPhase?, completed: Int?, total: Int?, message: String?): String = when (phase) {
    null -> appText("Optional: download one English image for every catalog card. The app requires 3.5 GiB free space and limits the cache to 4 GiB.", "Optional: LÃ¤dt ein englisches Bild fÃ¼r jede Katalogkarte herunter. Die App benÃ¶tigt 3,5 GiB freien Speicher und begrenzt den Cache auf 4 GiB.")
    ArtworkPackPhase.QUEUED, ArtworkPackPhase.RUNNING -> appText("Downloading offline images: ${completed ?: 0} / ${total ?: 0}.", "Offline-Bilder werden heruntergeladen: ${completed ?: 0} / ${total ?: 0}.")
    ArtworkPackPhase.RETRYING -> appText("Image download will resume when connected.", "Bilddownload wird bei Verbindung fortgesetzt.")
    ArtworkPackPhase.SUCCEEDED -> appText("Offline English images are ready: ${completed ?: 0} cards.", "Offline-englische Bilder sind bereit: ${completed ?: 0} Karten.")
    ArtworkPackPhase.QUOTA_REACHED, ArtworkPackPhase.FAILED -> message ?: appText("Image download stopped.", "Bilddownload wurde angehalten.")
}

@Composable
private fun artworkAction(phase: ArtworkPackPhase?): String = when (phase) {
    null -> appText("Download offline card images", "Offline-Kartenbilder herunterladen")
    ArtworkPackPhase.SUCCEEDED -> appText("Check cached images", "Gespeicherte Bilder prÃ¼fen")
    ArtworkPackPhase.QUOTA_REACHED -> appText("Retry after freeing space", "Nach Speicherfreigabe wiederholen")
    else -> appText("Resume image download", "Bilddownload fortsetzen")
}
