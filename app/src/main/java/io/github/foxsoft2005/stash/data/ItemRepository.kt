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
    }

    private companion object {
        const val RECENT_PLACES_OVERFETCH = 5
    }
}
