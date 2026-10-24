package io.github.foxsoft2005.stash.ui.home

import io.github.foxsoft2005.stash.data.Item
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SelectionTest {

    private fun item(id: Long) = Item(id = id, name = "Item $id", place = "Shelf")
    private val all = (1L..5L).map(::item)

    private fun ids(s: Selection, visible: List<Item> = all) = s.visibleIn(visible).map { it.id }

    @Test fun `long press starts batch mode with that item`() {
        assertEquals(listOf(3L), ids(Selection().longPress(3, all)))
    }

    @Test fun `taps add and remove, last one leaves batch mode`() {
        var s = Selection().longPress(2, all)
        s = s.toggle(4, all)
        assertEquals(listOf(2L, 4L), ids(s))
        s = s.toggle(2, all).toggle(4, all)
        assertTrue(ids(s).isEmpty())
    }

    @Test fun `long press while selecting toggles instead of restarting`() {
        val s = Selection().longPress(1, all).longPress(5, all)
        assertEquals(listOf(1L, 5L), ids(s))
    }

    @Test fun `select all takes only what is on screen`() {
        val searchResults = all.filter { it.id % 2 == 1L }
        assertEquals(listOf(1L, 3L, 5L), ids(Selection().selectAll(searchResults), searchResults))
    }

    @Test fun `items hidden by search or filter are never part of the selection`() {
        val s = Selection().selectAll(all)
        val visible = all.take(2)
        assertEquals(listOf(1L, 2L), ids(s, visible))
        // Toggling after the view narrowed works from what's visible, not stale ids.
        assertEquals(listOf(1L), ids(s.toggle(2, visible), all))
    }

    @Test fun `selection follows grid order`() {
        val s = Selection(setOf(5, 1, 3))
        assertEquals(listOf(1L, 3L, 5L), ids(s))
    }

    @Test fun `pending deletion exposes its ids`() {
        assertEquals(setOf(2L, 3L), PendingDeletion(token = 1, items = listOf(item(2), item(3))).ids)
    }
}
