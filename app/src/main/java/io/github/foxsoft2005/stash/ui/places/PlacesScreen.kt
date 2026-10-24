package io.github.foxsoft2005.stash.ui.places

import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.rounded.Place
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import io.github.foxsoft2005.stash.R
import io.github.foxsoft2005.stash.data.PlaceSummary

/** Every place things are stored, A–Z. Picking one shows just that place on Home. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlacesScreen(
    viewModel: PlacesViewModel,
    onPick: (String) -> Unit,
    onBack: () -> Unit,
) {
    val places by viewModel.places.collectAsStateWithLifecycle()
    Scaffold(
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
                    PlaceRow(place, onClick = { onPick(place.name) })
                }
            }
        }
    }
}

@Composable
private fun PlaceRow(place: PlaceSummary, onClick: () -> Unit) {
    ListItem(
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
        modifier = Modifier.clickable(onClick = onClick),
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
