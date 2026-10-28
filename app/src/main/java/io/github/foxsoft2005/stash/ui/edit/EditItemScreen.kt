package io.github.foxsoft2005.stash.ui.edit

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import io.github.foxsoft2005.stash.share.ItemSharing
import androidx.compose.ui.platform.LocalContext
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.DropdownMenu
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.ChatBubbleOutline
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AddAPhoto
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import io.github.foxsoft2005.stash.R
import io.github.foxsoft2005.stash.ai.NanoState
import io.github.foxsoft2005.stash.ai.TagSource
import io.github.foxsoft2005.stash.ai.canOfferNano

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun EditItemScreen(
    viewModel: EditItemViewModel,
    /** A place photo just taken by the camera, waiting to be picked up. */
    placePhotoResult: String?,
    onPlacePhotoConsumed: () -> Unit,
    onTakePlacePhoto: () -> Unit,
    /** Shows a photo full screen (pinch to zoom). */
    onOpenPhoto: (path: String, description: String?) -> Unit,
    /** "Open existing" in the duplicate warning. */
    onOpenExisting: (itemId: Long) -> Unit,
    onDone: () -> Unit,
    onBack: () -> Unit,
) {
    LaunchedEffect(placePhotoResult) {
        if (placePhotoResult != null) {
            viewModel.setPlacePhoto(placePhotoResult)
            onPlacePhotoConsumed()
        }
    }
    val recentPlaces by viewModel.recentPlaces.collectAsStateWithLifecycle()
    val nanoState by viewModel.nanoState.collectAsStateWithLifecycle()
    var confirmDelete by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(if (viewModel.isNew) R.string.edit_title_new else R.string.edit_title_existing)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
                actions = {
                    if (!viewModel.isNew) {
                        ShareMenu(viewModel)
                        IconButton(onClick = { confirmDelete = true }) {
                            Icon(Icons.Rounded.Delete, contentDescription = stringResource(R.string.action_delete))
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
            ) { Text(stringResource(R.string.edit_save)) }
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
                val itemName = viewModel.name.text.toString().ifBlank { null }
                AsyncImage(
                    model = path,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 280.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .clickable(onClickLabel = stringResource(R.string.photo_open)) { onOpenPhoto(path, itemName) },
                )
            }

            AiStatus(viewModel.aiState, nanoState, onOfferNano = viewModel::offerNano)

            OutlinedTextField(
                state = viewModel.name,
                label = { Text(stringResource(R.string.edit_name_label)) },
                isError = viewModel.showErrors && viewModel.name.text.isBlank(),
                lineLimits = TextFieldLineLimits.SingleLine,
                modifier = Modifier.fillMaxWidth(),
            )

            OutlinedTextField(
                state = viewModel.place,
                label = { Text(stringResource(R.string.edit_place_label)) },
                placeholder = { Text(stringResource(R.string.edit_place_placeholder)) },
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

            PlacePhoto(
                path = viewModel.placePhotoPath,
                onTake = onTakePlacePhoto,
                onRemove = viewModel::removePlacePhoto,
                onOpen = { onOpenPhoto(it, viewModel.place.text.toString().ifBlank { null }) },
            )

            Text(stringResource(R.string.edit_keywords_title), style = MaterialTheme.typography.labelLarge)
            if (viewModel.tags.isNotEmpty()) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    viewModel.tags.forEach { tag ->
                        InputChip(
                            selected = false,
                            onClick = { viewModel.removeTag(tag) },
                            label = { Text(tag) },
                            trailingIcon = { Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.edit_remove_keyword, tag), Modifier.size(16.dp)) },
                        )
                    }
                }
            }

            // Your own words for it: the AI can't know you call the passport case "travel wallet".
            OutlinedTextField(
                state = viewModel.newTag,
                label = { Text(stringResource(R.string.edit_add_keyword_label)) },
                placeholder = { Text(stringResource(R.string.edit_add_keyword_placeholder)) },
                lineLimits = TextFieldLineLimits.SingleLine,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                // Keep the keyboard open so several keywords can be added in a row.
                onKeyboardAction = { viewModel.addTag() },
                trailingIcon = {
                    if (viewModel.newTag.text.isNotBlank()) {
                        IconButton(onClick = viewModel::addTag) {
                            Icon(Icons.Rounded.Add, contentDescription = stringResource(R.string.edit_add_keyword))
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            )

            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    state = viewModel.lentTo,
                    label = { Text(stringResource(R.string.edit_lent_to_label)) },
                    lineLimits = TextFieldLineLimits.SingleLine,
                    modifier = Modifier.weight(1f),
                )
                if (viewModel.lentTo.text.isNotBlank()) {
                    TextButton(onClick = viewModel::markReturned) { Text(stringResource(R.string.edit_returned)) }
                }
            }

            OutlinedTextField(
                state = viewModel.note,
                label = { Text(stringResource(R.string.edit_note_label)) },
                lineLimits = TextFieldLineLimits.MultiLine(minHeightInLines = 2, maxHeightInLines = 5),
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.size(8.dp))
        }
    }

    viewModel.duplicate?.let { dup ->
        AlertDialog(
            onDismissRequest = viewModel::dismissDuplicate,
            title = { Text(stringResource(R.string.dup_title, dup.name)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.dup_where, dup.first.place))
                    if (dup.first.isLent) Text(stringResource(R.string.home_lent_to, dup.first.lentTo.orEmpty()))
                    if (dup.others > 0) Text(pluralStringResource(R.plurals.dup_more, dup.others, dup.others))
                }
            },
            confirmButton = {
                TextButton(onClick = { viewModel.saveAnyway(onSaved = onDone) }) { Text(stringResource(R.string.dup_save_anyway)) }
            },
            dismissButton = {
                TextButton(onClick = {
                    viewModel.dismissDuplicate()
                    onOpenExisting(dup.first.id)
                }) { Text(stringResource(R.string.dup_open_existing)) }
            },
        )
    }

    if (viewModel.showNanoPrompt) {
        AlertDialog(
            onDismissRequest = viewModel::dismissNanoPrompt,
            icon = { Icon(Icons.Rounded.AutoAwesome, contentDescription = null) },
            title = { Text(stringResource(R.string.ai_nano_prompt_title)) },
            text = {
                Text(stringResource(R.string.ai_nano_prompt_body))
            },
            confirmButton = {
                TextButton(onClick = viewModel::acceptNano) { Text(stringResource(R.string.ai_nano_prompt_download)) }
            },
            dismissButton = {
                TextButton(onClick = viewModel::declineNano) { Text(stringResource(R.string.ai_nano_prompt_decline)) }
            },
        )
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.edit_delete_title)) },
            text = { Text(stringResource(R.string.edit_delete_body)) },
            confirmButton = {
                TextButton(onClick = { confirmDelete = false; viewModel.delete(onDeleted = onDone) }) {
                    Text(stringResource(R.string.action_delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.action_cancel)) }
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
            stringResource(R.string.ai_nano_downloading),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        canOfferNano(nanoState) -> TextButton(onClick = onOfferNano, contentPadding = PaddingValues(0.dp)) {
            Text(stringResource(R.string.ai_nano_offer))
        }
    }
}

@Composable
private fun AiStatusLine(state: AiState) {
    when (state) {
        AiState.Idle -> Unit
        AiState.Thinking -> Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(stringResource(R.string.ai_thinking), style = MaterialTheme.typography.labelMedium)
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
                stringResource(
                    when (state.source) {
                        TagSource.GEMINI_NANO -> R.string.ai_suggested_nano
                        TagSource.MLKIT_LABELS -> R.string.ai_suggested_labels
                    },
                ),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        AiState.NoIdea -> Text(
            stringResource(R.string.ai_no_idea),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** The optional "where it is" photo: an Add button, or the photo with Retake and Remove. */
@Composable
private fun PlacePhoto(path: String?, onTake: () -> Unit, onRemove: () -> Unit, onOpen: (String) -> Unit) {
    if (path == null) {
        OutlinedButton(onClick = onTake, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Rounded.AddAPhoto, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.size(8.dp))
            Text(stringResource(R.string.edit_place_photo_add))
        }
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(stringResource(R.string.edit_place_photo_title), style = MaterialTheme.typography.labelLarge)
        Box {
            AsyncImage(
                model = path,
                contentDescription = stringResource(R.string.edit_place_photo_title),
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 220.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .clickable(onClickLabel = stringResource(R.string.photo_open)) { onOpen(path) },
            )
            Row(
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilledTonalIconButton(onClick = onTake) {
                    Icon(Icons.Rounded.PhotoCamera, contentDescription = stringResource(R.string.edit_place_photo_retake))
                }
                FilledTonalIconButton(onClick = onRemove) {
                    Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.edit_place_photo_remove))
                }
            }
        }
    }
}

/**
 * "Share where it is": as text ("Passport → Top drawer"), or with the photos too.
 * Shares what's on screen, so a just-typed new place goes out as well.
 */
@Composable
private fun ShareMenu(viewModel: EditItemViewModel) {
    val context = LocalContext.current
    var open by remember { mutableStateOf(false) }
    val photos = listOfNotNull(viewModel.photoPath, viewModel.placePhotoPath)
    fun share(withPhotos: Boolean) {
        open = false
        ItemSharing.share(
            context,
            name = viewModel.name.text.toString(),
            place = viewModel.place.text.toString(),
            lentTo = viewModel.lentTo.text.toString(),
            note = viewModel.note.text.toString(),
            photos = if (withPhotos) photos else emptyList(),
        )
    }
    Box {
        IconButton(onClick = { if (photos.isEmpty()) share(withPhotos = false) else open = true }) {
            Icon(Icons.Rounded.Share, contentDescription = stringResource(R.string.share_action))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.share_as_text)) },
                leadingIcon = { Icon(Icons.Rounded.ChatBubbleOutline, contentDescription = null) },
                onClick = { share(withPhotos = false) },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.share_with_photos)) },
                leadingIcon = { Icon(Icons.Rounded.Image, contentDescription = null) },
                onClick = { share(withPhotos = true) },
            )
        }
    }
}
