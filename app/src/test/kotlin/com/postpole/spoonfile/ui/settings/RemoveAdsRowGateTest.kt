package com.postpole.spoonfile.ui.settings

import com.postpole.spoonfile.analytics.Analytics
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.TestScope
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RemoveAdsRowGateTest {
    private class Recorder : Analytics {
        val names = mutableListOf<String>()
        override fun logEvent(name: String, params: Map<String, Any>) { names += name }
        override fun setUserProperty(name: String, value: String?) = Unit
        override fun logScreenView(screenName: String) = Unit
        override fun recordNonFatal(throwable: Throwable) = Unit
    }

    private val analytics = Recorder()
    private val entitlement = MutableStateFlow<Boolean?>(null)
    private val gate = RemoveAdsRowGate(entitlement, TestScope(UnconfinedTestDispatcher()), analytics)

    @Test fun unresolved_hidesRowAndLogsNothing() {
        assertFalse(gate.show.value)
        gate.onViewed()
        assertTrue(analytics.names.isEmpty())
    }

    @Test fun notEntitled_showsRowAndLogsView() {
        entitlement.value = false
        assertTrue(gate.show.value)
        gate.onViewed()
        assertEquals(listOf("remove_ads_paywall_view"), analytics.names)
    }

    @Test fun entitled_hidesRowAndLogsNothing() {
        entitlement.value = true
        assertFalse(gate.show.value)
        gate.onViewed()
        assertTrue(analytics.names.isEmpty())
    }
}
