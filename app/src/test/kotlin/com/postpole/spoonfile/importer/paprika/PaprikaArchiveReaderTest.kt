package com.postpole.spoonfile.importer.paprika

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.ByteArrayInputStream
import java.time.LocalDateTime
import java.time.ZoneId

@RunWith(RobolectricTestRunner::class)
class PaprikaArchiveReaderTest {
    private val fixedNow = 1_700_000_000_000L
    private val reader = PaprikaArchiveReader { fixedNow }

    private fun read(bytes: ByteArray, photos: Boolean = true): Pair<List<PaprikaRecipe>, PaprikaReadSummary> {
        val list = mutableListOf<PaprikaRecipe>()
        val summary = reader.read(ByteArrayInputStream(bytes), photos) { list += it; true }
        return list to summary
    }

    private fun resource(name: String): ByteArray =
        checkNotNull(javaClass.classLoader!!.getResourceAsStream("paprika/$name")) { "missing $name" }.use { it.readBytes() }

    private fun one(json: String, photos: Boolean = true): PaprikaRecipe =
        read(PaprikaFixtures.zip(PaprikaFixtures.recipe("x.paprikarecipe", json)), photos).first.single()

    // ---- archive layout ----

    @Test
    fun flatArchive() {
        val (recipes, summary) = read(PaprikaFixtures.flat())
        assertEquals(listOf("Tomato Soup", "Pancakes"), recipes.map { it.recipe.title })
        assertEquals(listOf(0, 1), recipes.map { it.index })
        assertEquals(0, summary.failed)
        assertEquals(listOf("1 onion", "2 tomatoes"), recipes[0].recipe.ingredients.map { it.text })
    }

    @Test
    fun nestedFolderSkipsDebris() {
        val (recipes, summary) = read(PaprikaFixtures.nestedWithDebris())
        assertEquals(listOf("Salad"), recipes.map { it.recipe.title })
        assertEquals(0, summary.failed)
    }

    @Test
    fun entryNameMatching() {
        assertTrue(PaprikaArchiveReader.isRecipeEntry("a/b/c.paprikarecipe"))
        assertTrue(PaprikaArchiveReader.isRecipeEntry("c.PAPRIKARECIPE"))
        assertFalse(PaprikaArchiveReader.isRecipeEntry("__MACOSX/a/._c.paprikarecipe"))
        assertFalse(PaprikaArchiveReader.isRecipeEntry("a/.c.paprikarecipe"))
        assertFalse(PaprikaArchiveReader.isRecipeEntry("a/c.txt"))
        assertFalse(PaprikaArchiveReader.isRecipeEntry("a/c.paprikarecipes"))
    }

    @Test
    fun titleComesFromJsonNotEntryName() {
        val (recipes, _) = read(PaprikaFixtures.nonAsciiNames())
        assertEquals(listOf("Crème brûlée", "Crème", "カレー"), recipes.map { it.recipe.title })
    }

    @Test
    fun duplicateUidIsKeptForThePreviewToFlag() {
        val (recipes, _) = read(PaprikaFixtures.duplicateUid())
        assertEquals(listOf("dup", "dup"), recipes.map { it.uid })
    }

    // ---- failures and limits ----

    @Test
    fun nonGzipEntryIsFailedAndSkipped() {
        val (recipes, summary) = read(PaprikaFixtures.nonGzipEntry())
        assertEquals(listOf("Fine"), recipes.map { it.recipe.title })
        assertEquals(1, summary.failures[PaprikaFailure.NOT_GZIP])
    }

    @Test
    fun oversizedEntryIsFailedAndSkipped() {
        val (recipes, summary) = read(PaprikaFixtures.oversizedEntry())
        assertEquals(listOf("Fine"), recipes.map { it.recipe.title })
        assertEquals(1, summary.failures[PaprikaFailure.TOO_LARGE])
    }

    @Test
    fun invalidJsonAndMissingNameAreFailed() {
        val bytes = PaprikaFixtures.zip(
            PaprikaFixtures.recipe("a.paprikarecipe", "{ not json"),
            PaprikaFixtures.recipe("b.paprikarecipe", PaprikaFixtures.recipeJson(null, "x")),
            PaprikaFixtures.recipe("c.paprikarecipe", PaprikaFixtures.recipeJson("   ", "y")),
            PaprikaFixtures.recipe("d.paprikarecipe", PaprikaFixtures.recipeJson("Ok", "z")),
        )
        val (recipes, summary) = read(bytes)
        assertEquals(listOf("Ok"), recipes.map { it.recipe.title })
        assertEquals(1, summary.failures[PaprikaFailure.INVALID])
        assertEquals(2, summary.failures[PaprikaFailure.NO_TITLE])
    }

    @Test
    fun nonZipThrows() {
        try {
            read("just some text, not a zip file".toByteArray())
            fail("expected NotAnArchiveException")
        } catch (_: NotAnArchiveException) {
        }
        try {
            read(ByteArray(0))
            fail("expected NotAnArchiveException")
        } catch (_: NotAnArchiveException) {
        }
    }

    @Test
    fun emptyZipHasNoRecipes() {
        val (recipes, summary) = read(PaprikaFixtures.zip())
        assertTrue(recipes.isEmpty())
        assertEquals(0, summary.failed)
    }

    @Test
    fun entryCountLimit() {
        val entry = PaprikaFixtures.recipe("x.paprikarecipe", """{"name":"A"}""")
        val entries = (0..PaprikaArchiveReader.MAX_ENTRIES).map { PaprikaFixtures.Entry("r$it.paprikarecipe", entry.bytes) }
        val (recipes, summary) = read(PaprikaFixtures.zip(entries))
        assertEquals(PaprikaArchiveReader.MAX_ENTRIES, recipes.size)
        assertTrue(summary.truncated)
    }

    @Test
    fun stopsEarlyWhenCallbackReturnsFalse() {
        var seen = 0
        reader.read(ByteArrayInputStream(PaprikaFixtures.flat())) { seen++; false }
        assertEquals(1, seen)
    }

    // ---- field mapping ----

    @Test
    fun fullFieldMapping() {
        val r = one(
            PaprikaFixtures.recipeJson(
                "  Soup  ", "u-1",
                "ingredients" to "1 onion\n\n  2 carrots  \n",
                "directions" to "1. Chop.\n\n2. Simmer.",
                "notes" to "Tasty",
                "description" to "A soup",
                "nutritional_info" to "100 kcal",
                "servings" to "4 servings",
                "prep_time" to "15 mins",
                "cook_time" to "1 hr 20 min",
                "total_time" to "1 hr 35 min",
                "difficulty" to "Easy",
                "rating" to 9,
                "categories" to listOf("Soup", " Dinner ", "Soup", ""),
                "source" to "Gran",
                "source_url" to "https://example.com/s",
                "image_url" to "https://example.com/i.jpg",
                "created" to "2023-05-06 07:08:09",
                "photo_hash" to "abc",
                "something_new" to listOf(1, 2),
            ),
        )
        assertEquals("Soup", r.recipe.title)
        assertEquals("u-1", r.uid)
        assertEquals(listOf("1 onion", "2 carrots"), r.recipe.ingredients.map { it.text })
        assertEquals(listOf("Chop.", "Simmer."), r.recipe.steps.map { it.text })
        assertEquals("A soup", r.recipe.description)
        assertEquals("100 kcal", r.recipe.nutritionText)
        assertEquals("4 servings", r.recipe.servingsText)
        assertEquals(4.0, r.recipe.servings!!, 0.0)
        assertEquals(15, r.recipe.prepMinutes)
        assertEquals(80, r.recipe.cookMinutes)
        assertEquals(95, r.recipe.totalMinutes)
        assertEquals("Tasty\n\nDifficulty: Easy", r.notes)
        assertEquals(5, r.rating)
        assertEquals(listOf("Soup", "Dinner"), r.categories)
        assertEquals("Gran", r.recipe.sourceName)
        assertEquals("https://example.com/s", r.recipe.sourceUrl)
        assertNull(r.recipe.imageUrl)
        val expected = LocalDateTime.of(2023, 5, 6, 7, 8, 9).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        assertEquals(expected, r.createdAt)
    }

    @Test
    fun nullAndEmptyFields() {
        val r = one(
            PaprikaFixtures.recipeJson(
                "Bare", null,
                "ingredients" to null, "directions" to "", "notes" to "  ", "servings" to null,
                "rating" to null, "categories" to null, "created" to null, "source" to null,
            ),
        )
        assertEquals("Bare", r.recipe.title)
        assertNull(r.uid)
        assertTrue(r.recipe.ingredients.isEmpty())
        assertTrue(r.recipe.steps.isEmpty())
        assertNull(r.notes)
        assertNull(r.recipe.servingsText)
        assertNull(r.recipe.servings)
        assertNull(r.recipe.sourceName)
        assertEquals("", r.recipe.sourceUrl)
        assertEquals(0, r.rating)
        assertTrue(r.categories.isEmpty())
        assertEquals(fixedNow, r.createdAt)
        assertFalse(r.hasPhoto)
    }

    @Test
    fun freeTextTimesFallBackToNotes() {
        val r = one(
            PaprikaFixtures.recipeJson(
                "T", "t",
                "prep_time" to "overnight", "cook_time" to "about an hour", "total_time" to "2 hrs",
            ),
        )
        assertNull(r.recipe.prepMinutes)
        assertNull(r.recipe.cookMinutes)
        assertEquals(120, r.recipe.totalMinutes)
        assertEquals("Prep time: overnight\n\nCook time: about an hour", r.notes)
    }

    @Test
    fun servingsLeadingNumber() {
        assertEquals(6.0, one(PaprikaFixtures.recipeJson("S", "s", "servings" to "6-8 people")).recipe.servings!!, 0.0)
        assertNull(one(PaprikaFixtures.recipeJson("S", "s", "servings" to "a crowd")).recipe.servings)
    }

    @Test
    fun directionsWithAndWithoutBlankLines() {
        assertEquals(listOf("A", "B"), PaprikaRecipeJson.splitDirections("1. A\n2. B"))
        assertEquals(listOf("A line\nsecond line", "B"), PaprikaRecipeJson.splitDirections("A line\nsecond line\n\nB"))
        assertEquals(listOf("A", "B"), PaprikaRecipeJson.splitDirections("A\r\n\r\nB\r\n"))
        assertEquals(listOf("10 minutes is long"), PaprikaRecipeJson.splitDirections("10 minutes is long"))
        assertTrue(PaprikaRecipeJson.splitDirections("  \n ").isEmpty())
    }

    @Test
    fun badCreatedFallsBackToNow() {
        assertEquals(fixedNow, one(PaprikaFixtures.recipeJson("C", "c", "created" to "yesterday")).createdAt)
    }

    @Test
    fun photoDecoding() {
        val ok = read(PaprikaFixtures.goodPhoto(byteArrayOf(1, 2, 3, 4))).first.single()
        assertTrue(ok.hasPhoto)
        assertEquals(listOf<Byte>(1, 2, 3, 4), ok.photo!!.toList())

        val withoutPhotos = read(PaprikaFixtures.goodPhoto(), photos = false).first.single()
        assertTrue(withoutPhotos.hasPhoto)
        assertNull(withoutPhotos.photo)
    }

    @Test
    fun badBase64KeepsTheRecipeWithoutAPhoto() {
        val r = read(PaprikaFixtures.badBase64()).first.single()
        assertEquals("Photo Fail", r.recipe.title)
        assertNull(r.photo)
    }

    // ---- committed CC0 archives ----

    @Test
    fun committedWeeknightBasics() {
        val (recipes, summary) = read(resource("weeknight-basics.paprikarecipes"))
        assertEquals(listOf("Tomato Soup", "Pancakes"), recipes.map { it.recipe.title })
        assertEquals(0, summary.failed)
        assertEquals(listOf("Soup", "Vegetarian"), recipes[0].categories)
        assertEquals(3, recipes[0].recipe.steps.size)
        assertEquals(80, recipes[1].recipe.prepMinutes)
        assertEquals(listOf("Whisk everything into a smooth batter.", "Rest 10 minutes.", "Fry thin pancakes in a hot buttered pan."),
            recipes[1].recipe.steps.map { it.text })
    }

    @Test
    fun committedNestedWithDebris() {
        val (recipes, summary) = read(resource("nested-with-debris.paprikarecipes"))
        assertEquals(listOf("Green Salad"), recipes.map { it.recipe.title })
        assertEquals(0, summary.failed)
        assertEquals("https://example.com/green-salad", recipes[0].recipe.sourceUrl)
    }

    @Test
    fun committedMixedQuality() {
        val (recipes, summary) = read(resource("mixed-quality.paprikarecipes"))
        assertEquals(listOf("Plain Rice"), recipes.map { it.recipe.title })
        assertEquals(1, summary.failures[PaprikaFailure.NOT_GZIP])
        assertEquals(1, summary.failures[PaprikaFailure.NO_TITLE])
        assertEquals("Total time: overnight", recipes[0].notes)
        assertNull(recipes[0].photo)
    }
}
