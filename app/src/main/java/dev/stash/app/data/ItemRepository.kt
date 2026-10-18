package dev.stash.app.data

import kotlinx.coroutines.flow.Flow

class ItemRepository(
    private val dao: ItemDao,
    private val photos: PhotoStore,
) {
    fun observeAll(): Flow<List<Item>> = dao.observeAll()

    fun observeRecentPlaces(limit: Int = 8): Flow<List<String>> = dao.observeRecentPlaces(limit)

    suspend fun get(id: Long): Item? = dao.get(id)

    suspend fun save(item: Item) {
        dao.upsert(item.copy(updatedAt = System.currentTimeMillis()))
    }

    suspend fun delete(item: Item) {
        dao.delete(item.id)
        photos.delete(item.photoPath)
    }
}
