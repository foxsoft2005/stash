package io.github.foxsoft2005.stash.data

import io.github.foxsoft2005.stash.data.CheckIns.DAY_MS
import org.junit.Assert.assertEquals
import org.junit.Test

class CheckInsTest {

    private val now = 1_800_000_000_000L
    private fun daysAgo(days: Int) = now - days * DAY_MS

    private fun item(id: Long, updatedDaysAgo: Int, checkedDaysAgo: Int? = null, lentTo: String? = null) = Item(
        id = id, name = "Item $id", place = "Shelf", lentTo = lentTo,
        createdAt = daysAgo(updatedDaysAgo), updatedAt = daysAgo(updatedDaysAgo),
        checkedAt = checkedDaysAgo?.let(::daysAgo),
    )

    @Test fun `due after a year without changes, oldest first`() {
        val items = listOf(item(1, 400), item(2, 364), item(3, 365), item(4, 900))
        assertEquals(listOf(4L, 1L, 3L), CheckIns.due(items, now).map { it.id })
    }

    @Test fun `a recent confirmation counts like a change`() {
        val items = listOf(item(1, 800, checkedDaysAgo = 10), item(2, 800, checkedDaysAgo = 500))
        assertEquals(listOf(2L), CheckIns.due(items, now).map { it.id })
    }

    @Test fun `lent items are never asked about`() {
        assertEquals(emptyList<Long>(), CheckIns.due(listOf(item(1, 800, lentTo = "Mark")), now).map { it.id })
    }

    @Test fun `card respects Later and shows nothing when nothing is due`() {
        val items = listOf(item(1, 800), item(2, 700))
        assertEquals(2, CheckIns.cardCount(items, snoozedUntil = 0, now = now))
        assertEquals(0, CheckIns.cardCount(items, snoozedUntil = CheckIns.snoozedUntil(now), now = now))
        assertEquals(2, CheckIns.cardCount(items, snoozedUntil = CheckIns.snoozedUntil(now), now = now + 31 * DAY_MS))
        assertEquals(0, CheckIns.cardCount(listOf(item(3, 5)), snoozedUntil = 0, now = now))
    }

    @Test fun `months since last seen`() {
        assertEquals(14, CheckIns.monthsSince(item(1, 425), now))
        assertEquals(12, CheckIns.monthsSince(item(2, 800, checkedDaysAgo = 365), now))
    }
}
