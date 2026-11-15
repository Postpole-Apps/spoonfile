package com.postpole.spoonfile.ui.importer

import android.net.Uri
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
import com.postpole.spoonfile.data.IngredientEntity
import com.postpole.spoonfile.data.RecipeEntity
import com.postpole.spoonfile.data.RecipeOrigin
import com.postpole.spoonfile.data.RecipeRepository
import com.postpole.spoonfile.data.StepEntity
import com.postpole.spoonfile.importer.web.ImportedRecipe
import com.postpole.spoonfile.importer.web.JsonLdRecipeParser
import com.postpole.spoonfile.importer.web.PageResult
import com.postpole.spoonfile.importer.web.SharedTextUrl
import com.postpole.spoonfile.importer.web.WebPageFetcher
import com.postpole.spoonfile.ui.recipe.linesToText
import com.postpole.spoonfile.ui.recipe.parseLines
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import java.util.UUID
import javax.inject.Inject

/** What the Import-from-link screen is showing. */
sealed interface ImportUiState {
    data class Input(val invalid: Boolean = false) : ImportUiState

    /** The same link is already saved (screen 06). */
    data class Duplicate(val recipe: RecipeEntity) : ImportUiState
    data object Loading : ImportUiState

    /** No recipe data on the page; offers pasting the page text or typing it in. */
    data object NoRecipe : ImportUiState

    /** Screen 05. [tooBig] uses the same card with its own message and no Retry. */
    data class Failed(val tooBig: Boolean, val site: String?) : ImportUiState
    data object Preview : ImportUiState
}

data class SuggestedCategory(val name: String, val ticked: Boolean = false)

/** The editable Import preview. Nothing in it is saved until the user taps Save. */
data class ImportPreviewForm(
    val title: String = "",
    val description: String = "",
    val servings: String = "",
    val prepMinutes: String = "",
    val cookMinutes: String = "",
    val totalMinutes: String = "",
    val ingredients: String = "",
    val steps: String = "",
    val nutrition: String = "",
    val sourceName: String = "",
    val sourceUrl: String = "",
    val categories: List<SuggestedCategory> = emptyList(),
    val titleError: Boolean = false,
    val saving: Boolean = false,
)

sealed interface ImportEvent {
    data class Saved(val id: Long) : ImportEvent
    data object OpenPro : ImportEvent
    data object SaveFailed : ImportEvent
    data object TypeItIn : ImportEvent
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
    private val _link = MutableStateFlow("")
    val link: StateFlow<String> = _link

    private val _state = MutableStateFlow<ImportUiState>(ImportUiState.Input())
    val state: StateFlow<ImportUiState> = _state

    private val _form = MutableStateFlow(ImportPreviewForm())
    val form: StateFlow<ImportPreviewForm> = _form

    /** Downloaded photo, held in memory and only written to disk when the user saves. */
    private val _photo = MutableStateFlow<ByteArray?>(null)
    val photo: StateFlow<ByteArray?> = _photo

    private val _clipboardUrl = MutableStateFlow<String?>(null)
    val clipboardUrl: StateFlow<String?> = _clipboardUrl

    private val _events = Channel<ImportEvent>(Channel.BUFFERED)
    val events: Flow<ImportEvent> = _events.receiveAsFlow()

    private var job: Job? = null
    private var currentUrl: String? = null

    init {
        // A link arriving from the share sheet is an explicit user action, so it starts straight away.
        val initial = savedStateHandle.get<String>("url")?.let(Uri::decode).orEmpty()
        if (initial.isNotBlank()) {
            _link.value = initial
            submit()
        }
    }

    suspend fun loadPhoto(name: String, maxEdge: Int): ImageBitmap? = repository.loadPhoto(name, maxEdge)?.asImageBitmap()

    fun setLink(value: String) {
        _link.value = value.take(SharedTextUrl.MAX_INPUT_CHARS)
        if ((_state.value as? ImportUiState.Input)?.invalid == true) _state.value = ImportUiState.Input()
    }

    /** Offers a link found on the clipboard; never fetches it. */
    fun onClipboardText(text: String?) {
        _clipboardUrl.value = SharedTextUrl.extract(text)
    }

    fun useClipboardLink() {
        _clipboardUrl.value?.let { _link.value = it }
        _clipboardUrl.value = null
    }

    /** Validates the link, checks for an already-saved copy, then fetches. */
    fun submit() {
        val url = SharedTextUrl.extract(_link.value)
        if (url == null) {
            _state.value = ImportUiState.Input(invalid = true)
            return
        }
        val https = SharedTextUrl.toHttps(url)
        currentUrl = https
        job?.cancel()
        job = viewModelScope.launch {
            val saved = repository.findBySourceUrl(https) ?: if (url != https) repository.findBySourceUrl(url) else null
            if (saved != null) {
                _state.value = ImportUiState.Duplicate(saved)
            } else {
                load(https)
            }
        }
    }

    /** "Save copy" on the duplicate dialog: carry on as a separate recipe. */
    fun importAnyway() {
        val url = currentUrl ?: return
        job?.cancel()
        job = viewModelScope.launch { load(url) }
    }

    fun dismissDuplicate() {
        _state.value = ImportUiState.Input()
    }

    fun retry() {
        val url = currentUrl ?: return submit()
        job?.cancel()
        job = viewModelScope.launch { load(url) }
    }

    fun cancel() {
        job?.cancel()
        job = null
        _state.value = ImportUiState.Input()
    }

    /** Back from the preview or an error to the link box. */
    fun backToInput() {
        _photo.value = null
        _state.value = ImportUiState.Input()
    }

    private suspend fun load(url: String) {
        _state.value = ImportUiState.Loading
        val site = SharedTextUrl.host(url)
        when (val page = fetcher.fetchPage(url)) {
            is PageResult.Page -> {
                val recipe = withContext(Dispatchers.Default) { JsonLdRecipeParser.parse(page.html, page.finalUrl) }
                if (recipe == null) {
                    log("no_recipe")
                    _state.value = ImportUiState.NoRecipe
                    return
                }
                // The photo is optional: any failure just leaves the recipe without one.
                _photo.value = recipe.imageUrl?.let { fetcher.fetchImage(it) }
                _form.value = recipe.toForm(sourceUrl = url)
                log("ok")
                _state.value = ImportUiState.Preview
            }
            is PageResult.TooBig -> fail("too_big", tooBig = true, site = site)
            is PageResult.Timeout -> fail("timeout", tooBig = false, site = site)
            is PageResult.HttpError, PageResult.Blocked, PageResult.Unreachable -> fail("http_error", tooBig = false, site = site)
        }
    }

    private fun fail(result: String, tooBig: Boolean, site: String?) {
        log(result)
        _state.value = ImportUiState.Failed(tooBig, site)
    }

    /** Only the import source and a result code are logged: never URLs, domains or recipe text. */
    private fun log(result: String) {
        analytics.logEvent(
            AnalyticsEvents.RECIPE_IMPORT,
            mapOf(AnalyticsParams.IMPORT_SOURCE to "web", AnalyticsParams.RESULT to result),
        )
    }

    /**
     * Stub for R5: the page had no recipe data, so the user pasted the page text. R5 swaps in the shared
     * OCR line cleanup; for now each non-blank line becomes a step the user can sort out in the preview.
     */
    fun usePastedText(text: String) {
        val lines = PastedTextStub.cleanLines(text)
        if (lines.isEmpty()) return
        _photo.value = null
        _form.value = ImportPreviewForm(
            steps = lines.joinToString("\n"),
            sourceName = currentUrl?.let(SharedTextUrl::host).orEmpty(),
            sourceUrl = currentUrl.orEmpty(),
        )
        _state.value = ImportUiState.Preview
    }

    fun typeItIn() {
        _events.trySend(ImportEvent.TypeItIn)
    }

    fun edit(change: (ImportPreviewForm) -> ImportPreviewForm) = _form.update(change)

    fun toggleCategory(name: String) = _form.update { f ->
        f.copy(categories = f.categories.map { if (it.name == name) it.copy(ticked = !it.ticked) else it })
    }

    fun removePhoto() {
        _photo.value = null
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
            var imageFile: String? = null
            try {
                imageFile = _photo.value?.let {
                    try {
                        repository.savePhoto(it)
                    } catch (_: IllegalArgumentException) {
                        null // undecodable photo: save the recipe without it
                    }
                }
                val categoryIds = f.categories.filter { it.ticked }.map { categoryDao.getOrCreate(it.name) }
                val (servings, servingsText) = splitServings(f.servings)
                val now = System.currentTimeMillis()
                val recipe = RecipeEntity(
                    uid = UUID.randomUUID().toString(),
                    title = f.title.trim(),
                    description = f.description.trim().ifEmpty { null },
                    servings = servings,
                    servingsText = servingsText,
                    prepMinutes = f.prepMinutes.trim().toIntOrNull(),
                    cookMinutes = f.cookMinutes.trim().toIntOrNull(),
                    totalMinutes = f.totalMinutes.trim().toIntOrNull(),
                    nutritionText = f.nutrition.trim().ifEmpty { null },
                    sourceName = f.sourceName.trim().ifEmpty { null },
                    sourceUrl = f.sourceUrl.trim().ifEmpty { null },
                    imageFile = imageFile,
                    origin = RecipeOrigin.WEB,
                    createdAt = now,
                    updatedAt = now,
                )
                val ingredients = parseLines(f.ingredients).map { (t, h) -> IngredientEntity(position = 0, text = t, isHeader = h) }
                val steps = parseLines(f.steps).map { (t, h) -> StepEntity(position = 0, text = t, isHeader = h) }
                val id = repository.upsertWithChildren(recipe, ingredients, steps, categoryIds)
                _events.trySend(ImportEvent.Saved(id))
            } catch (e: kotlinx.coroutines.CancellationException) {
                imageFile?.let { repository.deleteImage(it) }
                throw e
            } catch (_: IOException) {
                failSave(imageFile)
            } catch (_: RuntimeException) {
                failSave(imageFile)
            }
        }
    }

    private suspend fun failSave(imageFile: String?) {
        imageFile?.let { repository.deleteImage(it) }
        _form.update { it.copy(saving = false) }
        _events.trySend(ImportEvent.SaveFailed)
    }

    override fun onCleared() {
        job?.cancel()
        super.onCleared()
    }
}

/** Placeholder for the line cleanup R5 shares with OCR. */
internal object PastedTextStub {
    fun cleanLines(text: String): List<String> = text.lines().map { it.trim() }.filter { it.isNotEmpty() }
}

private val leadingNumber = Regex("^\\s*(\\d+(?:[.,]\\d+)?)")

/** "4" -> 4.0 only; "4 servings" -> 4.0 plus the text; "a few" -> text only. */
internal fun splitServings(text: String): Pair<Double?, String?> {
    val t = text.trim()
    if (t.isEmpty()) return null to null
    val number = leadingNumber.find(t)?.groupValues?.get(1)?.replace(',', '.')?.toDoubleOrNull()?.takeIf { it > 0 }
    val numericOnly = t.replace(',', '.').toDoubleOrNull() != null
    return number to if (numericOnly) null else t
}

private fun formatNumber(value: Double): String =
    if (value == value.toLong().toDouble()) value.toLong().toString() else value.toString()

internal fun ImportedRecipe.toForm(sourceUrl: String) = ImportPreviewForm(
    title = title,
    description = description.orEmpty(),
    servings = servingsText ?: servings?.let(::formatNumber).orEmpty(),
    prepMinutes = prepMinutes?.toString().orEmpty(),
    cookMinutes = cookMinutes?.toString().orEmpty(),
    totalMinutes = totalMinutes?.toString().orEmpty(),
    ingredients = linesToText(ingredients.map { it.text to it.isHeader }),
    steps = linesToText(steps.map { it.text to it.isHeader }),
    nutrition = nutritionText.orEmpty(),
    sourceName = sourceName.orEmpty(),
    sourceUrl = sourceUrl,
    // Suggestions only; the user ticks the ones they want.
    categories = suggestedCategories.map { SuggestedCategory(it, ticked = false) },
)
