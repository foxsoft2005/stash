package dev.stash.app.data.backup

import android.content.Context
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Remembers the last successful backup and any "Later" snooze, and publishes
 * changes so the home screen's reminder disappears the moment a backup finishes.
 * Same SharedPreferences file as the Gemini Nano consent; excluded from cloud
 * backup by the backup rules like all app data.
 */
class BackupStatusStore(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private val _status = MutableStateFlow(read())
    val status: StateFlow<BackupStatus> = _status.asStateFlow()

    fun markBackedUp(at: Long) {
        prefs.edit {
            putLong(KEY_LAST_BACKUP, at)
            remove(KEY_SNOOZED_UNTIL) // a fresh backup resets any snooze
        }
        _status.value = read()
    }

    fun snooze(until: Long) {
        prefs.edit { putLong(KEY_SNOOZED_UNTIL, until) }
        _status.value = read()
    }

    private fun read() = BackupStatus(
        lastBackupAt = prefs.getLong(KEY_LAST_BACKUP, NEVER).takeIf { it != NEVER },
        snoozedUntil = prefs.getLong(KEY_SNOOZED_UNTIL, 0),
    )

    private companion object {
        const val PREFS = "stash_settings"
        const val KEY_LAST_BACKUP = "last_backup_at"
        const val KEY_SNOOZED_UNTIL = "backup_snoozed_until"
        const val NEVER = -1L
    }
}
