package com.foxsoft2005.stash.app.ui.edit

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.History
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.foxsoft2005.stash.app.ai.NanoState
import com.foxsoft2005.stash.app.ai.TagSource
import com.foxsoft2005.stash.app.ai.canOfferNano

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun EditItemScreen(
    viewModel: EditItemViewModel,
    onDone: () -> Unit,
    onBack: () -> Unit,
) {
    val recentPlaces by viewModel.recentPlaces.collectAsStateWithLifecycle()
    val nanoState by viewModel.nanoState.collectAsStateWithLifecycle()
    var confirmDelete by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (viewModel.isNew) "New stash" else "Stashed item") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (!viewModel.isNew) {
                        IconButton(onClick = { confirmDelete = true }) {
                            Icon(Icons.Rounded.Delete, contentDescription = "Delete")
                        }
                    }
                },
            )
        },
        bottomBar = {
            Button(
                onClick = { viewModel.save(onSaved = onDone) },
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .imePadding()
                    .padding(16.dp)
                    .heightIn(min = 56.dp),
            ) { Text("Save") }
        },
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            viewModel.photoPath?.let { path ->
                AsyncImage(
                    model = path,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 280.dp)
                        .clip(RoundedCornerShape(20.dp)),
                )
            }

            AiStatus(viewModel.aiState, nanoState, onOfferNano = viewModel::offerNano)

            OutlinedTextField(
                state = viewModel.name,
                label = { Text("What is it?") },
                isError = viewModel.showErrors && viewModel.name.text.isBlank(),
                lineLimits = TextFieldLineLimits.SingleLine,
                modifier = Modifier.fillMaxWidth(),
            )

            OutlinedTextField(
                state = viewModel.place,
                label = { Text("Where did you put it?") },
                placeholder = { Text("Top drawer, blue box") },
                isError = viewModel.showErrors && viewModel.place.text.isBlank(),
                lineLimits = TextFieldLineLimits.SingleLine,
                modifier = Modifier.fillMaxWidth(),
            )

            // One-tap places: most people reuse the same 10 spots.
            if (recentPlaces.isNotEmpty()) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    recentPlaces.forEach { place ->
                        SuggestionChip(
                            onClick = { viewModel.usePlace(place) },
                            label = { Text(place) },
                            icon = { Icon(Icons.Rounded.History, contentDescription = null, Modifier.size(16.dp)) },
                        )
                    }
                }
            }

            Text("Search keywords", style = MaterialTheme.typography.labelLarge)
            if (viewModel.tags.isNotEmpty()) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    viewModel.tags.forEach { tag ->
                        InputChip(
                            selected = false,
                            onClick = { viewModel.removeTag(tag) },
                            label = { Text(tag) },
                            trailingIcon = { Icon(Icons.Rounded.Close, contentDescription = "Remove $tag", Modifier.size(16.dp)) },
                        )
                    }
                }
            }

            // Your own words for it: the AI can't know you call the passport case "travel wallet".
            OutlinedTextField(
                state = viewModel.newTag,
                label = { Text("Add a keyword") },
                placeholder = { Text("e.g. travel wallet") },
                lineLimits = TextFieldLineLimits.SingleLine,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                // Keep the keyboard open so several keywords can be added in a row.
                onKeyboardAction = { viewModel.addTag() },
                trailingIcon = {
                    if (viewModel.newTag.text.isNotBlank()) {
                        IconButton(onClick = viewModel::addTag) {
                            Icon(Icons.Rounded.Add, contentDescription = "Add keyword")
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            )

            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    state = viewModel.lentTo,
                    label = { Text("Lent to (optional)") },
                    lineLimits = TextFieldLineLimits.SingleLine,
                    modifier = Modifier.weight(1f),
                )
                if (viewModel.lentTo.text.isNotBlank()) {
                    TextButton(onClick = viewModel::markReturned) { Text("Returned") }
                }
            }

            OutlinedTextField(
                state = viewModel.note,
                label = { Text("Note (optional)") },
                lineLimits = TextFieldLineLimits.MultiLine(minHeightInLines = 2, maxHeightInLines = 5),
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.size(8.dp))
        }
    }

    if (viewModel.showNanoPrompt) {
        AlertDialog(
            onDismissRequest = viewModel::dismissNanoPrompt,
            icon = { Icon(Icons.Rounded.AutoAwesome, contentDescription = null) },
            title = { Text("Smarter suggestions?") },
            text = {
                Text(
                    "Stash can use Gemini Nano, Google's on-device AI, to name what's in your photos " +
                        "and add better search keywords. It runs entirely on this phone: nothing is uploaded.\n\n" +
                        "It's a large one-time download, handled by Android. Wi-Fi recommended.\n\n" +
                        "Without it, Stash uses simpler built-in image labels.",
                )
            },
            confirmButton = {
                TextButton(onClick = viewModel::acceptNano) { Text("Download") }
            },
            dismissButton = {
                TextButton(onClick = viewModel::declineNano) { Text("No thanks") }
            },
        )
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete this item?") },
            text = { Text("The photo and its location will be removed from this phone.") },
            confirmButton = {
                TextButton(onClick = { confirmDelete = false; viewModel.delete(onDeleted = onDone) }) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun AiStatus(state: AiState, nanoState: NanoState, onOfferNano: () -> Unit) {
    Column {
        AiStatusLine(state)
        val usedFallback = state is AiState.NoIdea ||
            (state is AiState.Suggested && state.source == TagSource.MLKIT_LABELS)
        if (usedFallback) NanoHint(nanoState, onOfferNano)
    }
}

/** Under a labels-only (or failed) suggestion: say Nano is coming, or offer it. */
@Composable
private fun NanoHint(nanoState: NanoState, onOfferNano: () -> Unit) {
    when {
        nanoState == NanoState.DOWNLOADING -> Text(
            "Gemini Nano is downloading. Suggestions get smarter once it's ready.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        canOfferNano(nanoState) -> TextButton(onClick = onOfferNano, contentPadding = PaddingValues(0.dp)) {
            Text("Get smarter suggestions with Gemini Nano")
        }
    }
}

@Composable
private fun AiStatusLine(state: AiState) {
    when (state) {
        AiState.Idle -> Unit
        AiState.Thinking -> Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Recognizing on-device…", style = MaterialTheme.typography.labelMedium)
            LinearProgressIndicator(Modifier.fillMaxWidth())
        }
        is AiState.Suggested -> Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Rounded.AutoAwesome,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.size(6.dp))
            Text(
                when (state.source) {
                    TagSource.GEMINI_NANO -> "Suggested by Gemini Nano, on this phone"
                    TagSource.MLKIT_LABELS -> "Suggested by on-device image labels"
                },
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        AiState.NoIdea -> Text(
            "Couldn't recognize it. Just type what it is.",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
