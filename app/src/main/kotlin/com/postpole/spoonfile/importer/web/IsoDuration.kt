package com.postpole.spoonfile.importer.web

import java.time.Duration

/** Parses schema.org time values (ISO-8601 durations) into whole minutes. */
object IsoDuration {
    private const val MAX_MINUTES = 10_000
    private val hours = Regex("""(\d+)\s*H""", RegexOption.IGNORE_CASE)
    private val minutes = Regex("""(\d+)\s*M""", RegexOption.IGNORE_CASE)

    /** Minutes for [text], or null when it can't be read or is not within 1..10,000. */
    fun parseMinutes(text: String?): Int? {
        val value = text?.trim().orEmpty()
        if (value.isEmpty()) return null
        val parsed = try {
            Duration.parse(value.uppercase()).toMinutes()
        } catch (_: java.time.format.DateTimeParseException) {
            fallback(value)
        }
        return validMinutes(parsed)
    }

    /** Range check shared with numeric `QuantitativeValue` times. */
    fun validMinutes(value: Long?): Int? = value?.takeIf { it in 1..MAX_MINUTES }?.toInt()

    private fun fallback(value: String): Long? {
        val h = hours.find(value)?.groupValues?.get(1)?.toLongOrNull()
        val m = minutes.find(value)?.groupValues?.get(1)?.toLongOrNull()
        if (h == null && m == null) return null
        return (h ?: 0L) * 60 + (m ?: 0L)
    }
}
