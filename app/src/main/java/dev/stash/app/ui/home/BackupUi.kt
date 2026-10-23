package dev.stash.app.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Backup
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.SettingsBackupRestore
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties

/** ⋮ next to the search bar. */
@Composable
fun BackupMenu(onBackUp: () -> Unit, onRestore: () -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) {
            Icon(Icons.Rounded.MoreVert, contentDescription = "More options")
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(
                text = { Text("Back up…") },
                leadingIcon = { Icon(Icons.Rounded.Backup, contentDescription = null) },
                onClick = { open = false; onBackUp() },
            )
            DropdownMenuItem(
                text = { Text("Restore…") },
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
            title = { Text("Back up your stash") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        "All items and photos go into one encrypted file. Choose a password for it: " +
                            "you'll need it to restore, and a forgotten password can't be recovered.",
                    )
                    OutlinedSecureTextField(
                        state = viewModel.password,
                        label = { Text("Password") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedSecureTextField(
                        state = viewModel.confirmPassword,
                        label = { Text("Repeat password") },
                        isError = viewModel.passwordError != null,
                        supportingText = viewModel.passwordError?.let { error -> { Text(error) } },
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
                ) { Text("Choose where to save") }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissDialog) { Text("Cancel") }
            },
        )

        is BackupDialog.EnterRestorePassword -> AlertDialog(
            onDismissRequest = viewModel::dismissDialog,
            icon = { Icon(Icons.Rounded.SettingsBackupRestore, contentDescription = null) },
            title = { Text("Restore from backup") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        "Items from the backup are added to this phone. " +
                            "Nothing that's already here is changed or deleted.",
                    )
                    OutlinedSecureTextField(
                        state = viewModel.password,
                        label = { Text("Backup password") },
                        isError = viewModel.passwordError != null,
                        supportingText = viewModel.passwordError?.let { error -> { Text(error) } },
                        onKeyboardAction = { viewModel.confirmRestorePassword() },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = viewModel::confirmRestorePassword) { Text("Restore") }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissDialog) { Text("Cancel") }
            },
        )

        is BackupDialog.Working -> AlertDialog(
            onDismissRequest = {}, // must finish: a half-written backup is useless
            properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false),
            title = { Text(if (dialog.restoring) "Restoring…" else "Backing up…") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                    Text(
                        "Keep Stash open until this finishes.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            },
            confirmButton = {},
        )
    }
}
