package com.postpole.spoonfile.importer.web

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Each test parses a small hand-written page from `src/test/resources/jsonld`. */
@RunWith(RobolectricTestRunner::class)
class JsonLdRecipeParserTest {
    private val pageUrl = "https://example.com/r/1"

    private fun parse(name: String, url: String = pageUrl): ImportedRecipe? {
        val html = checkNotNull(javaClass.classLoader!!.getResourceAsStream("jsonld/$name.html")) { "missing fixture $name" }
            .use { it.readBytes().toString(Charsets.UTF_8) }
        return JsonLdRecipeParser.parse(html, url)
    }

    private fun recipe(name: String, url: String = pageUrl) = checkNotNull(parse(name, url)) { "no recipe in $name" }

    private fun ImportedRecipe.stepTexts() = steps.map { it.text }

    @Test
    fun basicRecipeFields() {
        val r = recipe("basic")
        assertEquals("Plain Pancakes", r.title)
        assertEquals("Fluffy & quick.", r.description)
        assertEquals(listOf("1 cup flour", "1 egg", "1 cup milk"), r.ingredients.map { it.text })
        assertEquals(listOf("Mix everything.", "Fry in a pan."), r.stepTexts())
        assertEquals(10, r.prepMinutes)
        assertEquals(15, r.cookMinutes)
        assertEquals(25, r.totalMinutes)
        assertEquals("4 servings", r.servingsText)
        assertEquals(4.0, r.servings!!, 0.0)
        assertEquals("https://img.example.com/pancakes.jpg", r.imageUrl)
        assertEquals(pageUrl, r.sourceUrl)
    }

    @Test
    fun recipeInsideGraph() {
        val r = recipe("graph")
        assertEquals("Graph Soup", r.title)
        assertEquals(listOf("Boil the water. Add salt."), r.stepTexts())
    }

    @Test
    fun recipeInsideNestedGraphArrays() {
        assertEquals("Deep Stew", recipe("nested_graph").title)
    }

    @Test
    fun recipeInsideMainEntity() {
        assertEquals("Entity Toast", recipe("main_entity").title)
    }

    @Test
    fun typeArrayMatchesCaseInsensitively() {
        assertEquals("Typed Rice", recipe("type_array").title)
    }

    @Test
    fun topLevelArray() {
        assertEquals("Array Salad", recipe("top_level_array").title)
    }

    @Test
    fun invalidJsonBlockIsSkipped() {
        assertEquals("Survivor Tea", recipe("invalid_json_skipped").title)
    }

    @Test
    fun pageWithoutRecipeGivesNull() {
        assertNull(parse("no_recipe"))
    }

    @Test
    fun recipeWithNoIngredientsOrStepsIsNotARecipe() {
        assertNull(parse("recipe_without_content"))
    }

    @Test
    fun firstRecipeWins() {
        assertEquals("First", recipe("first_recipe_wins").title)
    }

    @Test
    fun instructionStringSplitsOnNewlinesAndHtmlAndDropsNumbers() {
        assertEquals(
            listOf("Chop the onion.", "Heat the oil.", "Add the onion.", "Stir well.", "Serve warm."),
            recipe("instructions_string").stepTexts(),
        )
    }

    @Test
    fun howToStepNameIsUsedOnlyWhenNotAPrefixOfTheText() {
        assertEquals(
            listOf("Preheat the oven to 200C.", "Season: Salt the fish well.", "Bake for 20 minutes.", "Rest"),
            recipe("howto_steps").stepTexts(),
        )
    }

    @Test
    fun howToSectionsBecomeHeadersFollowedByTheirSteps() {
        val steps = recipe("howto_sections").steps
        assertEquals(
            listOf("Dough", "Mix flour and water.", "Knead.", "Topping", "Spread the sauce."),
            steps.map { it.text },
        )
        assertEquals(listOf(true, false, false, true, false), steps.map { it.isHeader })
    }

    @Test
    fun nestedInstructionArraysAndCapitalisedKey() {
        assertEquals(
            listOf("First thing.", "Second thing.", "Third thing.", "Fourth thing."),
            recipe("instructions_nested").stepTexts(),
        )
    }

    @Test
    fun isoDurationVariants() {
        val r = recipe("durations")
        assertEquals(20, r.prepMinutes)
        assertEquals(90, r.cookMinutes)
        assertEquals(90, r.totalMinutes)
    }

    @Test
    fun zeroAndHugeTimesAreIgnoredAndLooseTextFallsBackToRegex() {
        val r = recipe("durations_bad")
        assertNull(r.prepMinutes)
        assertNull(r.cookMinutes)
        assertEquals(75, r.totalMinutes)
    }

    @Test
    fun quantitativeValueTimes() {
        val r = recipe("quantitative_time")
        assertEquals(15, r.prepMinutes)
        assertEquals(40, r.cookMinutes)
    }

    @Test
    fun yieldArrayUsesFirstNonEmpty() {
        val r = recipe("yield_array")
        assertEquals("6", r.servingsText)
        assertEquals(6.0, r.servings!!, 0.0)
    }

    @Test
    fun yieldNumber() {
        val r = recipe("yield_number")
        assertEquals("8", r.servingsText)
        assertEquals(8.0, r.servings!!, 0.0)
    }

    @Test
    fun widestImageIsChosenAndResolvedAgainstThePage() {
        assertEquals("https://example.com/img/large.jpg", recipe("image_objects").imageUrl)
    }

    @Test
    fun categoriesAndCuisinesAreSplitAndDeduplicated() {
        assertEquals(listOf("Dinner", "Main course", "Italian"), recipe("categories").suggestedCategories)
    }

    @Test
    fun nutritionText() {
        val text = recipe("nutrition").nutritionText!!
        assertTrue(text, "calories: 320 kcal" in text)
        assertTrue(text, "saturated fat: 2 g" in text)
        assertTrue(text, "serving size: 1 bowl" in text)
    }

    @Test
    fun legacyIngredientsStringIsSplitOnNewlines() {
        assertEquals(listOf("2 apples", "1 pear", "3 plums"), recipe("legacy_ingredients").ingredients.map { it.text })
    }

    @Test
    fun entitiesTagsAndWhitespaceAreCleaned() {
        val r = recipe("entities")
        assertEquals("Mac & Cheese – Easy", r.title)
        assertEquals("Creamy and rich", r.description)
        assertEquals(listOf("1½ cups macaroni", "2 tbsp butter"), r.ingredients.map { it.text })
        assertEquals(listOf("Boil the pasta.", "Drain & stir."), r.stepTexts())
    }

    @Test
    fun siteNameFromOpenGraph() {
        assertEquals("Example Kitchen", recipe("basic").sourceName)
    }

    @Test
    fun siteNameFromJsonLdWebSite() {
        assertEquals("Json Site", recipe("site_jsonld").sourceName)
    }

    @Test
    fun siteNameFromPublisher() {
        assertEquals("The Publisher", recipe("site_publisher").sourceName)
    }

    @Test
    fun authorIsUsedOnlyWhenThereIsNoSiteName() {
        assertEquals("Ann, Bob", recipe("site_author_only").sourceName)
    }

    @Test
    fun hostIsTheLastResortForSourceName() {
        assertEquals("example.com", recipe("site_host_fallback", "https://www.example.com/x").sourceName)
    }
}
