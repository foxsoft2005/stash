package com.foxsoft2005.stash.app.data

/**
 * Search keywords are stored in a single TEXT column, comma-separated, so one tag
 * can contain spaces ("power tool", "musical instrument") and still come back as
 * one chip after a reload.
 *
 * Every tag goes through [normalize] on the way in and on the way out, so the
 * separator can never appear inside a tag and stored data stays canonical.
 */
object Tags {

    const val SEPARATOR = ","

    private val whitespace = Regex("\\s+")

    /** Lowercase, trimmed, single-spaced, and free of the separator. May return "". */
    fun normalize(raw: String): String =
        raw.replace(SEPARATOR, " ")
            .trim()
            .lowercase()
            .replace(whitespace, " ")

    /** Stored column value → tag list (blank and duplicate entries dropped). */
    fun split(stored: String): List<String> =
        clean(stored.split(SEPARATOR))

    /** Tag list → stored column value. */
    fun join(tags: Iterable<String>): String =
        clean(tags).joinToString(SEPARATOR)

    /** Normalizes, drops blanks and duplicates, keeps first-seen order. */
    fun clean(tags: Iterable<String>): List<String> =
        tags.map(::normalize).filter { it.isNotEmpty() }.distinct()
}
