package io.github.foxsoft2005.stash.ui.places

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.foxsoft2005.stash.R
import io.github.foxsoft2005.stash.data.ItemRepository
import io.github.foxsoft2005.stash.data.PlaceSummary
import io.github.foxsoft2005.stash.data.Places
import io.github.foxsoft2005.stash.ui.UiText
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class PlacesViewModel(private val repository: ItemRepository) : ViewModel() {
    /** Null while loading. */
    val places: StateFlow<List<PlaceSummary>?> = repository.observeAll()
        .map { Places.summarize(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** One-off result for the snackbar. */
    var message by mutableStateOf<UiText?>(null)
        private set

    fun messageShown() {
        message = null
    }

    /** The existing place a rename would join, for the "will merge" hint. */
    fun mergeTarget(place: PlaceSummary, newName: String): PlaceSummary? =
        Places.mergeTarget(places.value.orEmpty(), place.key, newName)

    /** Renames [place] to [newName], merging it into an existing place with that name. */
    fun rename(place: PlaceSummary, newName: String) {
        val name = newName.trim()
        if (name.isEmpty() || name == place.name) return
        val merging = mergeTarget(place, name) != null
        viewModelScope.launch {
            val changed = repository.renamePlace(place.key, name)
            if (changed == 0) return@launch
            message = if (merging) {
                UiText.plural(R.plurals.selection_moved, changed, name)
            } else {
                UiText.of(R.string.places_renamed, name)
            }
        }
    }
}
