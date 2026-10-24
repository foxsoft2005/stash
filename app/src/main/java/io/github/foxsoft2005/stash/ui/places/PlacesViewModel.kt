package io.github.foxsoft2005.stash.ui.places

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.foxsoft2005.stash.data.ItemRepository
import io.github.foxsoft2005.stash.data.PlaceSummary
import io.github.foxsoft2005.stash.data.Places
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

class PlacesViewModel(repository: ItemRepository) : ViewModel() {
    /** Null while loading. */
    val places: StateFlow<List<PlaceSummary>?> = repository.observeAll()
        .map { Places.summarize(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
}
