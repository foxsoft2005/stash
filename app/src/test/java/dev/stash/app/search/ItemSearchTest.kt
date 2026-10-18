package dev.stash.app.search

import dev.stash.app.data.Item
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ItemSearchTest {

    private val passport = Item(id = 1, name = "Passport", place = "Top drawer, blue box", tags = "document travel id")
    private val drill = Item(id = 2, name = "Drill", place = "Garage shelf", tags = "tool power", lentTo = "Mark")
    private val keys = Item(id = 3, name = "Spare keys", place = "Kitchen jar", tags = "key house")
    private val pencil = Item(id = 4, name = "Pencil case", place = "Desk", tags = "stationery")
    private val all = listOf(passport, drill, keys, pencil)

    @Test fun `natural question finds the item`() {
        assertEquals(passport, ItemSearch.search("Where's my passport?", all).first())
    }

    @Test fun `russian question with a different word ending`() {
        val ruPassport = passport.copy(name = "Паспорт", tags = "документ")
        assertEquals(ruPassport, ItemSearch.search("где мой паспорта", listOf(ruPassport, drill)).first())
    }

    @Test fun `plural and singular match`() {
        assertEquals(keys, ItemSearch.search("key", all).first())
        assertEquals(passport, ItemSearch.search("passports", all).first())
    }

    @Test fun `tags and borrower are searchable`() {
        assertEquals(passport, ItemSearch.search("travel documents", all).first())
        assertEquals(drill, ItemSearch.search("what did mark borrow", all).first())
    }

    @Test fun `short prefixes do not over-match`() {
        assertFalse(ItemSearch.matches("pen", "pencil"))
        assertTrue(ItemSearch.matches("ключ", "ключи"))
    }

    @Test fun `only filler words returns nothing`() {
        assertTrue(ItemSearch.search("where is my", all).isEmpty())
    }
}
