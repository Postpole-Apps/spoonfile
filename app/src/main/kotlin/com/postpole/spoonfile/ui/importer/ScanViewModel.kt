package com.postpole.spoonfile.ui.importer

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.postpole.spoonfile.analytics.Analytics
import com.postpole.spoonfile.analytics.AnalyticsEvents
import com.postpole.spoonfile.analytics.AnalyticsParams
import com.postpole.spoonfile.importer.ocr.CleanupController
import com.postpole.spoonfile.importer.ocr.CleanupState
import com.postpole.spoonfile.importer.ocr.LineRole
import com.postpole.spoonfile.importer.ocr.LineRoleGuesser
import com.postpole.spoonfile.importer.ocr.OcrEngine
import com.postpole.spoonfile.importer.ocr.PageReader
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

data class ScanState(
    val pages: List<Uri> = emptyList(),
    /** A page is being read. */
    val recognizing: Boolean = false,
    /** The last page held no readable text. */
    val noText: Boolean = false,
    /** The last page couldn't be read at all. */
    val failed: Boolean = false,
    /** Off by default: the first page photo is only kept as the recipe photo when the user asks. */
    val usePagePhoto: Boolean = false,
)

sealed interface ScanEvent {
    /** Cleanup is done: show the import preview with this form (and the page photo, if asked for). */
    data class Continue(val form: RecipeEditForm, val photo: ByteArray?) : ScanEvent
}

@HiltViewModel
class ScanViewModel @Inject constructor(
    private val ocr: OcrEngine,
    private val pageReader: PageReader,
    private val analytics: Analytics,
) : ViewModel() {
    val cleanup = CleanupController()

    private val _state = MutableStateFlow(ScanState())
    val state: StateFlow<ScanState> = _state

    private val _events = Channel<ScanEvent>(Channel.BUFFERED)
    val events: Flow<ScanEvent> = _events.receiveAsFlow()

    val canAddPage: Boolean get() = _state.value.pages.size < MAX_PAGES && !_state.value.recognizing

    /** Reads a page and appends its lines. Ignored past [MAX_PAGES] pages or while another page is being read. */
    fun addPage(uri: Uri) {
        if (!canAddPage) return
        _state.update { it.copy(pages = it.pages + uri, recognizing = true, noText = false, failed = false) }
        viewModelScope.launch {
            try {
                val lines = ocr.recognize(uri)
                cleanup.append(lines)
                _state.update { it.copy(recognizing = false, noText = lines.isEmpty()) }
                logResult(if (lines.isEmpty()) RESULT_NO_TEXT else RESULT_OK)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // Never report the page or its text: only that reading failed.
                _state.update { it.copy(recognizing = false, failed = true) }
                logResult(RESULT_FAILED)
            }
        }
    }

    fun removePage(uri: Uri) = _state.update { it.copy(pages = it.pages - uri) }

    fun setUsePagePhoto(value: Boolean) = _state.update { it.copy(usePagePhoto = value) }

    fun setRole(id: Long, role: LineRole) = cleanup.setRole(id, role)

    /** Builds the recipe from the cleaned-up lines and hands it to the import preview. */
    fun continueToPreview() {
        val s = _state.value
        if (s.recognizing) return
        val form = cleanup.state.value.toForm()
        viewModelScope.launch {
            val photo = if (s.usePagePhoto) s.pages.firstOrNull()?.let { pageReader.readBytes(it) } else null
            _events.trySend(ScanEvent.Continue(form, photo))
        }
    }

    private fun logResult(result: String) {
        analytics.logEvent(
            AnalyticsEvents.RECIPE_IMPORT,
            mapOf(AnalyticsParams.IMPORT_SOURCE to SOURCE_OCR, AnalyticsParams.RESULT to result),
        )
    }

    companion object {
        const val MAX_PAGES = 4
        private const val SOURCE_OCR = "ocr"
        private const val RESULT_OK = "ok"
        private const val RESULT_NO_TEXT = "no_text"
        private const val RESULT_FAILED = "failed"
    }
}

/** Turns the role-tagged lines into recipe fields. Ignored lines are dropped. */
internal fun CleanupState.toForm(sourceName: String = "", sourceUrl: String = ""): RecipeEditForm {
    fun texts(role: LineRole, tidy: (String) -> String = { it }) =
        lines.filter { it.role == role }.map { tidy(it.text.trim()) }.filter { it.isNotEmpty() }
    return RecipeEditForm(
        title = texts(LineRole.TITLE).joinToString(" "),
        ingredients = texts(LineRole.INGREDIENT, LineRoleGuesser::stripBullet).joinToString("\n"),
        steps = texts(LineRole.STEP, LineRoleGuesser::stripNumber).joinToString("\n"),
        notes = texts(LineRole.NOTE).joinToString("\n"),
        sourceName = sourceName,
        sourceUrl = sourceUrl,
        loaded = true,
    )
}
