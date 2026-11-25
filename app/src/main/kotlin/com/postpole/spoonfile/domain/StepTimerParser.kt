package com.postpole.spoonfile.domain

import java.util.Locale

/**
 * Finds the first explicit cooking duration in a recipe step ("Simmer for 15 minutes", "1 h 30 min",
 * "10–12 min"). Understands the units of the six app locales. Temperatures and quantities are ignored
 * because only numbers directly followed by a time unit count.
 */
object StepTimerParser {
    private const val MAX_SECONDS = 12 * 3600

    private enum class Unit(val seconds: Int) { HOUR(3600), MINUTE(60), SECOND(1) }

    private val units: Map<String, Unit> = buildMap {
        listOf("h", "hr", "hrs", "hour", "hours", "std", "stunde", "stunden", "heure", "heures", "ora", "ore", "hora", "horas")
            .forEach { put(it, Unit.HOUR) }
        listOf("min", "mins", "minute", "minutes", "minuten", "minuto", "minutos", "minuti")
            .forEach { put(it, Unit.MINUTE) }
        listOf(
            "s", "sec", "secs", "second", "seconds", "sek", "sekunde", "sekunden", "seconde", "secondes",
            "segundo", "segundos", "secondo", "secondi",
        ).forEach { put(it, Unit.SECOND) }
    }

    private const val NUM = "\\d+(?:[.,]\\d+)?"
    private val timeRegex = Regex(
        "(?<![\\p{L}\\d.,])($NUM)(?:\\s*(?:[-–—]|to|bis|a|à|ou|or)\\s*$NUM)?\\s*(\\p{L}+)",
        RegexOption.IGNORE_CASE,
    )
    private val followRegex = Regex(
        "\\.?\\s*(?:(?:and|und|et|e|y|,)\\s*)?(\\d+)\\s*(\\p{L}+)",
        RegexOption.IGNORE_CASE,
    )

    /** Seconds of the first duration in [text], or null if none (or longer than 12 hours). */
    fun parse(text: String): Int? {
        for (match in timeRegex.findAll(text)) {
            val unit = unitOf(match.groupValues[2]) ?: continue
            var seconds = number(match.groupValues[1]) * unit.seconds
            if (unit == Unit.HOUR) {
                // "1 h 30 min": only an immediately following minutes part counts.
                val start = match.range.last + 1
                val follow = followRegex.find(text, start)?.takeIf { it.range.first == start }
                if (follow != null && unitOf(follow.groupValues[2]) == Unit.MINUTE) {
                    seconds += number(follow.groupValues[1]) * 60
                }
            }
            val total = Math.round(seconds).toInt()
            return if (total in 1..MAX_SECONDS) total else null
        }
        return null
    }

    private fun unitOf(token: String): Unit? = units[token.lowercase(Locale.ROOT)]

    private fun number(raw: String): Double = raw.replace(',', '.').toDouble()
}
