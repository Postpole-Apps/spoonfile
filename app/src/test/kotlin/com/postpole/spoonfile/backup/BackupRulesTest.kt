package com.postpole.spoonfile.backup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/** The phone-to-phone transfer rules: cloud backup gets nothing, device transfer gets only the database and photos. */
class BackupRulesTest {

    private fun root(path: String): Element {
        val file = File("src/main/res/$path")
        assertTrue("$path should exist", file.isFile)
        return DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file).documentElement
    }

    private fun Element.children(tag: String): List<Element> =
        (0 until childNodes.length).map { childNodes.item(it) }.filterIsInstance<Element>().filter { it.tagName == tag }

    private fun Element.single(tag: String): Element = children(tag).also { assertEquals(tag, 1, it.size) }.single()

    private fun Element.includes(): List<Element> = children("include")

    private fun Element.pairs(): List<Pair<String, String>> = includes().map { it.getAttribute("domain") to it.getAttribute("path") }

    @Test
    fun cloudBackup_excludesEveryDomain_andIncludesNothing() {
        val cloud = root("xml/data_extraction_rules.xml").single("cloud-backup")
        assertTrue(cloud.includes().isEmpty())
        assertEquals(
            setOf("root", "file", "database", "sharedpref", "external"),
            cloud.children("exclude").map { it.getAttribute("domain") }.toSet(),
        )
    }

    @Test
    fun deviceTransfer_includesOnlyDatabaseAndImages() {
        val transfer = root("xml/data_extraction_rules.xml").single("device-transfer")
        assertEquals(listOf("database" to ".", "file" to "images/"), transfer.pairs())
    }

    @Test
    fun nothingUnderDatastoreIsIncluded() {
        val files = listOf("xml/data_extraction_rules.xml", "xml/backup_rules.xml", "xml-v28/backup_rules.xml")
        files.forEach { path ->
            assertFalse(path, File("src/main/res/$path").readText().replace(Regex("<!--.*?-->", RegexOption.DOT_MATCHES_ALL), "")
                .contains("datastore"))
        }
    }

    @Test
    fun crossPlatformTransfer_ifPresent_excludesEverything() {
        val ios = root("xml/data_extraction_rules.xml").children("cross-platform-transfer")
        ios.forEach { assertTrue(it.includes().isEmpty()) }
    }

    @Test
    fun v28Rules_onlyDeviceToDeviceIncludes() {
        val rules = root("xml-v28/backup_rules.xml")
        assertEquals(listOf("database" to ".", "file" to "images/"), rules.pairs())
        rules.includes().forEach { assertEquals("deviceToDeviceTransfer", it.getAttribute("requireFlags")) }
        assertTrue(rules.children("exclude").isEmpty())
    }

    @Test
    fun baseBackupRules_haveNoIncludes() {
        assertTrue(root("xml/backup_rules.xml").includes().isEmpty())
    }

    @Test
    fun manifest_pointsAtTheRules_withoutABackupAgent() {
        val manifest = File("src/main/AndroidManifest.xml").readText()
        assertTrue(manifest.contains("""android:allowBackup="true""""))
        assertTrue(manifest.contains("""android:dataExtractionRules="@xml/data_extraction_rules""""))
        assertTrue(manifest.contains("""android:fullBackupContent="@xml/backup_rules""""))
        assertFalse(manifest.contains("backupAgent"))
    }
}
