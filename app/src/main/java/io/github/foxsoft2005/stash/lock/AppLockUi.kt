package io.github.foxsoft2005.stash.lock

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.foxsoft2005.stash.R
import io.github.foxsoft2005.stash.ui.UiText

/** What screens need to know about App lock. */
data class AppLockUi(
    val enabled: Boolean = false,
    val locked: Boolean = false,
    val message: UiText? = null,
    val onToggle: () -> Unit = {},
    val onMessageShown: () -> Unit = {},
)

val LocalAppLock = staticCompositionLocalOf { AppLockUi() }

/**
 * Covers the app while locked. Opens the unlock prompt right away; if that's
 * dismissed, the button opens it again. Back leaves the app instead of reaching
 * the screens underneath.
 */
@Composable
fun LockScreen(onUnlock: () -> Unit, onLeave: () -> Unit) {
    BackHandler(onBack = onLeave)
    LaunchedEffect(Unit) { onUnlock() }
    Surface(modifier = Modifier.fillMaxSize()) { // an opaque Surface also blocks touches
        Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(Icons.Rounded.Lock, contentDescription = null, modifier = Modifier.size(56.dp), tint = MaterialTheme.colorScheme.primary)
                Text(stringResource(R.string.lock_title), style = MaterialTheme.typography.titleLarge)
                Text(
                    stringResource(R.string.lock_body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                Button(onClick = onUnlock) { Text(stringResource(R.string.lock_unlock)) }
            }
        }
    }
}
