package io.github.foxsoft2005.stash.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

class PlaceSummaryTest {

    private fun item(id: Long, place: String, updated: Long = id, photo: String? = null) =
        Item(id = id, name = "Item $id", place = place, updatedAt = updated, photoPath = photo)

    @Test fun `places are grouped case- and space-insensitively with counts`() {
        val items = listOf(item(1, "Garage shelf"), item(2, "garage  shelf "), item(3, "Top drawer"))
        val places = Places.summarize(items, Locale.ENGLISH)
        assertEquals(listOf("garage shelf" to 2, "top drawer" to 1), places.map { it.key to it.itemCount })
    }

    @Test fun `the most recent spelling is shown`() {
        val items = listOf(item(1, "garage", updated = 5), item(2, "Garage", updated = 9), item(3, "GARAGE", updated = 7))
        assertEquals("Garage", Places.summarize(items, Locale.ENGLISH).single().name)
    }

    @Test fun `alphabetical in the app language`() {
        val ru = listOf(item(1, "Шкаф"), item(2, "антресоль"), item(3, "Ёмкость в кладовке"), item(4, "Ванная"))
        assertEquals(
            listOf("антресоль", "Ванная", "Ёмкость в кладовке", "Шкаф"),
            Places.summarize(ru, Locale.forLanguageTag("ru")).map { it.name },
        )
    }

    @Test fun `thumbnail is the newest item that has a photo`() {
        val items = listOf(
            item(1, "Desk", updated = 1, photo = "/p/old.jpg"),
            item(2, "Desk", updated = 3, photo = null),
            item(3, "Desk", updated = 2, photo = "/p/new.jpg"),
        )
        assertEquals("/p/new.jpg", Places.summarize(items, Locale.ENGLISH).single().latestPhoto)
        assertEquals(null, Places.summarize(listOf(item(4, "Attic")), Locale.ENGLISH).single().latestPhoto)
    }

    @Test fun `blank places are left out`() {
        assertTrue(Places.summarize(listOf(item(1, "  ")), Locale.ENGLISH).isEmpty())
    }

    @Test fun `place filter matches regardless of spelling`() {
        val key = Places.key("Garage Shelf")
        assertTrue(Places.contains(key, item(1, " garage   shelf")))
        assertFalse(Places.contains(key, item(2, "Garage")))
    }
}
