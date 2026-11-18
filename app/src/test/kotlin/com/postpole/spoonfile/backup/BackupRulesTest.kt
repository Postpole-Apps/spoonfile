package com.postpole.spoonfile.backup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/** Phone-to-phone transfer carries the recipe database and photos only; cloud backup gets nothing. */
class BackupRulesTest {
    private fun resFile(path: String): File {
        // Gradle runs unit tests with the module directory as the working directory.
        val candidates = listOf(File("src/main/res/$path"), File("app/src/main/res/$path"))
        return checkNotNull(candidates.firstOrNull { it.isFile }) { "Missing $path" }
    }

    private fun root(path: String): Element =
        DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(resFile(path)).documentElement

    private fun Element.children(tag: String): List<Element> =
        (0 until childNodes.length).map { childNodes.item(it) }.filterIsInstance<Element>().filter { it.tagName == tag }

    private fun Element.rules(): List<Triple<String, String, String>> =
        children("include").map { Triple(it.getAttribute("domain"), it.getAttribute("path"), it.getAttribute("requireFlags")) }

    @Test
    fun cloudBackup_hasNoInclude_andExcludesEveryDomain() {
        val cloud = root("xml/data_extraction_rules.xml").children("cloud-backup").single()
        assertTrue(cloud.children("include").isEmpty())
        assertEquals(
            setOf("root", "file", "database", "sharedpref", "external"),
            cloud.children("exclude").map { it.getAttribute("domain") }.toSet(),
        )
    }

    @Test
    fun deviceTransfer_includesOnlyDatabaseAndImages() {
        val transfer = root("xml/data_extraction_rules.xml").children("device-transfer").single()
        assertEquals(setOf("database" to ".", "file" to "images/"), transfer.rules().map { it.first to it.second }.toSet())
        assertEquals(2, transfer.rules().size)
    }

    @Test
    fun nothingUnderDatastoreIsIncluded() {
        val rules = root("xml/data_extraction_rules.xml").children("device-transfer").single().rules() +
            root("xml-v28/backup_rules.xml").rules() + root("xml/backup_rules.xml").rules()
        assertFalse(rules.any { it.second.contains("datastore") })
        assertFalse(rules.any { it.first == "sharedpref" || it.first == "root" })
    }

    @Test
    fun crossPlatformTransfer_excludesEverything() {
        val ios = root("xml/data_extraction_rules.xml").children("cross-platform-transfer").single()
        assertEquals("ios", ios.getAttribute("platform"))
        assertTrue(ios.children("include").isEmpty())
        assertEquals(5, ios.children("exclude").size)
    }

    @Test
    fun v28Rules_useOnlyDeviceToDeviceIncludes() {
        val rules = root("xml-v28/backup_rules.xml").rules()
        assertEquals(setOf(Triple("database", ".", "deviceToDeviceTransfer"), Triple("file", "images/", "deviceToDeviceTransfer")), rules.toSet())
        assertEquals(2, rules.size)
        assertTrue(root("xml-v28/backup_rules.xml").children("exclude").isEmpty())
    }

    @Test
    fun baseBackupRules_haveNoIncludes() {
        val base = root("xml/backup_rules.xml")
        assertTrue(base.rules().isEmpty())
        assertEquals(5, base.children("exclude").size)
    }

    @Test
    fun manifest_pointsAtTheRulesWithoutABackupAgent() {
        val manifest = File("src/main/AndroidManifest.xml").takeIf { it.isFile } ?: File("app/src/main/AndroidManifest.xml")
        val text = manifest.readText()
        assertNotNull(text)
        assertTrue(text.contains("android:allowBackup=\"true\""))
        assertTrue(text.contains("android:dataExtractionRules=\"@xml/data_extraction_rules\""))
        assertTrue(text.contains("android:fullBackupContent=\"@xml/backup_rules\""))
        assertFalse(text.contains("backupAgent"))
    }
}
