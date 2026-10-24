package io.github.foxsoft2005.stash

import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.foxsoft2005.stash.lock.AppLockUi
import io.github.foxsoft2005.stash.lock.AppLockViewModel
import io.github.foxsoft2005.stash.lock.LocalAppLock
import io.github.foxsoft2005.stash.lock.LockScreen
import io.github.foxsoft2005.stash.ui.StashNavigation
import io.github.foxsoft2005.stash.ui.theme.StashTheme
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.launch

// A FragmentActivity because the system unlock prompt (androidx.biometric) needs one.
class MainActivity : FragmentActivity() {

    private val lock: AppLockViewModel by viewModels {
        viewModelFactory { initializer { AppLockViewModel(stashContainer.appLock) } }
    }

    /** What to do once the user has confirmed with fingerprint / face / screen lock. */
    private var afterAuth: (() -> Unit)? = null

    // Created here (not on demand) so a prompt open during rotation reconnects to this activity.
    private val prompt by lazy {
        BiometricPrompt(
            this,
            ContextCompat.getMainExecutor(this),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    afterAuth?.invoke()
                    afterAuth = null
                }

                // Cancelled or failed for good: stay as we are (locked screen keeps its Unlock button).
                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    afterAuth = null
                }
            },
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        prompt // create now, see above

        // Launched from the "Stash something" app shortcut → open straight into the camera.
        val startWithCamera = intent?.action == ACTION_CAPTURE

        // With App lock on, the app switcher shows a blank card instead of your stash (Android 13+).
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.CREATED) {
                lock.state.distinctUntilChangedBy { it.enabled }.collect { state ->
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) setRecentsScreenshotEnabled(!state.enabled)
                }
            }
        }

        setContent {
            StashTheme {
                val state by lock.state.collectAsStateWithLifecycle()
                val ui = AppLockUi(
                    enabled = state.enabled,
                    locked = state.locked,
                    message = state.message,
                    onToggle = ::toggleAppLock,
                    onMessageShown = lock::messageShown,
                )
                CompositionLocalProvider(LocalAppLock provides ui) {
                    Box(Modifier.fillMaxSize()) {
                        // Kept composed while locked so screens keep their state, but invisible
                        // and hidden from TalkBack; the lock screen on top takes all touches.
                        Box(
                            Modifier
                                .fillMaxSize()
                                .then(if (state.locked) Modifier.alpha(0f).clearAndSetSemantics {} else Modifier),
                        ) {
                            StashNavigation(startWithCamera = startWithCamera)
                        }
                        if (state.locked) {
                            LockScreen(
                                onUnlock = { authenticate(R.string.lock_prompt_title) { lock.unlocked() } },
                                onLeave = ::finish,
                            )
                        }
                    }
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        lock.onForeground(SystemClock.elapsedRealtime(), canUnlock = hasScreenLock())
    }

    override fun onStop() {
        super.onStop()
        // A rotation isn't leaving the app.
        if (!isChangingConfigurations) lock.onBackground(SystemClock.elapsedRealtime())
    }

    /** ⋮ › App lock. Both directions need the phone's lock, so nobody else can switch it off. */
    private fun toggleAppLock() {
        val turnOn = !lock.state.value.enabled
        if (turnOn && !hasScreenLock()) {
            lock.noScreenLock()
            return
        }
        authenticate(if (turnOn) R.string.lock_prompt_enable else R.string.lock_prompt_disable) {
            lock.setEnabled(turnOn)
        }
    }

    private fun hasScreenLock(): Boolean =
        BiometricManager.from(this).canAuthenticate(AUTHENTICATORS) == BiometricManager.BIOMETRIC_SUCCESS

    private fun authenticate(titleRes: Int, onSuccess: () -> Unit) {
        afterAuth = onSuccess
        prompt.authenticate(
            BiometricPrompt.PromptInfo.Builder()
                .setTitle(getString(titleRes))
                .setAllowedAuthenticators(AUTHENTICATORS)
                .build(),
        )
    }

    companion object {
        const val ACTION_CAPTURE = "io.github.foxsoft2005.stash.action.CAPTURE"

        // Fingerprint/face, or the phone's PIN/pattern/password. This combination works on
        // every Android version Stash supports; it's a gate for the UI, not a crypto key.
        private const val AUTHENTICATORS = BIOMETRIC_WEAK or DEVICE_CREDENTIAL
    }
}
