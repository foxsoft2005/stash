package dev.stash.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import dev.stash.app.ui.StashNavigation
import dev.stash.app.ui.theme.StashTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Launched from the "Stash something" app shortcut → open straight into the camera.
        val startWithCamera = intent?.action == ACTION_CAPTURE

        setContent {
            StashTheme {
                StashNavigation(startWithCamera = startWithCamera)
            }
        }
    }

    companion object {
        const val ACTION_CAPTURE = "dev.stash.app.action.CAPTURE"
    }
}
