package dev.stash.app.data

import org.junit.Assert.assertEquals
import org.junit.Test

class TagsTest {

    @Test fun `multi-word tag survives a save and reload`() {
        val stored = Tags.join(listOf("power tool", "drill"))
        assertEquals("power tool,drill", stored)
        assertEquals(listOf("power tool", "drill"), Item(name = "Drill", place = "Garage", tags = stored).tagList)
    }

    @Test fun `tags are normalized`() {
        assertEquals("musical instrument", Tags.normalize("  Musical   Instrument "))
    }

    @Test fun `the separator can never leak into a tag`() {
        assertEquals(listOf("salt pepper"), Tags.split(Tags.join(listOf("salt, pepper"))))
    }

    @Test fun `blanks and duplicates are dropped, order is kept`() {
        assertEquals(listOf("b", "a"), Tags.clean(listOf("B", " ", "a", "b", "")))
        assertEquals(listOf("a", "b"), Tags.split(",a,,b,"))
        assertEquals(emptyList<String>(), Tags.split(""))
    }

    @Test fun `typed keywords split on commas only`() {
        // What EditItemViewModel.addTag() does with the "Add a keyword" field.
        val existing = listOf("passport")
        assertEquals(
            listOf("passport", "travel wallet", "id"),
            Tags.clean(existing + " Travel Wallet, id ,passport".split(Tags.SEPARATOR)),
        )
    }

    @Test fun `v1 migration keeps every old single-word tag`() {
        // Mirrors MIGRATION_1_2_SQL: REPLACE(TRIM(tags), ' ', ',')
        val v1 = " document  travel id "
        val v2 = v1.trim().replace(" ", Tags.SEPARATOR)
        assertEquals(listOf("document", "travel", "id"), Tags.split(v2))
    }
}
