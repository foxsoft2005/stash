package dev.stash.app.ui.home

import android.net.Uri
import android.util.Log
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.clearText
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.stash.app.data.backup.BackupException
import dev.stash.app.data.backup.BackupManager
import kotlinx.coroutines.launch
import java.io.IOException
import java.time.LocalDate
import kotlin.coroutines.cancellation.CancellationException

/** Which backup dialog is showing, if any. */
sealed interface BackupDialog {
    data object None : BackupDialog
    data object ChooseExportPassword : BackupDialog
    data class EnterRestorePassword(val file: Uri) : BackupDialog
    data class Working(val restoring: Boolean) : BackupDialog
}

/**
 * Back up: choose a password → pick where to save → write.
 * Restore: pick a file → enter its password → merge.
 */
class BackupViewModel(private val backup: BackupManager) : ViewModel() {

    val password = TextFieldState()
    val confirmPassword = TextFieldState()

    var dialog by mutableStateOf<BackupDialog>(BackupDialog.None)
        private set
    var passwordError by mutableStateOf<String?>(null)
        private set

    /** One-off result for the snackbar; call [messageShown] once displayed. */
    var message by mutableStateOf<String?>(null)
        private set

    // Held only between "password chosen" and "file picked".
    private var pendingExportPassword: CharArray? = null

    // --- Back up

    fun startExport() {
        clearPasswords()
        dialog = BackupDialog.ChooseExportPassword
    }

    /** Returns true when the password is acceptable; the screen then opens the file picker. */
    fun confirmExportPassword(): Boolean {
        val chosen = password.text.toString()
        passwordError = when {
            chosen.length < MIN_PASSWORD_LENGTH -> "Use at least $MIN_PASSWORD_LENGTH characters."
            chosen != confirmPassword.text.toString() -> "The passwords don't match."
            else -> null
        }
        if (passwordError != null) return false
        pendingExportPassword = chosen.toCharArray()
        clearPasswords()
        dialog = BackupDialog.None
        return true
    }

    fun suggestedFileName(): String = "stash-backup-${LocalDate.now()}.stash"

    /** Result of the "save as" picker; null = the user backed out. */
    fun exportTo(file: Uri?) {
        val pw = pendingExportPassword
        pendingExportPassword = null
        if (file == null) {
            pw?.fill('\u0000')
            return
        }
        if (pw == null) { // app was killed while the picker was open
            message = "Please start the backup again."
            return
        }
        run(restoring = false, onError = { message = it }) {
            val result = try {
                backup.export(file, pw)
            } finally {
                pw.fill('\u0000')
            }
            message = "Backed up ${items(result.items)}. Keep the file and its password somewhere safe: " +
                "without the password it can't be opened."
        }
    }

    // --- Restore

    /** Result of the "open" picker; null = the user backed out. */
    fun restoreFrom(file: Uri?) {
        if (file == null) return
        clearPasswords()
        dialog = BackupDialog.EnterRestorePassword(file)
    }

    fun confirmRestorePassword() {
        val file = (dialog as? BackupDialog.EnterRestorePassword)?.file ?: return
        val pw = password.text.toString().toCharArray()
        clearPasswords()
        run(
            restoring = true,
            onError = { error ->
                message = error
            },
            onWrongPassword = {
                // Let them try again without picking the file again.
                dialog = BackupDialog.EnterRestorePassword(file)
                passwordError = "Wrong password, or the file is damaged."
            },
        ) {
            val result = try {
                backup.restore(file, pw)
            } finally {
                pw.fill('\u0000')
            }
            message = when {
                result.added == 0 && result.alreadyOnPhone == 0 -> "That backup is empty."
                result.added == 0 -> "Nothing new: everything in that backup is already on this phone."
                result.alreadyOnPhone == 0 -> "Restored ${items(result.added)}."
                else -> "Restored ${items(result.added)}. ${items(result.alreadyOnPhone)} already on this phone were skipped."
            }
        }
    }

    // --- Shared

    fun dismissDialog() {
        if (dialog is BackupDialog.Working) return // can't cancel mid-write
        clearPasswords()
        dialog = BackupDialog.None
    }

    fun messageShown() {
        message = null
    }

    private fun run(
        restoring: Boolean,
        onError: (String) -> Unit,
        onWrongPassword: (() -> Unit)? = null,
        block: suspend () -> Unit,
    ) {
        dialog = BackupDialog.Working(restoring)
        viewModelScope.launch {
            try {
                block()
                dialog = BackupDialog.None
            } catch (e: CancellationException) {
                throw e
            } catch (e: BackupException) {
                dialog = BackupDialog.None
                if (e.reason == BackupException.Reason.WRONG_PASSWORD_OR_DAMAGED && onWrongPassword != null) {
                    onWrongPassword()
                } else {
                    onError(describe(e.reason))
                }
            } catch (e: IOException) {
                Log.w(TAG, "Backup file I/O failed", e)
                dialog = BackupDialog.None
                onError(if (restoring) "Couldn't read that file." else "Couldn't save the backup there. Try another place.")
            } catch (e: Exception) {
                Log.e(TAG, "Backup failed", e)
                dialog = BackupDialog.None
                onError(if (restoring) "Restore failed. Nothing was changed." else "Backup failed.")
            }
        }
    }

    private fun describe(reason: BackupException.Reason) = when (reason) {
        BackupException.Reason.NOT_A_BACKUP -> "That file isn't a Stash backup."
        BackupException.Reason.WRONG_PASSWORD_OR_DAMAGED -> "Wrong password, or the file is damaged."
        BackupException.Reason.DAMAGED -> "This backup is damaged and can't be restored. Nothing was changed."
        BackupException.Reason.NEWER_VERSION -> "This backup was made by a newer version of Stash. Update the app, then try again."
    }

    private fun clearPasswords() {
        password.clearText()
        confirmPassword.clearText()
        passwordError = null
    }

    override fun onCleared() {
        pendingExportPassword?.fill('\u0000')
    }

    private companion object {
        const val TAG = "Backup"
        const val MIN_PASSWORD_LENGTH = 8

        fun items(n: Int) = if (n == 1) "1 item" else "$n items"
    }
}
