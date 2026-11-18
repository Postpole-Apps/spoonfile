package com.postpole.spoonfile.domain

import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.roundToLong

/**
 * Scales only the leading quantity of an ingredient line ("1 1/2 cups flour" x2 -> "3 cups flour").
 * Understands integers, decimals, `a/b`, mixed numbers, unicode vulgar fractions and ranges
 * (`2-3`). Lines without a leading number are returned unchanged; units are never converted.
 */
object QuantityScaler {
    private const val UNICODE = "½¼¾⅓⅔⅛⅜⅝⅞"
    private const val NUM = "(?:\\d+\\s+\\d+/\\d+|\\d+/\\d+|\\d+\\s*[$UNICODE]|[$UNICODE]|\\d+(?:\\.\\d+)?)"
    private val LEADING = Regex("^(\\s*)($NUM)(?:(\\s*[-–—]\\s*|\\s+to\\s+)($NUM))?(?![\\d/])")

    private val BRACKETED = Regex("\\((\\d+(?:\\.\\d+)?)(\\s*)(g|kg|ml|l|oz|lb)\\)", RegexOption.IGNORE_CASE)
    private const val SMALL_COUNT = 3.0

    /** Words that follow a quantity as a measure (so the amount may be fractional), across EN/DE/FR/ES. */
    private val UNITS = setOf(
        "g", "gr", "gram", "grams", "kg", "mg", "ml", "cl", "dl", "l", "liter", "liters", "litre", "litres",
        "oz", "ounce", "ounces", "lb", "lbs", "pound", "pounds", "cup", "cups", "tbsp", "tbs", "tablespoon", "tablespoons",
        "tsp", "teaspoon", "teaspoons", "pinch", "pinches", "dash", "dashes", "handful", "handfuls", "can", "cans",
        "tin", "tins", "jar", "jars", "pack", "packs", "package", "packages", "bunch", "bunches", "sprig", "sprigs",
        "slice", "slices", "stick", "sticks", "pint", "pints", "quart", "quarts", "gallon", "gallons", "fl",
        "el", "tl", "prise", "prisen", "bund", "dose", "dosen", "becher", "tasse", "tassen",
        "cuillère", "cuillères", "cs", "cc", "pincée", "pincées", "boîte", "boîtes", "verre", "verres",
        "cucharada", "cucharadas", "cucharadita", "cucharaditas", "pizca", "pizcas", "taza", "tazas", "lata", "latas",
    )

    private val UNICODE_VALUES = mapOf(
        '½' to 0.5, '¼' to 0.25, '¾' to 0.75, '⅓' to 1.0 / 3, '⅔' to 2.0 / 3,
        '⅛' to 0.125, '⅜' to 0.375, '⅝' to 0.625, '⅞' to 0.875,
    )

    fun scale(line: String, factor: Double): String {
        if (factor == 1.0 || !factor.isFinite() || factor <= 0.0) return line
        val match = LEADING.find(line) ?: return line
        val (lead, first, separator, second) = match.destructured
        val rest = line.substring(match.range.last + 1)
        val countable = isCountable(rest)
        fun scaled(token: String): String {
            val value = parse(token) * factor
            return format(if (countable) roundCountable(value) else value)
        }
        val replacement = if (separator.isEmpty()) scaled(first) else scaled(first) + separator + scaled(second)
        // "2 (14 oz) cans" is a pack size, not an amount of its own, so a bracket right after the number stays.
        val tail = if (rest.trimStart().startsWith("(")) rest else scaleBracketed(rest, factor)
        return lead + replacement + tail
    }

    /** True when the quantity counts whole things ("3 garlic cloves", "2 large eggs") rather than a unit or bracket. */
    private fun isCountable(rest: String): Boolean {
        val trimmed = rest.trimStart()
        if (trimmed.isEmpty() || !trimmed[0].isLetter()) return false
        val word = trimmed.takeWhile { it.isLetter() || it == '.' }.lowercase().trimEnd('.')
        return word !in UNITS
    }

    /** Items that can't be split read as whole numbers, or halves when the amount is small. */
    private fun roundCountable(value: Double): Double {
        val rounded = if (value < SMALL_COUNT) floor(value * 2 + 0.5) / 2 else floor(value + 0.5)
        return maxOf(rounded, 0.5)
    }

    /** Scales weight/volume amounts in brackets, "(75 g)", so they stay in step with the main quantity. */
    private fun scaleBracketed(text: String, factor: Double): String =
        BRACKETED.replace(text) { m ->
            val amount = m.groupValues[1].toDoubleOrNull() ?: return@replace m.value
            val unit = m.groupValues[3]
            val scaled = amount * factor
            val out = when (unit.lowercase()) {
                "g", "ml" -> (if (scaled >= 50) floor(scaled / 5 + 0.5) * 5 else floor(scaled + 0.5)).toLong().toString()
                else -> format(scaled)
            }
            "($out${m.groupValues[2]}$unit)"
        }

    /** Splits a line into its leading quantity (possibly empty) and the rest: "3 cups flour" -> "3" to "cups flour". */
    fun split(line: String): Pair<String, String> {
        val match = LEADING.find(line) ?: return "" to line.trim()
        return line.substring(0, match.range.last + 1).trim() to line.substring(match.range.last + 1).trim()
    }

    internal fun parse(token: String): Double {
        val t = token.trim()
        UNICODE_VALUES[t.lastOrNull()]?.let { fraction ->
            val whole = t.dropLast(1).trim()
            return (whole.toDoubleOrNull() ?: 0.0) + fraction
        }
        if ('/' in t) {
            val parts = t.split(Regex("\\s+"))
            val (num, den) = parts.last().split('/').map { it.toDouble() }
            val whole = if (parts.size == 2) parts[0].toDouble() else 0.0
            return if (den == 0.0) whole else whole + num / den
        }
        return t.toDouble()
    }

    /** Whole numbers and common fractions (1/8, 1/4, 1/3, 1/2, 2/3, 3/4 ...) read nicely; else up to 2 decimals. */
    internal fun format(value: Double): String {
        var whole = floor(value + EPS).toLong()
        val rest = value - whole
        if (rest < TOLERANCE) return whole.toString()
        if (rest > 1 - TOLERANCE) return (whole + 1).toString()
        for (den in intArrayOf(2, 3, 4, 8)) {
            val num = (rest * den).roundToLong()
            if (num in 1 until den && abs(rest - num.toDouble() / den) < TOLERANCE) {
                val (n, d) = reduce(num.toInt(), den)
                return if (whole == 0L) "$n/$d" else "$whole $n/$d"
            }
        }
        val rounded = (value * 100).roundToLong() / 100.0
        whole = rounded.toLong()
        return if (rounded == whole.toDouble()) whole.toString() else rounded.toString().trimEnd('0').trimEnd('.')
    }

    private fun reduce(n: Int, d: Int): Pair<Int, Int> {
        var a = n
        var b = d
        while (b != 0) a = b.also { b = a % b }
        return n / a to d / a
    }

    private const val EPS = 1e-9
    private const val TOLERANCE = 0.02
}
