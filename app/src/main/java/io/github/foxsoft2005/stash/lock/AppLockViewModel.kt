package io.github.foxsoft2005.stash.lock

import androidx.lifecycle.ViewModel
import io.github.foxsoft2005.stash.R
import io.github.foxsoft2005.stash.ui.UiText
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class AppLockState(
    val enabled: Boolean,
    val locked: Boolean,
    /** One-off result for a snackbar ("App lock is on"). */
    val message: UiText? = null,
)

/**
 * App lock for the whole activity. Lives as long as the activity (survives rotation);
 * a new process starts locked if the lock is on. The prompt itself is shown by the
 * activity, which reports back through [unlocked] / [setEnabled].
 */
class AppLockViewModel(private val store: AppLockStore) : ViewModel() {

    private val _state = MutableStateFlow(
        AppLockState(enabled = store.enabled, locked = AppLockPolicy.lockOnLaunch(store.enabled)),
    )
    val state: StateFlow<AppLockState> = _state.asStateFlow()

    private var leftAt: Long? = null

    fun onBackground(now: Long) {
        leftAt = now
    }

    /** [canUnlock] = the phone still has a screen lock; without one, locking would shut the user out. */
    fun onForeground(now: Long, canUnlock: Boolean) {
        _state.update { s ->
            val lock = s.locked || AppLockPolicy.lockOnReturn(s.enabled, leftAt, now)
            s.copy(locked = lock && canUnlock)
        }
        leftAt = null
    }

    fun unlocked() = _state.update { it.copy(locked = false) }

    /** Called after the user confirmed with the phone's lock. */
    fun setEnabled(on: Boolean) {
        store.enabled = on
        _state.update {
            it.copy(enabled = on, message = UiText.of(if (on) R.string.lock_enabled else R.string.lock_disabled))
        }
    }

    /** Turning the lock on needs a screen lock on the phone. */
    fun noScreenLock() = _state.update { it.copy(message = UiText.of(R.string.lock_needs_screen_lock)) }

    fun messageShown() = _state.update { it.copy(message = null) }
}
