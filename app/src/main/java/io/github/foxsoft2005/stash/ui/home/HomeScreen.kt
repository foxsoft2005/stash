package io.github.foxsoft2005.stash.ui.home

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.AssignmentReturn
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material.icons.rounded.SelectAll
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Inventory2
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material.icons.rounded.Place
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import io.github.foxsoft2005.stash.R
import io.github.foxsoft2005.stash.data.Item

@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    backupViewModel: BackupViewModel,
    onCapture: () -> Unit,
    onOpenItem: (Long) -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }

    // System file pickers: the user decides where backups live.
    val saveBackup = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream"),
        backupViewModel::exportTo,
    )
    val openBackup = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
        backupViewModel::restoreFrom,
    )

    val message = backupViewModel.message
    val resources = LocalResources.current
    LaunchedEffect(message) {
        if (message != null) {
            snackbar.showSnackbar(message.resolve(resources), withDismissAction = true, duration = SnackbarDuration.Long)
            backupViewModel.messageShown()
        }
    }

    val homeMessage = viewModel.message
    LaunchedEffect(homeMessage) {
        if (homeMessage != null) {
            snackbar.showSnackbar(homeMessage.resolve(resources), withDismissAction = true)
            viewModel.messageShown()
        }
    }

    // "Deleted 3 items · Undo": the delete only becomes final when this goes away.
    val pending = state.pendingDeletion
    LaunchedEffect(pending?.token) {
        if (pending != null) {
            val result = snackbar.showSnackbar(
                message = resources.getQuantityString(R.plurals.selection_deleted, pending.items.size, pending.items.size),
                actionLabel = resources.getString(R.string.action_undo),
                withDismissAction = true,
                duration = SnackbarDuration.Long,
            )
            if (result == SnackbarResult.ActionPerformed) {
                viewModel.undoDelete(pending.token)
            } else {
                viewModel.confirmDelete(pending.token)
            }
        }
    }

    BackHandler(enabled = state.isSelecting, onBack = viewModel::clearSelection)

    BackupDialogs(backupViewModel, onChooseExportFile = { name -> saveBackup.launch(name) })

    Scaffold(
        topBar = {
            if (state.isSelecting) {
                SelectionBar(
                    count = state.selected.size,
                    canMarkReturned = state.anySelectedLent,
                    onCancel = viewModel::clearSelection,
                    onSelectAll = viewModel::selectAll,
                    onMarkReturned = viewModel::markSelectedReturned,
                    onDelete = viewModel::deleteSelected,
                )
            } else Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(start = 16.dp, end = 4.dp, top = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                WhereSearchField(
                    viewModel = viewModel,
                    showClear = state.isSearching,
                    modifier = Modifier.weight(1f),
                )
                BackupMenu(
                    onBackUp = backupViewModel::startExport,
                    // .stash has no registered MIME type, so pickers usually report octet-stream.
                    onRestore = { openBackup.launch(arrayOf("application/octet-stream", "*/*")) },
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
        floatingActionButton = {
            if (!state.isSelecting) ExtendedFloatingActionButton(
                onClick = onCapture,
                icon = { Icon(Icons.Rounded.PhotoCamera, contentDescription = null) },
                text = { Text(stringResource(R.string.home_capture)) },
            )
        },
    ) { padding ->
        when {
            state.isLoading -> Unit
            state.totalCount == 0 -> EmptyState(Modifier.padding(padding))
            else -> LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 160.dp),
                contentPadding = PaddingValues(
                    start = 16.dp, end = 16.dp,
                    top = padding.calculateTopPadding() + 8.dp,
                    bottom = padding.calculateBottomPadding() + 96.dp,
                ),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                val reminder = state.backupReminder
                if (reminder != null && !state.isSearching) {
                    item(span = { GridItemSpan(maxLineSpan) }, key = "backup-reminder") {
                        BackupReminderCard(
                            reminder = reminder,
                            onBackUp = backupViewModel::startExport,
                            onLater = viewModel::snoozeBackupReminder,
                        )
                    }
                }
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = !state.lentOnly,
                            onClick = { viewModel.setLentOnly(false) },
                            label = { Text(stringResource(R.string.home_filter_all, state.totalCount)) },
                        )
                        FilterChip(
                            selected = state.lentOnly,
                            onClick = { viewModel.setLentOnly(true) },
                            label = { Text(stringResource(R.string.home_filter_lent, state.lentCount)) },
                        )
                    }
                }
                if (state.isSearching) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        AnswerCard(query = state.query, best = state.bestMatch, onOpenItem = onOpenItem)
                    }
                }
                val selectedIds = state.selectedIds
                items(state.items, key = { it.id }) { item ->
                    ItemCard(
                        item = item,
                        selecting = state.isSelecting,
                        selected = item.id in selectedIds,
                        onClick = { if (!viewModel.onItemClick(item.id)) onOpenItem(item.id) },
                        onLongClick = { viewModel.onItemLongPress(item.id) },
                    )
                }
            }
        }
    }
}

@Composable
private fun WhereSearchField(
    viewModel: HomeViewModel,
    showClear: Boolean,
    modifier: Modifier = Modifier,
) {
    TextField(
        state = viewModel.queryState,
        modifier = modifier,
        placeholder = { Text(stringResource(R.string.home_search_placeholder)) },
        leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
        trailingIcon = {
            if (showClear) {
                IconButton(onClick = viewModel::clearQuery) {
                    Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.home_clear_search))
                }
            }
        },
        lineLimits = TextFieldLineLimits.SingleLine,
        shape = RoundedCornerShape(28.dp),
        colors = TextFieldDefaults.colors(
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
        ),
    )
}

/** The headline answer: "Passport → top drawer, blue box". */
@Composable
private fun AnswerCard(query: String, best: Item?, onOpenItem: (Long) -> Unit) {
    Card(
        onClick = { best?.let { onOpenItem(it.id) } },
        enabled = best != null,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            disabledContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(20.dp)) {
            if (best == null) {
                Text(stringResource(R.string.home_no_match_title, query), style = MaterialTheme.typography.titleMedium)
                Text(stringResource(R.string.home_no_match_body), style = MaterialTheme.typography.bodyMedium)
            } else {
                Text(best.name, style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.size(4.dp))
                if (best.isLent) {
                    Text(stringResource(R.string.home_lent_to, best.lentTo.orEmpty()), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                    Text(stringResource(R.string.home_last_stored_in, best.place), style = MaterialTheme.typography.bodyMedium)
                } else {
                    Text(best.place, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

/** Batch mode's top bar: "3 selected" and the actions for them. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SelectionBar(
    count: Int,
    canMarkReturned: Boolean,
    onCancel: () -> Unit,
    onSelectAll: () -> Unit,
    onMarkReturned: () -> Unit,
    onDelete: () -> Unit,
) {
    TopAppBar(
        navigationIcon = {
            IconButton(onClick = onCancel) {
                Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.selection_cancel))
            }
        },
        title = { Text(pluralStringResource(R.plurals.selection_count, count, count)) },
        actions = {
            IconButton(onClick = onSelectAll) {
                Icon(Icons.Rounded.SelectAll, contentDescription = stringResource(R.string.selection_select_all))
            }
            if (canMarkReturned) {
                IconButton(onClick = onMarkReturned) {
                    Icon(
                        Icons.AutoMirrored.Rounded.AssignmentReturn,
                        contentDescription = stringResource(R.string.selection_mark_returned),
                    )
                }
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Rounded.Delete, contentDescription = stringResource(R.string.selection_delete))
            }
        },
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ItemCard(
    item: Item,
    selecting: Boolean,
    selected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val haptics = LocalHapticFeedback.current
    val shape = CardDefaults.shape
    Card(
        modifier = Modifier
            .then(if (selected) Modifier.border(3.dp, MaterialTheme.colorScheme.primary, shape) else Modifier)
            .clip(shape)
            .combinedClickable(
                onClick = onClick,
                onLongClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    onLongClick()
                },
                onLongClickLabel = stringResource(R.string.selection_select),
            )
            .then(if (selecting) Modifier.semantics { this.selected = selected } else Modifier),
    ) {
        Box {
            AsyncImage(
                model = item.photoPath,
                contentDescription = item.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp)),
            )
            if (selecting) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp),
                ) {
                    Icon(
                        if (selected) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked,
                        contentDescription = null, // state is announced via semantics { selected }
                        tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(28.dp),
                    )
                }
            }
        }
        Column(Modifier.padding(12.dp)) {
            Text(
                item.name,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            MetaLine(Icons.Rounded.Place, item.place)
            if (item.isLent) MetaLine(Icons.Rounded.Person, stringResource(R.string.home_lent_to, item.lentTo.orEmpty()))
        }
    }
}

@Composable
private fun MetaLine(icon: ImageVector, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.size(4.dp))
        Text(
            text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun EmptyState(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Rounded.Inventory2, contentDescription = null, modifier = Modifier.size(56.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.size(16.dp))
            Text(stringResource(R.string.home_empty_title), style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.size(8.dp))
            Text(
                stringResource(R.string.home_empty_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
