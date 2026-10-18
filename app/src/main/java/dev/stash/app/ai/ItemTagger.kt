package dev.stash.app.ai

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

enum class TagSource { GEMINI_NANO, MLKIT_LABELS }

/** What the AI thinks is in the photo. The user can always edit it. */
data class TagSuggestion(
    val name: String,
    val tags: List<String>,
    val source: TagSource,
)

/**
 * Picks the best on-device option:
 *  1. Gemini Nano (via ML Kit GenAI Prompt API): understands the scene, gives a
 *     natural name + synonyms ("passport" → travel, document, id).
 *  2. ML Kit image labeling (bundled model, works on every device): coarse labels.
 * Nothing ever leaves the phone.
 */
class SmartTagger(
    private val nano: GeminiNanoTagger,
    private val labels: MlKitLabelTagger,
) {
    /** Call once at startup: checks Gemini Nano and starts its download if needed. */
    suspend fun prepare() = nano.prepare()

    suspend fun suggest(photoPath: String): TagSuggestion? {
        val bitmap = decodeSampled(photoPath, maxDim = 768) ?: return null
        return try {
            withTimeoutOrNull(NANO_TIMEOUT_MS) { nano.suggest(bitmap) }
                ?: labels.suggest(bitmap)
        } catch (e: Exception) {
            Log.w(TAG, "Tagging failed", e)
            runCatching { labels.suggest(bitmap) }.getOrNull()
        }
    }

    private suspend fun decodeSampled(path: String, maxDim: Int): Bitmap? =
        withContext(Dispatchers.IO) {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(path, bounds)
            var sample = 1
            while (bounds.outWidth / (sample * 2) >= maxDim && bounds.outHeight / (sample * 2) >= maxDim) {
                sample *= 2
            }
            val bitmap = BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = sample })
                ?: return@withContext null
            rotateUpright(bitmap, exifRotationDegrees(path))
        }

    /**
     * CameraX saves JPEGs in sensor orientation plus an EXIF "rotate me" flag, and
     * BitmapFactory ignores that flag. Without this the AI often sees the photo
     * sideways, which noticeably hurts ML Kit labels. (Coil already honors EXIF,
     * which is why the photos look right on screen.)
     */
    private fun exifRotationDegrees(path: String): Int =
        runCatching {
            exifOrientationToDegrees(
                ExifInterface(path).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL),
            )
        }.getOrDefault(0)

    private fun rotateUpright(bitmap: Bitmap, degrees: Int): Bitmap {
        if (degrees == 0) return bitmap
        val matrix = Matrix().apply { postRotate(degrees.toFloat()) }
        val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        if (rotated !== bitmap) bitmap.recycle()
        return rotated
    }

    private companion object {
        const val TAG = "SmartTagger"
        const val NANO_TIMEOUT_MS = 10_000L
    }
}

/**
 * EXIF orientation → clockwise degrees to turn the image upright.
 * Mirrored variants (TRANSPOSE/TRANSVERSE, FLIP_*) only get their rotation:
 * the back camera never mirrors, and a mirror image doesn't change what the
 * object *is*, which is all the tagger cares about.
 */
internal fun exifOrientationToDegrees(orientation: Int): Int = when (orientation) {
    ExifInterface.ORIENTATION_ROTATE_90, ExifInterface.ORIENTATION_TRANSPOSE -> 90
    ExifInterface.ORIENTATION_ROTATE_180, ExifInterface.ORIENTATION_FLIP_VERTICAL -> 180
    ExifInterface.ORIENTATION_ROTATE_270, ExifInterface.ORIENTATION_TRANSVERSE -> 270
    else -> 0
}
