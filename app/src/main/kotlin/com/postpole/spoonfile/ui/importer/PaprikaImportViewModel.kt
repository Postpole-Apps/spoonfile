package com.postpole.spoonfile.ui.importer

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.postpole.spoonfile.analytics.Analytics
import com.postpole.spoonfile.analytics.AnalyticsEvents
import com.postpole.spoonfile.analytics.AnalyticsParams
import com.postpole.spoonfile.billing.BillingRepository
import com.postpole.spoonfile.data.CategoryDao
import com.postpole.spoonfile.data.DedupeKey
import com.postpole.spoonfile.data.IngredientEntity
import com.postpole.spoonfile.data.RecipeEntity
import com.postpole.spoonfile.data.RecipeOrigin
import com.postpole.spoonfile.data.RecipeRepository
import com.postpole.spoonfile.pro.NoOpProPromptSignals
import com.postpole.spoonfile.pro.ProPromptSignals
import com.postpole.spoonfile.data.StepEntity
import com.postpole.spoonfile.di.IoDispatcher
import com.postpole.spoonfile.importer.paprika.NotAnArchiveException
import com.postpole.spoonfile.importer.paprika.PaprikaArchiveReader
import com.postpole.spoonfile.importer.paprika.PaprikaFailure
import com.postpole.spoonfile.importer.paprika.PaprikaRecipe
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.util.UUID
import javax.inject.Inject

/** One row of the bulk preview. */
data class PaprikaItem(
    /** Position among the readable entries of the file; also the key for ticking. */
    val index: Int,
    val title: String,
    val duplicate: Boolean,
    val hasPhoto: Boolean,
)

enum class PaprikaError { NOT_ZIP, UNREADABLE, EMPTY }

data class PaprikaSummary(
    val imported: Int,
    /** Recipes the user left unticked or that no longer fit under the free limit; duplicates are counted separately. */
    val skipped: Int,
    /** Duplicates (already in the app, or repeated in the file) that were not imported. */
    val duplicatesSkipped: Int,
    /** Entries that couldn't be read, by reason. */
    val unreadable: Map<PaprikaFailure, Int>,
    /** Recipes that were read but couldn't be saved. */
    val saveFailed: Int,
) {
    val failed: Int get() = unreadable.values.sum() + saveFailed
}

sealed interface PaprikaState {
    /** Waiting for a file to be chosen. */
    data object Pick : PaprikaState

    data object Reading : PaprikaState

    data class Error(val kind: PaprikaError) : PaprikaState

    data class Preview(
        val items: List<PaprikaItem>,
        val ticked: Set<Int>,
        val unreadable: Int,
        val truncated: Boolean,
        val isPro: Boolean,
        /** Free slots left (Int.MAX_VALUE for Pro). */
        val remaining: Int,
    ) : PaprikaState {
        /** Recipes that would be imported: duplicates are not counted. */
        val newCount: Int get() = items.count { !it.duplicate }

        /** The free-room banner shows when not all new recipes fit. */
        val overCap: Boolean get() = !isPro && newCount > remaining
    }

    data class Importing(val done: Int, val total: Int) : PaprikaState

    data class Done(val summary: PaprikaSummary) : PaprikaState
}

/** Flags recipes already saved (same uid, or same title + source) and repeats inside the file itself. */
internal class DuplicateFinder(existing: List<DedupeKey>) {
    private val uids = existing.mapTo(HashSet()) { it.uid }
    private val titleSources = existing.mapTo(HashSet()) { titleSource(it.title, it.sourceUrl ?: it.sourceName) }

    /** True when [uid] or [title] + [source] was seen before; otherwise remembers them for later entries. */
    fun isDuplicate(uid: String?, title: String, source: String?): Boolean {
        val key = titleSource(title, source)
        val duplicate = (uid != null && uid in uids) || key in titleSources
        if (uid != null) uids += uid
        titleSources += key
        return duplicate
    }

    private fun titleSource(title: String, source: String?) = title.trim().lowercase() + "\u0000" + source.orEmpty().trim().lowercase()
}

/** The first [remaining] new recipes, in file order. Duplicates start unticked. */
internal fun initialTicks(items: List<PaprikaItem>, remaining: Int): Set<Int> =
    items.asSequence().filter { !it.duplicate }.take(remaining.coerceAtLeast(0)).mapTo(LinkedHashSet()) { it.index }

/** (other skipped, duplicates skipped). A duplicate the user ticked by hand and imported is not skipped. */
internal fun skipCounts(items: List<PaprikaItem>, ticked: Set<Int>, imported: Int, failed: Int): Pair<Int, Int> {
    val duplicatesSkipped = items.count { it.duplicate && it.index !in ticked }
    return (items.size - duplicatesSkipped - imported - failed).coerceAtLeast(0) to duplicatesSkipped
}

@HiltViewModel
class PaprikaImportViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: RecipeRepository,
    private val categoryDao: CategoryDao,
    private val billing: BillingRepository,
    private val analytics: Analytics,
    @IoDispatcher private val io: CoroutineDispatcher,
    private val proPrompts: ProPromptSignals = NoOpProPromptSignals,
) : ViewModel() {
    private val _state = MutableStateFlow<PaprikaState>(PaprikaState.Pick)
    val state: StateFlow<PaprikaState> = _state

    private val reader = PaprikaArchiveReader()
    private var uri: Uri? = null
    private var job: Job? = null
    private val thumbnails = HashMap<Int, ByteArray>()

    /** Reads the chosen file for the preview. Nothing is saved yet. */
    fun load(uri: Uri) {
        this.uri = uri
        thumbnails.clear()
        _state.value = PaprikaState.Reading
        job?.cancel()
        job = viewModelScope.launch {
            val isPro = billing.isPro.value
            val dedupe = DuplicateFinder(repository.dedupeKeys())
            val result = withContext(io) {
                try {
                    val items = ArrayList<PaprikaItem>()
                    val summary = openStream(uri).use { input ->
                        reader.read(input, includePhotos = true) { r ->
                            val source = r.recipe.sourceUrl.ifBlank { r.recipe.sourceName }
                            items += PaprikaItem(r.index, r.recipe.title, dedupe.isDuplicate(r.uid, r.recipe.title, source), r.photo != null)
                            if (r.photo != null && thumbnails.size < MAX_THUMBNAILS) {
                                makeThumbnail(r.photo)?.let { thumbnails[r.index] = it }
                            }
                            true
                        }
                    }
                    Result.success(items to summary)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Result.failure(e)
                }
            }
            result.fold(
                onSuccess = { (items, summary) ->
                    if (items.isEmpty()) {
                        logResult(RESULT_EMPTY)
                        _state.value = PaprikaState.Error(PaprikaError.EMPTY)
                    } else {
                        val remaining = repository.remainingSlots(isPro)
                        _state.value = PaprikaState.Preview(
                            items = items,
                            ticked = initialTicks(items, remaining),
                            unreadable = summary.failed,
                            truncated = summary.truncated,
                            isPro = isPro,
                            remaining = remaining,
                        )
                    }
                },
                onFailure = { e ->
                    logResult(if (e is NotAnArchiveException) RESULT_NOT_ZIP else RESULT_UNREADABLE)
                    _state.value = PaprikaState.Error(if (e is NotAnArchiveException) PaprikaError.NOT_ZIP else PaprikaError.UNREADABLE)
                },
            )
        }
    }

    /** A small decoded photo for a preview row, or null when it has none. */
    fun thumbnail(index: Int): ImageBitmap? =
        thumbnails[index]?.let { BitmapFactory.decodeByteArray(it, 0, it.size)?.asImageBitmap() }

    fun toggle(index: Int) = updatePreview { p ->
        when {
            index in p.ticked -> p.copy(ticked = p.ticked - index)
            !p.isPro && p.ticked.size >= p.remaining -> p // at the free limit: untick another first
            else -> p.copy(ticked = p.ticked + index)
        }
    }

    fun clearAll() = updatePreview { it.copy(ticked = emptySet()) }

    fun backToPick() {
        job?.cancel()
        thumbnails.clear()
        _state.value = PaprikaState.Pick
    }

    private fun updatePreview(change: (PaprikaState.Preview) -> PaprikaState.Preview) {
        _state.update { s: PaprikaState -> if (s is PaprikaState.Preview) change(s) else s }
    }

    /** Re-reads the file and saves the ticked recipes in file order, never going past the free limit. */
    fun import() {
        val preview = _state.value as? PaprikaState.Preview ?: return
        val source = uri ?: return
        val ticked = preview.ticked
        if (ticked.isEmpty()) return
        _state.value = PaprikaState.Importing(0, ticked.size)
        job?.cancel()
        job = viewModelScope.launch {
            val saved = withContext(io) { saveTicked(source, ticked) }
            val (skipped, duplicatesSkipped) = skipCounts(preview.items, ticked, saved.imported, saved.failed)
            val summary = PaprikaSummary(
                imported = saved.imported,
                skipped = skipped,
                duplicatesSkipped = duplicatesSkipped,
                unreadable = saved.unreadable,
                saveFailed = saved.failed,
            )
            logResult(if (saved.imported > 0) RESULT_OK else RESULT_NONE)
            // A whole-file import is one engaged event, however many recipes it brought in.
            if (saved.imported > 0) proPrompts.recordEngaged()
            thumbnails.clear()
            _state.value = PaprikaState.Done(summary)
        }
    }

    /** "Unlock Pro" on the oversized-file banner is a hard gate for the automatic Pro prompts. */
    fun onUnlockPro() {
        viewModelScope.launch { proPrompts.recordHardGate() }
    }

    private class Saved(val imported: Int, val failed: Int, val unreadable: Map<PaprikaFailure, Int>)

    private suspend fun saveTicked(source: Uri, ticked: Set<Int>): Saved {
        val scope = kotlinx.coroutines.currentCoroutineContext()
        val allowed = repository.remainingSlots(billing.isPro.value)
        val knownUids = repository.dedupeKeys().mapTo(HashSet()) { it.uid }
        var processed = 0
        var imported = 0
        var failed = 0
        val unreadable = try {
            openStream(source).use { input ->
                reader.read(input, includePhotos = true) { r ->
                    scope.ensureActive()
                    if (r.index !in ticked) return@read true
                    processed++
                    if (imported < allowed) {
                        if (runBlocking { saveOne(r, knownUids) }) imported++ else failed++
                    }
                    _state.value = PaprikaState.Importing(processed, ticked.size)
                    processed < ticked.size
                }.failures
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: IOException) {
            emptyMap()
        }
        return Saved(imported, failed, unreadable)
    }

    private suspend fun saveOne(r: PaprikaRecipe, knownUids: MutableSet<String>): Boolean {
        var photo: String? = null
        try {
            photo = r.photo?.let { bytes -> runCatching { repository.savePhoto(bytes) }.getOrNull() }
            val categoryIds = r.categories.map { categoryDao.getOrCreate(it) }
            // A uid already in the app (a duplicate ticked by hand) gets a fresh one so the old recipe isn't overwritten.
            val uid = r.uid?.takeIf { knownUids.add(it) } ?: UUID.randomUUID().toString()
            val now = System.currentTimeMillis()
            val x = r.recipe
            val entity = RecipeEntity(
                uid = uid,
                title = x.title,
                description = x.description,
                servingsText = x.servingsText,
                servings = x.servings,
                prepMinutes = x.prepMinutes,
                cookMinutes = x.cookMinutes,
                totalMinutes = x.totalMinutes,
                notes = r.notes,
                nutritionText = x.nutritionText,
                sourceName = x.sourceName,
                sourceUrl = x.sourceUrl.ifBlank { null },
                imageFile = photo,
                rating = r.rating,
                origin = RecipeOrigin.PAPRIKA,
                createdAt = r.createdAt,
                updatedAt = now,
            )
            repository.upsertWithChildren(
                entity,
                x.ingredients.map { IngredientEntity(position = 0, text = it.text) },
                x.steps.map { StepEntity(position = 0, text = it.text) },
                categoryIds,
            )
            return true
        } catch (e: CancellationException) {
            photo?.let { repository.deleteImage(it) }
            throw e
        } catch (_: Exception) {
            // Never leave half a recipe behind: the photo file goes too.
            photo?.let { repository.deleteImage(it) }
            return false
        }
    }

    private fun openStream(uri: Uri) = context.contentResolver.openInputStream(uri) ?: throw IOException("Can't open file")

    private fun logResult(result: String) {
        analytics.logEvent(
            AnalyticsEvents.RECIPE_IMPORT,
            mapOf(AnalyticsParams.IMPORT_SOURCE to SOURCE_FILE, AnalyticsParams.RESULT to result),
        )
    }

    private companion object {
        const val SOURCE_FILE = "recipe_file"
        const val RESULT_OK = "ok"
        const val RESULT_NONE = "none"
        const val RESULT_EMPTY = "empty"
        const val RESULT_NOT_ZIP = "not_zip"
        const val RESULT_UNREADABLE = "unreadable"
        const val MAX_THUMBNAILS = 2_000
        const val THUMB_EDGE = 192

        fun makeThumbnail(bytes: ByteArray): ByteArray? {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
            var sample = 1
            var edge = maxOf(bounds.outWidth, bounds.outHeight)
            while (edge / 2 >= THUMB_EDGE) {
                sample *= 2
                edge /= 2
            }
            val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
                ?: return null
            val out = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, 70, out)
            bitmap.recycle()
            return out.toByteArray()
        }
    }
}
