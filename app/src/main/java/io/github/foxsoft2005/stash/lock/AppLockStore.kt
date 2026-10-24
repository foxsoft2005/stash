package io.github.foxsoft2005.stash.lock

import android.content.Context
import androidx.core.content.edit

/** Whether App lock is on (same settings file as other app preferences). */
class AppLockStore(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    var enabled: Boolean
        get() = prefs.getBoolean(KEY, false)
        set(value) = prefs.edit { putBoolean(KEY, value) }

    private companion object {
        const val PREFS = "stash_settings"
        const val KEY = "app_lock"
    }
}
