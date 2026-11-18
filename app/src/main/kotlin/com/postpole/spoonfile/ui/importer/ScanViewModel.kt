package com.postpole.spoonfile.ui.importer

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.postpole.spoonfile.analytics.Analytics
import com.postpole.spoonfile.analytics.AnalyticsEvents
import com.postpole.spoonfile.analytics.AnalyticsParams
import com.postpole.spoonfile.importer.ocr.LineRole
import com.postpole.spoonfile.importer.ocr.LineRoleGuesser
import com.postpole.spoonfile.importer.ocr.OcrEngine
import com.postpole.spoonfile.importer.ocr.PagePhotoReader
import com.postpole.spoonfile.importer.ocr.ScanPhotoCache
import com.postpole.spoonfile.ui.recipe.RecipeEditForm
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** One recognised (or pasted) line on the cleanup screen. */
data class CleanupLine(val id: Long, val text: String, val role: LineRole)

data class ScanUiState(
    val pages: List<Uri> = emptyList(),
    val lines: List<CleanupLine> = emptyList(),
    /** Off by default: the page photo only becomes the recipe photo when the user asks. */
    val usePagePhoto: Boolean = false,
    val busy: Boolean = false,
    /** The last photo couldn't be read or held no text. */
    val readFailed: Boolean = false,
    val expandedId: Long? = null,
    val editingId: Long? = null,
) {
    val canAddPage: Boolean get() = pages.size < ScanViewModel.MAX_PAGES && !busy
}

sealed interface ScanEvent {
    /** The cleaned-up text, ready for the import preview. [photo] is set only when "use page photo" is on. */
    data class Continue(val form: RecipeEditForm, val photo: ByteArray?) : ScanEvent
}

@HiltViewModel
class ScanViewModel @Inject constructor(
    private val ocr: OcrEngine,
    private val photoReader: PagePhotoReader,
    private val analytics: Analytics,
    private val photoCache: ScanPhotoCache,
    private val savedState: SavedStateHandle,
) : ViewModel() {
    private val _state = MutableStateFlow(restore())
    val state: StateFlow<ScanUiState> = _state

    private val _events = Channel<ScanEvent>(Channel.BUFFERED)
    val events: Flow<ScanEvent> = _events.receiveAsFlow()

    private var nextId = savedState.get<Long>(KEY_NEXT_ID) ?: _state.value.lines.maxOfOrNull { it.id + 1 } ?: 0L

    init {
        // Keep the restorable part of the state in the SavedStateHandle so it survives process death.
        viewModelScope.launch { _state.collect { persist(it) } }
    }

    /**
     * Reads a page photo and appends its lines. Ignored once [MAX_PAGES] pages are added.
     * A photo that is unreadable or holds no text adds no page and only sets [ScanUiState.readFailed].
     */
    fun addPage(uri: Uri) {
        val current = _state.value
        if (!current.canAddPage) return
        _state.update { it.copy(busy = true, readFailed = false) }
        viewModelScope.launch {
            try {
                val lines = ocr.recognize(uri)
                if (lines.isEmpty()) {
                    _state.update { it.copy(busy = false, readFailed = true) }
                    logResult(RESULT_FAILED)
                    return@launch
                }
                _state.update { s ->
                    s.copy(
                        pages = s.pages + uri,
                        lines = s.lines + toLines(lines, existing = s.lines),
                        busy = false,
                        readFailed = false,
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // Never report what the page said (or why it failed to read); just show the retry message.
                _state.update { it.copy(busy = false, readFailed = true) }
                logResult(RESULT_FAILED)
            }
        }
    }

    /** Starts the cleanup from pasted text (the R3 fallback) instead of photos. */
    fun startFromText(text: String) {
        if (_state.value.lines.isNotEmpty()) return // keep edits across a rotation
        val lines = text.lines().map { it.trim() }.filter { it.isNotEmpty() }
        _state.value = ScanUiState(lines = toLines(lines, existing = emptyList()))
    }

    /** Forgets the lines (leaving the cleanup screen), so the next visit starts from fresh text. */
    fun reset() {
        _state.value = ScanUiState()
    }

    /** Leaves the scan flow: forgets the pages and lines and deletes the cached camera captures. */
    fun discardScan() {
        reset()
        photoCache.clear()
    }

    /** The scan flow has ended (saved, cancelled or left): free the recognizer and the cached captures. */
    override fun onCleared() {
        ocr.close()
        photoCache.clear()
    }

    private fun restore(): ScanUiState {
        val texts = savedState.get<ArrayList<String>>(KEY_TEXTS).orEmpty()
        val ids = savedState.get<LongArray>(KEY_IDS) ?: LongArray(0)
        val roles = savedState.get<ArrayList<String>>(KEY_ROLES).orEmpty()
        val count = minOf(texts.size, ids.size, roles.size)
        val lines = (0 until count).map { i ->
            CleanupLine(ids[i], texts[i], LineRole.entries.firstOrNull { it.name == roles[i] } ?: LineRole.NOTE)
        }
        return ScanUiState(
            pages = savedState.get<ArrayList<String>>(KEY_PAGES).orEmpty().map(Uri::parse),
            lines = lines,
            usePagePhoto = savedState.get<Boolean>(KEY_USE_PHOTO) ?: false,
        )
    }

    private fun persist(s: ScanUiState) {
        savedState[KEY_PAGES] = ArrayList(s.pages.map { it.toString() })
        savedState[KEY_IDS] = s.lines.map { it.id }.toLongArray()
        savedState[KEY_TEXTS] = ArrayList(s.lines.map { it.text })
        savedState[KEY_ROLES] = ArrayList(s.lines.map { it.role.name })
        savedState[KEY_USE_PHOTO] = s.usePagePhoto
        savedState[KEY_NEXT_ID] = nextId
    }

    fun setUsePagePhoto(value: Boolean) = _state.update { it.copy(usePagePhoto = value) }

    fun toggleExpanded(id: Long) = _state.update {
        if (it.expandedId == id) it.copy(expandedId = null, editingId = null) else it.copy(expandedId = id, editingId = null)
    }

    fun setRole(id: Long, role: LineRole) = updateLine(id) { it.copy(role = role) }

    fun startEditing(id: Long) = _state.update { it.copy(expandedId = id, editingId = id) }

    fun stopEditing() = _state.update { it.copy(editingId = null) }

    fun editText(id: Long, text: String) = updateLine(id) { it.copy(text = text) }

    /** Joins the line with the one after it (keeping this line's role). */
    fun mergeWithNext(id: Long) = _state.update { s ->
        val i = s.lines.indexOfFirst { it.id == id }
        if (i < 0 || i >= s.lines.lastIndex) return@update s
        val merged = s.lines[i].copy(text = joinText(s.lines[i].text, s.lines[i + 1].text))
        s.copy(lines = s.lines.take(i) + merged + s.lines.drop(i + 2), editingId = null)
    }

    /** Splits the line at character [at]; the second half gets a freshly guessed role. No-op if either half would be empty. */
    fun split(id: Long, at: Int) = _state.update { s ->
        val i = s.lines.indexOfFirst { it.id == id }
        if (i < 0) return@update s
        val line = s.lines[i]
        val cut = at.coerceIn(0, line.text.length)
        val first = line.text.substring(0, cut).trim()
        val second = line.text.substring(cut).trim()
        if (first.isEmpty() || second.isEmpty()) return@update s
        val secondLine = CleanupLine(nextId++, second, guessFollowing(second))
        s.copy(
            lines = s.lines.take(i) + line.copy(text = first) + secondLine + s.lines.drop(i + 1),
            editingId = null,
        )
    }

    fun deleteLine(id: Long) = _state.update { s ->
        s.copy(lines = s.lines.filterNot { it.id == id }, expandedId = null, editingId = null)
    }

    /** Turns the cleaned lines into a form for the import preview. [source] is the origin tag for analytics. */
    fun continueImport(logScan: Boolean) {
        val s = _state.value
        if (s.busy) return
        viewModelScope.launch {
            val photo = if (s.usePagePhoto) s.pages.firstOrNull()?.let { photoReader.read(it) } else null
            val form = buildForm(s.lines)
            if (logScan) logResult(RESULT_OK)
            _events.trySend(ScanEvent.Continue(form, photo))
        }
    }

    private fun updateLine(id: Long, change: (CleanupLine) -> CleanupLine) = _state.update { s ->
        s.copy(lines = s.lines.map { if (it.id == id) change(it) else it })
    }

    private fun toLines(texts: List<String>, existing: List<CleanupLine>): List<CleanupLine> {
        // A title is only guessed once: a later page can't claim a second one.
        val hasTitle = existing.any { it.role == LineRole.TITLE }
        val roles = LineRoleGuesser.guess(texts).map { if (hasTitle && it == LineRole.TITLE) LineRole.NOTE else it }
        return texts.mapIndexed { i, text -> CleanupLine(nextId++, text, roles[i]) }
    }

    private fun guessFollowing(text: String): LineRole =
        LineRoleGuesser.guess(listOf(text)).first().let { if (it == LineRole.TITLE) LineRole.NOTE else it }

    private fun logResult(result: String) {
        analytics.logEvent(
            AnalyticsEvents.RECIPE_IMPORT,
            mapOf(AnalyticsParams.IMPORT_SOURCE to SOURCE_OCR, AnalyticsParams.RESULT to result),
        )
    }

    companion object {
        const val MAX_PAGES = 4
        const val SOURCE_OCR = "ocr"
        const val RESULT_OK = "ok"
        const val RESULT_FAILED = "ocr_failed"

        private const val KEY_PAGES = "scan_pages"
        private const val KEY_IDS = "scan_line_ids"
        private const val KEY_TEXTS = "scan_line_texts"
        private const val KEY_ROLES = "scan_line_roles"
        private const val KEY_USE_PHOTO = "scan_use_photo"
        private const val KEY_NEXT_ID = "scan_next_id"

        private fun joinText(a: String, b: String): String {
            val left = a.trimEnd()
            return if (left.endsWith("-")) left.dropLast(1) + b.trimStart() else "$left ${b.trim()}"
        }

        /** Groups the lines by role; ignored lines are dropped and list markers are removed. */
        fun buildForm(lines: List<CleanupLine>): RecipeEditForm {
            fun texts(role: LineRole) = lines.filter { it.role == role }
                .map { LineRoleGuesser.stripMarker(it.text, role) }.filter { it.isNotEmpty() }
            return RecipeEditForm(
                title = texts(LineRole.TITLE).joinToString(" "),
                ingredients = texts(LineRole.INGREDIENT).joinToString("\n"),
                steps = texts(LineRole.STEP).joinToString("\n"),
                notes = texts(LineRole.NOTE).joinToString("\n"),
                loaded = true,
            )
        }
    }
}
