package com.postpole.spoonfile.importer.ocr

enum class LineRole { TITLE, INGREDIENT, STEP, NOTE, IGNORE }

/** Pure-Kotlin first guess at what each scanned line is; the cleanup screen lets the user fix mistakes. */
object LineRoleGuesser {
    private const val LONG_SENTENCE_CHARS = 60
    private const val TITLE_MAX_CHARS = 60

    private val bullet = Regex("^\\s*[•·▪‣◦*\\-–—+]\\s+")
    private val numbered = Regex("^\\s*(?:step\\s+)?\\d{1,2}\\s*[.):]\\s+\\S", RegexOption.IGNORE_CASE)
    private val numberPrefix = Regex("^\\s*(?:step\\s+)?\\d{1,2}\\s*[.):]\\s+", RegexOption.IGNORE_CASE)
    private const val FRACTIONS = "½⅓⅔¼¾⅛⅜⅝⅞"
    private val quantity = Regex(
        "^\\s*(?:\\d+\\s+\\d+\\s*/\\s*\\d+|\\d+\\s*/\\s*\\d+|\\d+(?:[.,]\\d+)?(?:\\s*[-–]\\s*\\d+(?:[.,]\\d+)?)?|[$FRACTIONS])" +
            "(?:\\s*[$FRACTIONS])?(?:\\s|$)",
    )

    fun startsWithBullet(line: String): Boolean = bullet.containsMatchIn(line)

    fun startsWithNumber(line: String): Boolean = numbered.containsMatchIn(line)

    fun startsWithQuantity(line: String): Boolean = quantity.containsMatchIn(line)

    /** Guesses a role for every line. The first short line that is not an ingredient or step is the title. */
    fun guess(lines: List<String>): List<LineRole> {
        var titleTaken = false
        return lines.map { raw ->
            val line = raw.trim()
            when {
                line.isEmpty() -> LineRole.IGNORE
                startsWithNumber(line) -> LineRole.STEP
                startsWithBullet(line) || startsWithQuantity(line) -> LineRole.INGREDIENT
                line.length >= LONG_SENTENCE_CHARS -> LineRole.STEP
                !titleTaken && line.length <= TITLE_MAX_CHARS -> {
                    titleTaken = true
                    LineRole.TITLE
                }
                else -> LineRole.NOTE
            }
        }
    }

    /** The line without its leading bullet or step number, for saving into a list. */
    fun stripMarker(line: String, role: LineRole): String {
        val text = line.trim()
        return when (role) {
            LineRole.INGREDIENT -> text.replace(bullet, "")
            LineRole.STEP -> text.replace(numberPrefix, "").replace(bullet, "")
            else -> text
        }.trim()
    }
}
