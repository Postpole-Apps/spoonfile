package com.postpole.spoonfile.copy

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Prices must only ever come from billing, never from string resources. */
class NoHardcodedPriceTest {
    private val forbidden = Regex("""\$\d|€\d|£\d|9\.99""")

    private fun resDir(): File =
        listOf("src/main/res", "app/src/main/res").map(::File).first { it.isDirectory }

    @Test
    fun stringsContainNoHardcodedPrice() {
        val files = resDir().listFiles { f -> f.isDirectory && f.name.startsWith("values") }.orEmpty()
            .map { File(it, "strings.xml") }
            .filter { it.isFile }
        assertTrue("expected at least values/strings.xml", files.isNotEmpty())
        val offenders = files.flatMap { file ->
            file.readLines().withIndex().filter { forbidden.containsMatchIn(it.value) }
                .map { "${file.path}:${it.index + 1}: ${it.value.trim()}" }
        }
        assertTrue("Hard-coded price in strings:\n" + offenders.joinToString("\n"), offenders.isEmpty())
    }
}
