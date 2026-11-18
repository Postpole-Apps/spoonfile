package com.postpole.spoonfile.ui.importer

import com.postpole.spoonfile.data.DedupeKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PaprikaImportPlanTest {
    private fun item(i: Int, dup: Boolean = false) = PaprikaItem(i, "R$i", dup, hasPhoto = false)

    private fun preview(items: List<PaprikaItem>, remaining: Int, pro: Boolean = false) = PaprikaState.Preview(
        items = items, ticked = initialTicks(items, remaining), unreadable = 0, truncated = false, isPro = pro, remaining = remaining,
    )

    @Test
    fun duplicatesByUidAndByTitleAndSource() {
        val finder = DuplicateFinder(
            listOf(
                DedupeKey("saved-1", "Soup", null, "https://a.example/soup"),
                DedupeKey("saved-2", "Cake", "Gran", null),
            ),
        )
        assertTrue(finder.isDuplicate("saved-1", "Other title", null))
        assertTrue(finder.isDuplicate("new-1", " soup ", "https://a.example/soup"))
        assertTrue(finder.isDuplicate(null, "CAKE", "gran"))
        assertFalse(finder.isDuplicate("new-2", "Soup", "https://b.example/soup"))
    }

    @Test
    fun repeatsInsideTheFileAreDuplicates() {
        val finder = DuplicateFinder(emptyList())
        assertFalse(finder.isDuplicate("x", "A", null))
        assertTrue(finder.isDuplicate("x", "B", null))
        assertFalse(finder.isDuplicate("y", "C", "s"))
        assertTrue(finder.isDuplicate("z", "C", "s"))
    }

    @Test
    fun freeRoomExampleCountsExcludeDuplicates() {
        // 48 recipes in the file, 2 duplicates, 33 free slots.
        val items = (0 until 48).map { item(it, dup = it == 5 || it == 20) }
        val p = preview(items, remaining = 33)
        assertEquals(46, p.newCount)
        assertTrue(p.overCap)
        assertEquals(33, p.ticked.size)
        assertFalse(5 in p.ticked || 20 in p.ticked)
        // First 33 new recipes in file order: indexes 0..34 minus the two duplicates.
        assertEquals((0..34).filter { it != 5 && it != 20 }.toSet(), p.ticked)
    }

    @Test
    fun summaryKeepsDuplicatesApartFromOtherSkips() {
        // 48 in file, 2 duplicates, 33 free slots: preview and import-all M are 46, 33 ticked are imported.
        val items = (0 until 48).map { item(it, dup = it == 5 || it == 20) }
        val p = preview(items, remaining = 33)
        assertEquals(46, p.newCount)
        val (skipped, duplicates) = skipCounts(items, p.ticked, imported = 33, failed = 0)
        assertEquals(2, duplicates)
        assertEquals(13, skipped)
        assertEquals(48, 33 + skipped + duplicates)
    }

    @Test
    fun tickedDuplicateIsNotCountedAsSkipped() {
        val items = (0 until 4).map { item(it, dup = it < 2) }
        val (skipped, duplicates) = skipCounts(items, setOf(0, 2, 3), imported = 3, failed = 0)
        assertEquals(1, duplicates)
        assertEquals(0, skipped)
    }

    @Test
    fun everythingFitsHasNoBanner() {
        val items = (0 until 10).map { item(it, dup = it == 0) }
        val p = preview(items, remaining = 10)
        assertFalse(p.overCap)
        assertEquals(9, p.ticked.size)
    }

    @Test
    fun duplicatesDoNotTriggerTheBanner() {
        // 12 recipes but 3 are duplicates: 9 new ones fit in 9 slots.
        val items = (0 until 12).map { item(it, dup = it < 3) }
        assertFalse(preview(items, remaining = 9).overCap)
    }

    @Test
    fun proNeverShowsTheBanner() {
        val items = (0 until 200).map { item(it) }
        val p = preview(items, remaining = Int.MAX_VALUE, pro = true)
        assertFalse(p.overCap)
        assertEquals(200, p.ticked.size)
    }

    @Test
    fun noRoomTicksNothing() {
        assertTrue(initialTicks((0 until 5).map { item(it) }, 0).isEmpty())
    }
}
