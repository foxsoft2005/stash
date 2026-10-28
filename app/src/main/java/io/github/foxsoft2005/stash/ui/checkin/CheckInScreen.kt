package io.github.foxsoft2005.stash.ui.checkin

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Place
import androidx.compose.material.icons.rounded.TaskAlt
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import io.github.foxsoft2005.stash.R
import io.github.foxsoft2005.stash.data.Item
import io.github.foxsoft2005.stash.ui.common.ConfirmDeleteDialog
import io.github.foxsoft2005.stash.ui.common.PlaceDialog

/** "Still there?": one item at a time, answered with Still there / Moved… / Gone. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CheckInScreen(viewModel: CheckInViewModel, onBack: () -> Unit) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val resources = LocalResources.current

    // "“Drill” deleted · Undo": the delete only becomes final when this goes away.
    val pending = state.pendingGone
    LaunchedEffect(pending?.token) {
        if (pending != null) {
            val result = snackbar.showSnackbar(
                message = resources.getString(R.string.checkin_gone_done, pending.items.single().name),
                actionLabel = resources.getString(R.string.action_undo),
                withDismissAction = true,
                duration = SnackbarDuration.Long,
            )
            if (result == SnackbarResult.ActionPerformed) viewModel.undoGone(pending.token) else viewModel.confirmGone(pending.token)
        }
    }

    var moving by rememberSaveable { mutableStateOf(false) }
    val current = state.current

    var confirmingGone by rememberSaveable { mutableStateOf(false) }
    if (confirmingGone && current != null) {
        ConfirmDeleteDialog(
            title = stringResource(R.string.delete_item_title, current.name),
            body = stringResource(R.string.delete_items_body),
            onConfirm = {
                confirmingGone = false
                viewModel.gone()
            },
            onDismiss = { confirmingGone = false },
        )
    }
    if (moving && current != null) {
        val recentPlaces by viewModel.recentPlaces.collectAsStateWithLifecycle()
        PlaceDialog(
            title = stringResource(R.string.checkin_moved_title, current.name),
            confirmLabel = stringResource(R.string.edit_save),
            recentPlaces = recentPlaces,
            onConfirm = { place ->
                moving = false
                viewModel.moved(place)
            },
            onDismiss = { moving = false },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(stringResource(R.string.checkin_title))
                        if (current != null) {
                            Text(
                                stringResource(R.string.checkin_progress, state.position, state.total),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
                actions = {
                    if (current != null) TextButton(onClick = viewModel::skip) { Text(stringResource(R.string.checkin_skip)) }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            if (current != null) {
                Answers(
                    onStillThere = viewModel::stillThere,
                    onMoved = { moving = true },
                    onGone = { confirmingGone = true },
                )
            }
        },
    ) { padding ->
        when {
            state.isLoading -> Unit
            current == null -> Done(onBack, Modifier.padding(padding))
            else -> Question(current, state.months, Modifier.padding(padding))
        }
    }
}

@Composable
private fun Question(item: Item, months: Int, modifier: Modifier = Modifier) {
    Column(
        modifier
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item.photoPath?.let { path ->
            AsyncImage(
                model = path,
                contentDescription = item.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 280.dp)
                    .clip(RoundedCornerShape(20.dp)),
            )
        }
        Text(item.name, style = MaterialTheme.typography.titleLarge)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.Place, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.size(8.dp))
            Text(item.place, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
        }
        item.placePhotoPath?.let { path ->
            AsyncImage(
                model = path,
                contentDescription = stringResource(R.string.edit_place_photo_title),
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 200.dp)
                    .clip(RoundedCornerShape(16.dp)),
            )
        }
        Text(
            pluralStringResource(R.plurals.checkin_last_seen, months, months),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.size(8.dp))
    }
}

@Composable
private fun Answers(onStillThere: () -> Unit, onMoved: () -> Unit, onGone: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Button(onClick = onStillThere, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
            Text(stringResource(R.string.checkin_still_there))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onMoved, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) {
                Text(stringResource(R.string.checkin_moved), maxLines = 1)
            }
            OutlinedButton(
                onClick = onGone,
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                modifier = Modifier.weight(1f).heightIn(min = 48.dp),
            ) {
                Text(stringResource(R.string.checkin_gone), maxLines = 1)
            }
        }
    }
}

@Composable
private fun Done(onBack: () -> Unit, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(Icons.Rounded.TaskAlt, contentDescription = null, modifier = Modifier.size(56.dp), tint = MaterialTheme.colorScheme.primary)
            Text(stringResource(R.string.checkin_done_title), style = MaterialTheme.typography.titleLarge)
            Text(
                stringResource(R.string.checkin_done_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.size(8.dp))
            Button(onClick = onBack) { Text(stringResource(R.string.checkin_done_action)) }
        }
    }
}
