package io.github.foxsoft2005.stash.ui.home

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.clearText
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.foxsoft2005.stash.R
import io.github.foxsoft2005.stash.data.Item
import io.github.foxsoft2005.stash.data.ItemRepository
import io.github.foxsoft2005.stash.data.ItemSort
import io.github.foxsoft2005.stash.data.ItemSortStore
import io.github.foxsoft2005.stash.data.Places
import io.github.foxsoft2005.stash.data.sortItems
import io.github.foxsoft2005.stash.data.backup.BackupReminder
import io.github.foxsoft2005.stash.data.backup.BackupReminders
import io.github.foxsoft2005.stash.data.backup.BackupStatusStore
import io.github.foxsoft2005.stash.search.ItemSearch
import io.github.foxsoft2005.stash.ui.UiText
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class HomeUiState(
    val isLoading: Boolean = true,
    val query: String = "",
    val lentOnly: Boolean = false,
    /** Lent-out items in scope (the chosen place, or everywhere). */
    val lentCount: Int = 0,
    /** All items in scope (the chosen place, or everywhere). */
    val scopeCount: Int = 0,
    /** Everything stored, regardless of filters; 0 = show the first-run empty state. */
    val totalCount: Int = 0,
    /** Browsing one place: how it's written (latest spelling). Null = all places. */
    val place: String? = null,
    /** Items to show in the grid: search results, or everything. */
    val items: List<Item> = emptyList(),
    /** Non-null when a "back up your stash" card should show. */
    val backupReminder: BackupReminder? = null,
    /** Batch mode: the selected items among [items]. Empty = not in batch mode. */
    val selected: List<Item> = emptyList(),
    /** Items just deleted, waiting for the Undo window to close. */
    val pendingDeletion: PendingDeletion? = null,
    /** Grid order when not searching (search results stay ordered by relevance). */
    val sort: ItemSort = ItemSort.DEFAULT,
) {
    val isSearching: Boolean get() = query.isNotBlank()
    /** The single best answer to "where is my …?" */
    val bestMatch: Item? get() = if (isSearching) items.firstOrNull() else null

    val isSelecting: Boolean get() = selected.isNotEmpty()
    val selectedIds: Set<Long> get() = selected.mapTo(mutableSetOf()) { it.id }
    val anySelectedLent: Boolean get() = selected.any { it.isLent }
}

class HomeViewModel(
    private val repository: ItemRepository,
    private val backupStatus: BackupStatusStore,
    private val sortStore: ItemSortStore,
    /** Outlives this screen, so a pending delete still completes if the screen goes away. */
    private val appScope: CoroutineScope,
    /** Place chosen on the Places screen (kept by the navigation, so it survives process death). */
    initialPlace: String? = null,
    private val now: () -> Long = System::currentTimeMillis,
) : ViewModel() {

    /** Compose's state-based text field state; the UI binds to it directly. */
    val queryState = TextFieldState()
    private val lentOnly = MutableStateFlow(false)
    private val placeFilter = MutableStateFlow(initialPlace?.takeIf { it.isNotBlank() })
    private val selection = MutableStateFlow(Selection())
    private val pendingDeletion = MutableStateFlow<PendingDeletion?>(null)
    // Deleted for good, but the database may not have caught up yet; IDs are never
    // reused (AUTOINCREMENT), so hiding them for the rest of the screen is safe.
    private val deletedIds = MutableStateFlow<Set<Long>>(emptySet())
    private var nextDeletionToken = 0L

    /** One-off result for the snackbar (e.g. "3 items marked as returned"). */
    var message by mutableStateOf<UiText?>(null)
        private set

    private data class Controls(
        val place: String?,
        val lentOnly: Boolean,
        val selection: Selection,
        val pending: PendingDeletion?,
        val deleted: Set<Long>,
    ) {
        val hidden: Set<Long> get() = pending?.ids.orEmpty() + deleted
    }

    private val controls = combine(placeFilter, lentOnly, selection, pendingDeletion, deletedIds, ::Controls)

    val uiState: StateFlow<HomeUiState> = combine(
        repository.observeAll(),
        snapshotFlow { queryState.text.toString() },
        controls,
        backupStatus.status,
        sortStore.sort,
    ) { stored, query, controls, status, sort ->
        // Items waiting for Undo are already gone as far as the user can see.
        val hidden = controls.hidden
        val all = if (hidden.isEmpty()) stored else stored.filter { it.id !in hidden }
        // Browsing one place: everything below (chips, search, sorting, batch mode) stays inside it.
        val placeKey = controls.place?.let(Places::key)
        val scope = if (placeKey == null) all else all.filter { Places.contains(placeKey, it) }
        val pool = if (controls.lentOnly) scope.filter { it.isLent } else scope
        // Searching: best match first. Otherwise: the order the user picked.
        val visible = if (query.isBlank()) sortItems(pool, sort) else ItemSearch.search(query, pool)
        HomeUiState(
            isLoading = false,
            query = query,
            lentOnly = controls.lentOnly,
            lentCount = scope.count { it.isLent },
            scopeCount = scope.size,
            totalCount = all.size,
            // Show the place the way it was last written; keep the picked name if nothing is left there.
            place = controls.place?.let { picked -> scope.maxByOrNull { it.updatedAt }?.place?.trim() ?: picked },
            items = visible,
            backupReminder = BackupReminders.reminder(all, status, now()),
            selected = controls.selection.visibleIn(visible),
            pendingDeletion = controls.pending,
            sort = sort,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    fun setLentOnly(value: Boolean) {
        lentOnly.value = value
    }

    fun clearQuery() = queryState.clearText()

    /** Browse one place (as picked on the Places screen), or null for all places. */
    fun setPlaceFilter(place: String?) {
        val next = place?.takeIf { it.isNotBlank() }
        if (next?.let(Places::key) == placeFilter.value?.let(Places::key)) return
        placeFilter.value = next
        clearSelection() // a selection made in another place would be invisible
    }

    fun setSort(sort: ItemSort) = sortStore.set(sort)

    /** "Later" on the backup card. */
    fun snoozeBackupReminder() = backupStatus.snooze(BackupReminders.snoozedUntil(now()))

    fun messageShown() {
        message = null
    }

    // --- Batch mode

    fun onItemLongPress(id: Long) = selection.update { it.longPress(id, uiState.value.items) }

    /** Returns true if the tap was used for selection (so the item shouldn't open). */
    fun onItemClick(id: Long): Boolean {
        if (!uiState.value.isSelecting) return false
        selection.update { it.toggle(id, uiState.value.items) }
        return true
    }

    fun selectAll() = selection.update { it.selectAll(uiState.value.items) }

    fun clearSelection() {
        selection.value = Selection()
    }

    /** Hides the selected items now; they're really deleted when the Undo window closes. */
    fun deleteSelected() {
        val items = uiState.value.selected
        if (items.isEmpty()) return
        // A previous delete still waiting for Undo is final once the next one starts.
        pendingDeletion.value?.let { commit(it) }
        pendingDeletion.value = PendingDeletion(token = ++nextDeletionToken, items = items)
        clearSelection()
    }

    fun undoDelete(token: Long) {
        pendingDeletion.update { if (it?.token == token) null else it }
    }

    /** The Undo snackbar was dismissed or timed out. */
    fun confirmDelete(token: Long) {
        val pending = pendingDeletion.value?.takeIf { it.token == token } ?: return
        commit(pending)
    }

    private fun commit(pending: PendingDeletion) {
        deletedIds.update { it + pending.ids } // stay hidden: no flicker while the DB catches up
        pendingDeletion.update { if (it?.token == pending.token) null else it }
        appScope.launch { repository.deleteAll(pending.items) }
    }

    /** One-tap places for the "Move to…" dialog. */
    val recentPlaces: StateFlow<List<String>> = repository.observeRecentPlaces()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Batch "Move to…". Items moved out of the place being browsed simply leave the grid. */
    fun moveSelected(place: String) {
        val target = place.trim()
        val items = uiState.value.selected
        if (target.isEmpty() || items.isEmpty()) return
        clearSelection()
        viewModelScope.launch {
            val moved = repository.moveTo(items, target)
            message = if (moved > 0) {
                UiText.plural(R.plurals.selection_moved, moved, target)
            } else {
                UiText.of(R.string.selection_already_there, target)
            }
        }
    }

    fun markSelectedReturned() {
        val items = uiState.value.selected
        clearSelection()
        viewModelScope.launch {
            val changed = repository.markReturned(items)
            if (changed > 0) message = UiText.plural(R.plurals.selection_marked_returned, changed)
        }
    }

    override fun onCleared() {
        // Screen gone while Undo was still possible (e.g. Back out of the app): finish the delete.
        pendingDeletion.value?.let { pending -> appScope.launch { repository.deleteAll(pending.items) } }
    }
}
