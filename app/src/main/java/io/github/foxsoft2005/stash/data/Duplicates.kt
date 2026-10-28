package io.github.foxsoft2005.stash.data

/**
 * "You already have Passport": the same thing stashed twice usually means one of the
 * two places is out of date, so Stash points it out before saving.
 */
object Duplicates {

    /** Other items with the same name (ignoring case and spacing), most recently changed first. */
    fun of(items: List<Item>, name: String, exceptId: Long? = null): List<Item> {
        val key = Tags.normalize(name)
        if (key.isEmpty()) return emptyList()
        return items
            .filter { it.id != exceptId && Tags.normalize(it.name) == key }
            .sortedByDescending { it.updatedAt }
    }

    /**
     * Whether saving should check at all: always for a new item, but for an existing one
     * only when its name changed, so editing an old duplicate doesn't ask every time.
     */
    fun shouldCheck(originalName: String?, newName: String): Boolean =
        originalName == null || Tags.normalize(originalName) != Tags.normalize(newName)
}
