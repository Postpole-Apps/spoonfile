package com.postpole.spoonfile.importer.web

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class JsonLdRecipeParserTest {
    private val page = "https://site.example.com/recipes/x"

    private fun parse(name: String, url: String = page): ImportedRecipe? {
        val html = javaClass.classLoader!!.getResourceAsStream("jsonld/$name.html")!!.bufferedReader().use { it.readText() }
        return JsonLdRecipeParser.parse(html, url)
    }

    private fun recipe(name: String): ImportedRecipe = parse(name) ?: error("fixture $name should parse")

    private fun List<ImportedLine>.texts() = map { it.text }

    @Test
    fun basicRecipe_readsAllCoreFields() {
        val r = recipe("basic")
        assertEquals("Plain Porridge", r.title)
        assertEquals("A warm bowl & more.", r.description)
        assertEquals(listOf("1 cup oats", "2 cups water", "pinch of salt"), r.ingredients.texts())
        assertEquals(listOf("Boil the water.", "Stir in the oats."), r.steps.texts())
        assertEquals(5, r.prepMinutes)
        assertEquals(10, r.cookMinutes)
        assertEquals(15, r.totalMinutes)
        assertEquals("2 servings", r.servingsText)
        assertEquals(2.0, r.servings!!, 0.0)
        assertEquals("https://img.example.com/porridge.jpg", r.imageUrl)
        assertEquals("Test Kitchen", r.sourceName)
        assertEquals(page, r.sourceUrl)
    }

    @Test
    fun graph_withTypeArray_findsRecipeAndWebSiteName() {
        val r = recipe("graph")
        assertEquals("Graph Toast", r.title)
        assertEquals("Graph Eats", r.sourceName)
    }

    @Test
    fun topLevelArray_matchesTypeCaseInsensitively_firstRecipeWins() {
        assertEquals("Lower Case Soup", recipe("array_top").title)
    }

    @Test
    fun mainEntity_isSearched() {
        val r = recipe("main_entity")
        assertEquals("Entity Eggs", r.title)
        assertEquals(listOf("Whisk.", "Cook."), r.steps.texts())
    }

    @Test
    fun invalidJsonBlock_isSkipped() {
        assertEquals("Survivor Salad", recipe("invalid_block").title)
    }

    @Test
    fun pageWithoutRecipe_returnsNull() {
        assertNull(parse("no_recipe"))
    }

    @Test
    fun recipeWithoutIngredientsOrSteps_returnsNull() {
        assertNull(parse("recipe_without_content"))
    }

    @Test
    fun howToSteps_useNameOnlyWhenNotPrefixDuplicate() {
        assertEquals(
            listOf("Mix the batter until smooth.", "Rest: Leave it for ten minutes.", "Bake it.", "Plate up", "Serve warm."),
            recipe("howto_steps").steps.texts(),
        )
    }

    @Test
    fun howToSections_becomeHeaders_listOrSingleDict() {
        val steps = recipe("howto_section").steps
        assertEquals(listOf("Dough", "Knead.", "Proof.", "Filling", "Chop the apples."), steps.texts())
        assertEquals(listOf(true, false, false, true, false), steps.map { it.isHeader })
    }

    @Test
    fun instructionString_splitsOnTagsAndNewlines_decodesEntities_dropsNumbers() {
        val r = recipe("instructions_string")
        assertEquals(listOf("Heat the pan & add oil.", "Add the batter.", "Flip once.", "Serve."), r.steps.texts())
        assertEquals(listOf("1 cup flour", "2 eggs", "1 tsp salt"), r.ingredients.texts())
    }

    @Test
    fun nestedInstructionArrays_areFlattened_andCapitalKeyAccepted() {
        assertEquals(listOf("First.", "Second.", "Third.", "Fourth."), recipe("nested_arrays").steps.texts())
    }

    @Test
    fun durations_isoAndQuantitativeValue() {
        val r = recipe("durations")
        assertEquals(20, r.prepMinutes)
        assertEquals(90, r.cookMinutes)
        assertEquals(120, r.totalMinutes)
    }

    @Test
    fun durations_zeroAndHugeIgnored_looseTextFallsBack() {
        val r = recipe("bad_durations")
        assertNull(r.prepMinutes)
        assertNull(r.cookMinutes)
        assertEquals(75, r.totalMinutes)
    }

    @Test
    fun yield_arrayUsesFirstNonEmpty() {
        val r = recipe("yield_variants")
        assertEquals("6 portions", r.servingsText)
        assertEquals(6.0, r.servings!!, 0.0)
    }

    @Test
    fun yield_numberIsAccepted() {
        val r = recipe("yield_number")
        assertEquals("4", r.servingsText)
        assertEquals(4.0, r.servings!!, 0.0)
    }

    @Test
    fun image_picksWidestKnown_resolvesRelative() {
        assertEquals("https://site.example.com/img/large.jpg", recipe("images").imageUrl)
    }

    @Test
    fun image_relativePathResolvedAgainstPage() {
        assertEquals("https://site.example.com/photos/dish.jpg", recipe("image_relative").imageUrl)
    }

    @Test
    fun categoriesAndCuisine_areSplitAndDeduplicated_nutritionFormatted() {
        val r = recipe("categories_nutrition")
        assertEquals(listOf("Dinner", "Soup", "Italian"), r.suggestedCategories)
        val nutrition = r.nutritionText!!
        assertTrue(nutrition, "calories: 320 kcal" in nutrition && "fat: 12 g" in nutrition)
    }

    @Test
    fun siteName_fallsBackToPublisher() {
        assertEquals("Pub House", recipe("site_publisher").sourceName)
    }

    @Test
    fun siteName_hostWithAuthorWhenNothingElse() {
        assertEquals("site.example.com (Ann, Bob)", recipe("site_author_host").sourceName)
    }

    @Test
    fun legacyIngredientsStringIsSplitOnNewlines() {
        assertEquals(listOf("2 apples", "1 pear"), recipe("legacy_ingredients").ingredients.texts())
    }

    @Test
    fun missingName_fallsBackToOgTitle() {
        assertEquals("Fallback & Title", recipe("title_fallback").title)
    }
}
