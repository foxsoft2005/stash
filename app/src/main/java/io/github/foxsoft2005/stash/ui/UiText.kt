package io.github.foxsoft2005.stash.ui

import android.content.res.Resources
import androidx.annotation.PluralsRes
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalResources

/**
 * Text a ViewModel wants to show, as resource IDs + arguments rather than a
 * finished String. The screen resolves it in the user's current language, so
 * ViewModels need no Context and a language switch never shows stale text.
 */
sealed interface UiText {
    data class Res(@StringRes val id: Int, val args: List<Any> = emptyList()) : UiText

    /** A count in a sentence ("3 items" / "3 вещи"); [count] picks the plural form and fills %d. */
    data class Plural(@PluralsRes val id: Int, val count: Int) : UiText

    /** Several sentences shown together, separated by spaces. */
    data class Join(val parts: List<UiText>) : UiText

    fun resolve(resources: Resources): String = when (this) {
        is Res -> resources.getString(id, *args.toTypedArray())
        is Plural -> resources.getQuantityString(id, count, count)
        is Join -> parts.joinToString(" ") { it.resolve(resources) }
    }

    companion object {
        fun of(@StringRes id: Int, vararg args: Any): UiText = Res(id, args.toList())
        fun plural(@PluralsRes id: Int, count: Int): UiText = Plural(id, count)
    }
}

@Composable
fun UiText.asString(): String = resolve(LocalResources.current) // recomposes on a language change
