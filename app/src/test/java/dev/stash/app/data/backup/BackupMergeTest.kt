package dev.stash.app.data.backup

import dev.stash.app.data.Item
import org.junit.Assert.assertEquals
import org.junit.Test

class BackupMergeTest {

    private fun backup(name: String, createdAt: Long) =
        BackupItem(name, "Somewhere", emptyList(), "", null, createdAt, createdAt, null)

    private fun onPhone(name: String, createdAt: Long) =
        Item(id = createdAt, name = name, place = "Elsewhere", createdAt = createdAt)

    @Test fun `items already on the phone are skipped, new ones added`() {
        val phone = listOf(onPhone("Passport", 1), onPhone("Drill", 2))
        val file = listOf(backup("Passport", 1), backup("Keys", 3), backup("Drill", 2))
        assertEquals(listOf(backup("Keys", 3)), BackupMerge.itemsToAdd(phone, file))
    }

    @Test fun `name match ignores case and spacing`() {
        assertEquals(emptyList<BackupItem>(), BackupMerge.itemsToAdd(listOf(onPhone(" passport ", 1)), listOf(backup("Passport", 1))))
    }

    @Test fun `same name but a different item is added`() {
        // Two separate "Keys" items stashed at different times.
        assertEquals(listOf(backup("Keys", 9)), BackupMerge.itemsToAdd(listOf(onPhone("Keys", 1)), listOf(backup("Keys", 9))))
    }

    @Test fun `restoring the same backup twice adds nothing the second time`() {
        val file = listOf(backup("A", 1), backup("B", 2))
        val firstRun = BackupMerge.itemsToAdd(emptyList(), file)
        val phoneAfter = firstRun.map { it.toItem(null) }
        assertEquals(emptyList<BackupItem>(), BackupMerge.itemsToAdd(phoneAfter, file))
    }

    @Test fun `duplicates inside the backup itself are added once`() {
        assertEquals(listOf(backup("A", 1)), BackupMerge.itemsToAdd(emptyList(), listOf(backup("A", 1), backup("a", 1))))
    }
}
