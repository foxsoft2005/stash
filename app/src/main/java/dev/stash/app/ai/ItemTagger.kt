package dev.stash.app.ai

import android.graphics.Bitmap
import android.graphics.BitmapFactory
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
            BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = sample })
        }

    private companion object {
        const val TAG = "SmartTagger"
        const val NANO_TIMEOUT_MS = 10_000L
    }
}
