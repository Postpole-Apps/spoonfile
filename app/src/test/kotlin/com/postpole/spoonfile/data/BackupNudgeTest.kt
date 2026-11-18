package com.postpole.spoonfile.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupNudgeTest {
    private val day = 24L * 60 * 60 * 1000
    private val now = 100 * day

    private fun show(remind: Boolean = true, count: Int = 10, last: Long? = null, dismissed: Long? = null) =
        BackupNudge.shouldShow(remind, count, last, dismissed, now)

    @Test fun neverExported_withTenRecipes_shows() = assertTrue(show())

    @Test fun fewerThanTenRecipes_hidden() = assertFalse(show(count = 9))

    @Test fun reminderOff_hidden() = assertFalse(show(remind = false))

    @Test fun exportedRecently_hidden() = assertFalse(show(last = now - 30 * day))

    @Test fun exportedOverThirtyDaysAgo_shows() = assertTrue(show(last = now - 31 * day))

    @Test fun dismissedRecently_hidden_thenReturns() {
        assertFalse(show(last = now - 60 * day, dismissed = now - 5 * day))
        assertTrue(show(last = now - 60 * day, dismissed = now - 31 * day))
    }
}
