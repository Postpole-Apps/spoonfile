package com.postpole.spoonfile.importer.paprika

import com.postpole.spoonfile.importer.web.IsoDuration

/** Reads the free-text times of a recipe-file entry ("15 mins", "1 hr 20 min", "1.5 hours") into whole minutes. */
object PaprikaTime {
    private val hours = Regex("""(\d+(?:[.,]\d+)?)\s*(?:hours?|hrs?|h)(?![a-z])""", RegexOption.IGNORE_CASE)
    private val minutes = Regex("""(\d+)\s*(?:minutes?|mins?|m)(?![a-z])""", RegexOption.IGNORE_CASE)

    /** Minutes for [text], or null when it has no hours/minutes or the total is outside 1..10,000. */
    fun parseMinutes(text: String?): Int? {
        val value = text?.trim().orEmpty()
        if (value.isEmpty()) return null
        val h = hours.find(value)?.groupValues?.get(1)?.replace(',', '.')?.toDoubleOrNull()
        val m = minutes.find(value)?.groupValues?.get(1)?.toLongOrNull()
        if (h == null && m == null) return null
        val total = Math.round((h ?: 0.0) * 60) + (m ?: 0L)
        return IsoDuration.validMinutes(total)
    }
}
