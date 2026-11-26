package com.postpole.spoonfile.ui.recipe

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.postpole.spoonfile.R
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

/** How a last-cooked stamp is shown relative to today. */
sealed interface LastCookedLabel {
    data object Today : LastCookedLabel
    data object Yesterday : LastCookedLabel
    data class On(val date: LocalDate) : LastCookedLabel
}

/** Classifies [lastCookedAt] (epoch ms) as today, yesterday or a calendar date in [zone]. */
fun lastCookedLabel(lastCookedAt: Long, nowMillis: Long, zone: ZoneId = ZoneId.systemDefault()): LastCookedLabel {
    val day = Instant.ofEpochMilli(lastCookedAt).atZone(zone).toLocalDate()
    val today = Instant.ofEpochMilli(nowMillis).atZone(zone).toLocalDate()
    return when (day) {
        today -> LastCookedLabel.Today
        today.minusDays(1) -> LastCookedLabel.Yesterday
        else -> LastCookedLabel.On(day)
    }
}

/** Locale-formatted medium date, e.g. "Nov 3, 2026". */
fun formatCookedDate(date: LocalDate, locale: Locale = Locale.getDefault()): String =
    DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale).format(date)

/** The "Last cooked ..." text for a stamp, or null when the recipe was never cooked. */
@Composable
fun lastCookedText(lastCookedAt: Long?): String? {
    if (lastCookedAt == null) return null
    return when (val label = lastCookedLabel(lastCookedAt, System.currentTimeMillis())) {
        LastCookedLabel.Today -> stringResource(R.string.detail_last_cooked_today)
        LastCookedLabel.Yesterday -> stringResource(R.string.detail_last_cooked_yesterday)
        is LastCookedLabel.On -> stringResource(R.string.detail_last_cooked, formatCookedDate(label.date))
    }
}
