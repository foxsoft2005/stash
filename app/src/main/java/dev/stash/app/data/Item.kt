package dev.stash.app.data

import androidx.room3.Entity
import androidx.room3.PrimaryKey

/**
 * One stored thing: a photo, what it is, and where it went.
 *
 * [tags] is a space-separated, lowercase keyword list (AI-suggested + user-edited).
 * [lentTo] is non-null when the item is currently lent out to someone.
 */
@Entity(tableName = "items")
data class Item(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val place: String,
    val tags: String = "",
    val note: String = "",
    val photoPath: String? = null,
    val lentTo: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
) {
    val isLent: Boolean get() = !lentTo.isNullOrBlank()
    val tagList: List<String> get() = tags.split(' ').filter { it.isNotBlank() }
}
