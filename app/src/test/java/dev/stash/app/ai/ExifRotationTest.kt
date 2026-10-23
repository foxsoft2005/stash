package dev.stash.app.ai

import androidx.exifinterface.media.ExifInterface
import org.junit.Assert.assertEquals
import org.junit.Test

class ExifRotationTest {

    @Test fun `upright photos are left alone`() {
        assertEquals(0, exifOrientationToDegrees(ExifInterface.ORIENTATION_NORMAL))
        assertEquals(0, exifOrientationToDegrees(ExifInterface.ORIENTATION_UNDEFINED))
    }

    @Test fun `portrait shots from CameraX are turned upright`() {
        assertEquals(90, exifOrientationToDegrees(ExifInterface.ORIENTATION_ROTATE_90))
        assertEquals(180, exifOrientationToDegrees(ExifInterface.ORIENTATION_ROTATE_180))
        assertEquals(270, exifOrientationToDegrees(ExifInterface.ORIENTATION_ROTATE_270))
    }

    @Test fun `mirrored variants still get their rotation`() {
        assertEquals(90, exifOrientationToDegrees(ExifInterface.ORIENTATION_TRANSPOSE))
        assertEquals(270, exifOrientationToDegrees(ExifInterface.ORIENTATION_TRANSVERSE))
        assertEquals(180, exifOrientationToDegrees(ExifInterface.ORIENTATION_FLIP_VERTICAL))
        assertEquals(0, exifOrientationToDegrees(ExifInterface.ORIENTATION_FLIP_HORIZONTAL))
    }
}
