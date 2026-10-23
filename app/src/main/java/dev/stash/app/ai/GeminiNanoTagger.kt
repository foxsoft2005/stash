package dev.stash.app.ai

import android.graphics.Bitmap
import android.util.Log
import com.google.mlkit.genai.common.DownloadStatus
import com.google.mlkit.genai.common.FeatureStatus
import com.google.mlkit.genai.prompt.Generation
import com.google.mlkit.genai.prompt.ImagePart
import com.google.mlkit.genai.prompt.TextPart
import com.google.mlkit.genai.prompt.generateContentRequest
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.coroutines.cancellation.CancellationException

/**
 * Multimodal Gemini Nano through the ML Kit GenAI Prompt API (runs in AICore, on-device).
 * Returns null whenever Nano isn't ready, so callers fall back to ML Kit labels.
 */
class GeminiNanoTagger {

    private val model = Generation.getClient()

    @Volatile
    private var ready = false

    suspend fun prepare() {
        try {
            when (model.checkStatus()) {
                FeatureStatus.AVAILABLE -> {
                    ready = true
                    model.warmup() // loads Nano into memory so the first photo is fast
                }
                FeatureStatus.DOWNLOADABLE -> model.download().collect { status ->
                    when (status) {
                        is DownloadStatus.DownloadCompleted -> ready = true
                        is DownloadStatus.DownloadFailed ->
                            Log.w(TAG, "Gemini Nano download failed", status.e)
                        else -> Unit
                    }
                }
                // DOWNLOADING: AICore is already fetching it; UNAVAILABLE: unsupported device.
                else -> Unit
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Gemini Nano not available", e)
        }
    }

    suspend fun suggest(bitmap: Bitmap): TagSuggestion? {
        // Nano may have finished downloading since startup (AICore downloads in the background).
        if (!ready) ready = model.checkStatus() == FeatureStatus.AVAILABLE
        if (!ready) return null
        val response = model.generateContent(
            generateContentRequest(ImagePart(bitmap), TextPart(PROMPT)) {
                temperature = 0.1f
                maxOutputTokens = 120
            },
        )
        val raw = response.candidates.firstOrNull()?.text ?: return null
        val answer = parse(raw) ?: return null
        if (answer.name.isBlank()) return null
        return TagSuggestion(
            name = answer.name.trim().replaceFirstChar { it.uppercase() },
            tags = answer.tags.map { it.trim().lowercase() }.filter { it.isNotBlank() }.distinct(),
            source = TagSource.GEMINI_NANO,
        )
    }

    private fun parse(raw: String): NanoAnswer? {
        val start = raw.indexOf('{')
        val end = raw.lastIndexOf('}')
        if (start < 0 || end <= start) return null
        return runCatching { json.decodeFromString<NanoAnswer>(raw.substring(start, end + 1)) }.getOrNull()
    }

    @Serializable
    private data class NanoAnswer(val name: String = "", val tags: List<String> = emptyList())

    private companion object {
        const val TAG = "GeminiNanoTagger"
        val json = Json { ignoreUnknownKeys = true; isLenient = true }

        val PROMPT = """
            You help a person remember where they stored physical things.
            Look at the photo and identify the ONE main object being stored
            (ignore the drawer, box, shelf or hand holding it).
            Reply with JSON only, no other text:
            {"name": "1-3 word name of the object", "tags": ["4-6 lowercase search keywords: category, synonyms, material, color"]}
        """.trimIndent()
    }
}
