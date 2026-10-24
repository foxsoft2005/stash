package io.github.foxsoft2005.stash.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MoveTargetsTest {

    private fun item(id: Long, place: String) = Item(id = id, name = "Item $id", place = place)

    @Test fun `only items not already there are moved`() {
        val items = listOf(item(1, "Garage"), item(2, "Attic"), item(3, "Desk"))
        assertEquals(listOf(2L, 3L), Places.moveTargets(items, "Garage").map { it.id })
    }

    @Test fun `a different spelling of the same place is tidied up`() {
        val items = listOf(item(1, "garage "), item(2, "Garage"))
        assertEquals(listOf(1L), Places.moveTargets(items, "  Garage ").map { it.id })
    }

    @Test fun `a blank place moves nothing`() {
        assertTrue(Places.moveTargets(listOf(item(1, "Garage")), "   ").isEmpty())
    }
}
