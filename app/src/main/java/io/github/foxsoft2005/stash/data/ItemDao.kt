package io.github.foxsoft2005.stash.data

import androidx.room3.Dao
import androidx.room3.Query
import androidx.room3.Upsert
import kotlinx.coroutines.flow.Flow

// Room 3 DAOs are async-only: every function is either `suspend` or returns a Flow.
@Dao
interface ItemDao {

    @Query("SELECT * FROM items ORDER BY updatedAt DESC")
    fun observeAll(): Flow<List<Item>>

    @Query("SELECT * FROM items WHERE id = :id")
    suspend fun get(id: Long): Item?

    /** Snapshot of every item, oldest first (backup order). */
    @Query("SELECT * FROM items ORDER BY createdAt")
    suspend fun getAll(): List<Item>

    /** Most recently used places, so the "Where?" field can be filled with one tap. */
    @Query("SELECT place FROM items GROUP BY place ORDER BY MAX(updatedAt) DESC LIMIT :limit")
    fun observeRecentPlaces(limit: Int): Flow<List<String>>

    @Upsert
    suspend fun upsert(item: Item): Long

    @Query("DELETE FROM items WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM items WHERE id IN (:ids)")
    suspend fun deleteAll(ids: List<Long>)
}
