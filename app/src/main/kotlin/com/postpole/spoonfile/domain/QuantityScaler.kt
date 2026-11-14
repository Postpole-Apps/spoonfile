package com.postpole.spoonfile.domain

import kotlin.math.abs
import kotlin.math.floor

/**
 * Scales only the leading quantity of an ingredient line ("1 1/2 cups flour" x2 -> "3 cups flour").
 * Handles integers, decimals, a/b, mixed "1 1/2", unicode fractions and ranges ("2-3").
 * Units are never converted and lines without a leading number are returned unchanged.
 */
object QuantityScaler {
    private const val UNICODE = "½¼¾⅓⅔⅛⅜⅝⅞⅕⅖⅗⅘⅙⅚"
    private const val NUM =
        "(?:\\d+\\s+\\d+/\\d+|\\d+\\s*[$UNICODE]|\\d+/\\d+|[$UNICODE]|\\d+(?:[.,]\\d+)?|\\.\\d+)"
    private val LEADING = Regex("^(\\s*)($NUM)(?:(\\s*[-–—]\\s*|\\s+to\\s+)($NUM))?")

    private val UNICODE_VALUES = mapOf(
        '½' to 1.0 / 2, '¼' to 1.0 / 4, '¾' to 3.0 / 4, '⅓' to 1.0 / 3, '⅔' to 2.0 / 3,
        '⅛' to 1.0 / 8, '⅜' to 3.0 / 8, '⅝' to 5.0 / 8, '⅞' to 7.0 / 8,
        '⅕' to 1.0 / 5, '⅖' to 2.0 / 5, '⅗' to 3.0 / 5, '⅘' to 4.0 / 5,
        '⅙' to 1.0 / 6, '⅚' to 5.0 / 6,
    )

    private val NICE_FRACTIONS = listOf(
        0.0 to "", 1.0 / 8 to "1/8", 1.0 / 4 to "1/4", 1.0 / 3 to "1/3", 3.0 / 8 to "3/8",
        1.0 / 2 to "1/2", 5.0 / 8 to "5/8", 2.0 / 3 to "2/3", 3.0 / 4 to "3/4", 7.0 / 8 to "7/8",
        1.0 to "",
    )

    /** Multiplier to go from [baseServings] to [targetServings]; 1.0 when the base is unknown. */
    fun factor(baseServings: Double?, targetServings: Double): Double =
        if (baseServings == null || baseServings <= 0.0 || targetServings <= 0.0) 1.0 else targetServings / baseServings

    fun scale(line: String, factor: Double): String {
        if (factor == 1.0 || factor <= 0.0 || factor.isNaN() || factor.isInfinite()) return line
        val match = LEADING.find(line) ?: return line
        val lead = match.groupValues[1]
        val firstText = match.groupValues[2]
        val first = parse(firstText) ?: return line
        val separator = match.groupValues[3]
        val secondText = match.groupValues[4]
        val second = if (secondText.isEmpty()) null else parse(secondText)
        if (second != null && second > first) {
            val scaled = format(first * factor, firstText) + separator + format(second * factor, secondText)
            return lead + scaled + line.substring(match.range.last + 1)
        }
        // Not a range (or a nonsensical one): scale only the first number.
        val firstEnd = match.groups[2]!!.range.last + 1
        return lead + format(first * factor, firstText) + line.substring(firstEnd)
    }

    private fun parse(text: String): Double? {
        val t = text.trim()
        val uni = t.lastOrNull()?.let { UNICODE_VALUES[it] }
        if (uni != null) {
            val whole = t.dropLast(1).trim()
            val w = if (whole.isEmpty()) 0.0 else whole.toDoubleOrNull() ?: return null
            return w + uni
        }
        if (t.contains('/')) {
            val parts = t.split(Regex("\\s+"))
            val frac = parts.last().split('/')
            if (frac.size != 2) return null
            val num = frac[0].toDoubleOrNull() ?: return null
            val den = frac[1].toDoubleOrNull() ?: return null
            if (den == 0.0) return null
            val whole = if (parts.size == 2) parts[0].toDoubleOrNull() ?: return null else 0.0
            return whole + num / den
        }
        return t.replace(',', '.').toDoubleOrNull()
    }

    /** Decimal inputs stay decimal; everything else is shown as a mixed number when it is close to one. */
    private fun format(value: Double, original: String): String {
        val isDecimal = original.any { it == '.' || it == ',' }
        if (!isDecimal) {
            val whole = floor(value + 1e-9)
            val frac = value - whole
            val best = NICE_FRACTIONS.minByOrNull { abs(it.first - frac) }!!
            if (abs(best.first - frac) < 0.02) {
                val w = (if (best.first == 1.0) whole + 1 else whole).toLong()
                return when {
                    best.second.isEmpty() -> w.toString()
                    w == 0L -> best.second
                    else -> "$w ${best.second}"
                }
            }
        }
        return decimal(value)
    }

    private fun decimal(value: Double): String {
        val rounded = Math.round(value * 100) / 100.0
        return if (rounded == floor(rounded)) rounded.toLong().toString()
        else rounded.toString().trimEnd('0').trimEnd('.')
    }
}
