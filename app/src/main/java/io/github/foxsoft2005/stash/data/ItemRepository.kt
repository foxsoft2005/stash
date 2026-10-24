package io.github.foxsoft2005.stash.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class ItemRepository(
    private val dao: ItemDao,
    private val photos: PhotoStore,
) {
    fun observeAll(): Flow<List<Item>> = dao.observeAll()

    fun observeRecentPlaces(limit: Int = 8): Flow<List<String>> =
        // Over-fetch so case-only duplicates ("Garage"/"garage") can't push real places out.
        dao.observeRecentPlaces(limit * RECENT_PLACES_OVERFETCH).map { Places.recent(it, limit) }

    suspend fun get(id: Long): Item? = dao.get(id)

    suspend fun save(item: Item) {
        dao.upsert(item.copy(updatedAt = System.currentTimeMillis()))
    }

    suspend fun delete(item: Item) {
        dao.delete(item.id)
        photos.delete(item.photoPath)
        photos.delete(item.placePhotoPath)
    }

    /** Batch delete: rows in one statement, then their photos. */
    suspend fun deleteAll(items: List<Item>) {
        if (items.isEmpty()) return
        dao.deleteAll(items.map { it.id })
        items.forEach {
            photos.delete(it.photoPath)
            photos.delete(it.placePhotoPath)
        }
    }

    /** Batch "Move to…": returns how many items actually changed place. */
    suspend fun moveTo(items: List<Item>, place: String): Int {
        val targets = Places.moveTargets(items, place)
        if (targets.isEmpty()) return 0
        return dao.setPlace(targets.map { it.id }, place.trim(), System.currentTimeMillis())
    }

    /** Renames (or merges) a place: every item there gets [newName]. Returns how many changed. */
    suspend fun renamePlace(fromKey: String, newName: String): Int =
        moveTo(dao.getAll().filter { Places.contains(fromKey, it) }, newName)

    /** "Still there?" → yes. Doesn't change the item, so it keeps its place in "Recently changed". */
    suspend fun confirmStillThere(item: Item) = dao.setCheckedAt(item.id, System.currentTimeMillis())

    /** Clears "lent to" on each item that has it. Returns how many changed. */
    suspend fun markReturned(items: List<Item>): Int {
        val lent = items.filter { it.isLent }
        lent.forEach { save(it.copy(lentTo = null)) }
        return lent.size
    }

    private companion object {
        const val RECENT_PLACES_OVERFETCH = 5
    }
}
