package com.postpole.spoonfile.ui.importer

import android.net.Uri
import com.postpole.spoonfile.analytics.Analytics
import com.postpole.spoonfile.analytics.AnalyticsEvents
import com.postpole.spoonfile.analytics.AnalyticsParams
import com.postpole.spoonfile.importer.ocr.LineRole
import com.postpole.spoonfile.importer.ocr.OcrEngine
import com.postpole.spoonfile.importer.ocr.PageReader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class ScanViewModelTest {
    private class FakeOcr(var lines: List<String> = emptyList(), var fail: Boolean = false) : OcrEngine {
        val seen = mutableListOf<Uri>()
        override suspend fun recognize(uri: Uri): List<String> {
            seen += uri
            if (fail) throw java.io.IOException("unreadable")
            return lines
        }
    }

    private class FakeReader : PageReader {
        override suspend fun readBytes(uri: Uri): ByteArray? = byteArrayOf(1, 2, 3)
    }

    private class RecordingAnalytics : Analytics {
        val events = mutableListOf<Pair<String, Map<String, Any>>>()
        override fun logEvent(name: String, params: Map<String, Any>) {
            events += name to params
        }
        override fun setUserProperty(name: String, value: String?) = Unit
        override fun logScreenView(screenName: String) = Unit
        override fun recordNonFatal(throwable: Throwable) = Unit
    }

    private val ocr = FakeOcr(listOf("Tomato Soup", "2 cups tomatoes", "1. Simmer until soft."))
    private val analytics = RecordingAnalytics()
    private lateinit var vm: ScanViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        vm = ScanViewModel(ocr, FakeReader(), analytics)
    }

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun page(i: Int): Uri = Uri.parse("content://pages/$i")

    @Test
    fun pagesAreCappedAtFour() {
        repeat(6) { vm.addPage(page(it)) }
        assertEquals(4, vm.state.value.pages.size)
        assertEquals(4, ocr.seen.size)
        assertFalse(vm.canAddPage)
    }

    @Test
    fun recognisedLinesGetGuessedRoles() {
        vm.addPage(page(1))
        val roles = vm.cleanup.state.value.lines.map { it.role }
        assertEquals(listOf(LineRole.TITLE, LineRole.INGREDIENT, LineRole.STEP), roles)
    }

    @Test
    fun aSecondPageAppendsAndKeepsOneTitle() {
        vm.addPage(page(1))
        ocr.lines = listOf("Second page heading", "1 tsp salt")
        vm.addPage(page(2))
        val lines = vm.cleanup.state.value.lines
        assertEquals(5, lines.size)
        assertEquals(1, lines.count { it.role == LineRole.TITLE })
    }

    @Test
    fun rolesCanBeEdited() {
        vm.addPage(page(1))
        val id = vm.cleanup.state.value.lines[1].id
        vm.setRole(id, LineRole.NOTE)
        assertEquals(LineRole.NOTE, vm.cleanup.state.value.lines[1].role)
    }

    @Test
    fun usePagePhotoIsOffByDefault() {
        assertFalse(vm.state.value.usePagePhoto)
        vm.setUsePagePhoto(true)
        assertTrue(vm.state.value.usePagePhoto)
    }

    @Test
    fun continueDoesNotAttachThePhotoUnlessAsked() = runTest {
        vm.addPage(page(1))
        vm.continueToPreview()
        val event = vm.events.first() as ScanEvent.Continue
        assertNull(event.photo)
        assertEquals("Tomato Soup", event.form.title)
        assertEquals("2 cups tomatoes", event.form.ingredients)
        assertEquals("Simmer until soft.", event.form.steps)
    }

    @Test
    fun continueAttachesThePhotoWhenSwitchedOn() = runTest {
        vm.addPage(page(1))
        vm.setUsePagePhoto(true)
        vm.continueToPreview()
        val event = vm.events.first() as ScanEvent.Continue
        assertArrayEquals(byteArrayOf(1, 2, 3), event.photo)
    }

    @Test
    fun ignoredLinesAreLeftOutOfTheRecipe() = runTest {
        vm.addPage(page(1))
        vm.setRole(vm.cleanup.state.value.lines[1].id, LineRole.IGNORE)
        vm.continueToPreview()
        val event = vm.events.first() as ScanEvent.Continue
        assertEquals("", event.form.ingredients)
    }

    @Test
    fun anUnreadablePageIsReportedWithoutCrashing() {
        ocr.fail = true
        vm.addPage(page(1))
        assertTrue(vm.state.value.failed)
        assertFalse(vm.state.value.recognizing)
        assertTrue(vm.cleanup.state.value.lines.isEmpty())
    }

    @Test
    fun aPageWithNoTextIsFlagged() {
        ocr.lines = emptyList()
        vm.addPage(page(1))
        assertTrue(vm.state.value.noText)
    }

    @Test
    fun analyticsGetsOnlySourceAndResult() {
        vm.addPage(page(1))
        val (name, params) = analytics.events.single()
        assertEquals(AnalyticsEvents.RECIPE_IMPORT, name)
        assertEquals(mapOf(AnalyticsParams.IMPORT_SOURCE to "ocr", AnalyticsParams.RESULT to "ok"), params)
    }

    @Test
    fun mergeAndSplitWork() {
        vm.addPage(page(1))
        val first = vm.cleanup.state.value.lines[1].id
        vm.cleanup.mergeWithNext(first)
        assertEquals(2, vm.cleanup.state.value.lines.size)
        assertEquals("2 cups tomatoes 1. Simmer until soft.", vm.cleanup.state.value.lines[1].text)
        vm.cleanup.split(first)
        assertEquals(3, vm.cleanup.state.value.lines.size)
        assertEquals("2 cups tomatoes 1.", vm.cleanup.state.value.lines[1].text)
    }
}
