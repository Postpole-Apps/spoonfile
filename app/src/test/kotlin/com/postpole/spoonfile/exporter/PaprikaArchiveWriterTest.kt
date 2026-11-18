package com.postpole.spoonfile.exporter

import com.postpole.spoonfile.data.CategoryEntity
import com.postpole.spoonfile.data.IngredientEntity
import com.postpole.spoonfile.data.RecipeEntity
import com.postpole.spoonfile.data.RecipeWithDetails
import com.postpole.spoonfile.data.StepEntity
import com.postpole.spoonfile.importer.paprika.PaprikaArchiveReader
import com.postpole.spoonfile.importer.paprika.PaprikaRecipe
import org.json.JSONObject
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
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
    private val photo = ByteArray(300) { (it * 7).toByte() }

    private fun recipe(
        id: Long,
        title: String,
        ingredients: List<String>,
        steps: List<String>,
        categories: List<String> = emptyList(),
        image: String? = null,
    ) = RecipeWithDetails(
        RecipeEntity(
            id = id, uid = "uid-$id", title = title, description = "Tasty", servingsText = "4 servings", servings = 4.0,
            prepMinutes = 10, cookMinutes = 90, totalMinutes = 100, notes = "Keep warm", nutritionText = "200 kcal",
            sourceName = "Gran", sourceUrl = "https://example.com/$id", imageFile = image, rating = 4,
            createdAt = 1_700_000_000_000, updatedAt = 1_700_000_000_000,
        ),
        ingredients.map { IngredientEntity(position = 0, text = it) },
        steps.map { StepEntity(position = 0, text = it) },
        categories.mapIndexed { i, n -> CategoryEntity(i + 1L, n) },
    )

    private fun write(recipes: List<RecipeWithDetails>): ByteArray {
        val out = ByteArrayOutputStream()
        PaprikaArchiveWriter().write(out, recipes) { photo }
        return out.toByteArray()
    }

    private fun read(bytes: ByteArray): List<PaprikaRecipe> {
        val list = mutableListOf<PaprikaRecipe>()
        PaprikaArchiveReader().read(ByteArrayInputStream(bytes)) { list += it; true }
        return list
    }

    @Test
    fun roundTrip_throughTheReader() {
        val input = listOf(
            recipe(1, "Tomato Soup", listOf("1 onion", "2 tomatoes"), listOf("Chop", "Simmer"), listOf("Dinner", "Soup"), image = "a.jpg"),
            recipe(2, "Pancakes ₹ 日本語", listOf("flour"), listOf("Mix well")),
        )
        val back = read(write(input))

        assertEquals(listOf("Tomato Soup", "Pancakes ₹ 日本語"), back.map { it.recipe.title })
        assertEquals(listOf("1 onion", "2 tomatoes"), back[0].recipe.ingredients.map { it.text })
        assertEquals(listOf("Chop", "Simmer"), back[0].recipe.steps.map { it.text })
        assertEquals(listOf("Dinner", "Soup"), back[0].categories)
        assertArrayEquals(photo, back[0].photo)
        assertEquals(false, back[1].hasPhoto)
        assertEquals("uid-1", back[0].uid)
        assertEquals(4, back[0].rating)
        assertEquals(10, back[0].recipe.prepMinutes)
        assertEquals(100, back[0].recipe.totalMinutes)
        assertEquals("https://example.com/1", back[0].recipe.sourceUrl)
    }

    @Test
    fun entries_areGzipJsonWithTheDocumentedFields() {
        val bytes = write(listOf(recipe(1, "Soup", listOf("a", "b"), listOf("one", "two"), listOf("Dinner"), image = "a.jpg")))
        val entries = ZipInputStream(ByteArrayInputStream(bytes)).use { z ->
            generateSequence { z.nextEntry }.map { it.name to GZIPInputStream(ByteArrayInputStream(z.readBytes())).readBytes() }.toList()
        }
        val (name, body) = entries.single()
        assertTrue(name.endsWith(".paprikarecipe"))
        val o = JSONObject(String(body, Charsets.UTF_8))
        assertEquals("uid-1", o.getString("uid"))
        assertEquals("a\nb", o.getString("ingredients"))
        assertEquals("one\n\ntwo", o.getString("directions"))
        assertEquals("90 min", o.getString("cook_time"))
        assertEquals("Gran", o.getString("source"))
        assertEquals(java.util.Base64.getEncoder().encodeToString(photo), o.getString("photo_data"))
        assertEquals(64, o.getString("hash").length)
        listOf("notes", "nutritional_info", "description", "servings", "prep_time", "total_time", "rating", "categories", "source_url", "created")
            .forEach { assertTrue(it, o.has(it)) }
    }

    @Test
    fun duplicateUids_getDistinctEntries() {
        val a = recipe(1, "A", listOf("x"), listOf("y"))
        val b = recipe(2, "B", listOf("x"), listOf("y")).let { it.copy(recipe = it.recipe.copy(uid = a.recipe.uid)) }
        assertEquals(listOf("A", "B"), read(write(listOf(a, b))).map { it.recipe.title })
    }
}
