package io.github.foxsoft2005.stash.data

import java.text.Collator
import java.util.Locale

/** How the home grid is ordered (search results keep their relevance order). */
enum class ItemSort {
    /** Most recently changed first (the original order). */
    UPDATED,

    /** Most recently added first. */
    ADDED,

    /** Name A–Z. */
    NAME,

    /** Place A–Z, then name. */
    PLACE,
    ;

    companion object {
        val DEFAULT = UPDATED
    }
}

/**
 * Sorts [items] by [sort]. Text is compared the way people read it in [locale]:
 * case- and accent-insensitive, so "apple" sits next to "Apple", and in Russian
 * "ёлка" sorts with "елка". Ties fall back to most recently changed.
 */
fun sortItems(items: List<Item>, sort: ItemSort, locale: Locale = Locale.getDefault()): List<Item> {
    val newestFirst = compareByDescending<Item> { it.updatedAt }.thenByDescending { it.id }
    val text = Collator.getInstance(locale).apply { strength = Collator.PRIMARY }
    val comparator = when (sort) {
        ItemSort.UPDATED -> newestFirst
        ItemSort.ADDED -> compareByDescending<Item> { it.createdAt }.thenByDescending { it.id }
        ItemSort.NAME -> Comparator<Item> { a, b -> text.compare(a.name.trim(), b.name.trim()) }.then(newestFirst)
        ItemSort.PLACE -> Comparator<Item> { a, b -> text.compare(a.place.trim(), b.place.trim()) }
            .then { a, b -> text.compare(a.name.trim(), b.name.trim()) }
            .then(newestFirst)
    }
    return items.sortedWith(comparator)
}
