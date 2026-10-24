package io.github.foxsoft2005.stash.ui.checkin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.foxsoft2005.stash.data.CheckIns
import io.github.foxsoft2005.stash.data.Item
import io.github.foxsoft2005.stash.data.ItemRepository
import io.github.foxsoft2005.stash.ui.home.PendingDeletion
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class CheckInUiState(
    val isLoading: Boolean = true,
    /** The item being asked about; null once the review is finished. */
    val current: Item? = null,
    /** 1-based position of [current] in the review. */
    val position: Int = 0,
    val total: Int = 0,
    /** Months since [current] was last changed or confirmed. */
    val months: Int = 0,
    /** "Gone" waiting for its Undo window to close. */
    val pendingGone: PendingDeletion? = null,
) {
    val isDone: Boolean get() = !isLoading && current == null
}

/**
 * Goes through the items due for a "Still there?" check, one at a time. The list is
 * fixed when the review starts, so answering never makes it jump around.
 */
class CheckInViewModel(
    private val repository: ItemRepository,
    /** Outlives this screen, so a pending "Gone" still completes if the screen goes away. */
    private val appScope: CoroutineScope,
    private val now: () -> Long = System::currentTimeMillis,
) : ViewModel() {

    private val queue = MutableStateFlow<List<Long>?>(null)
    private val index = MutableStateFlow(0)
    private val pendingGone = MutableStateFlow<PendingDeletion?>(null)
    private var nextToken = 0L

    val recentPlaces: StateFlow<List<String>> = repository.observeRecentPlaces()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        viewModelScope.launch {
            queue.value = CheckIns.due(repository.observeAll().first(), now()).map { it.id }
        }
    }

    val uiState: StateFlow<CheckInUiState> = combine(
        repository.observeAll(), queue, index, pendingGone,
    ) { items, ids, at, pending ->
        if (ids == null) return@combine CheckInUiState()
        val byId = items.associateBy { it.id }
        // Skip anything deleted meanwhile (e.g. on another screen).
        val position = (at until ids.size).firstOrNull { ids[it] in byId }
        val current = position?.let { byId.getValue(ids[it]) }
        CheckInUiState(
            isLoading = false,
            current = current,
            position = (position ?: ids.size) + 1,
            total = ids.size,
            months = current?.let { CheckIns.monthsSince(it, now()) } ?: 0,
            pendingGone = pending,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CheckInUiState())

    private fun advance(from: Item) {
        val ids = queue.value ?: return
        index.value = ids.indexOf(from.id) + 1
    }

    fun stillThere() {
        val item = uiState.value.current ?: return
        advance(item)
        viewModelScope.launch { repository.confirmStillThere(item) }
    }

    fun moved(place: String) {
        val item = uiState.value.current ?: return
        advance(item)
        viewModelScope.launch { repository.moveTo(listOf(item), place) }
    }

    fun skip() {
        uiState.value.current?.let(::advance)
    }

    /** Hides the item now; it's really deleted when the Undo window closes. */
    fun gone() {
        val item = uiState.value.current ?: return
        pendingGone.value?.let(::commit)
        pendingGone.value = PendingDeletion(token = ++nextToken, items = listOf(item))
        advance(item)
    }

    fun undoGone(token: Long) {
        val pending = pendingGone.value?.takeIf { it.token == token } ?: return
        pendingGone.value = null
        // Back to that item, so it can be answered differently.
        val ids = queue.value ?: return
        index.value = ids.indexOf(pending.items.single().id).coerceAtLeast(0)
    }

    fun confirmGone(token: Long) {
        pendingGone.value?.takeIf { it.token == token }?.let(::commit)
    }

    private fun commit(pending: PendingDeletion) {
        pendingGone.update { if (it?.token == pending.token) null else it }
        appScope.launch { repository.deleteAll(pending.items) }
    }

    override fun onCleared() {
        pendingGone.value?.let { pending -> appScope.launch { repository.deleteAll(pending.items) } }
    }
}
