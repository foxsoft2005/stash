package io.github.foxsoft2005.stash.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.Locale

class RenamePlaceTest {

    private fun item(id: Long, place: String) = Item(id = id, name = "Item $id", place = place)

    private val items = listOf(
        item(1, "Garage shelve"), item(2, "garage shelve "), item(3, "Garage shelf"), item(4, "Attic"),
    )
    private val places = Places.summarize(items, Locale.ENGLISH)

    @Test fun `renaming changes every spelling of that place and nothing else`() {
        val targets = Places.renameTargets(items, Places.key("Garage shelve"), "Garage shelf")
        assertEquals(listOf(1L, 2L), targets.map { it.id })
    }

    @Test fun `fixing only the case changes the items written differently`() {
        val targets = Places.renameTargets(items, Places.key("attic"), "Attic")
        assertEquals(emptyList<Long>(), targets.map { it.id })
        assertEquals(listOf(4L), Places.renameTargets(items, Places.key("attic"), "ATTIC").map { it.id })
    }

    @Test fun `a name matching another place is a merge`() {
        val into = Places.mergeTarget(places, Places.key("Garage shelve"), " garage SHELF ")
        assertEquals("garage shelf", into?.key)
        assertEquals(1, into?.itemCount)
    }

    @Test fun `a new name or a respelling is not a merge`() {
        assertNull(Places.mergeTarget(places, Places.key("Attic"), "Loft"))
        assertNull(Places.mergeTarget(places, Places.key("Attic"), "ATTIC"))
        assertNull(Places.mergeTarget(places, Places.key("Attic"), "   "))
    }
}
