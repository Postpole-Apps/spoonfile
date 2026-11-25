package com.postpole.spoonfile.ui.recipe

import android.os.Bundle
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.postpole.spoonfile.billing.BillingRepository
import com.postpole.spoonfile.data.CategoryDao
import com.postpole.spoonfile.data.CategoryEntity
import com.postpole.spoonfile.data.IngredientEntity
import com.postpole.spoonfile.data.RecipeEntity
import com.postpole.spoonfile.data.RecipeRepository
import com.postpole.spoonfile.data.StepEntity
import com.postpole.spoonfile.promo.ProPromptRecorder
import com.postpole.spoonfile.review.ReviewPrompter
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.InputStream
import java.util.UUID
import javax.inject.Inject

/** One line per item; a line starting with `#` is a section header. Blank lines are dropped. */
internal fun parseLines(text: String): List<Pair<String, Boolean>> =
    text.lines().map { it.trim() }.filter { it.isNotEmpty() }.mapNotNull { line ->
        if (line.startsWith("#")) {
            line.trimStart('#').trim().takeIf { it.isNotEmpty() }?.let { it to true }
        } else {
            line to false
        }
    }

internal fun linesToText(items: List<Pair<String, Boolean>>): String =
    items.joinToString("\n") { (text, header) -> if (header) "# $text" else text }

data class RecipeEditForm(
    val title: String = "",
    val description: String = "",
    val servings: String = "",
    val prepMinutes: String = "",
    val cookMinutes: String = "",
    val totalMinutes: String = "",
    val ingredients: String = "",
    val steps: String = "",
    val notes: String = "",
    val nutrition: String = "",
    val sourceName: String = "",
    val sourceUrl: String = "",
    val imageFile: String? = null,
    val rating: Int = 0,
    val categoryIds: Set<Long> = emptySet(),
    val titleError: Boolean = false,
    val loaded: Boolean = false,
    val saving: Boolean = false,
)

/** Copies the form's fields onto [base] (shared by the edit screen and the web import preview). */
internal fun RecipeEditForm.applyTo(base: RecipeEntity, now: Long): RecipeEntity {
    val servingsValue = servings.trim().replace(',', '.').toDoubleOrNull()?.takeIf { it > 0 }
    return base.copy(
        title = title.trim(),
        description = description.trim().ifEmpty { null },
        servings = servingsValue,
        servingsText = if (servingsValue == null) servings.trim().ifEmpty { null } else null,
        prepMinutes = prepMinutes.trim().toIntOrNull(),
        cookMinutes = cookMinutes.trim().toIntOrNull(),
        totalMinutes = totalMinutes.trim().toIntOrNull(),
        notes = notes.trim().ifEmpty { null },
        nutritionText = nutrition.trim().ifEmpty { null },
        sourceName = sourceName.trim().ifEmpty { null },
        sourceUrl = sourceUrl.trim().ifEmpty { null },
        imageFile = imageFile,
        rating = rating.coerceIn(0, 5),
        updatedAt = now,
    )
}

internal fun RecipeEditForm.ingredientEntities(): List<IngredientEntity> =
    parseLines(ingredients).map { (t, h) -> IngredientEntity(position = 0, text = t, isHeader = h) }

internal fun RecipeEditForm.stepEntities(): List<StepEntity> =
    parseLines(steps).map { (t, h) -> StepEntity(position = 0, text = t, isHeader = h) }

sealed interface RecipeEditEvent {
    /** Saved; [showReview] is true when the Play review prompt should be offered. */
    data class Saved(val id: Long, val showReview: Boolean) : RecipeEditEvent
    data object OpenPro : RecipeEditEvent
    data object PhotoFailed : RecipeEditEvent
}

@HiltViewModel
class RecipeEditViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    private val repository: RecipeRepository,
    private val categoryDao: CategoryDao,
    private val billing: BillingRepository,
    private val review: ReviewPrompter,
    private val proPrompts: ProPromptRecorder,
) : ViewModel() {
    /** 0 means a new recipe. */
    private val recipeId: Long = savedStateHandle.get<Long>("id") ?: 0L
    val isNew: Boolean get() = recipeId == 0L

    private var existing: RecipeEntity? = null
    private var originalImage: String? = null

    /** Unsaved draft restored after process death, if any. */
    private val restoredDraft: RecipeEditForm? = savedStateHandle.get<Bundle>(DRAFT_KEY)?.toForm()

    // For an existing recipe the form stays not-loaded (Save disabled, photo/discard no-ops) until the
    // row has been read, even when a draft is restored: `existing` and `originalImage` come from the database.
    private val _form = MutableStateFlow(
        restoredDraft?.copy(loaded = recipeId == 0L) ?: RecipeEditForm(loaded = recipeId == 0L),
    )
    val form: StateFlow<RecipeEditForm> = _form

    val categories: StateFlow<List<CategoryEntity>> = categoryDao.observeAllWithCounts()
        .map { list -> list.map { it.category } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _events = Channel<RecipeEditEvent>(Channel.BUFFERED)
    val events: Flow<RecipeEditEvent> = _events.receiveAsFlow()

    init {
        // Keep the draft in SavedStateHandle so it survives process death.
        viewModelScope.launch {
            _form.collect { if (it.loaded) savedStateHandle[DRAFT_KEY] = it.toBundle() }
        }
        if (recipeId != 0L) {
            viewModelScope.launch {
                val details = repository.observeDetails(recipeId).first()
                if (details == null) {
                    _form.update { it.copy(loaded = true) }
                    return@launch
                }
                val r = details.recipe
                existing = r
                originalImage = r.imageFile
                if (restoredDraft != null) {
                    _form.update { it.copy(loaded = true) }
                    return@launch
                }
                _form.value = RecipeEditForm(
                    title = r.title,
                    description = r.description.orEmpty(),
                    servings = r.servings?.let(::formatNumber) ?: r.servingsText.orEmpty(),
                    prepMinutes = r.prepMinutes?.toString().orEmpty(),
                    cookMinutes = r.cookMinutes?.toString().orEmpty(),
                    totalMinutes = r.totalMinutes?.toString().orEmpty(),
                    ingredients = linesToText(details.ingredients.map { it.text to it.isHeader }),
                    steps = linesToText(details.steps.map { it.text to it.isHeader }),
                    notes = r.notes.orEmpty(),
                    nutrition = r.nutritionText.orEmpty(),
                    sourceName = r.sourceName.orEmpty(),
                    sourceUrl = r.sourceUrl.orEmpty(),
                    imageFile = r.imageFile,
                    rating = r.rating,
                    categoryIds = details.categories.map { it.id }.toSet(),
                    loaded = true,
                )
            }
        }
    }

    suspend fun loadPhoto(name: String, maxEdge: Int): ImageBitmap? = repository.loadPhoto(name, maxEdge)?.asImageBitmap()

    fun edit(change: (RecipeEditForm) -> RecipeEditForm) = _form.update(change)

    fun setTitle(value: String) = _form.update { it.copy(title = value, titleError = false) }

    /** Rating is always kept within 0..5. */
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

    fun onPhotoPicked(open: () -> InputStream?) {
        if (!_form.value.loaded) return
        viewModelScope.launch {
            val saved = repository.savePhotoFrom(open)
            if (saved == null) {
                _events.trySend(RecipeEditEvent.PhotoFailed)
                return@launch
            }
            discardUnsavedPhoto()
            _form.update { it.copy(imageFile = saved) }
        }
    }

    fun removePhoto() {
        if (!_form.value.loaded) return
        viewModelScope.launch {
            discardUnsavedPhoto()
            _form.update { it.copy(imageFile = null) }
        }
    }

    /** Leaving without saving: delete a photo that was picked in this session. */
    fun discard(onDone: () -> Unit) {
        viewModelScope.launch {
            discardUnsavedPhoto()
            onDone()
        }
    }

    private suspend fun discardUnsavedPhoto() {
        // Not loaded yet: originalImage is unknown, so never delete anything.
        if (!_form.value.loaded) return
        val current = _form.value.imageFile
        if (current != null && current != originalImage) repository.deleteImage(current)
    }

    fun save() {
        val f = _form.value
        if (f.saving || !f.loaded) return
        if (f.title.isBlank()) {
            _form.update { it.copy(titleError = true) }
            return
        }
        _form.update { it.copy(saving = true) }
        viewModelScope.launch {
            // Existing recipes stay editable even if Pro was revoked; only new ones count against the cap.
            if (existing == null && !repository.canAdd(billing.isPro.value)) {
                _form.update { it.copy(saving = false) }
                _events.trySend(RecipeEditEvent.OpenPro)
                return@launch
            }
            val now = System.currentTimeMillis()
            val base = existing ?: RecipeEntity(uid = UUID.randomUUID().toString(), title = "", createdAt = now, updatedAt = now)
            val id = repository.upsertWithChildren(f.applyTo(base, now), f.ingredientEntities(), f.stepEntities(), f.categoryIds.toList())
            originalImage = f.imageFile
            proPrompts.recordEngagedEvent()
            val showReview = review.recordPositiveAction()
            _events.trySend(RecipeEditEvent.Saved(id, showReview))
        }
    }

    private companion object {
        const val DRAFT_KEY = "edit_draft"
    }

    private fun formatNumber(value: Double): String =
        if (value == value.toLong().toDouble()) value.toLong().toString() else value.toString()
}

private fun RecipeEditForm.toBundle() = Bundle().apply {
    putString("title", title)
    putString("description", description)
    putString("servings", servings)
    putString("prep", prepMinutes)
    putString("cook", cookMinutes)
    putString("total", totalMinutes)
    putString("ingredients", ingredients)
    putString("steps", steps)
    putString("notes", notes)
    putString("nutrition", nutrition)
    putString("sourceName", sourceName)
    putString("sourceUrl", sourceUrl)
    putString("image", imageFile)
    putInt("rating", rating)
    putLongArray("categories", categoryIds.toLongArray())
}

private fun Bundle.toForm() = RecipeEditForm(
    title = getString("title").orEmpty(),
    description = getString("description").orEmpty(),
    servings = getString("servings").orEmpty(),
    prepMinutes = getString("prep").orEmpty(),
    cookMinutes = getString("cook").orEmpty(),
    totalMinutes = getString("total").orEmpty(),
    ingredients = getString("ingredients").orEmpty(),
    steps = getString("steps").orEmpty(),
    notes = getString("notes").orEmpty(),
    nutrition = getString("nutrition").orEmpty(),
    sourceName = getString("sourceName").orEmpty(),
    sourceUrl = getString("sourceUrl").orEmpty(),
    imageFile = getString("image"),
    rating = getInt("rating").coerceIn(0, 5),
    categoryIds = getLongArray("categories")?.toSet().orEmpty(),
    loaded = true,
)
