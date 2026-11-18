package com.postpole.spoonfile.copy

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Copy rules for every `values*` strings file and `docs/play-listing.md`: no banned phrases, no hard-coded price,
 * and "Paprika" only where the listing's full description allows it. (The price check used to be `NoHardcodedPriceTest`.)
 */
class CopyRulesTest {
    // ---- Rules ------------------------------------------------------------------------------------------

    private val banned = listOf(
        "offline", "hors ligne", "sin conexión", "no internet needed", "works without internet", "no data collected",
    )
    private val priceLike = Regex("""\$\d|€\d|£\d|9\.99""")

    /** The exact trademark line, and its marked translations (DE / FR / ES drafts need native review). */
    private val trademarkLines = setOf(
        "Paprika is a trademark of its owner; this app is not affiliated with it.",
        "Paprika ist eine Marke ihres Inhabers; diese App steht in keiner Verbindung zu ihr.",
        "Paprika est une marque de son propriétaire ; cette appli n'est pas affiliée à celle-ci.",
        "Paprika es una marca de su propietario; esta app no está afiliada a ella.",
    )

    private fun bannedAndPriceViolations(name: String, text: String): List<String> {
        val out = mutableListOf<String>()
        text.lines().forEachIndexed { i, line ->
            val lower = line.lowercase()
            banned.filter { it in lower }.forEach { out += "$name:${i + 1} contains \"$it\"" }
            if (priceLike.containsMatchIn(line)) out += "$name:${i + 1} looks like a hard-coded price"
        }
        return out
    }

    /** `strings.xml` has no whitelist: "paprika" in any form fails. */
    internal fun stringsViolations(name: String, xml: String): List<String> =
        bannedAndPriceViolations(name, xml) +
            xml.lines().withIndex().filter { "paprika" in it.value.lowercase() }.map { "$name:${it.index + 1} mentions Paprika" }

    private class Section(val lang: String, val kind: String, val lines: List<String>)

    private fun sections(md: String): List<Section> {
        val out = mutableListOf<Section>()
        var lang = ""
        var kind = ""
        var buffer = mutableListOf<String>()
        fun flush() {
            out += Section(lang, kind, buffer)
            buffer = mutableListOf()
        }
        md.lines().forEach { line ->
            when {
                line.startsWith("### ") -> { flush(); kind = line.removePrefix("### ").trim() }
                line.startsWith("## ") -> { flush(); lang = line.removePrefix("## ").trim().substringBefore(' '); kind = "" }
                else -> buffer += line
            }
        }
        flush()
        return out
    }

    private fun Section.firstValue(): String = lines.firstOrNull { it.isNotBlank() }.orEmpty().trim().trim('`')

    private fun paprikaInFullDescription(lines: List<String>): List<String> = lines.mapNotNull { line ->
        val trimmed = line.trim()
        if (trimmed in trademarkLines) return@mapNotNull null
        val rest = trimmed.replace(".paprikarecipes", "", ignoreCase = true)
        if ("paprika" in rest.lowercase()) "\"$trimmed\" mentions Paprika outside .paprikarecipes / the trademark line" else null
    }

    internal fun listingViolations(md: String): List<String> {
        val out = bannedAndPriceViolations("play-listing.md", md).toMutableList()
        val all = sections(md)
        for (s in all) {
            val full = s.kind == "Full description"
            if (full) {
                out += paprikaInFullDescription(s.lines).map { "${s.lang} full description: $it" }
            } else {
                // Title, short description, keywords, data safety, notes: no Paprika at all.
                s.lines.filter { "paprika" in it.lowercase() }.forEach { out += "${s.lang} ${s.kind.ifEmpty { "intro" }}: \"${it.trim()}\" mentions Paprika" }
            }
            if (s.kind == "Title" && s.firstValue().length > 30) out += "${s.lang} title is over 30 characters"
            if (s.kind == "Short description" && s.firstValue().length > 80) out += "${s.lang} short description is over 80 characters"
        }
        return out
    }

    // ---- Files ------------------------------------------------------------------------------------------

    private fun resDir() = listOf(File("src/main/res"), File("app/src/main/res")).first { it.isDirectory }

    private fun stringsFiles(): List<File> =
        resDir().listFiles { f -> f.isDirectory && f.name.startsWith("values") }.orEmpty()
            .map { File(it, "strings.xml") }.filter { it.exists() }

    private fun listing(): String = listOf(File("../docs/play-listing.md"), File("docs/play-listing.md")).first { it.isFile }.readText()

    private fun keys(xml: String): Set<String> = Regex("""<(?:string|plurals)\s+name="([^"]+)"""").findAll(xml).map { it.groupValues[1] }.toSet()

    // ---- Tests on the real files ------------------------------------------------------------------------

    @Test
    fun scansTheDefaultAndTranslatedStrings() {
        val dirs = stringsFiles().map { it.parentFile.name }
        assertTrue(dirs.containsAll(listOf("values", "values-de", "values-fr", "values-es")))
    }

    @Test
    fun stringsFilesFollowTheCopyRules() {
        val violations = stringsFiles().flatMap { stringsViolations(it.path, it.readText()) }
        assertEquals(emptyList<String>(), violations)
    }

    @Test
    fun translationsCoverEveryKey() {
        val default = keys(File(resDir(), "values/strings.xml").readText())
        for (lang in listOf("de", "fr", "es")) {
            val translated = keys(File(resDir(), "values-$lang/strings.xml").readText())
            assertEquals("$lang is missing keys", emptySet<String>(), default - translated)
            assertEquals("$lang has extra keys", emptySet<String>(), translated - default)
        }
    }

    @Test
    fun translationsAreFlaggedForNativeReview() {
        for (lang in listOf("de", "fr", "es")) {
            assertTrue(File(resDir(), "values-$lang/strings.xml").readText().contains("needs native review"))
        }
    }

    @Test
    fun playListingFollowsTheCopyRules() {
        assertEquals(emptyList<String>(), listingViolations(listing()))
    }

    @Test
    fun playListingHasTitleShortDescriptionAndFullDescriptionForEveryLanguage() {
        val all = sections(listing())
        for (lang in listOf("EN", "DE", "FR", "ES")) {
            for (kind in listOf("Title", "Short description", "Full description")) {
                assertTrue("$lang is missing $kind", all.any { it.lang == lang && it.kind == kind && it.firstValue().isNotEmpty() })
            }
        }
        assertEquals("Spoonfile: Recipe Organizer", all.first { it.lang == "EN" && it.kind == "Title" }.firstValue())
        assertEquals(
            "Save recipes from any website or cookbook. Pay once. Recipes stay on your phone.",
            all.first { it.lang == "EN" && it.kind == "Short description" }.firstValue(),
        )
    }

    @Test
    fun playListingKeywordsAvoidMealPlannerAndPaprika() {
        val md = listing().lowercase()
        assertTrue("meal planner" !in md)
        assertTrue(listing().contains("Paprika is a trademark of its owner; this app is not affiliated with it."))
    }

    // ---- Rule behaviour ---------------------------------------------------------------------------------

    private fun listingWith(title: String = "Spoonfile: Recipe Organizer", short: String = "Short text.", full: String): String =
        "## EN listing\n\n### Title\n\n`$title`\n\n### Short description\n\n`$short`\n\n### Full description\n\n$full\n"

    @Test
    fun allowedTrademarkLineAndExtensionPass() {
        val md = listingWith(
            full = "• Import .paprikarecipes files.\n\nPaprika is a trademark of its owner; this app is not affiliated with it.",
        )
        assertEquals(emptyList<String>(), listingViolations(md))
    }

    @Test
    fun paprikaAlternativeInFullDescriptionFails() {
        assertTrue(listingViolations(listingWith(full = "A Paprika alternative.")).isNotEmpty())
    }

    @Test
    fun paprikaInShortDescriptionFails() {
        assertTrue(listingViolations(listingWith(short = "Paprika import.", full = "Fine text.")).isNotEmpty())
        assertTrue(listingViolations(listingWith(short = "Import .paprikarecipes files.", full = "Fine text.")).isNotEmpty())
    }

    @Test
    fun paprikaInTitleFails() {
        assertTrue(listingViolations(listingWith(title = "Paprika Organizer", full = "Fine text.")).isNotEmpty())
    }

    @Test
    fun anyPaprikaInStringsFails() {
        assertTrue(stringsViolations("x", "<string name=\"a\">Paprika file</string>").isNotEmpty())
        assertTrue(stringsViolations("x", "<string name=\"a\">Import .paprikarecipes</string>").isNotEmpty())
        assertEquals(emptyList<String>(), stringsViolations("x", "<string name=\"a\">Recipe file from another app</string>"))
    }

    @Test
    fun lengthLimitsAreEnforced() {
        assertTrue(listingViolations(listingWith(title = "x".repeat(31), full = "Fine.")).isNotEmpty())
        assertTrue(listingViolations(listingWith(title = "x".repeat(30), full = "Fine.")).isEmpty())
        assertTrue(listingViolations(listingWith(short = "x".repeat(81), full = "Fine.")).isNotEmpty())
        assertTrue(listingViolations(listingWith(short = "x".repeat(80), full = "Fine.")).isEmpty())
    }

    @Test
    fun bannedPhrasesAndPricesFail() {
        listOf(
            "Works OFFLINE", "Fonctionne hors ligne", "Funciona sin conexión", "No internet needed", "Works without internet",
            "No data collected", "Only \$4.99", "Only 4€5 or €5", "Just 9.99",
        ).forEach { text ->
            assertTrue("\"$text\" should fail", stringsViolations("x", "<string name=\"a\">$text</string>").isNotEmpty())
            assertTrue("\"$text\" should fail in the listing", listingViolations(listingWith(full = text)).isNotEmpty())
        }
    }
}
