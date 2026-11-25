package com.postpole.spoonfile.ui

import androidx.compose.material3.SnackbarHostState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SnackbarsTest {
    @Test
    fun repeatedShowsReplaceCurrentSnackbarInsteadOfQueueing() = runTest(UnconfinedTestDispatcher()) {
        val host = SnackbarHostState()
        val jobs = (1..5).map { i -> launch { host.showLatestSnackbar("Added $i") } }

        assertEquals("Added 5", host.currentSnackbarData?.visuals?.message)
        // Earlier calls were dismissed; only the latest is still pending.
        assertEquals(4, jobs.count { it.isCompleted })

        host.currentSnackbarData?.dismiss()
        // Nothing stays queued afterwards.
        assertNull(host.currentSnackbarData)
        assertEquals(5, jobs.count { it.isCompleted })
    }
}
