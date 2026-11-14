package com.postpole.spoonfile.ui.recipe

import android.content.Context
import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.postpole.spoonfile.billing.ProStatus
import com.postpole.spoonfile.data.CategoryDao
import com.postpole.spoonfile.data.CategoryWithCount
import com.postpole.spoonfile.data.IngredientEntity
import com.postpole.spoonfile.data.RecipeEntity
import com.postpole.spoonfile.data.RecipeOrigin
import com.postpole.spoonfile.data.RecipeRepository
import com.postpole.spoonfile.data.RecipeWithDetails
import com.postpole.spoonfile.data.StepEntity
import com.postpole.spoonfile.di.IoDispatcher
import com.postpole.spoonfile.domain.ParsedLine
import com.postpole.spoonfile.domain.linesToEditorText
import com.postpole.spoonfile.domain.parseLines
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import javax.inject.Inject

data class RecipeEditForm(
    val title: String = "",
    val description: String = "",
    val servings: String = "",
    val prep: String = "",
    val cook: String = "",
    val total: String = "",
    val ingredients: String = "",
    val steps: String = "",
    val notes: String = "",
    val nutrition: String = "",
    val sourceName: String = "",
    val sourceUrl: String = "",
    val imageFile: String? = null,
    val categoryIds: Set<Long> = emptySet(),
    val rating: Int = 0,
)

data class RecipeEditState(
    val loading: Boolean = true,
    val form: RecipeEditForm = RecipeEditForm(),
    val titleError: Boolean = false,
    val photoError: Boolean = false,
    val saving: Boolean = false,
)

sealed interface RecipeEditEvent {
    data class Saved(val id: Long, val isNew: Boolean) : RecipeEditEvent

    /** A new recipe was blocked by the free cap. */
    data object OpenPro : RecipeEditEvent
}

@HiltViewModel
class RecipeEditViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repo: RecipeRepository,
    private val categoryDao: CategoryDao,
    private val pro: ProStatus,
    @ApplicationContext private val context: Context,
    @IoDispatcher private val io: CoroutineDispatcher,
) : ViewModel() {
    val recipeId: Long = savedStateHandle.get<Long>(RecipeDetailViewModel.ARG_ID) ?: 0L
    val isNew: Boolean = recipeId == 0L

    private val _state = MutableStateFlow(RecipeEditState(loading = !isNew))
    val state: StateFlow<RecipeEditState> = _state.asStateFlow()

    val allCategories: StateFlow<List<CategoryWithCount>> = categoryDao.observeAllWithCounts()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _events = Channel<RecipeEditEvent>(Channel.BUFFERED)
    val events: Flow<RecipeEditEvent> = _events.receiveAsFlow()

    private var existing: RecipeWithDetails? = null

    /** Photos picked in this editing session that are not (yet) saved with the recipe. */
    private val pendingPhotos = mutableSetOf<String>()

    init {
        if (!isNew) {
            viewModelScope.launch {
                existing = repo.getDetails(recipeId)
                existing?.let { d -> _state.update { it.copy(loading = false, form = d.toForm()) } }
                    ?: _state.update { it.copy(loading = false) }
            }
        }
    }

    fun photoFile(name: String): File = repo.photoFile(name)

    fun update(transform: (RecipeEditForm) -> RecipeEditForm) {
        _state.update { it.copy(form = transform(it.form), titleError = false) }
    }

    fun toggleCategory(id: Long) = update {
        it.copy(categoryIds = if (id in it.categoryIds) it.categoryIds - id else it.categoryIds + id)
    }

    fun createCategory(name: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch {
            val id = categoryDao.getOrCreate(trimmed)
            update { it.copy(categoryIds = it.categoryIds + id) }
        }
    }

    fun setRating(rating: Int) = update { it.copy(rating = rating.coerceIn(0, 5)) }

    fun onPhotoPicked(uri: Uri) {
        viewModelScope.launch {
            try {
                val bytes = withContext(io) { context.contentResolver.openInputStream(uri)?.use { it.readBytes() } }
                    ?: throw IllegalArgumentException("Unreadable photo")
                val name = repo.savePhoto(bytes)
                dropCurrentPendingPhoto()
                pendingPhotos += name
                _state.update { it.copy(form = it.form.copy(imageFile = name), photoError = false) }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                _state.update { it.copy(photoError = true) }
            }
        }
    }

    fun removePhoto() {
        viewModelScope.launch {
            dropCurrentPendingPhoto()
            _state.update { it.copy(form = it.form.copy(imageFile = null), photoError = false) }
        }
    }

    private suspend fun dropCurrentPendingPhoto() {
        val current = _state.value.form.imageFile ?: return
        if (pendingPhotos.remove(current)) repo.deletePhoto(current)
    }

    fun save() {
        val f = _state.value.form
        if (f.title.isBlank()) {
            _state.update { it.copy(titleError = true) }
            return
        }
        if (_state.value.saving) return
        _state.update { it.copy(saving = true) }
        viewModelScope.launch {
            if (isNew && !repo.canAdd(pro.isPro.value)) {
                _state.update { it.copy(saving = false) }
                _events.send(RecipeEditEvent.OpenPro)
                return@launch
            }
            val now = System.currentTimeMillis()
            val base = existing?.recipe
            val servingsText = f.servings.trim()
            val recipe = RecipeEntity(
                id = base?.id ?: 0L,
                uid = base?.uid ?: UUID.randomUUID().toString(),
                title = f.title.trim(),
                description = f.description.trim().ifEmpty { null },
                servingsText = servingsText.ifEmpty { null },
                servings = SERVINGS_NUMBER.find(servingsText)?.value?.toDoubleOrNull(),
                prepMinutes = f.prep.trim().toIntOrNull(),
                cookMinutes = f.cook.trim().toIntOrNull(),
                totalMinutes = f.total.trim().toIntOrNull(),
                notes = f.notes.trim().ifEmpty { null },
                nutritionText = f.nutrition.trim().ifEmpty { null },
                sourceName = f.sourceName.trim().ifEmpty { null },
                sourceUrl = f.sourceUrl.trim().ifEmpty { null },
                imageFile = f.imageFile,
                rating = f.rating.coerceIn(0, 5),
                favorite = base?.favorite ?: false,
                origin = base?.origin ?: RecipeOrigin.MANUAL,
                createdAt = base?.createdAt ?: now,
                updatedAt = now,
            )
            val id = repo.upsertWithChildren(
                recipe,
                parseLines(f.ingredients).mapIndexed { i, l -> IngredientEntity(position = i, text = l.text, isHeader = l.isHeader) },
                parseLines(f.steps).mapIndexed { i, l -> StepEntity(position = i, text = l.text, isHeader = l.isHeader) },
                f.categoryIds.toList(),
            )
            // Photos picked and then replaced were already deleted; the kept one now belongs to the recipe.
            pendingPhotos.clear()
            _state.update { it.copy(saving = false) }
            _events.send(RecipeEditEvent.Saved(id, isNew))
        }
    }

    private fun RecipeWithDetails.toForm() = RecipeEditForm(
        title = recipe.title,
        description = recipe.description.orEmpty(),
        servings = recipe.servingsText ?: recipe.servings?.let(::formatServings).orEmpty(),
        prep = recipe.prepMinutes?.toString().orEmpty(),
        cook = recipe.cookMinutes?.toString().orEmpty(),
        total = recipe.totalMinutes?.toString().orEmpty(),
        ingredients = linesToEditorText(ingredients.map { ParsedLine(it.text, it.isHeader) }),
        steps = linesToEditorText(steps.map { ParsedLine(it.text, it.isHeader) }),
        notes = recipe.notes.orEmpty(),
        nutrition = recipe.nutritionText.orEmpty(),
        sourceName = recipe.sourceName.orEmpty(),
        sourceUrl = recipe.sourceUrl.orEmpty(),
        imageFile = recipe.imageFile,
        categoryIds = categories.map { it.id }.toSet(),
        rating = recipe.rating.coerceIn(0, 5),
    )

    private companion object {
        val SERVINGS_NUMBER = Regex("\\d+(\\.\\d+)?")
    }
}
