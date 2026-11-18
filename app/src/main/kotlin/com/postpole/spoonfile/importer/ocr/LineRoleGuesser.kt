package com.postpole.spoonfile.importer.ocr

enum class LineRole { TITLE, INGREDIENT, STEP, NOTE, IGNORE }

/** Guesses what each recognised line is. The cleanup screen lets the user fix any mistake. */
object LineRoleGuesser {
    private const val MAX_TITLE_LENGTH = 60
    private const val LONG_SENTENCE_LENGTH = 70
    private const val LONG_SENTENCE_WORDS = 9

    private const val BULLETS = "-•*·–—▪●○"
    private val numbered = Regex("""^\s*(step\s*)?\d+\s*[.):]\s+\S""", RegexOption.IGNORE_CASE)
    private val bullet = Regex("^\\s*[$BULLETS]\\s*\\S")
    private val bulletPrefix = Regex("^\\s*[$BULLETS]\\s*")
    private val numberPrefix = Regex("""^\s*(step\s*)?\d+\s*[.):]\s+""", RegexOption.IGNORE_CASE)
    private val quantity = Regex("""^\s*(\d+\s+\d+/\d+|\d+([.,]\d+)?(\s*[-–]\s*\d+([.,]\d+)?)?|\d+/\d+|[½⅓⅔¼¾⅛⅜⅝⅞])(\s|[a-zA-Z]|$)""")
    private val sectionHeading = Regex(
        """^\s*(ingredients?|method|directions?|instructions?|preparation|steps|notes?)\s*:?\s*$""",
        RegexOption.IGNORE_CASE,
    )

    /** Strips the leading bullet from an ingredient line. */
    fun stripBullet(text: String): String = text.replaceFirst(bulletPrefix, "").trim()

    /** Strips a leading "1." / "2)" / "Step 3:" from a step line. */
    fun stripNumber(text: String): String = text.replaceFirst(numberPrefix, "").trim()

    /**
     * Roles for [lines] in order. The first line becomes the Title when it is short and looks like a heading
     * (unless [hasTitle] says the recipe already has one).
     */
    fun guess(lines: List<String>, hasTitle: Boolean = false): List<LineRole> {
        var titleTaken = hasTitle
        var previous: LineRole? = null
        return lines.mapIndexed { index, raw ->
            val line = raw.trim()
            val role = when {
                line.isEmpty() -> LineRole.IGNORE
                sectionHeading.matches(line) -> LineRole.IGNORE
                numbered.containsMatchIn(line) -> LineRole.STEP
                bullet.containsMatchIn(line) || quantity.containsMatchIn(line) -> LineRole.INGREDIENT
                index == 0 && !titleTaken && line.length <= MAX_TITLE_LENGTH && !line.endsWith(".") -> LineRole.TITLE
                isLongSentence(line) -> LineRole.STEP
                // A short unmarked line right after an ingredient is most likely another ingredient.
                previous == LineRole.INGREDIENT -> LineRole.INGREDIENT
                else -> LineRole.NOTE
            }
            if (role == LineRole.TITLE) titleTaken = true
            previous = role
            role
        }
    }

    private fun isLongSentence(line: String): Boolean =
        line.length >= LONG_SENTENCE_LENGTH ||
            (line.endsWith(".") && line.split(Regex("\\s+")).size >= LONG_SENTENCE_WORDS)
}
