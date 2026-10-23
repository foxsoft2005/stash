package com.foxsoft2005.stash.app.data

/** Helpers for the one-tap "recent places" chips on the edit screen. */
internal object Places {

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
}
