package io.github.foxsoft2005.stash.ui.home

import android.net.Uri
import android.util.Log
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.clearText
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.foxsoft2005.stash.data.backup.BackupException
import io.github.foxsoft2005.stash.R
import io.github.foxsoft2005.stash.data.backup.BackupManager
import io.github.foxsoft2005.stash.ui.UiText
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
    var passwordError by mutableStateOf<UiText?>(null)
        private set

    /** One-off result for the snackbar; call [messageShown] once displayed. */
    var message by mutableStateOf<UiText?>(null)
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
            chosen.length < MIN_PASSWORD_LENGTH -> UiText.plural(R.plurals.backup_password_too_short, MIN_PASSWORD_LENGTH)
            chosen != confirmPassword.text.toString() -> UiText.of(R.string.backup_password_mismatch)
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
            message = UiText.of(R.string.backup_start_again)
            return
        }
        run(restoring = false, onError = { message = it }) {
            val result = try {
                backup.export(file, pw)
            } finally {
                pw.fill('\u0000')
            }
            message = UiText.plural(R.plurals.backup_done, result.items)
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
                passwordError = UiText.of(R.string.backup_error_wrong_password)
            },
        ) {
            val result = try {
                backup.restore(file, pw)
            } finally {
                pw.fill('\u0000')
            }
            message = when {
                result.added == 0 && result.alreadyOnPhone == 0 -> UiText.of(R.string.restore_empty)
                result.added == 0 -> UiText.of(R.string.restore_nothing_new)
                result.alreadyOnPhone == 0 -> UiText.plural(R.plurals.restore_added, result.added)
                else -> UiText.Join(
                    listOf(
                        UiText.plural(R.plurals.restore_added, result.added),
                        UiText.plural(R.plurals.restore_skipped, result.alreadyOnPhone),
                    ),
                )
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
        onError: (UiText) -> Unit,
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
                onError(UiText.of(if (restoring) R.string.backup_error_read else R.string.backup_error_write))
            } catch (e: Exception) {
                Log.e(TAG, "Backup failed", e)
                dialog = BackupDialog.None
                onError(UiText.of(if (restoring) R.string.restore_failed else R.string.backup_failed))
            }
        }
    }

    private fun describe(reason: BackupException.Reason) = UiText.of(
        when (reason) {
            BackupException.Reason.NOT_A_BACKUP -> R.string.backup_error_not_a_backup
            BackupException.Reason.WRONG_PASSWORD_OR_DAMAGED -> R.string.backup_error_wrong_password
            BackupException.Reason.DAMAGED -> R.string.backup_error_damaged
            BackupException.Reason.NEWER_VERSION -> R.string.backup_error_newer_version
        },
    )

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
    }
}
