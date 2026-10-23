package dev.stash.app.ui.home

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Inventory2
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material.icons.rounded.Place
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import dev.stash.app.data.Item

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
    LaunchedEffect(message) {
        if (message != null) {
            snackbar.showSnackbar(message, withDismissAction = true, duration = SnackbarDuration.Long)
            backupViewModel.messageShown()
        }
    }

    BackupDialogs(backupViewModel, onChooseExportFile = { name -> saveBackup.launch(name) })

    Scaffold(
        topBar = {
            Row(
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
            ExtendedFloatingActionButton(
                onClick = onCapture,
                icon = { Icon(Icons.Rounded.PhotoCamera, contentDescription = null) },
                text = { Text("Stash it") },
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
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = !state.lentOnly,
                            onClick = { viewModel.setLentOnly(false) },
                            label = { Text("All ${state.totalCount}") },
                        )
                        FilterChip(
                            selected = state.lentOnly,
                            onClick = { viewModel.setLentOnly(true) },
                            label = { Text("Lent out ${state.lentCount}") },
                        )
                    }
                }
                if (state.isSearching) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        AnswerCard(query = state.query, best = state.bestMatch, onOpenItem = onOpenItem)
                    }
                }
                items(state.items, key = { it.id }) { item ->
                    ItemCard(item = item, onClick = { onOpenItem(item.id) })
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
        placeholder = { Text("Where's my…?") },
        leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
        trailingIcon = {
            if (showClear) {
                IconButton(onClick = viewModel::clearQuery) {
                    Icon(Icons.Rounded.Close, contentDescription = "Clear search")
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
                Text("Nothing matches “$query”", style = MaterialTheme.typography.titleMedium)
                Text("Maybe it hasn't been stashed yet.", style = MaterialTheme.typography.bodyMedium)
            } else {
                Text(best.name, style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.size(4.dp))
                if (best.isLent) {
                    Text("Lent to ${best.lentTo}", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                    Text("Last stored in: ${best.place}", style = MaterialTheme.typography.bodyMedium)
                } else {
                    Text(best.place, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
private fun ItemCard(item: Item, onClick: () -> Unit) {
    Card(onClick = onClick) {
        AsyncImage(
            model = item.photoPath,
            contentDescription = item.name,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp)),
        )
        Column(Modifier.padding(12.dp)) {
            Text(
                item.name,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            MetaLine(Icons.Rounded.Place, item.place)
            if (item.isLent) MetaLine(Icons.Rounded.Person, "Lent to ${item.lentTo}")
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
            Text("Nothing stashed yet", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.size(8.dp))
            Text(
                "Snap a photo when you put something away. Months later, just ask “where's my passport?”",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
