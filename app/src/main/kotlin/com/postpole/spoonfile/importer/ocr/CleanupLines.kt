package com.postpole.spoonfile.importer.ocr

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

data class CleanupLine(val id: Long, val text: String, val role: LineRole)

data class CleanupState(
    val lines: List<CleanupLine> = emptyList(),
    /** The row showing its role group and actions. */
    val expandedId: Long? = null,
    /** The row being edited inline. */
    val editingId: Long? = null,
)

/** The cleanup screen's line list and its edits. Shared by the page scan and the pasted-text fallback. */
class CleanupController {
    private var nextId = 0L
    private val _state = MutableStateFlow(CleanupState())
    val state: StateFlow<CleanupState> = _state

    /** Replaces everything with [texts], guessing a role for each. */
    fun reset(texts: List<String>) {
        _state.value = CleanupState()
        append(texts)
    }

    /** Adds [texts] after the existing lines (a further page). A Title is only guessed if there is none yet. */
    fun append(texts: List<String>) {
        val clean = texts.map { it.trim() }.filter { it.isNotEmpty() }
        _state.update { current ->
            val roles = LineRoleGuesser.guess(clean, hasTitle = current.lines.any { it.role == LineRole.TITLE })
            current.copy(lines = current.lines + clean.zip(roles).map { (text, role) -> CleanupLine(nextId++, text, role) })
        }
    }

    fun setRole(id: Long, role: LineRole) = mapLine(id) { it.copy(role = role) }

    fun toggleExpanded(id: Long) = _state.update {
        it.copy(expandedId = if (it.expandedId == id) null else id, editingId = null)
    }

    fun startEdit(id: Long) = _state.update { it.copy(expandedId = id, editingId = id) }

    fun edit(id: Long, text: String) = mapLine(id) { it.copy(text = text) }

    /** Ends inline editing; a line left empty is removed. */
    fun finishEdit() = _state.update { s ->
        val id = s.editingId
        val lines = if (id != null) s.lines.filterNot { it.id == id && it.text.isBlank() } else s.lines
        s.copy(lines = lines, editingId = null, expandedId = s.expandedId.takeIf { e -> lines.any { it.id == e } })
    }

    /** Joins the line with the one after it; the result keeps the first line's role. */
    fun mergeWithNext(id: Long) = _state.update { s ->
        val i = s.lines.indexOfFirst { it.id == id }
        if (i < 0 || i == s.lines.lastIndex) return@update s
        val merged = s.lines[i].copy(text = joinText(s.lines[i].text, s.lines[i + 1].text))
        s.copy(lines = s.lines.toMutableList().also { it[i] = merged; it.removeAt(i + 1) }, editingId = null)
    }

    /** Splits the line in two (at a sentence break, else near the middle); both halves keep the role. */
    fun split(id: Long) = _state.update { s ->
        val i = s.lines.indexOfFirst { it.id == id }
        if (i < 0) return@update s
        val line = s.lines[i]
        val at = splitIndex(line.text) ?: return@update s
        val first = line.copy(text = line.text.substring(0, at).trim())
        val second = CleanupLine(nextId++, line.text.substring(at).trim(), line.role)
        s.copy(lines = s.lines.toMutableList().also { it[i] = first; it.add(i + 1, second) }, editingId = null)
    }

    private fun mapLine(id: Long, change: (CleanupLine) -> CleanupLine) = _state.update { s ->
        s.copy(lines = s.lines.map { if (it.id == id) change(it) else it })
    }

    companion object {
        fun joinText(first: String, second: String): String {
            val a = first.trimEnd()
            val b = second.trimStart()
            // A word hyphenated across the line break is stitched back together.
            val hyphenated = a.length > 1 && a.endsWith("-") && a[a.length - 2].isLetter() && b.firstOrNull()?.isLowerCase() == true
            return if (hyphenated) a.dropLast(1) + b else "$a $b".trim()
        }

        /** Index to cut [text] at, or null when there is nowhere sensible to cut. */
        fun splitIndex(text: String): Int? {
            val sentence = Regex("""[.;!?]\s+(?=\S)""").find(text)
            if (sentence != null) return sentence.range.last + 1
            val middle = text.length / 2
            return text.indices
                .filter { text[it] == ' ' && it > 0 && it < text.lastIndex }
                .minByOrNull { kotlin.math.abs(it - middle) }
        }
    }
}
