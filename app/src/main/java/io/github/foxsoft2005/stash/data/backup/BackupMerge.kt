package io.github.foxsoft2005.stash.data.backup

import io.github.foxsoft2005.stash.data.Item
import io.github.foxsoft2005.stash.data.Tags

/**
 * Restore = merge: items from the backup are added, items already on the phone are
 * left exactly as they are (nothing is overwritten or deleted).
 *
 * "Already on the phone" = same creation time (to the millisecond) and same name,
 * ignoring case/spacing. Creation time never changes when an item is edited, so an
 * item that was renamed or moved since the backup counts as *different* and is
 * added again — the safe side: a duplicate is easy to delete, a lost item isn't.
 *
 * Restoring the same file twice adds nothing the second time, so an interrupted
 * restore can simply be run again.
 */
object BackupMerge {

    private fun key(name: String, createdAt: Long) = "$createdAt|${Tags.normalize(name)}"

    /** The backup items that should be added, in backup order, without duplicates. */
    fun itemsToAdd(onPhone: List<Item>, fromBackup: List<BackupItem>): List<BackupItem> {
        val existing = onPhone.mapTo(HashSet()) { key(it.name, it.createdAt) }
        return fromBackup.filter { existing.add(key(it.name, it.createdAt)) }
    }
}

fun Item.toBackupItem(photoEntry: String?) = BackupItem(
    name = name,
    place = place,
    tags = tagList,
    note = note,
    lentTo = lentTo,
    createdAt = createdAt,
    updatedAt = updatedAt,
    photo = photoEntry,
)

/** A new row ([Item.id] = 0, so Room assigns one) keeping the original timestamps. */
fun BackupItem.toItem(photoPath: String?) = Item(
    name = name.trim(),
    place = place.trim(),
    tags = Tags.join(tags),
    note = note,
    photoPath = photoPath,
    lentTo = lentTo?.trim()?.ifBlank { null },
    createdAt = createdAt,
    updatedAt = updatedAt,
)
