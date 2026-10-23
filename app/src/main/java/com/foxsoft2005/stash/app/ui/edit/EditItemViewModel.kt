package com.foxsoft2005.stash.app.ui.edit

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.foxsoft2005.stash.app.ai.NanoState
import com.foxsoft2005.stash.app.ai.SmartTagger
import com.foxsoft2005.stash.app.ai.TagSource
import com.foxsoft2005.stash.app.data.Item
import com.foxsoft2005.stash.app.data.ItemRepository
import com.foxsoft2005.stash.app.data.PhotoStore
import com.foxsoft2005.stash.app.data.Tags
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface AiState {
    data object Idle : AiState
    data object Thinking : AiState
    data class Suggested(val source: TagSource) : AiState
    data object NoIdea : AiState
}

/**
 * One form for both "new item from a fresh photo" and "view / edit an existing item".
 * Text fields use Compose's TextFieldState so the AI can fill them without fighting the user.
 */
class EditItemViewModel(
    private val repository: ItemRepository,
    private val tagger: SmartTagger,
    private val photos: PhotoStore,
    private val appScope: CoroutineScope,
    private val itemId: Long?,
    newPhotoPath: String?,
) : ViewModel() {

    val name = TextFieldState()
    val place = TextFieldState()
    val lentTo = TextFieldState()
    val note = TextFieldState()

    /** The "Add a keyword" field; its text becomes a tag on Enter/+ or on Save. */
    val newTag = TextFieldState()

    var tags by mutableStateOf(emptyList<String>())
        private set
    var photoPath by mutableStateOf(newPhotoPath)
        private set
    var aiState by mutableStateOf<AiState>(AiState.Idle)
        private set
    var showErrors by mutableStateOf(false)
        private set

    /** The one-time "download Gemini Nano?" dialog. */
    var showNanoPrompt by mutableStateOf(false)
        private set
    val nanoState: StateFlow<NanoState> = tagger.nanoState

    val isNew: Boolean get() = itemId == null

    val recentPlaces: StateFlow<List<String>> = repository.observeRecentPlaces()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private var original: Item? = null
    private var saved = false

    init {
        if (itemId != null) {
            viewModelScope.launch { load(itemId) }
        } else if (newPhotoPath != null) {
            // This photo is tagged with whatever is on the phone now (labels if Nano is
            // missing), so asking never delays the first suggestion.
            viewModelScope.launch { suggestFromPhoto(newPhotoPath) }
            viewModelScope.launch { if (tagger.needsNanoPrompt()) showNanoPrompt = true }
        }
    }

    private suspend fun load(id: Long) {
        val item = repository.get(id) ?: return
        original = item
        photoPath = item.photoPath
        name.setTextAndPlaceCursorAtEnd(item.name)
        place.setTextAndPlaceCursorAtEnd(item.place)
        lentTo.setTextAndPlaceCursorAtEnd(item.lentTo.orEmpty())
        note.setTextAndPlaceCursorAtEnd(item.note)
        tags = item.tagList
    }

    /** Runs while the user is already typing "where", so it never slows capture down. */
    private suspend fun suggestFromPhoto(path: String) {
        aiState = AiState.Thinking
        val suggestion = tagger.suggest(path)
        if (suggestion == null) {
            aiState = AiState.NoIdea
            return
        }
        // Don't overwrite anything the user typed while we were thinking.
        if (name.text.isBlank()) name.setTextAndPlaceCursorAtEnd(suggestion.name)
        tags = Tags.clean(tags + suggestion.tags)
        aiState = AiState.Suggested(suggestion.source)
    }

    fun usePlace(value: String) = place.setTextAndPlaceCursorAtEnd(value)

    fun removeTag(tag: String) {
        tags = tags - tag
    }

    /**
     * Adds whatever is in [newTag]. Commas split it into several keywords, so
     * "passport, travel" adds two, while "power tool" stays one.
     */
    fun addTag() {
        val typed = newTag.text.toString()
        if (typed.isBlank()) return
        tags = Tags.clean(tags + typed.split(Tags.SEPARATOR))
        newTag.clearText()
    }

    fun markReturned() = lentTo.setTextAndPlaceCursorAtEnd("")

    fun acceptNano() {
        tagger.acceptNano()
        showNanoPrompt = false
    }

    fun declineNano() {
        tagger.declineNano()
        showNanoPrompt = false
    }

    /** Tapped outside the dialog: no answer, so it's asked again with the next photo. */
    fun dismissNanoPrompt() {
        showNanoPrompt = false
    }

    /** "Get smarter suggestions" link: lets someone who said no change their mind. */
    fun offerNano() {
        showNanoPrompt = true
    }

    val canSave: Boolean get() = name.text.isNotBlank() && place.text.isNotBlank()

    fun save(onSaved: () -> Unit) {
        // A keyword typed but not yet added would otherwise be silently lost.
        addTag()
        if (!canSave) {
            showErrors = true
            return
        }
        viewModelScope.launch {
            val base = original ?: Item(name = "", place = "", photoPath = photoPath)
            repository.save(
                base.copy(
                    name = name.text.toString().trim(),
                    place = place.text.toString().trim(),
                    lentTo = lentTo.text.toString().trim().ifBlank { null },
                    note = note.text.toString().trim(),
                    tags = Tags.join(tags),
                ),
            )
            saved = true
            onSaved()
        }
    }

    fun delete(onDeleted: () -> Unit) {
        val item = original ?: return
        viewModelScope.launch {
            repository.delete(item)
            onDeleted()
        }
    }

    /** A new photo that was never saved shouldn't linger on disk. */
    override fun onCleared() {
        if (isNew && !saved) {
            val path = photoPath
            // viewModelScope is already cancelled here, so clean up on the app scope.
            appScope.launch { photos.delete(path) }
        }
    }
}
