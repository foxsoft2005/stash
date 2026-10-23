package com.foxsoft2005.stash.app.data

import org.junit.Assert.assertEquals
import org.junit.Test

class PlacesTest {

    @Test fun `case and spacing variants collapse to the most recent spelling`() {
        val mostRecentFirst = listOf("Garage shelf", "garage  shelf", "Top drawer", "GARAGE SHELF ")
        assertEquals(listOf("Garage shelf", "Top drawer"), Places.recent(mostRecentFirst, limit = 8))
    }

    @Test fun `cyrillic is folded too`() {
        assertEquals(listOf("Гараж"), Places.recent(listOf("Гараж", "гараж"), limit = 8))
    }

    @Test fun `limit applies after removing duplicates`() {
        val places = listOf("A", "a", "B", "b", "C")
        assertEquals(listOf("A", "B"), Places.recent(places, limit = 2))
    }

    @Test fun `blank places are skipped`() {
        assertEquals(listOf("Desk"), Places.recent(listOf(" ", "Desk", ""), limit = 8))
    }
}
