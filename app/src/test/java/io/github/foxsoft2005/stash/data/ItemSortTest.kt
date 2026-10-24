package io.github.foxsoft2005.stash.data

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Locale

class ItemSortTest {

    private fun item(id: Long, name: String, place: String = "Shelf", created: Long = id, updated: Long = id) =
        Item(id = id, name = name, place = place, createdAt = created, updatedAt = updated)

    private fun names(items: List<Item>) = items.map { it.name }

    private val items = listOf(
        item(1, "passport", place = "Top drawer", created = 10, updated = 50),
        item(2, "Drill", place = "Garage", created = 20, updated = 20),
        item(3, "apple charger", place = "desk", created = 30, updated = 30),
        item(4, "Bike lock", place = "Garage", created = 40, updated = 40),
    )

    @Test fun `recently changed is the default and newest first`() {
        assertEquals(ItemSort.UPDATED, ItemSort.DEFAULT)
        assertEquals(listOf("passport", "Bike lock", "apple charger", "Drill"), names(sortItems(items, ItemSort.UPDATED)))
    }

    @Test fun `recently added ignores later edits`() {
        assertEquals(listOf("Bike lock", "apple charger", "Drill", "passport"), names(sortItems(items, ItemSort.ADDED)))
    }

    @Test fun `name order ignores case`() {
        assertEquals(listOf("apple charger", "Bike lock", "Drill", "passport"), names(sortItems(items, ItemSort.NAME, Locale.ENGLISH)))
    }

    @Test fun `place order groups places, then names within a place`() {
        assertEquals(
            listOf("apple charger", "Bike lock", "Drill", "passport"),
            names(sortItems(items, ItemSort.PLACE, Locale.ENGLISH)),
        )
        val garage = sortItems(items, ItemSort.PLACE, Locale.ENGLISH).filter { it.place == "Garage" }
        assertEquals(listOf("Bike lock", "Drill"), names(garage))
    }

    @Test fun `russian alphabet, with ё sorted like е`() {
        val ru = listOf(item(1, "Ёлочные игрушки"), item(2, "Банка"), item(3, "Яблочный пресс"), item(4, "Ежедневник"), item(5, "аптечка"))
        assertEquals(
            listOf("аптечка", "Банка", "Ежедневник", "Ёлочные игрушки", "Яблочный пресс"),
            names(sortItems(ru, ItemSort.NAME, Locale.forLanguageTag("ru"))),
        )
    }

    @Test fun `equal names fall back to most recently changed`() {
        val twins = listOf(item(1, "Keys", updated = 5), item(2, "keys", updated = 9))
        assertEquals(listOf(2L, 1L), sortItems(twins, ItemSort.NAME, Locale.ENGLISH).map { it.id })
    }
}
