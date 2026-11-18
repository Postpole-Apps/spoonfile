package com.postpole.spoonfile.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupNudgeTest {
    private val day = 24L * 60 * 60 * 1000
    private val now = 100 * day

    private fun show(last: Long? = null, count: Int = 10, remind: Boolean = true, dismissed: Long? = null) =
        BackupNudge.shouldShow(now, last, count, remind, dismissed)

    @Test
    fun showsWhenLastExportIsOverThirtyDaysOld() = assertTrue(show(last = now - 31 * day))

    @Test
    fun hiddenWhenExportIsRecent() = assertFalse(show(last = now - 30 * day))

    @Test
    fun showsWhenNeverExported() = assertTrue(show(last = null))

    @Test
    fun needsTenRecipes() {
        assertFalse(show(count = 9))
        assertTrue(show(count = 10))
    }

    @Test
    fun respectsTheSwitch() = assertFalse(show(remind = false))

    @Test
    fun dismissalHidesItForThirtyDays() {
        assertFalse(show(dismissed = now - 5 * day))
        assertTrue(show(dismissed = now - 31 * day))
    }
}
