package com.postpole.spoonfile.ui.importer

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import com.postpole.spoonfile.analytics.Analytics
import com.postpole.spoonfile.importer.ocr.LineRole
import com.postpole.spoonfile.importer.ocr.OcrEngine
import com.postpole.spoonfile.importer.ocr.PagePhotoReader
import com.postpole.spoonfile.importer.ocr.ScanCache
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
            if (fail) throw IllegalStateException("boom")
            return lines
        }
        var closed = 0
        override fun close() {
            closed++
        }
    }

    private class FakeCache : ScanCache {
        var cleared = 0
        override fun clear() {
            cleared++
        }
    }

    private class FakeReader : PagePhotoReader {
        override suspend fun read(uri: Uri): ByteArray? = byteArrayOf(1, 2, 3)
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

    private val ocr = FakeOcr(listOf("Lemon Cake", "200 g flour", "1. Mix the flour", "Keeps well"))
    private val analytics = RecordingAnalytics()

    @Before fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After fun tearDown() = Dispatchers.resetMain()

    private val cache = FakeCache()

    private fun vm(handle: SavedStateHandle = SavedStateHandle()) =
        ScanViewModel(ocr, FakeReader(), analytics, cache, handle)

    private fun page(n: Int): Uri = Uri.parse("content://test/page$n")

    @Test fun usePagePhotoIsOffByDefault() {
        assertFalse(vm().state.value.usePagePhoto)
    }

    @Test fun addPageReadsLinesAndGuessesRoles() {
        val vm = vm()
        vm.addPage(page(1))
        val s = vm.state.value
        assertEquals(1, s.pages.size)
        assertEquals(listOf(LineRole.TITLE, LineRole.INGREDIENT, LineRole.STEP, LineRole.NOTE), s.lines.map { it.role })
    }

    @Test fun pageCapIsFour() {
        val vm = vm()
        (1..6).forEach { vm.addPage(page(it)) }
        assertEquals(4, vm.state.value.pages.size)
        assertEquals(4, ocr.seen.size)
        assertFalse(vm.state.value.canAddPage)
    }

    @Test fun laterPageDoesNotClaimSecondTitle() {
        val vm = vm()
        vm.addPage(page(1))
        vm.addPage(page(2))
        assertEquals(1, vm.state.value.lines.count { it.role == LineRole.TITLE })
    }

    @Test fun failedReadAddsNoPageAndShowsMessage() {
        ocr.fail = true
        val vm = vm()
        vm.addPage(page(1))
        assertTrue(vm.state.value.readFailed)
        assertTrue(vm.state.value.pages.isEmpty())
        assertFalse(vm.state.value.busy)
        // Analytics carry only the source and the result.
        assertEquals(mapOf("import_source" to "ocr", "result" to "ocr_failed"), analytics.events.single().second)
    }

    @Test fun emptyOcrAddsNoPageSetsReadFailedAndKeepsEarlierPages() {
        val vm = vm()
        vm.addPage(page(1))
        val before = vm.state.value
        ocr.lines = emptyList()
        vm.addPage(page(2))
        val s = vm.state.value
        assertTrue(s.readFailed)
        assertFalse(s.busy)
        assertEquals(before.pages, s.pages)
        assertEquals(before.lines, s.lines)
        // A later good photo clears the message.
        ocr.lines = listOf("More")
        vm.addPage(page(3))
        assertFalse(vm.state.value.readFailed)
        assertEquals(2, vm.state.value.pages.size)
    }

    @Test fun emptyOcrOnFirstPhotoAddsNoPage() {
        ocr.lines = emptyList()
        val vm = vm()
        vm.addPage(page(1))
        assertTrue(vm.state.value.readFailed)
        assertTrue(vm.state.value.pages.isEmpty())
    }

    @Test fun closingTheViewModelClosesTheRecognizerAndClearsTheCache() {
        val vm = vm()
        vm.addPage(page(1))
        vm.onSaved()
        assertEquals(1, cache.cleared)
        val onCleared = ScanViewModel::class.java.getDeclaredMethod("onCleared").apply { isAccessible = true }
        onCleared.invoke(vm)
        assertEquals(1, ocr.closed)
        assertEquals(2, cache.cleared)
    }

    @Test fun resetClearsStateAndCache() {
        val vm = vm()
        vm.addPage(page(1))
        vm.reset()
        assertTrue(vm.state.value.pages.isEmpty())
        assertTrue(vm.state.value.lines.isEmpty())
        assertEquals(1, cache.cleared)
    }

    @Test fun stateSurvivesProcessDeathViaSavedStateHandle() {
        val handle = SavedStateHandle()
        val first = vm(handle)
        first.addPage(page(1))
        first.setUsePagePhoto(true)
        first.setRole(first.state.value.lines[3].id, LineRole.IGNORE)
        val expected = first.state.value

        val restored = vm(handle)
        val s = restored.state.value
        assertEquals(expected.pages, s.pages)
        assertEquals(expected.lines, s.lines)
        assertTrue(s.usePagePhoto)
        // New lines never reuse a restored id.
        restored.split(s.lines[1].id, 5)
        assertEquals(s.lines.size + 1, restored.state.value.lines.map { it.id }.toSet().size)
    }

    @Test fun roleEdit() {
        val vm = vm()
        vm.addPage(page(1))
        val id = vm.state.value.lines[3].id
        vm.setRole(id, LineRole.IGNORE)
        assertEquals(LineRole.IGNORE, vm.state.value.lines[3].role)
    }

    @Test fun mergeWithNextJoinsAndKeepsFirstRole() {
        val vm = vm()
        vm.addPage(page(1))
        vm.mergeWithNext(vm.state.value.lines[1].id)
        val lines = vm.state.value.lines
        assertEquals(3, lines.size)
        assertEquals("200 g flour 1. Mix the flour", lines[1].text)
        assertEquals(LineRole.INGREDIENT, lines[1].role)
    }

    @Test fun mergeOnLastLineIsNoOp() {
        val vm = vm()
        vm.addPage(page(1))
        vm.mergeWithNext(vm.state.value.lines.last().id)
        assertEquals(4, vm.state.value.lines.size)
    }

    @Test fun splitMakesTwoLines() {
        val vm = vm()
        vm.addPage(page(1))
        vm.split(vm.state.value.lines[1].id, 5)
        val lines = vm.state.value.lines
        assertEquals(5, lines.size)
        assertEquals("200 g", lines[1].text)
        assertEquals("flour", lines[2].text)
    }

    @Test fun splitAtEdgeIsNoOp() {
        val vm = vm()
        vm.addPage(page(1))
        vm.split(vm.state.value.lines[1].id, 0)
        assertEquals(4, vm.state.value.lines.size)
    }

    @Test fun editText() {
        val vm = vm()
        vm.addPage(page(1))
        vm.editText(vm.state.value.lines[0].id, "Lemon Drizzle")
        assertEquals("Lemon Drizzle", vm.state.value.lines[0].text)
    }

    @Test fun pastedTextUsesTheSameLines() {
        val vm = vm()
        vm.startFromText("Pancakes\n\n2 eggs\n  1. Whisk  \n")
        assertEquals(listOf("Pancakes", "2 eggs", "1. Whisk"), vm.state.value.lines.map { it.text })
        assertEquals(listOf(LineRole.TITLE, LineRole.INGREDIENT, LineRole.STEP), vm.state.value.lines.map { it.role })
        assertTrue(vm.state.value.pages.isEmpty())
    }

    @Test fun buildFormGroupsByRoleAndDropsIgnored() {
        val vm = vm()
        vm.addPage(page(1))
        vm.setRole(vm.state.value.lines[3].id, LineRole.IGNORE)
        val form = ScanViewModel.buildForm(vm.state.value.lines)
        assertEquals("Lemon Cake", form.title)
        assertEquals("200 g flour", form.ingredients)
        assertEquals("Mix the flour", form.steps)
        assertEquals("", form.notes)
    }

    @Test fun continueWithoutPhotoByDefault() = runTest {
        val vm = vm()
        vm.addPage(page(1))
        vm.continueImport(logScan = true)
        val event = vm.events.first() as ScanEvent.Continue
        assertNull(event.photo)
        assertEquals("Lemon Cake", event.form.title)
        assertEquals(mapOf("import_source" to "ocr", "result" to "ok"), analytics.events.single().second)
    }

    @Test fun continueWithPhotoWhenSwitchedOn() = runTest {
        val vm = vm()
        vm.addPage(page(1))
        vm.setUsePagePhoto(true)
        vm.continueImport(logScan = true)
        assertArrayEquals(byteArrayOf(1, 2, 3), (vm.events.first() as ScanEvent.Continue).photo)
    }

    @Test fun pastedContinueLogsNothing() = runTest {
        val vm = vm()
        vm.startFromText("Soup\n2 onions")
        vm.continueImport(logScan = false)
        vm.events.first()
        assertTrue(analytics.events.isEmpty())
    }
}
