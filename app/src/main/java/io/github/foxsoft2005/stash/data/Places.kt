package io.github.foxsoft2005.stash.data

import java.text.Collator
import java.util.Locale

/** One place on the Places screen: "Garage shelf · 7 items". */
data class PlaceSummary(
    /** Normalized identity, see [Places.key]. */
    val key: String,
    /** How it was last written (most recently changed item). */
    val name: String,
    val itemCount: Int,
    /** Photo of the most recently changed item there that has one. */
    val latestPhoto: String?,
)

/** Places: the one-tap chips on the edit screen, the Places screen, and the place filter. */
object Places {

    private val whitespace = Regex("\\s+")

    /** "Garage", "garage " and "GARAGE" are the same spot. */
    fun key(place: String): String = place.trim().lowercase().replace(whitespace, " ")

    /**
     * [places] is ordered most-recent first. Keeps the most recent spelling of each
     * place (case- and space-insensitive) and returns at most [limit] of them.
     * Done in Kotlin rather than SQL because SQLite's NOCASE only folds ASCII,
     * so it would still show "Гараж" and "гараж" twice.
     */
    fun recent(places: List<String>, limit: Int): List<String> =
        places.filter { it.isNotBlank() }.distinctBy(::key).take(limit)

    /**
     * The items a "Move to [place]" changes: those not already written exactly
     * as [place]. ("garage" → "Garage" counts, so a move also tidies spelling.)
     */
    fun moveTargets(items: List<Item>, place: String): List<Item> {
        val target = place.trim()
        if (target.isEmpty()) return emptyList()
        return items.filter { it.place.trim() != target }
    }

    /** True if [item] is stored at the place identified by [placeKey]. */
    fun contains(placeKey: String, item: Item): Boolean = key(item.place) == placeKey

    /**
     * Every place in [items] with its item count, alphabetical in [locale]
     * (case/accent-insensitive). "Garage" and "garage " are one place, shown
     * with the spelling of its most recently changed item.
     */
    fun summarize(items: List<Item>, locale: Locale = Locale.getDefault()): List<PlaceSummary> {
        val text = Collator.getInstance(locale).apply { strength = Collator.PRIMARY }
        return items
            .filter { it.place.isNotBlank() }
            .groupBy { key(it.place) }
            .map { (placeKey, here) ->
                val newestFirst = here.sortedByDescending { it.updatedAt }
                PlaceSummary(
                    key = placeKey,
                    name = newestFirst.first().place.trim(),
                    itemCount = here.size,
                    latestPhoto = newestFirst.firstOrNull { it.photoPath != null }?.photoPath,
                )
            }
            .sortedWith { a, b -> text.compare(a.name, b.name) }
    }
}
