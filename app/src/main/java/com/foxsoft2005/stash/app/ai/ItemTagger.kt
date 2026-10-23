package com.foxsoft2005.stash.app.ai

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.util.Log
import androidx.exifinterface.media.ExifInterface
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.cancellation.CancellationException

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
    private val consent: NanoConsentStore,
    private val appScope: CoroutineScope,
) {
    val nanoState: StateFlow<NanoState> get() = nano.state

    /**
     * Call once at startup. Checks Gemini Nano, but only downloads it if the user
     * already agreed (an earlier download got interrupted, or AICore evicted it).
     */
    suspend fun prepare() {
        val state = nano.refreshStatus()
        when {
            state == NanoState.READY -> nano.warmup()
            shouldDownloadOnStartup(state, consent.value) -> nano.download()
        }
    }

    /** True when this photo should come with the "download Gemini Nano?" question. */
    suspend fun needsNanoPrompt(): Boolean {
        // The startup check may not have finished yet (e.g. opened straight from the camera shortcut).
        val state = nano.state.value.takeUnless { it == NanoState.CHECKING } ?: nano.refreshStatus()
        return shouldAskForNano(state, consent.value)
    }

    /** User said yes: remember it and download in the background (outlives the screen). */
    fun acceptNano() {
        consent.value = NanoConsent.ACCEPTED
        appScope.launch { nano.download() }
    }

    /** User said no: ML Kit labels from now on; never asked again automatically. */
    fun declineNano() {
        consent.value = NanoConsent.DECLINED
    }

    /**
     * Gemini Nano first (null on timeout, error or no Nano), then ML Kit labels.
     * Cancellation (the user left the screen) is always rethrown: catching it
     * would keep running the fallback model for a screen that no longer exists.
     */
    suspend fun suggest(photoPath: String): TagSuggestion? {
        val bitmap = decodeSampled(photoPath, maxDim = 768) ?: return null
        return attempt("Gemini Nano") { withTimeoutOrNull(NANO_TIMEOUT_MS) { nano.suggest(bitmap) } }
            ?: attempt("ML Kit labels") { labels.suggest(bitmap) }
    }

    private suspend fun attempt(what: String, block: suspend () -> TagSuggestion?): TagSuggestion? =
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "$what tagging failed", e)
            null
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
