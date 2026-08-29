package com.ygocardscanner.ui.manual

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ygocardscanner.model.CardCondition
import com.ygocardscanner.model.CardEdition
import com.ygocardscanner.model.CardLanguage
import com.ygocardscanner.model.UnknownPrintingDraft
import com.ygocardscanner.ui.add.InventoryFields

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManualAddScreen(
    viewModel: ManualAddViewModel,
    onBack: () -> Unit,
    onAdded: () -> Unit,
) {
    val state by viewModel.uiState.collectAsState()
    var cardName by rememberSaveable { mutableStateOf("") }
    var setCode by rememberSaveable { mutableStateOf("") }
    var quantity by rememberSaveable { mutableStateOf("1") }
    var language by rememberSaveable { mutableStateOf(CardLanguage.ENGLISH) }
    var rarity by rememberSaveable { mutableStateOf("") }
    var edition by rememberSaveable { mutableStateOf(CardEdition.UNKNOWN) }
    var condition by rememberSaveable { mutableStateOf(CardCondition.NEAR_MINT) }
    var notes by rememberSaveable { mutableStateOf("") }
    var validationMessage by rememberSaveable { mutableStateOf<String?>(null) }

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            if (event is ManualAddEvent.EntryAdded) onAdded()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Unknown printing") },
                navigationIcon = { TextButton(onClick = onBack) { Text("Back") } },
            )
        },
    ) { innerPadding ->
        LazyColumn(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            item {
                Text(
                    "Record a card when its printing is not in the local catalog. You can match it later without losing these details.",
                    modifier = Modifier.padding(16.dp),
                    style = MaterialTheme.typography.bodyMedium,
                )
                OutlinedTextField(
                    value = cardName,
                    onValueChange = { cardName = it },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                    label = { Text("Card name") },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = setCode,
                    onValueChange = { setCode = it },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                    label = { Text("Set code (optional)") },
                    singleLine = true,
                )
                InventoryFields(
                    quantity = quantity,
                    onQuantityChange = { quantity = it },
                    language = language,
                    onLanguageChange = { language = it },
                    rarity = rarity,
                    onRarityChange = { rarity = it },
                    edition = edition,
                    onEditionChange = { edition = it },
                    condition = condition,
                    onConditionChange = { condition = it },
                    notes = notes,
                    onNotesChange = { notes = it },
                )
                validationMessage?.let { message ->
                    Text(
                        message,
                        modifier = Modifier.padding(horizontal = 16.dp),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                state.errorMessage?.let { message ->
                    Text(
                        message,
                        modifier = Modifier.padding(horizontal = 16.dp),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                Button(
                    onClick = {
                        val parsedQuantity = quantity.toIntOrNull()
                        when {
                            cardName.isBlank() -> validationMessage = "Enter a card name."
                            parsedQuantity == null || parsedQuantity <= 0 -> {
                                validationMessage = "Quantity must be at least 1."
                            }
                            else -> viewModel.addUnknownPrinting(
                                UnknownPrintingDraft(
                                    cardName = cardName.trim(),
                                    setCode = setCode.trim().ifBlank { null },
                                    language = language,
                                    rarity = rarity.trim().ifBlank { null },
                                    edition = edition,
                                    condition = condition,
                                    quantity = parsedQuantity,
                                    notes = notes.trim(),
                                ),
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    enabled = !state.isSaving,
                ) {
                    Text(if (state.isSaving) "Saving…" else "Save unknown printing")
                }
            }
        }
    }
}
