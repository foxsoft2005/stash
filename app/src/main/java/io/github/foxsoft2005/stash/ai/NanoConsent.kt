package io.github.foxsoft2005.stash.ai

import android.content.Context
import androidx.core.content.edit

/** The user's answer to "download Gemini Nano?". */
enum class NanoConsent { UNASKED, ACCEPTED, DECLINED }

/** Where Gemini Nano stands on this phone, as far as Stash is concerned. */
enum class NanoState {
    /** Haven't asked AICore yet. */
    CHECKING,

    /** Device doesn't support Gemini Nano: ML Kit labels only, never ask. */
    UNAVAILABLE,

    /** Supported but not on the phone yet: needs the user's OK to download. */
    NEEDS_DOWNLOAD,

    /** AICore is downloading it (because we asked, or another app did). */
    DOWNLOADING,

    /** On the phone and usable. */
    READY,
}

/**
 * Remembers the answer across launches. Plain SharedPreferences: it's one value,
 * so DataStore (a new dependency) would be overkill. Excluded from cloud backup
 * by the existing backup rules, like all app data.
 */
class NanoConsentStore(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    var value: NanoConsent
        get() = prefs.getString(KEY, null)
            ?.let { saved -> NanoConsent.entries.firstOrNull { it.name == saved } }
            ?: NanoConsent.UNASKED
        set(value) = prefs.edit { putString(KEY, value.name) }

    private companion object {
        const val PREFS = "stash_settings"
        const val KEY = "gemini_nano_consent"
    }
}

/** Ask only when there's something to download and the user hasn't answered yet. */
internal fun shouldAskForNano(state: NanoState, consent: NanoConsent): Boolean =
    state == NanoState.NEEDS_DOWNLOAD && consent == NanoConsent.UNASKED

/**
 * At app start, download only if the user already said yes (e.g. an earlier
 * download was interrupted, or AICore dropped the model to free space).
 */
internal fun shouldDownloadOnStartup(state: NanoState, consent: NanoConsent): Boolean =
    state == NanoState.NEEDS_DOWNLOAD && consent == NanoConsent.ACCEPTED

/** After "No thanks", still let the user change their mind from the edit screen. */
internal fun canOfferNano(state: NanoState): Boolean = state == NanoState.NEEDS_DOWNLOAD
