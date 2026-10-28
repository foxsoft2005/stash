package io.github.foxsoft2005.stash.ui.photo

import kotlin.math.max
import kotlin.math.min

/** A point or size in pixels; offsets are measured from the center of the view. */
data class Vec(val x: Float, val y: Float) {
    operator fun plus(o: Vec) = Vec(x + o.x, y + o.y)
    operator fun minus(o: Vec) = Vec(x - o.x, y - o.y)
    operator fun times(f: Float) = Vec(x * f, y * f)

    companion object {
        val Zero = Vec(0f, 0f)
    }
}

/** How far the photo is zoomed in ([scale]) and moved ([offset], from the view's center). */
data class Zoom(val scale: Float = 1f, val offset: Vec = Vec.Zero) {
    val isZoomed: Boolean get() = scale > 1.01f

    companion object {
        const val MAX_SCALE = 5f
        const val DOUBLE_TAP_SCALE = 2.5f
    }
}

/**
 * Pinch-to-zoom maths, kept free of Android so it can be unit-tested. [view] is the
 * screen area, [photo] the photo's own size; it's shown fitted (letterboxed) first.
 */
object ZoomMath {

    /** The photo's size on screen at scale 1 (fitted inside [view], aspect kept). */
    fun fitted(photo: Vec, view: Vec): Vec {
        if (photo.x <= 0f || photo.y <= 0f) return view
        val ratio = min(view.x / photo.x, view.y / photo.y)
        return Vec(photo.x * ratio, photo.y * ratio)
    }

    /**
     * Applies one pinch/drag step: zoom by [zoomBy] around [centroid] (from the view's
     * center, so the point under your fingers stays put), then move by [pan].
     */
    fun transform(z: Zoom, centroid: Vec, pan: Vec, zoomBy: Float, photo: Vec, view: Vec): Zoom {
        val scale = (z.scale * zoomBy).coerceIn(1f, Zoom.MAX_SCALE)
        val k = scale / z.scale
        val offset = centroid - (centroid - z.offset) * k + pan
        return clamp(Zoom(scale, offset), photo, view)
    }

    /** Double tap: zoom in on the tapped point, or back out if already zoomed. */
    fun doubleTap(z: Zoom, at: Vec, photo: Vec, view: Vec): Zoom =
        if (z.isZoomed) Zoom() else transform(z, at, Vec.Zero, Zoom.DOUBLE_TAP_SCALE / z.scale, photo, view)

    /** Keeps the photo's edges from being dragged inside the screen (no empty margins). */
    fun clamp(z: Zoom, photo: Vec, view: Vec): Zoom {
        val shown = fitted(photo, view) * z.scale
        val maxX = max(0f, (shown.x - view.x) / 2f)
        val maxY = max(0f, (shown.y - view.y) / 2f)
        // "+ 0f" turns -0.0 into 0.0, so an unmoved photo always compares equal to Vec.Zero.
        return z.copy(offset = Vec(z.offset.x.coerceIn(-maxX, maxX) + 0f, z.offset.y.coerceIn(-maxY, maxY) + 0f))
    }
}
