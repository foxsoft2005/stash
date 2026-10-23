package com.foxsoft2005.stash.app.data.backup

import com.foxsoft2005.stash.app.data.Item
import com.foxsoft2005.stash.app.data.backup.BackupReminders.DAY_MS
import org.junit.Assert.assertEquals
import org.junit.Test

class BackupReminderTest {

    private val now = 1_800_000_000_000L

    private fun items(count: Int, createdDaysAgo: Int = 1, updatedDaysAgo: Int = createdDaysAgo) =
        List(count) { i ->
            Item(
                id = i + 1L, name = "Item $i", place = "Shelf",
                createdAt = now - createdDaysAgo * DAY_MS, updatedAt = now - updatedDaysAgo * DAY_MS,
            )
        }

    private fun remind(items: List<Item>, status: BackupStatus = BackupStatus()) =
        BackupReminders.reminder(items, status, now)

    @Test fun `nothing stashed, nothing to remind`() {
        assertEquals(null, remind(emptyList()))
    }

    @Test fun `first backup once there are 5 items`() {
        assertEquals(null, remind(items(4)))
        assertEquals(BackupReminder.NeverBackedUp(5), remind(items(5)))
    }

    @Test fun `first backup a week after the first item, even with few items`() {
        assertEquals(null, remind(items(1, createdDaysAgo = 6)))
        assertEquals(BackupReminder.NeverBackedUp(1), remind(items(1, createdDaysAgo = 7)))
    }

    @Test fun `old backup with changes since reminds`() {
        val status = BackupStatus(lastBackupAt = now - 30 * DAY_MS)
        val stash = items(3, createdDaysAgo = 60) + items(2, createdDaysAgo = 2).map { it.copy(id = it.id + 10) }
        assertEquals(BackupReminder.Stale(daysSinceBackup = 30, changedSince = 2), remind(stash, status))
    }

    @Test fun `old backup but nothing changed stays quiet`() {
        val status = BackupStatus(lastBackupAt = now - 90 * DAY_MS)
        assertEquals(null, remind(items(10, createdDaysAgo = 100), status))
    }

    @Test fun `recent backup stays quiet even with changes`() {
        val status = BackupStatus(lastBackupAt = now - 29 * DAY_MS)
        assertEquals(null, remind(items(10, createdDaysAgo = 1), status))
    }

    @Test fun `later snoozes for a week`() {
        val snoozed = BackupStatus(snoozedUntil = BackupReminders.snoozedUntil(now - 6 * DAY_MS))
        assertEquals(null, remind(items(10), snoozed))
        val expired = BackupStatus(snoozedUntil = BackupReminders.snoozedUntil(now - 7 * DAY_MS))
        assertEquals(BackupReminder.NeverBackedUp(10), remind(items(10), expired))
    }

    @Test fun `clock set back never produces a reminder`() {
        val status = BackupStatus(lastBackupAt = now + 5 * DAY_MS)
        assertEquals(null, remind(items(10, createdDaysAgo = 1), status))
    }
}
