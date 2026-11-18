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

    private val UNICODE_VALUES = mapOf(
        '½' to 0.5, '¼' to 0.25, '¾' to 0.75, '⅓' to 1.0 / 3, '⅔' to 2.0 / 3,
        '⅛' to 0.125, '⅜' to 0.375, '⅝' to 0.625, '⅞' to 0.875,
    )

    fun scale(line: String, factor: Double): String {
        if (factor == 1.0 || !factor.isFinite() || factor <= 0.0) return line
        val match = LEADING.find(line) ?: return line
        val (lead, first, separator, second) = match.destructured
        val scaledFirst = format(parse(first) * factor)
        val replacement = if (separator.isEmpty()) scaledFirst else scaledFirst + separator + format(parse(second) * factor)
        return lead + replacement + line.substring(match.range.last + 1)
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
