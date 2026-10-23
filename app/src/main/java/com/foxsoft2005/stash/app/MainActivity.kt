package com.foxsoft2005.stash.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.foxsoft2005.stash.app.ui.StashNavigation
import com.foxsoft2005.stash.app.ui.theme.StashTheme

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
        const val ACTION_CAPTURE = "com.foxsoft2005.stash.app.action.CAPTURE"
    }
}
