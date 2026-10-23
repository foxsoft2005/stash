package io.github.foxsoft2005.stash.ai

import android.graphics.Bitmap
import android.util.Log
import com.google.mlkit.genai.common.DownloadStatus
import com.google.mlkit.genai.common.FeatureStatus
import com.google.mlkit.genai.prompt.Generation
import com.google.mlkit.genai.prompt.ImagePart
import com.google.mlkit.genai.prompt.TextPart
import com.google.mlkit.genai.prompt.generateContentRequest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.cancellation.CancellationException

/**
 * Multimodal Gemini Nano through the ML Kit GenAI Prompt API (runs in AICore, on-device).
 * Returns null whenever Nano isn't ready, so callers fall back to ML Kit labels.
 *
 * Never downloads on its own: the model is a large download, so [download] is
 * only called after the user agreed (see [SmartTagger] and [NanoConsent]).
 */
class GeminiNanoTagger {

    // Created on first use (not at app start) and allowed to fail: Gemini Nano is
    // an optional extra, so a broken or missing ML Kit GenAI must never take the
    // whole app down. null = treat Nano as unavailable on this phone.
    private val model by lazy {
        try {
            Generation.getClient()
        } catch (e: Exception) {
            Log.w(TAG, "Gemini Nano client unavailable", e)
            null
        }
    }

    private val _state = MutableStateFlow(NanoState.CHECKING)
    val state: StateFlow<NanoState> = _state.asStateFlow()

    private val downloadInFlight = AtomicBoolean(false)

    /** Asks AICore where Nano stands. Cheap; safe to call often. */
    suspend fun refreshStatus(): NanoState {
        val status = try {
            when (model?.checkStatus()) { // null client -> else -> UNAVAILABLE
                FeatureStatus.AVAILABLE -> NanoState.READY
                FeatureStatus.DOWNLOADABLE -> NanoState.NEEDS_DOWNLOAD
                FeatureStatus.DOWNLOADING -> NanoState.DOWNLOADING
                else -> NanoState.UNAVAILABLE
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Gemini Nano status check failed", e)
            NanoState.UNAVAILABLE
        }
        // While our own download runs, AICore can briefly still report DOWNLOADABLE; keep showing DOWNLOADING.
        if (!(downloadInFlight.get() && status == NanoState.NEEDS_DOWNLOAD)) _state.value = status
        return _state.value
    }

    /** Loads Nano into memory so the first photo is fast. No-op unless it's on the phone. */
    suspend fun warmup() {
        if (_state.value != NanoState.READY) return
        try {
            model?.warmup()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Gemini Nano warmup failed", e)
        }
    }

    /**
     * Downloads Nano through AICore. Only call after the user agreed. Suspends until
     * the download finishes or fails; a second call while one is running is ignored.
     */
    suspend fun download() {
        val client = model ?: return
        if (!downloadInFlight.compareAndSet(false, true)) return
        try {
            _state.value = NanoState.DOWNLOADING
            client.download().collect { status ->
                when (status) {
                    is DownloadStatus.DownloadCompleted -> _state.value = NanoState.READY
                    is DownloadStatus.DownloadFailed -> {
                        Log.w(TAG, "Gemini Nano download failed", status.e)
                        _state.value = NanoState.NEEDS_DOWNLOAD
                    }
                    else -> Unit
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Gemini Nano download failed", e)
            _state.value = NanoState.NEEDS_DOWNLOAD
        } finally {
            downloadInFlight.set(false)
        }
        warmup()
    }

    suspend fun suggest(bitmap: Bitmap): TagSuggestion? {
        // AICore may have finished a download in the background since we last looked.
        if (_state.value != NanoState.READY && refreshStatus() != NanoState.READY) return null
        val client = model ?: return null
        val response = client.generateContent(
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
