package io.github.foxsoft2005.stash.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Backup
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.SettingsBackupRestore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedSecureTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import io.github.foxsoft2005.stash.R
import io.github.foxsoft2005.stash.data.backup.BackupReminder
import io.github.foxsoft2005.stash.ui.asString

/** ⋮ next to the search bar. */
@Composable
fun BackupMenu(onBackUp: () -> Unit, onRestore: () -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) {
            Icon(Icons.Rounded.MoreVert, contentDescription = stringResource(R.string.backup_more_options))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.backup_menu_back_up)) },
                leadingIcon = { Icon(Icons.Rounded.Backup, contentDescription = null) },
                onClick = { open = false; onBackUp() },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.backup_menu_restore)) },
                leadingIcon = { Icon(Icons.Rounded.SettingsBackupRestore, contentDescription = null) },
                onClick = { open = false; onRestore() },
            )
        }
    }
}

/** All backup dialogs. [onChooseExportFile] opens the "save as" picker with a suggested name. */
@Composable
fun BackupDialogs(viewModel: BackupViewModel, onChooseExportFile: (String) -> Unit) {
    when (val dialog = viewModel.dialog) {
        BackupDialog.None -> Unit

        BackupDialog.ChooseExportPassword -> AlertDialog(
            onDismissRequest = viewModel::dismissDialog,
            icon = { Icon(Icons.Rounded.Backup, contentDescription = null) },
            title = { Text(stringResource(R.string.backup_export_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(stringResource(R.string.backup_export_body))
                    OutlinedSecureTextField(
                        state = viewModel.password,
                        label = { Text(stringResource(R.string.backup_password)) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedSecureTextField(
                        state = viewModel.confirmPassword,
                        label = { Text(stringResource(R.string.backup_repeat_password)) },
                        isError = viewModel.passwordError != null,
                        supportingText = viewModel.passwordError?.let { error -> { Text(error.asString()) } },
                        onKeyboardAction = {
                            if (viewModel.confirmExportPassword()) onChooseExportFile(viewModel.suggestedFileName())
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (viewModel.confirmExportPassword()) onChooseExportFile(viewModel.suggestedFileName())
                    },
                ) { Text(stringResource(R.string.backup_choose_location)) }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissDialog) { Text(stringResource(R.string.action_cancel)) }
            },
        )

        is BackupDialog.EnterRestorePassword -> AlertDialog(
            onDismissRequest = viewModel::dismissDialog,
            icon = { Icon(Icons.Rounded.SettingsBackupRestore, contentDescription = null) },
            title = { Text(stringResource(R.string.backup_restore_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(stringResource(R.string.backup_restore_body))
                    OutlinedSecureTextField(
                        state = viewModel.password,
                        label = { Text(stringResource(R.string.backup_restore_password)) },
                        isError = viewModel.passwordError != null,
                        supportingText = viewModel.passwordError?.let { error -> { Text(error.asString()) } },
                        onKeyboardAction = { viewModel.confirmRestorePassword() },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = viewModel::confirmRestorePassword) { Text(stringResource(R.string.backup_restore_action)) }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissDialog) { Text(stringResource(R.string.action_cancel)) }
            },
        )

        is BackupDialog.Working -> AlertDialog(
            onDismissRequest = {}, // must finish: a half-written backup is useless
            properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false),
            title = { Text(stringResource(if (dialog.restoring) R.string.backup_working_restore else R.string.backup_working_export)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                    Text(
                        stringResource(R.string.backup_keep_open),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            },
            confirmButton = {},
        )
    }
}

/** Home-screen nudge to back up; see BackupReminders for when it shows. */
@Composable
fun BackupReminderCard(reminder: BackupReminder, onBackUp: () -> Unit, onLater: () -> Unit) {
    val (title, body) = when (reminder) {
        is BackupReminder.NeverBackedUp ->
            stringResource(R.string.reminder_first_title) to stringResource(R.string.reminder_first_body)
        is BackupReminder.Stale ->
            stringResource(R.string.reminder_stale_title) to
                pluralStringResource(R.plurals.reminder_stale_days, reminder.daysSinceBackup, reminder.daysSinceBackup) +
                " " +
                pluralStringResource(R.plurals.reminder_stale_changed, reminder.changedSince, reminder.changedSince)
    }
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Backup, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.size(8.dp))
                Text(title, style = MaterialTheme.typography.titleSmall)
            }
            Text(body, style = MaterialTheme.typography.bodyMedium)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
            ) {
                TextButton(onClick = onLater) { Text(stringResource(R.string.reminder_later)) }
                Button(onClick = onBackUp) { Text(stringResource(R.string.reminder_back_up_now)) }
            }
        }
    }
}
