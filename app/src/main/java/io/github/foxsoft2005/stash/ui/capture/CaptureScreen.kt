package io.github.foxsoft2005.stash.ui.capture

import android.Manifest
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import android.util.Log
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.compose.CameraXViewfinder
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.foxsoft2005.stash.R
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException

@Composable
fun CaptureScreen(
    viewModel: CaptureViewModel,
    onPhotoTaken: (String) -> Unit,
    onClose: () -> Unit,
    /** Shown over the viewfinder, e.g. what to photograph. */
    hint: String? = null,
) {
    val context = LocalContext.current
    val activity = LocalActivity.current
    var hasPermission by remember { mutableStateOf(context.hasCameraPermission()) }
    // After "Don't allow" twice (or "don't ask again"), Android stops showing the
    // dialog and launch() just returns false, so the only way back is Settings.
    var permanentlyDenied by rememberSaveable { mutableStateOf(false) }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        hasPermission = granted
        permanentlyDenied = !granted &&
            activity?.shouldShowRequestPermissionRationale(Manifest.permission.CAMERA) == false
    }

    LaunchedEffect(Unit) {
        if (!hasPermission) permissionLauncher.launch(Manifest.permission.CAMERA)
    }

    // Coming back from Settings: pick up a permission that was granted there.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        hasPermission = context.hasCameraPermission()
        if (hasPermission) permanentlyDenied = false
    }

    Surface(color = Color.Black, contentColor = Color.White, modifier = Modifier.fillMaxSize()) {
        if (hasPermission) {
            Viewfinder(viewModel, onPhotoTaken)
        } else {
            Column(
                Modifier.fillMaxSize().padding(32.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    stringResource(
                        if (permanentlyDenied) R.string.capture_permission_denied else R.string.capture_permission_rationale,
                    ),
                )
                Spacer(Modifier.size(16.dp))
                Button(
                    onClick = {
                        if (permanentlyDenied) {
                            openAppSettings(activity ?: context)
                        } else {
                            permissionLauncher.launch(Manifest.permission.CAMERA)
                        }
                    },
                ) {
                    Text(stringResource(if (permanentlyDenied) R.string.capture_open_settings else R.string.capture_allow_camera))
                }
            }
        }

        Box(Modifier.fillMaxSize().statusBarsPadding().padding(8.dp)) {
            FilledIconButton(
                onClick = onClose,
                colors = IconButtonDefaults.filledIconButtonColors(containerColor = Color.Black.copy(alpha = 0.4f)),
            ) {
                Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.capture_close), tint = Color.White)
            }
            if (hint != null) {
                Text(
                    hint,
                    color = Color.White,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(start = 56.dp, end = 56.dp, top = 6.dp)
                        .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                )
            }
        }
    }
}

@Composable
private fun Viewfinder(viewModel: CaptureViewModel, onPhotoTaken: (String) -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val surfaceRequest by viewModel.surfaceRequest.collectAsStateWithLifecycle()
    val isCapturing by viewModel.isCapturing.collectAsStateWithLifecycle()
    var error by remember { mutableStateOf<Int?>(null) } // string resource, resolved when shown

    LaunchedEffect(lifecycleOwner) {
        viewModel.bindToCamera(context, lifecycleOwner)
    }

    Box(Modifier.fillMaxSize()) {
        surfaceRequest?.let { request ->
            CameraXViewfinder(surfaceRequest = request, modifier = Modifier.fillMaxSize())
        }

        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            error?.let {
                Text(stringResource(it), color = MaterialTheme.colorScheme.error)
                Spacer(Modifier.size(12.dp))
            }
            ShutterButton(
                busy = isCapturing,
                onClick = {
                    scope.launch {
                        val path = try {
                            viewModel.takePhoto(context)
                        } catch (e: CancellationException) {
                            throw e // screen closed mid-capture: nothing to show
                        } catch (e: Exception) {
                            Log.w("CaptureScreen", "Photo capture failed", e)
                            error = R.string.capture_failed
                            return@launch
                        }
                        error = null
                        onPhotoTaken(path)
                    }
                },
            )
        }
    }
}

@Composable
private fun ShutterButton(busy: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .size(84.dp)
            .border(4.dp, Color.White, CircleShape)
            .padding(8.dp),
        contentAlignment = Alignment.Center,
    ) {
        if (busy) {
            CircularProgressIndicator(color = Color.White)
        } else {
            Surface(
                onClick = onClick,
                shape = CircleShape,
                color = Color.White,
                modifier = Modifier.fillMaxSize(),
            ) {}
        }
    }
}

private fun Context.hasCameraPermission(): Boolean =
    ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED

/** Opens Stash's page in system Settings, where Permissions › Camera can be switched back on. */
private fun openAppSettings(context: Context) {
    val details = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
    // Outside an Activity (shouldn't happen here) startActivity needs its own task.
    if (context !is Activity) details.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    try {
        context.startActivity(details)
    } catch (e: ActivityNotFoundException) {
        // A few heavily customized ROMs lack the per-app page; the main Settings screen is the best we can do.
        context.startActivity(Intent(Settings.ACTION_SETTINGS).apply { if (context !is Activity) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) })
    }
}
