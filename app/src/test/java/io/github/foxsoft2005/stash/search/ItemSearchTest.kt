package io.github.foxsoft2005.stash.search

import io.github.foxsoft2005.stash.data.Item
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ItemSearchTest {

    private val passport = Item(id = 1, name = "Passport", place = "Top drawer, blue box", tags = "document,travel,id")
    private val drill = Item(id = 2, name = "Drill", place = "Garage shelf", tags = "power tool", lentTo = "Mark")
    private val keys = Item(id = 3, name = "Spare keys", place = "Kitchen jar", tags = "key,house")
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

    @Test fun `different words sharing a prefix do not match`() {
        assertFalse(ItemSearch.matches("car", "card"))
        assertFalse(ItemSearch.matches("part", "party"))
        assertFalse(ItemSearch.matches("box", "boxer"))
        assertFalse(ItemSearch.matches("boxes", "boxer"))
        assertFalse(ItemSearch.matches("cart", "card"))
        assertFalse(ItemSearch.matches("id", "ids")) // stems shorter than 3 letters only match exactly
    }

    @Test fun `english plurals match`() {
        assertTrue(ItemSearch.matches("box", "boxes"))
        assertTrue(ItemSearch.matches("glasses", "glass"))
        assertTrue(ItemSearch.matches("battery", "batteries"))
        assertTrue(ItemSearch.matches("documents", "document"))
    }

    @Test fun `russian case endings match`() {
        assertTrue(ItemSearch.matches("сумка", "сумку"))
        assertTrue(ItemSearch.matches("тетрадь", "тетради"))
        assertTrue(ItemSearch.matches("очки", "очков"))
        assertTrue(ItemSearch.matches("ключом", "ключей"))
        assertTrue(ItemSearch.matches("мама", "мамам"))
        assertFalse(ItemSearch.matches("кот", "котлета"))
    }

    @Test fun `card is not the answer to where is my car`() {
        val card = Item(id = 5, name = "Gift card", place = "Wallet")
        val car = Item(id = 6, name = "Car keys", place = "Hallway hook")
        assertEquals(listOf(car), ItemSearch.search("where are my car keys", listOf(card, car)))
    }

    @Test fun `words inside a multi-word tag are searchable`() {
        assertEquals(drill, ItemSearch.search("power", all).first())
        assertEquals(drill, ItemSearch.search("tools", all).first())
    }

    @Test fun `only filler words returns nothing`() {
        assertTrue(ItemSearch.search("where is my", all).isEmpty())
    }
}
