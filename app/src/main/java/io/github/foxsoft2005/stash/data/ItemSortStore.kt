package io.github.foxsoft2005.stash.data

import android.content.Context
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Remembers the chosen sort on this device (same settings file as other app preferences). */
class ItemSortStore(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private val _sort = MutableStateFlow(read())
    val sort: StateFlow<ItemSort> = _sort.asStateFlow()

    fun set(sort: ItemSort) {
        prefs.edit { putString(KEY, sort.name) }
        _sort.value = sort
    }

    private fun read(): ItemSort =
        prefs.getString(KEY, null)?.let { saved -> ItemSort.entries.firstOrNull { it.name == saved } }
            ?: ItemSort.DEFAULT

    private companion object {
        const val PREFS = "stash_settings"
        const val KEY = "home_sort"
    }
}
