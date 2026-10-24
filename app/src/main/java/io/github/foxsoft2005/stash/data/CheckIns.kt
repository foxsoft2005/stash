package io.github.foxsoft2005.stash.data

/**
 * "Still there?" check-ins: an answer is only as good as the data behind it, and
 * things get moved without anyone updating Stash. Items nobody has touched or
 * confirmed for a year are offered for a quick review.
 */
object CheckIns {

    const val DAY_MS = 24L * 60 * 60 * 1000

    /** An item is due when it hasn't been changed or confirmed for this long. */
    const val DUE_AFTER_DAYS = 365

    /** "Later" on the Home card hides it this long. */
    const val SNOOZE_DAYS = 30

    /** When someone last said where it is: edited, moved, or confirmed. */
    fun lastSeen(item: Item): Long = maxOf(item.updatedAt, item.checkedAt ?: 0L)

    /**
     * Items due for a check, longest-unchecked first. Lent items are skipped: they're
     * not at their place, so "still there?" has no sensible answer until they're back.
     */
    fun due(items: List<Item>, now: Long): List<Item> =
        items
            .filter { !it.isLent && now - lastSeen(it) >= DUE_AFTER_DAYS * DAY_MS }
            .sortedBy(::lastSeen)

    /** How many items the Home card should mention; 0 = no card. */
    fun cardCount(items: List<Item>, snoozedUntil: Long, now: Long): Int =
        if (now < snoozedUntil) 0 else due(items, now).size

    /** Whole months since [lastSeen], for "not checked for 14 months". */
    fun monthsSince(item: Item, now: Long): Int = ((now - lastSeen(item)) / (30 * DAY_MS)).toInt()

    fun snoozedUntil(now: Long): Long = now + SNOOZE_DAYS * DAY_MS
}
