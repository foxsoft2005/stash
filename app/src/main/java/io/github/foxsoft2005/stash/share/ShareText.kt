package io.github.foxsoft2005.stash.share

/**
 * The message for "Share where it is": "Passport → Top drawer, blue box", or for a lent
 * item "Drill → lent to Mark (usually: Garage)". Formats come from string resources so
 * the arrow line reads naturally in each language; this part stays testable.
 */
object ShareText {

    /**
     * [placeFormat]: "%1$s → %2$s" (name, place). [lentFormat]: "%1$s → lent to %2$s
     * (usually: %3$s)" (name, borrower, place). [note] is added on its own line if set.
     */
    fun of(name: String, place: String, lentTo: String?, note: String, placeFormat: String, lentFormat: String): String {
        val first = if (lentTo.isNullOrBlank()) {
            placeFormat.format(name.trim(), place.trim())
        } else {
            lentFormat.format(name.trim(), lentTo.trim(), place.trim())
        }
        return if (note.isBlank()) first else first + "\n" + note.trim()
    }
}
