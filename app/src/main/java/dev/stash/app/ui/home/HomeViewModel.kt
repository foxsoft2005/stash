package dev.stash.app.ui.home

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.clearText
import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.stash.app.data.Item
import dev.stash.app.data.ItemRepository
import dev.stash.app.search.ItemSearch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class HomeUiState(
    val isLoading: Boolean = true,
    val query: String = "",
    val lentOnly: Boolean = false,
    val lentCount: Int = 0,
    val totalCount: Int = 0,
    /** Items to show in the grid: search results, or everything. */
    val items: List<Item> = emptyList(),
) {
    val isSearching: Boolean get() = query.isNotBlank()
    /** The single best answer to "where is my …?" */
    val bestMatch: Item? get() = if (isSearching) items.firstOrNull() else null
}

class HomeViewModel(repository: ItemRepository) : ViewModel() {

    /** Compose's state-based text field state; the UI binds to it directly. */
    val queryState = TextFieldState()
    private val lentOnly = MutableStateFlow(false)

    val uiState: StateFlow<HomeUiState> = combine(
        repository.observeAll(),
        snapshotFlow { queryState.text.toString() },
        lentOnly,
    ) { all, query, lentOnly ->
        val pool = if (lentOnly) all.filter { it.isLent } else all
        HomeUiState(
            isLoading = false,
            query = query,
            lentOnly = lentOnly,
            lentCount = all.count { it.isLent },
            totalCount = all.size,
            items = if (query.isBlank()) pool else ItemSearch.search(query, pool),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    fun setLentOnly(value: Boolean) {
        lentOnly.value = value
    }

    fun clearQuery() = queryState.clearText()
}
