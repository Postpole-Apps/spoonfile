package com.postpole.spoonfile.domain

/** One line of an ingredient or step list; [isHeader] lines come from a leading `#` in the editor. */
data class ParsedLine(val text: String, val isHeader: Boolean)

/** Editor text -> lines: one per non-blank line; a line starting with `#` is a section header. */
fun parseLines(text: String): List<ParsedLine> = text.lines().mapNotNull { raw ->
    val line = raw.trim()
    when {
        line.isEmpty() -> null
        line.startsWith("#") -> line.trimStart('#').trim().takeIf { it.isNotEmpty() }?.let { ParsedLine(it, true) }
        else -> ParsedLine(line, false)
    }
}

/** Inverse of [parseLines], for filling the editor. */
fun linesToEditorText(lines: List<ParsedLine>): String =
    lines.joinToString("\n") { if (it.isHeader) "# ${it.text}" else it.text }
