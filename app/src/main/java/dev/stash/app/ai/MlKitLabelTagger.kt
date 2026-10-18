package dev.stash.app.ai

import android.graphics.Bitmap
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.label.ImageLabeling
import com.google.mlkit.vision.label.defaults.ImageLabelerOptions
import kotlinx.coroutines.tasks.await

/** Fallback for phones without Gemini Nano: bundled ML Kit model, ~400 generic labels. */
class MlKitLabelTagger {

    private val labeler = ImageLabeling.getClient(
        ImageLabelerOptions.Builder().setConfidenceThreshold(0.55f).build(),
    )

    suspend fun suggest(bitmap: Bitmap): TagSuggestion? {
        val labels = labeler.process(InputImage.fromBitmap(bitmap, 0)).await()
            .sortedByDescending { it.confidence }
            .map { it.text.lowercase() }
        if (labels.isEmpty()) return null
        return TagSuggestion(
            name = labels.first().replaceFirstChar { it.uppercase() },
            tags = labels.take(6),
            source = TagSource.MLKIT_LABELS,
        )
    }
}
