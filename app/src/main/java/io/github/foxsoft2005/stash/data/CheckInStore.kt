package io.github.foxsoft2005.stash.data

import android.content.Context
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Remembers "Later" on the "Still there?" card (same settings file as other app preferences). */
class CheckInStore(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private val _snoozedUntil = MutableStateFlow(prefs.getLong(KEY, 0L))
    val snoozedUntil: StateFlow<Long> = _snoozedUntil.asStateFlow()

    fun snooze(until: Long) {
        prefs.edit { putLong(KEY, until) }
        _snoozedUntil.value = until
    }

    private companion object {
        const val PREFS = "stash_settings"
        const val KEY = "checkins_snoozed_until"
    }
}
