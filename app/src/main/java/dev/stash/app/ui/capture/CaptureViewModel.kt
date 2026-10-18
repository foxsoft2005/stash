package dev.stash.app.ui.capture

import android.content.Context
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.core.SurfaceRequest
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.lifecycle.awaitInstance
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModel
import dev.stash.app.data.PhotoStore
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * CameraX + Compose: the Preview use case hands us a SurfaceRequest, which the
 * Compose-native CameraXViewfinder renders. No AndroidView/PreviewView needed.
 */
class CaptureViewModel(private val photos: PhotoStore) : ViewModel() {

    private val _surfaceRequest = MutableStateFlow<SurfaceRequest?>(null)
    val surfaceRequest: StateFlow<SurfaceRequest?> = _surfaceRequest.asStateFlow()

    private val _isCapturing = MutableStateFlow(false)
    val isCapturing: StateFlow<Boolean> = _isCapturing.asStateFlow()

    private val preview = Preview.Builder().build().apply {
        setSurfaceProvider { request -> _surfaceRequest.value = request }
    }

    // Speed over quality: the whole point is a < 3 second capture.
    private val imageCapture = ImageCapture.Builder()
        .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
        .build()

    /** Binds the camera for as long as the calling coroutine is active. */
    suspend fun bindToCamera(context: Context, lifecycleOwner: LifecycleOwner) {
        val provider = ProcessCameraProvider.awaitInstance(context.applicationContext)
        provider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, imageCapture)
        try {
            awaitCancellation()
        } finally {
            provider.unbindAll()
        }
    }

    /** Returns the saved photo path. */
    suspend fun takePhoto(context: Context): String {
        _isCapturing.value = true
        try {
            val file = photos.newPhotoFile()
            val options = ImageCapture.OutputFileOptions.Builder(file).build()
            suspendCancellableCoroutine<Unit> { cont ->
                imageCapture.takePicture(
                    options,
                    ContextCompat.getMainExecutor(context),
                    object : ImageCapture.OnImageSavedCallback {
                        override fun onImageSaved(output: ImageCapture.OutputFileResults) = cont.resume(Unit)
                        override fun onError(exception: ImageCaptureException) = cont.resumeWithException(exception)
                    },
                )
            }
            return file.absolutePath
        } finally {
            _isCapturing.value = false
        }
    }
}
