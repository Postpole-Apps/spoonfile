package com.postpole.spoonfile.importer.web

import java.time.Duration
import java.time.format.DateTimeParseException

/** Parses schema.org durations (ISO-8601, with a forgiving fallback) into whole minutes. */
object IsoDuration {
    const val MAX_MINUTES = 10_000

    private val hours = Regex("(\\d+)\\s*H", RegexOption.IGNORE_CASE)
    private val minutes = Regex("(\\d+)\\s*M", RegexOption.IGNORE_CASE)

    /** Minutes in 1..[MAX_MINUTES], or null when the text is missing, unreadable, zero, negative or absurd. */
    fun toMinutes(text: String?): Int? {
        val value = text?.trim().orEmpty()
        if (value.isEmpty()) return null
        val total = try {
            Duration.parse(value).toMinutes()
        } catch (_: DateTimeParseException) {
            fallback(value)
        }
        return validate(total)
    }

    fun validate(minutes: Long?): Int? = minutes?.takeIf { it in 1..MAX_MINUTES }?.toInt()

    private fun fallback(value: String): Long? {
        val h = hours.find(value)?.groupValues?.get(1)?.toLongOrNull()
        val m = minutes.find(value)?.groupValues?.get(1)?.toLongOrNull()
        if (h == null && m == null) return null
        return (h ?: 0L) * 60 + (m ?: 0L)
    }
}
