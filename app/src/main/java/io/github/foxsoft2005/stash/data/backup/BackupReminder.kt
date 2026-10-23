package io.github.foxsoft2005.stash.data.backup

import io.github.foxsoft2005.stash.data.Item

/** What the app remembers about backups. */
data class BackupStatus(
    /** When the last backup finished successfully; null = never. */
    val lastBackupAt: Long? = null,
    /** "Later" was tapped: no reminder before this time. */
    val snoozedUntil: Long = 0,
)

/** Why the home screen suggests a backup right now. */
sealed interface BackupReminder {
    data class NeverBackedUp(val itemCount: Int) : BackupReminder
    data class Stale(val daysSinceBackup: Int, val changedSince: Int) : BackupReminder
}

/**
 * When to nudge. The app can't back up by itself (Android only lets it write a
 * file where the user picks), so the next best thing is a well-timed reminder
 * that's easy to postpone and never nags about nothing.
 */
object BackupReminders {

    const val DAY_MS = 24L * 60 * 60 * 1000

    /** First backup: once the stash is worth losing... */
    const val FIRST_BACKUP_MIN_ITEMS = 5

    /** ...or the first item has been around for a week. */
    const val FIRST_BACKUP_AFTER_DAYS = 7

    /** Later backups: when the last one is this old and something changed since. */
    const val STALE_AFTER_DAYS = 30

    /** "Later" hides the reminder this long. */
    const val SNOOZE_DAYS = 7

    fun reminder(items: List<Item>, status: BackupStatus, now: Long): BackupReminder? {
        if (items.isEmpty() || now < status.snoozedUntil) return null
        val last = status.lastBackupAt
        if (last == null) {
            val oldest = items.minOf { it.createdAt }
            val worthIt = items.size >= FIRST_BACKUP_MIN_ITEMS || now - oldest >= FIRST_BACKUP_AFTER_DAYS * DAY_MS
            return if (worthIt) BackupReminder.NeverBackedUp(items.size) else null
        }
        // Restored items keep their original timestamps, so a restore alone doesn't trigger this.
        val changed = items.count { it.updatedAt > last }
        val days = ((now - last) / DAY_MS).toInt() // negative if the clock went back: no reminder
        return if (changed > 0 && days >= STALE_AFTER_DAYS) BackupReminder.Stale(days, changed) else null
    }

    fun snoozedUntil(now: Long): Long = now + SNOOZE_DAYS * DAY_MS
}
