package io.github.foxsoft2005.stash.ui.photo

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest
import coil3.size.Precision
import io.github.foxsoft2005.stash.R

/**
 * A photo full screen: pinch to zoom (up to 5x), drag to look around, double-tap to
 * zoom in on a spot or back out. Back or ✕ closes it.
 */
@Composable
fun PhotoScreen(path: String, description: String?, onClose: () -> Unit) {
    var zoom by remember { mutableStateOf(Zoom()) }
    var view by remember { mutableStateOf(Vec.Zero) }
    var photo by remember { mutableStateOf(Vec.Zero) }
    fun fromCenter(p: Offset) = Vec(p.x - view.x / 2f, p.y - view.y / 2f)

    // Sharp enough to zoom into (a 12 MP camera photo fits), without loading something huge.
    val context = LocalPlatformContext.current
    val request = remember(path) {
        ImageRequest.Builder(context).data(path).size(MAX_DECODE_PX).precision(Precision.INEXACT).build()
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .onSizeChanged { view = Vec(it.width.toFloat(), it.height.toFloat()) }
            .pointerInput(Unit) {
                detectTapGestures(onDoubleTap = { zoom = ZoomMath.doubleTap(zoom, fromCenter(it), photo, view) })
            }
            .pointerInput(Unit) {
                detectTransformGestures { centroid, pan, gestureZoom, _ ->
                    zoom = ZoomMath.transform(zoom, fromCenter(centroid), Vec(pan.x, pan.y), gestureZoom, photo, view)
                }
            },
    ) {
        AsyncImage(
            model = request,
            contentDescription = description,
            contentScale = ContentScale.Fit,
            onSuccess = { state -> state.painter.intrinsicSize.let { photo = Vec(it.width, it.height) } },
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = zoom.scale
                    scaleY = zoom.scale
                    translationX = zoom.offset.x
                    translationY = zoom.offset.y
                },
        )
        FilledIconButton(
            onClick = onClose,
            colors = IconButtonDefaults.filledIconButtonColors(containerColor = Color.Black.copy(alpha = 0.5f)),
            modifier = Modifier
                .align(Alignment.TopStart)
                .statusBarsPadding()
                .padding(8.dp),
        ) {
            Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.photo_close), tint = Color.White)
        }
    }
}

private const val MAX_DECODE_PX = 4096
