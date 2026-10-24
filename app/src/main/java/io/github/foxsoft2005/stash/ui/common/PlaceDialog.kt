package io.github.foxsoft2005.stash.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.History
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.foxsoft2005.stash.R

/**
 * Asks for a place: a text field plus one-tap chips for recent places.
 * Used by batch "Move to…" and by renaming a place.
 *
 * [extra] shows under the field and gets the current text (e.g. a "will merge" hint).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PlaceDialog(
    title: String,
    confirmLabel: String,
    recentPlaces: List<String>,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
    initial: String = "",
    extra: @Composable (current: String) -> Unit = {},
) {
    val place = rememberTextFieldState(initial)
    val current = place.text.toString()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.verticalScroll(rememberScrollState()),
            ) {
                OutlinedTextField(
                    state = place,
                    label = { Text(stringResource(R.string.edit_place_label)) },
                    placeholder = { Text(stringResource(R.string.edit_place_placeholder)) },
                    lineLimits = TextFieldLineLimits.SingleLine,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (recentPlaces.isNotEmpty()) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        recentPlaces.forEach { recent ->
                            SuggestionChip(
                                onClick = { place.setTextAndPlaceCursorAtEnd(recent) },
                                label = { Text(recent) },
                                icon = { Icon(Icons.Rounded.History, contentDescription = null, Modifier.size(16.dp)) },
                            )
                        }
                    }
                }
                extra(current)
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(current.trim()) }, enabled = current.isNotBlank()) {
                Text(confirmLabel)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}
