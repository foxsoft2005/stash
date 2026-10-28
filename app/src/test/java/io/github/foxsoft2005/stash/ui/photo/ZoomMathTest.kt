package io.github.foxsoft2005.stash.ui.photo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ZoomMathTest {

    private val view = Vec(1000f, 2000f)
    private val photo = Vec(4000f, 3000f) // landscape photo on a portrait screen

    @Test fun `fits inside the screen keeping the shape`() {
        assertEquals(Vec(1000f, 750f), ZoomMath.fitted(photo, view))
    }

    @Test fun `zoom stays between 1x and 5x`() {
        assertEquals(1f, ZoomMath.transform(Zoom(), Vec.Zero, Vec.Zero, 0.2f, photo, view).scale)
        assertEquals(Zoom.MAX_SCALE, ZoomMath.transform(Zoom(), Vec.Zero, Vec.Zero, 50f, photo, view).scale)
    }

    @Test fun `the point under the fingers stays put`() {
        // Pinch 2x around a point 200px right of center: that point must not move.
        val z = ZoomMath.transform(Zoom(), Vec(200f, 0f), Vec.Zero, 2f, photo, view)
        val pointBefore = 200f
        val photoPointUnderFingers = (200f - 0f) / 1f // where it was on the photo at 1x
        assertEquals(pointBefore, z.offset.x + photoPointUnderFingers * z.scale, 0.01f)
    }

    @Test fun `can't drag the photo off its edges`() {
        val z = ZoomMath.transform(Zoom(scale = 2f), Vec.Zero, Vec(5000f, 5000f), 1f, photo, view)
        // 2x: 2000x1500 on a 1000x2000 screen -> may move 500px sideways, not at all vertically.
        assertEquals(Vec(500f, 0f), z.offset)
    }

    @Test fun `at 1x it can't move at all`() {
        assertEquals(Vec.Zero, ZoomMath.transform(Zoom(), Vec.Zero, Vec(300f, -300f), 1f, photo, view).offset)
    }

    @Test fun `double tap zooms in, double tap again zooms out`() {
        val zoomed = ZoomMath.doubleTap(Zoom(), Vec(100f, 0f), photo, view)
        assertEquals(Zoom.DOUBLE_TAP_SCALE, zoomed.scale)
        assertTrue(zoomed.isZoomed)
        val back = ZoomMath.doubleTap(zoomed, Vec.Zero, photo, view)
        assertFalse(back.isZoomed)
        assertEquals(Vec.Zero, back.offset)
    }

    @Test fun `unknown photo size falls back to the screen size`() {
        assertEquals(view, ZoomMath.fitted(Vec.Zero, view))
    }
}
