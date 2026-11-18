package com.postpole.spoonfile.ui.importer

import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.postpole.spoonfile.analytics.Analytics
import com.postpole.spoonfile.analytics.AnalyticsEvents
import com.postpole.spoonfile.analytics.AnalyticsParams
import com.postpole.spoonfile.billing.BillingRepository
import com.postpole.spoonfile.data.CategoryDao
import com.postpole.spoonfile.data.CategoryEntity
import com.postpole.spoonfile.data.RecipeEntity
import com.postpole.spoonfile.data.RecipeOrigin
import com.postpole.spoonfile.data.RecipeRepository
import com.postpole.spoonfile.importer.web.FetchResult
import com.postpole.spoonfile.importer.web.ImportedLine
import com.postpole.spoonfile.importer.web.ImportedRecipe
import com.postpole.spoonfile.importer.web.JsonLdRecipeParser
import com.postpole.spoonfile.importer.web.SharedTextUrl
import com.postpole.spoonfile.importer.web.WebPageFetcher
import com.postpole.spoonfile.ui.recipe.RecipeEditForm
import com.postpole.spoonfile.ui.recipe.applyTo
import com.postpole.spoonfile.ui.recipe.ingredientEntities
import com.postpole.spoonfile.ui.recipe.linesToText
import com.postpole.spoonfile.ui.recipe.stepEntities
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID
import javax.inject.Inject

sealed interface ImportState {
    /** Waiting for a link. [invalid] flags a link that can't be imported. */
    data class Input(val invalid: Boolean = false) : ImportState

    /** Looking for an already-saved copy, before anything is fetched. */
    data object Checking : ImportState

    /** The link's page is already saved; asks whether to open it or save another copy. */
    data class Duplicate(val existing: RecipeEntity) : ImportState

    data object Loading : ImportState

    /** A page was read; the editable preview is shown. Nothing is saved yet. */
    data class Preview(val suggestedCategories: List<String>) : ImportState

    /** The page didn't load. [canRetry] is false when retrying can't help (page too big). */
    data class LoadFailed(val canRetry: Boolean) : ImportState

    /** The page loaded but holds no recipe data. */
    data object NoRecipe : ImportState

    /** The pasted page text is on the cleanup screen (the same one the page scan uses). */
    data object Cleanup : ImportState
}

sealed interface ImportEvent {
    data class OpenRecipe(val id: Long) : ImportEvent
    data class Saved(val id: Long) : ImportEvent
    data object OpenPro : ImportEvent
    data object TypeItIn : ImportEvent
    data object SaveFailed : ImportEvent
}

@HiltViewModel
class ImportLinkViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: RecipeRepository,
    private val categoryDao: CategoryDao,
    private val billing: BillingRepository,
    private val fetcher: WebPageFetcher,
    private val analytics: Analytics,
) : ViewModel() {
    private val _state = MutableStateFlow<ImportState>(ImportState.Input())
    val state: StateFlow<ImportState> = _state

    private val _input = MutableStateFlow("")
    val input: StateFlow<String> = _input

    private val _pasted = MutableStateFlow("")
    val pastedText: StateFlow<String> = _pasted

    private val _form = MutableStateFlow(RecipeEditForm(loaded = true))
    val form: StateFlow<RecipeEditForm> = _form

    private val _photo = MutableStateFlow<ImageBitmap?>(null)
    val photo: StateFlow<ImageBitmap?> = _photo

    private val _selectedSuggestions = MutableStateFlow<Set<String>>(emptySet())
    val selectedSuggestions: StateFlow<Set<String>> = _selectedSuggestions

    val categories: StateFlow<List<CategoryEntity>> = categoryDao.observeAllWithCounts()
        .map { list -> list.map { it.category } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _events = Channel<ImportEvent>(Channel.BUFFERED)
    val events: Flow<ImportEvent> = _events.receiveAsFlow()

    /** The validated https link being imported. */
    private var currentUrl: String? = null
    private var photoBytes: ByteArray? = null
    private var origin = RecipeOrigin.WEB
    private var job: Job? = null

    init {
        // A shared link arrives as a route argument; start straight away since sharing is the user's own action.
        val shared = savedStateHandle.get<String>("url").orEmpty()
        if (shared.isNotEmpty()) {
            _input.value = shared
            submit()
        }
    }

    suspend fun loadPhoto(name: String, maxEdge: Int): ImageBitmap? = repository.loadPhoto(name, maxEdge)?.asImageBitmap()

    fun onInputChange(value: String) {
        _input.value = value
        if ((_state.value as? ImportState.Input)?.invalid == true) _state.value = ImportState.Input()
    }

    /** Validate the link, check for a saved copy, then fetch. */
    fun submit() {
        val url = SharedTextUrl.toImportUrl(_input.value)
        if (url == null) {
            _state.value = ImportState.Input(invalid = true)
            return
        }
        currentUrl = url
        _state.value = ImportState.Checking
        job?.cancel()
        job = viewModelScope.launch {
            val existing = repository.findBySourceUrl(url)
            if (existing != null) _state.value = ImportState.Duplicate(existing) else load(url)
        }
    }

    fun openExisting() {
        val existing = (_state.value as? ImportState.Duplicate)?.existing ?: return
        _events.trySend(ImportEvent.OpenRecipe(existing.id))
    }

    fun saveCopy() {
        val url = currentUrl ?: return
        if (_state.value !is ImportState.Duplicate) return
        job?.cancel()
        job = viewModelScope.launch { load(url) }
    }

    fun dismissDuplicate() {
        if (_state.value is ImportState.Duplicate) _state.value = ImportState.Input()
    }

    fun retry() {
        val url = currentUrl ?: return
        job?.cancel()
        job = viewModelScope.launch { load(url) }
    }

    /** Stops the fetch and returns to the link box. */
    fun cancel() {
        job?.cancel()
        _state.value = ImportState.Input()
    }

    fun onPastedTextChange(value: String) {
        _pasted.value = value
    }

    /** "Open the page and copy the text": the pasted text goes to the cleanup screen. */
    fun usePastedText() {
        if (_pasted.value.isBlank()) return
        _state.value = ImportState.Cleanup
    }

    /** Back from the cleanup screen to the paste box. */
    fun closeCleanup() {
        if (_state.value == ImportState.Cleanup) _state.value = ImportState.NoRecipe
    }

    /** The cleaned-up text (pasted or scanned) becomes the editable preview. Nothing is saved yet. */
    fun startFromCleanup(cleaned: RecipeEditForm, photo: ByteArray?, fromOrigin: RecipeOrigin) {
        origin = fromOrigin
        val preview = photo?.let { decodePreview(it) }
        photoBytes = if (preview != null) photo else null
        _photo.value = preview
        _selectedSuggestions.value = emptySet()
        val fromPaste = fromOrigin == RecipeOrigin.PASTE
        _form.value = cleaned.copy(
            sourceName = if (fromPaste) hostOf(currentUrl).orEmpty() else "",
            sourceUrl = if (fromPaste) currentUrl.orEmpty() else "",
            loaded = true,
        )
        _state.value = ImportState.Preview(emptyList())
    }

    fun typeItIn() {
        _events.trySend(ImportEvent.TypeItIn)
    }

    // ---- fetch + parse ----

    private suspend fun load(url: String) {
        _state.value = ImportState.Loading
        origin = RecipeOrigin.WEB
        try {
            when (val result = fetcher.fetchPage(url)) {
                is FetchResult.Page -> readPage(url, result)
                is FetchResult.HttpError -> fail(RESULT_HTTP, canRetry = true)
                FetchResult.Timeout -> fail(RESULT_TIMEOUT, canRetry = true)
                FetchResult.TooBig -> fail(RESULT_TOO_BIG, canRetry = false)
                FetchResult.Insecure -> fail(RESULT_HTTP, canRetry = false)
                FetchResult.NotHtml -> noRecipe()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // Never crash on a strange page, and never report its content: just treat it as not loaded.
            fail(RESULT_HTTP, canRetry = true)
        }
    }

    private suspend fun readPage(url: String, page: FetchResult.Page) {
        val recipe = withContext(Dispatchers.Default) {
            try {
                JsonLdRecipeParser.parse(page.html, page.finalUrl)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                null
            }
        }
        if (recipe == null) {
            noRecipe()
            return
        }
        // The photo is optional: any problem with it just means no photo.
        photoBytes = null
        _photo.value = null
        recipe.imageUrl?.let { imageUrl ->
            val bytes = fetcher.fetchImage(imageUrl)
            val preview = bytes?.let { withContext(Dispatchers.Default) { decodePreview(it) } }
            if (preview != null) {
                photoBytes = bytes
                _photo.value = preview
            }
        }
        _selectedSuggestions.value = emptySet()
        _form.value = recipe.toForm(url)
        logResult(RESULT_OK)
        _state.value = ImportState.Preview(recipe.suggestedCategories)
    }

    private fun noRecipe() {
        logResult(RESULT_NO_RECIPE)
        _pasted.value = ""
        _state.value = ImportState.NoRecipe
    }

    private fun fail(result: String, canRetry: Boolean) {
        logResult(result)
        _state.value = ImportState.LoadFailed(canRetry)
    }

    private fun logResult(result: String) {
        analytics.logEvent(
            AnalyticsEvents.RECIPE_IMPORT,
            mapOf(AnalyticsParams.IMPORT_SOURCE to SOURCE_WEB, AnalyticsParams.RESULT to result),
        )
    }

    // ---- preview editing + save ----

    fun edit(change: (RecipeEditForm) -> RecipeEditForm) = _form.update(change)

    fun setTitle(value: String) = _form.update { it.copy(title = value, titleError = false) }

    fun setRating(value: Int) = _form.update { it.copy(rating = value.coerceIn(0, 5)) }

    fun toggleCategory(id: Long) = _form.update {
        it.copy(categoryIds = if (id in it.categoryIds) it.categoryIds - id else it.categoryIds + id)
    }

    fun createCategory(name: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch {
            val id = categoryDao.getOrCreate(trimmed)
            _form.update { it.copy(categoryIds = it.categoryIds + id) }
        }
    }

    fun toggleSuggestion(name: String) = _selectedSuggestions.update { if (name in it) it - name else it + name }

    fun removePhoto() {
        photoBytes = null
        _photo.value = null
    }

    /** Leaving the preview discards it; nothing was written. */
    fun discard() {
        job?.cancel()
        photoBytes = null
        _photo.value = null
    }

    /** Back from a scanned preview: discards it and leaves [ImportState.Preview] so the caller shows cleanup again. */
    fun closePreview() {
        discard()
        if (_state.value is ImportState.Preview) _state.value = ImportState.Input()
    }

    fun save() {
        val f = _form.value
        if (f.saving) return
        if (f.title.isBlank()) {
            _form.update { it.copy(titleError = true) }
            return
        }
        _form.update { it.copy(saving = true) }
        viewModelScope.launch {
            if (!repository.canAdd(billing.isPro.value)) {
                _form.update { it.copy(saving = false) }
                _events.trySend(ImportEvent.OpenPro)
                return@launch
            }
            var storedPhoto: String? = null
            try {
                storedPhoto = photoBytes?.let { bytes -> runCatching { repository.savePhoto(bytes) }.getOrNull() }
                val categoryIds = f.categoryIds + _selectedSuggestions.value.map { categoryDao.getOrCreate(it) }
                val now = System.currentTimeMillis()
                val base = RecipeEntity(uid = UUID.randomUUID().toString(), title = "", origin = origin, createdAt = now, updatedAt = now)
                val withPhoto = f.copy(imageFile = storedPhoto)
                val id = repository.upsertWithChildren(
                    withPhoto.applyTo(base, now),
                    withPhoto.ingredientEntities(),
                    withPhoto.stepEntities(),
                    categoryIds.toList(),
                )
                photoBytes = null
                _events.trySend(ImportEvent.Saved(id))
            } catch (e: CancellationException) {
                storedPhoto?.let { repository.deleteImage(it) }
                throw e
            } catch (_: Exception) {
                // Never leave half a recipe behind: the photo file goes too.
                storedPhoto?.let { repository.deleteImage(it) }
                _form.update { it.copy(saving = false) }
                _events.trySend(ImportEvent.SaveFailed)
            }
        }
    }

    private companion object {
        const val SOURCE_WEB = "web"
        const val RESULT_OK = "ok"
        const val RESULT_NO_RECIPE = "no_recipe"
        const val RESULT_HTTP = "http_error"
        const val RESULT_TIMEOUT = "timeout"
        const val RESULT_TOO_BIG = "too_big"
        const val PREVIEW_EDGE = 1000

        fun hostOf(url: String?): String? = try {
            url?.let { java.net.URI(it).host?.removePrefix("www.") }
        } catch (_: java.net.URISyntaxException) {
            null
        }

        fun decodePreview(bytes: ByteArray): ImageBitmap? {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
            var sample = 1
            var edge = maxOf(bounds.outWidth, bounds.outHeight)
            while (edge / 2 >= PREVIEW_EDGE) {
                sample *= 2
                edge /= 2
            }
            val opts = BitmapFactory.Options().apply { inSampleSize = sample }
            return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)?.asImageBitmap()
        }
    }
}

private fun ImportedRecipe.toForm(url: String): RecipeEditForm {
    fun List<ImportedLine>.asText() = linesToText(map { it.text to it.isHeader })
    return RecipeEditForm(
        title = title,
        description = description.orEmpty(),
        servings = servings?.let { if (it == it.toLong().toDouble()) it.toLong().toString() else it.toString() }
            ?: servingsText.orEmpty(),
        prepMinutes = prepMinutes?.toString().orEmpty(),
        cookMinutes = cookMinutes?.toString().orEmpty(),
        totalMinutes = totalMinutes?.toString().orEmpty(),
        ingredients = ingredients.asText(),
        steps = steps.asText(),
        nutrition = nutritionText.orEmpty(),
        sourceName = sourceName.orEmpty(),
        sourceUrl = url,
        loaded = true,
    )
}
