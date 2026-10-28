package io.github.foxsoft2005.stash.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DuplicatesTest {

    private fun item(id: Long, name: String, updated: Long = id) =
        Item(id = id, name = name, place = "Place $id", updatedAt = updated)

    private val items = listOf(item(1, "Passport", updated = 5), item(2, "passport ", updated = 9), item(3, "Passport cover"))

    @Test fun `same name ignoring case and spaces, newest first`() {
        assertEquals(listOf(2L, 1L), Duplicates.of(items, "  PASSPORT").map { it.id })
    }

    @Test fun `similar names are not duplicates`() {
        assertEquals(emptyList<Long>(), Duplicates.of(items, "Passports").map { it.id })
    }

    @Test fun `the item being edited isn't its own duplicate`() {
        assertEquals(listOf(2L), Duplicates.of(items, "Passport", exceptId = 1).map { it.id })
    }

    @Test fun `blank names never match`() {
        assertTrue(Duplicates.of(items + item(4, " "), "  ").isEmpty())
    }

    @Test fun `checks new items, and existing ones only when renamed`() {
        assertTrue(Duplicates.shouldCheck(originalName = null, newName = "Passport"))
        assertFalse(Duplicates.shouldCheck(originalName = "Passport", newName = " passport"))
        assertTrue(Duplicates.shouldCheck(originalName = "Wallet", newName = "Passport"))
    }
}
