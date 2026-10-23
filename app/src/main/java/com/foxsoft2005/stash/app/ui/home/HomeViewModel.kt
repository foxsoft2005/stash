package com.foxsoft2005.stash.app.ui.home

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.clearText
import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.foxsoft2005.stash.app.data.Item
import com.foxsoft2005.stash.app.data.ItemRepository
import com.foxsoft2005.stash.app.data.backup.BackupReminder
import com.foxsoft2005.stash.app.data.backup.BackupReminders
import com.foxsoft2005.stash.app.data.backup.BackupStatusStore
import com.foxsoft2005.stash.app.search.ItemSearch
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
    /** Non-null when a "back up your stash" card should show. */
    val backupReminder: BackupReminder? = null,
) {
    val isSearching: Boolean get() = query.isNotBlank()
    /** The single best answer to "where is my …?" */
    val bestMatch: Item? get() = if (isSearching) items.firstOrNull() else null
}

class HomeViewModel(
    repository: ItemRepository,
    private val backupStatus: BackupStatusStore,
    private val now: () -> Long = System::currentTimeMillis,
) : ViewModel() {

    /** Compose's state-based text field state; the UI binds to it directly. */
    val queryState = TextFieldState()
    private val lentOnly = MutableStateFlow(false)

    val uiState: StateFlow<HomeUiState> = combine(
        repository.observeAll(),
        snapshotFlow { queryState.text.toString() },
        lentOnly,
        backupStatus.status,
    ) { all, query, lentOnly, status ->
        val pool = if (lentOnly) all.filter { it.isLent } else all
        HomeUiState(
            isLoading = false,
            query = query,
            lentOnly = lentOnly,
            lentCount = all.count { it.isLent },
            totalCount = all.size,
            items = if (query.isBlank()) pool else ItemSearch.search(query, pool),
            backupReminder = BackupReminders.reminder(all, status, now()),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    fun setLentOnly(value: Boolean) {
        lentOnly.value = value
    }

    fun clearQuery() = queryState.clearText()

    /** "Later" on the backup card. */
    fun snoozeBackupReminder() = backupStatus.snooze(BackupReminders.snoozedUntil(now()))
}
