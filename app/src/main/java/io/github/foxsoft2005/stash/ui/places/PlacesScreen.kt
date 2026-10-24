package io.github.foxsoft2005.stash.ui.places

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Place
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import io.github.foxsoft2005.stash.R
import io.github.foxsoft2005.stash.data.PlaceSummary
import io.github.foxsoft2005.stash.ui.common.PlaceDialog

/** Every place things are stored, A–Z. Picking one shows just that place on Home. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlacesScreen(
    viewModel: PlacesViewModel,
    onPick: (String) -> Unit,
    /** A place was renamed (so a filter on its old name can follow it). */
    onRenamed: (fromKey: String, newName: String) -> Unit,
    onBack: () -> Unit,
) {
    val places by viewModel.places.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val resources = LocalResources.current
    val message = viewModel.message
    LaunchedEffect(message) {
        if (message != null) {
            snackbar.showSnackbar(message.resolve(resources), withDismissAction = true)
            viewModel.messageShown()
        }
    }

    // Saved by key, so the dialog survives rotation; dropped if the place is gone.
    var renamingKey by rememberSaveable { mutableStateOf<String?>(null) }
    val renaming = places?.firstOrNull { it.key == renamingKey }
    if (renaming != null) {
        PlaceDialog(
            title = stringResource(R.string.places_rename_title),
            confirmLabel = stringResource(R.string.places_rename_action),
            recentPlaces = emptyList(),
            initial = renaming.name,
            onConfirm = { newName ->
                renamingKey = null
                viewModel.rename(renaming, newName)
                onRenamed(renaming.key, newName)
            },
            onDismiss = { renamingKey = null },
            extra = { current ->
                val into = viewModel.mergeTarget(renaming, current)
                if (into != null) {
                    Text(
                        pluralStringResource(R.plurals.places_merge_hint, into.itemCount, into.itemCount, into.name),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
        )
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.places_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
            )
        },
    ) { padding ->
        val list = places
        when {
            list == null -> Unit
            list.isEmpty() -> EmptyPlaces(Modifier.padding(padding))
            else -> LazyColumn(contentPadding = PaddingValues(top = padding.calculateTopPadding(), bottom = padding.calculateBottomPadding() + 16.dp)) {
                items(list, key = { it.key }) { place ->
                    PlaceRow(place, onClick = { onPick(place.name) }, onRename = { renamingKey = place.key })
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PlaceRow(place: PlaceSummary, onClick: () -> Unit, onRename: () -> Unit) {
    val renameLabel = stringResource(R.string.places_rename_cd, place.name)
    ListItem(
        trailingContent = {
            IconButton(onClick = onRename) {
                Icon(Icons.Rounded.Edit, contentDescription = renameLabel)
            }
        },
        headlineContent = { Text(place.name, maxLines = 2, overflow = TextOverflow.Ellipsis) },
        supportingContent = { Text(pluralStringResource(R.plurals.places_count, place.itemCount, place.itemCount)) },
        leadingContent = {
            val thumb = Modifier.size(48.dp).clip(RoundedCornerShape(8.dp))
            if (place.latestPhoto != null) {
                AsyncImage(
                    model = place.latestPhoto,
                    contentDescription = null, // the name next to it says it all
                    contentScale = ContentScale.Crop,
                    modifier = thumb,
                )
            } else {
                Surface(color = MaterialTheme.colorScheme.secondaryContainer, modifier = thumb) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Rounded.Place, contentDescription = null, tint = MaterialTheme.colorScheme.onSecondaryContainer)
                    }
                }
            }
        },
        // Long-press is a shortcut; the pencil is the discoverable way to rename.
        modifier = Modifier.combinedClickable(onClick = onClick, onLongClick = onRename, onLongClickLabel = renameLabel),
    )
}

@Composable
private fun EmptyPlaces(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Rounded.Place, contentDescription = null, modifier = Modifier.size(56.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.size(16.dp))
            Text(stringResource(R.string.places_empty_title), style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.size(8.dp))
            Text(
                stringResource(R.string.places_empty_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
