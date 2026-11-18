package com.postpole.spoonfile.exporter

import com.postpole.spoonfile.data.CategoryEntity
import com.postpole.spoonfile.data.IngredientEntity
import com.postpole.spoonfile.data.RecipeEntity
import com.postpole.spoonfile.data.RecipeWithDetails
import com.postpole.spoonfile.data.StepEntity
import com.postpole.spoonfile.importer.paprika.PaprikaArchiveReader
import com.postpole.spoonfile.importer.paprika.PaprikaRecipe
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.GZIPInputStream
import java.util.zip.ZipInputStream

@RunWith(RobolectricTestRunner::class)
class PaprikaArchiveWriterTest {
    private val photoBytes = byteArrayOf(1, 2, 3, 4, 5, -1, -2, 127)

    private fun pancakes() = RecipeWithDetails(
        recipe = RecipeEntity(
            uid = "uid-1", title = "Pancakes", description = "Fluffy", servingsText = "4 servings", servings = 4.0,
            prepMinutes = 10, cookMinutes = 20, totalMinutes = 30, notes = "Rest the batter", nutritionText = "200 kcal",
            sourceName = "Gran", sourceUrl = "https://example.com/p", imageFile = "a.jpg", rating = 4,
            createdAt = 1_700_000_000_000, updatedAt = 1_700_000_000_000,
        ),
        ingredients = listOf(
            IngredientEntity(position = 0, text = "Batter", isHeader = true),
            IngredientEntity(position = 1, text = "2 cups flour"),
            IngredientEntity(position = 2, text = "1 egg"),
        ),
        steps = listOf(StepEntity(position = 0, text = "Mix well."), StepEntity(position = 1, text = "Fry\n\nuntil gold.")),
        categories = listOf(CategoryEntity(id = 1, name = "Breakfast"), CategoryEntity(id = 2, name = "Quick", sortOrder = 1)),
    )

    private fun toothpaste() = RecipeWithDetails(
        recipe = RecipeEntity(uid = "uid-2", title = "Soup: a/b?", createdAt = 1_600_000_000_000, updatedAt = 1),
        ingredients = listOf(IngredientEntity(position = 0, text = "water")),
        steps = listOf(StepEntity(position = 0, text = "Boil.")),
        categories = emptyList(),
    )

    private fun write(vararg recipes: RecipeWithDetails): ByteArray {
        val out = ByteArrayOutputStream()
        val count = PaprikaArchiveWriter().write(out, recipes.asSequence()) { if (it.recipe.imageFile != null) photoBytes else null }
        assertEquals(recipes.size, count)
        return out.toByteArray()
    }

    private fun read(bytes: ByteArray): List<PaprikaRecipe> {
        val list = mutableListOf<PaprikaRecipe>()
        val summary = PaprikaArchiveReader().read(ByteArrayInputStream(bytes)) { list += it; true }
        assertEquals(0, summary.failed)
        return list
    }

    @Test
    fun roundTrip_throughTheReader() {
        val result = read(write(pancakes(), toothpaste()))
        assertEquals(listOf("Pancakes", "Soup: a/b?"), result.map { it.recipe.title })

        val p = result[0]
        assertEquals(listOf("Batter", "2 cups flour", "1 egg"), p.recipe.ingredients.map { it.text })
        assertEquals(listOf("Mix well.", "Fry\nuntil gold."), p.recipe.steps.map { it.text })
        assertEquals(listOf("Breakfast", "Quick"), p.categories)
        assertArrayEquals(photoBytes, p.photo)
        assertEquals("uid-1", p.uid)
        assertEquals(4, p.rating)
        assertEquals(10, p.recipe.prepMinutes)
        assertEquals(20, p.recipe.cookMinutes)
        assertEquals(30, p.recipe.totalMinutes)
        assertEquals("Gran", p.recipe.sourceName)
        assertEquals("https://example.com/p", p.recipe.sourceUrl)
        assertEquals("Rest the batter", p.notes)
        assertEquals("200 kcal", p.recipe.nutritionText)
        assertEquals("Fluffy", p.recipe.description)

        assertNull(result[1].photo)
        assertEquals(emptyList<String>(), result[1].categories)
    }

    @Test
    fun entries_areGzipJsonWithTheDocumentedFields() {
        val zip = ZipInputStream(ByteArrayInputStream(write(pancakes(), toothpaste())))
        val names = mutableListOf<String>()
        var first: org.json.JSONObject? = null
        while (true) {
            val entry = zip.nextEntry ?: break
            names += entry.name
            val json = GZIPInputStream(object : java.io.FilterInputStream(zip) { override fun close() = Unit })
                .readBytes().toString(Charsets.UTF_8)
            if (first == null) first = org.json.JSONObject(json)
        }
        assertTrue(names.all { it.endsWith(".paprikarecipe") && !it.contains('/') && !it.contains(':') && !it.contains('?') })
        assertEquals(2, names.toSet().size)

        val o = first!!
        assertEquals("Batter\n2 cups flour\n1 egg", o.getString("ingredients"))
        assertEquals("Mix well.\n\nFry\nuntil gold.", o.getString("directions"))
        assertEquals("10 min", o.getString("prep_time"))
        assertEquals("30 min", o.getString("total_time"))
        assertEquals(64, o.getString("hash").length)
        assertTrue(o.getString("created").matches(Regex("""\d{4}-\d\d-\d\d \d\d:\d\d:\d\d""")))
        assertEquals(java.util.Base64.getEncoder().encodeToString(photoBytes), o.getString("photo_data"))
    }

    @Test
    fun duplicateTitles_getDistinctEntries() {
        val a = pancakes()
        val b = pancakes().let { it.copy(recipe = it.recipe.copy(uid = "other")) }
        val result = read(write(a, b))
        assertEquals(2, result.size)
    }

    @Test
    fun emptyLibrary_writesAnEmptyArchive() {
        val bytes = write()
        assertEquals(0, ZipInputStream(ByteArrayInputStream(bytes)).let { zip -> generateSequence { zip.nextEntry }.count() })
    }
}
