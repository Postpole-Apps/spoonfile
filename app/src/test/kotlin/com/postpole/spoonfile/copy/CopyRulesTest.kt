package com.postpole.spoonfile.copy

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** One language block of `docs/play-listing.md`. */
internal data class ListingLocale(val name: String, val title: String, val shortDescription: String, val fullDescription: String)

/** The copy rules, as pure functions so the tests can also prove they fail on bad text. */
internal object CopyRules {
    const val TITLE_MAX = 30
    const val SHORT_MAX = 80

    val TRADEMARK_LINES = listOf(
        "Paprika is a trademark of its owner; this app is not affiliated with it.",
        "Paprika ist eine Marke ihres Inhabers; diese App steht in keiner Verbindung zu ihr.",
        "Paprika est une marque de son propriétaire ; cette application n'est pas affiliée à celle-ci.",
        "Paprika es una marca de su propietario; esta aplicación no está afiliada a ella.",
    )

    private val banned = listOf(
        "offline", "hors ligne", "sin conexión",
        "no internet needed", "works without internet", "no data collected",
    )

    /** Hard-coded prices: the price may only ever come from Play (`BillingRepository.price`). */
    private val priceLike = Regex("""\$\d|€\d|£\d|9\.99""")

    fun bannedPhrases(text: String): List<String> = banned.filter { text.contains(it, ignoreCase = true) }

    fun hasPrice(text: String): Boolean = priceLike.containsMatchIn(text)

    /** In `strings.xml` and in the title / short description / non-description text there is no whitelist. */
    fun containsPaprika(text: String): Boolean = text.contains("paprika", ignoreCase = true)

    /** Every "Paprika" left once the allowed `.paprikarecipes` token and the trademark line are removed. */
    fun paprikaViolationsInFullDescription(full: String): List<String> {
        var rest = full
        TRADEMARK_LINES.forEach { rest = rest.replace(it, "") }
        rest = rest.replace(Regex("\\.paprikarecipes", RegexOption.IGNORE_CASE), "")
        return rest.lines().filter { containsPaprika(it) }
    }

    /** Parses language sections; also returns all listing text outside the full descriptions. */
    fun parseListing(markdown: String): Pair<List<ListingLocale>, String> {
        val locales = mutableListOf<ListingLocale>()
        val outside = StringBuilder()
        var locale: String? = null
        var field: String? = null
        val fields = mutableMapOf<String, StringBuilder>()

        fun flush() {
            val name = locale ?: return
            if (fields.containsKey("Title")) {
                fun f(k: String) = fields[k]?.toString()?.trim().orEmpty()
                locales += ListingLocale(name, f("Title"), f("Short description"), f("Full description"))
            }
            fields.clear()
        }

        for (line in markdown.lines()) {
            when {
                line.startsWith("## ") -> {
                    flush()
                    locale = line.removePrefix("## ").trim()
                    field = null
                    outside.appendLine(line)
                }
                line.startsWith("### ") -> {
                    field = line.removePrefix("### ").trim()
                    fields[field] = StringBuilder()
                    outside.appendLine(line)
                }
                field == "Full description" -> fields.getValue(field).appendLine(line)
                field != null -> {
                    fields.getValue(field).appendLine(line)
                    outside.appendLine(line)
                }
                else -> outside.appendLine(line)
            }
        }
        flush()
        return locales to outside.toString()
    }
}

class CopyRulesTest {
    private val resDir = listOf(File("src/main/res"), File("app/src/main/res")).first { it.isDirectory }
    private val listingFile = listOf(File("../docs/play-listing.md"), File("docs/play-listing.md")).first { it.isFile }

    private fun stringsFiles(): List<File> =
        resDir.listFiles { f -> f.isDirectory && f.name.startsWith("values") }.orEmpty()
            .map { File(it, "strings.xml") }
            .filter { it.exists() }
            .sortedBy { it.parentFile.name }

    private val listing get() = listingFile.readText()

    private fun keys(file: File): Set<String> =
        Regex("""<(?:string|plurals)\s+name="([^"]+)"""").findAll(file.readText()).map { it.groupValues[1] }.toSet()

    // --- strings.xml ---

    @Test
    fun scansTheDefaultAndAllThreeTranslations() {
        assertEquals(listOf("values", "values-de", "values-es", "values-fr"), stringsFiles().map { it.parentFile.name })
    }

    @Test
    fun noStringsFileHasABannedPhraseOrAPrice() {
        for (file in stringsFiles()) {
            file.readLines().forEachIndexed { index, line ->
                assertEquals("${file.path}:${index + 1} banned phrase: $line", emptyList<String>(), CopyRules.bannedPhrases(line))
                assertFalse("${file.path}:${index + 1} looks like a hard-coded price: $line", CopyRules.hasPrice(line))
            }
        }
    }

    @Test
    fun noPaprikaInAnyStringsFile() {
        for (file in stringsFiles()) {
            assertFalse("${file.path} mentions Paprika", CopyRules.containsPaprika(file.readText()))
        }
    }

    @Test
    fun translationsCoverEveryKeyAndAreFlaggedForReview() {
        val base = keys(File(resDir, "values/strings.xml"))
        for (lang in listOf("de", "fr", "es")) {
            val file = File(resDir, "values-$lang/strings.xml")
            assertEquals("values-$lang is missing keys", emptySet<String>(), base - keys(file))
            assertEquals("values-$lang has extra keys", emptySet<String>(), keys(file) - base)
            assertTrue("values-$lang must be flagged", file.readText().contains("NEEDS NATIVE REVIEW"))
        }
    }

    @Test
    fun phoneTransferNoteIsExactAndNeverMentionsCloud() {
        // strings.xml escapes the apostrophe as \'
        val expected = "Moving to a new phone? Android\\'s phone-to-phone transfer brings your recipes and photos along. " +
            "Keep a backup file too, just in case."
        val pattern = Regex("""<string name="settings_phone_transfer">(.*?)</string>""")
        val en = pattern.find(File(resDir, "values/strings.xml").readText())?.groupValues?.get(1)
        assertEquals(expected, en)
        for (file in stringsFiles()) {
            val value = pattern.find(file.readText())?.groupValues?.get(1)
            assertTrue("${file.path}: settings_phone_transfer missing", value != null)
            assertFalse("${file.path}: settings_phone_transfer mentions cloud", value!!.contains("cloud", ignoreCase = true))
        }
    }

    // --- docs/play-listing.md ---

    @Test
    fun listingHasNoBannedPhraseOrPrice() {
        assertEquals(emptyList<String>(), CopyRules.bannedPhrases(listing))
        assertFalse(CopyRules.hasPrice(listing))
    }

    @Test
    fun listingKeywordsAvoidMealPlannerAndHasTheRequiredOnes() {
        val text = listing.lowercase()
        assertFalse(text.contains("meal planner"))
        listOf("recipe organizer", "recipe keeper", "recipe box", "pay once", "no subscription", "no ads", "shopping list")
            .forEach { assertTrue("missing keyword $it", text.contains(it)) }
    }

    @Test
    fun listingHasAllFourLanguagesWithinLimits() {
        val (locales, _) = CopyRules.parseListing(listing)
        assertEquals(4, locales.size)
        for (l in locales) {
            assertTrue("${l.name}: title is empty", l.title.isNotEmpty())
            assertTrue("${l.name}: title ${l.title.length} > ${CopyRules.TITLE_MAX}", l.title.length <= CopyRules.TITLE_MAX)
            assertTrue("${l.name}: short ${l.shortDescription.length} > ${CopyRules.SHORT_MAX}", l.shortDescription.length <= CopyRules.SHORT_MAX)
            assertTrue("${l.name}: full description is empty", l.fullDescription.isNotEmpty())
        }
        val en = locales.first()
        assertEquals("Spoonfile: Recipe Organizer", en.title)
        assertEquals("Save recipes from any website or cookbook. Pay once. Recipes stay on your phone.", en.shortDescription)
        assertEquals(80, en.shortDescription.length)
    }

    @Test
    fun everyFullDescriptionCarriesItsTrademarkLineAndNoOtherPaprika() {
        val (locales, _) = CopyRules.parseListing(listing)
        locales.forEachIndexed { i, l ->
            assertTrue("${l.name}: trademark line missing", l.fullDescription.contains(CopyRules.TRADEMARK_LINES[i]))
            assertTrue("${l.name}: mentions the file format", l.fullDescription.contains(".paprikarecipes"))
            assertEquals(l.name, emptyList<String>(), CopyRules.paprikaViolationsInFullDescription(l.fullDescription))
        }
    }

    @Test
    fun listingMentionsPaprikaOnlyInFullDescriptions() {
        val (_, outside) = CopyRules.parseListing(listing)
        assertFalse("Paprika outside a full description:\n$outside", CopyRules.containsPaprika(outside))
    }

    // --- the checks themselves ---

    @Test
    fun allowedFullDescriptionPasses() {
        val full = "Import .paprikarecipes files.\n\n" + CopyRules.TRADEMARK_LINES[0]
        assertEquals(emptyList<String>(), CopyRules.paprikaViolationsInFullDescription(full))
    }

    @Test
    fun paprikaAlternativeInFullDescriptionFails() {
        assertEquals(1, CopyRules.paprikaViolationsInFullDescription("The best Paprika alternative.").size)
        assertEquals(1, CopyRules.paprikaViolationsInFullDescription("Paprika is a trademark of its owner.").size)
    }

    @Test
    fun paprikaInTitleShortDescriptionOrStringsFails() {
        val md = """
            ## English (EN)
            ### Title
            Spoonfile
            ### Short description
            Paprika
            ### Full description
            Fine.
        """.trimIndent()
        val (locales, outside) = CopyRules.parseListing(md)
        assertEquals("Paprika", locales.single().shortDescription)
        assertTrue(CopyRules.containsPaprika(outside))
        assertTrue(CopyRules.containsPaprika("Import .paprikarecipes"))
    }

    @Test
    fun bannedPhrasesAreCaughtInAnyCaseAndLanguage() {
        listOf("Works OFFLINE", "Fonctionne hors ligne", "Funciona sin conexión", "No internet needed", "Works without internet", "No data collected")
            .forEach { assertTrue(it, CopyRules.bannedPhrases(it).isNotEmpty()) }
        assertTrue(CopyRules.bannedPhrases("Your recipes stay on your device").isEmpty())
    }

    @Test
    fun hardCodedPricesAreCaught() {
        listOf("Only $4.99", "nur 4€5", "£3", "for 9.99").forEach { assertTrue(it, CopyRules.hasPrice(it)) }
        assertFalse(CopyRules.hasPrice("Unlock Pro — %1\$s"))
    }
}
