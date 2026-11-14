package com.postpole.spoonfile.copy

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** The price may only ever come from Play (`BillingRepository.price`), never from copy. */
class NoHardcodedPriceTest {
    private val priceLike = Regex("""\$\d|€\d|£\d|9\.99""")

    private fun stringsFiles(): List<File> {
        val res = listOf(File("src/main/res"), File("app/src/main/res")).first { it.isDirectory }
        return res.listFiles { f -> f.isDirectory && f.name.startsWith("values") }.orEmpty()
            .map { File(it, "strings.xml") }
            .filter { it.exists() }
    }

    @Test
    fun scansAtLeastTheDefaultStrings() {
        assertTrue(stringsFiles().any { it.parentFile.name == "values" })
    }

    @Test
    fun noStringsFileContainsAPrice() {
        for (file in stringsFiles()) {
            file.readLines().forEachIndexed { index, line ->
                assertFalse("${file.path}:${index + 1} looks like a hard-coded price: $line", priceLike.containsMatchIn(line))
            }
        }
    }
}
