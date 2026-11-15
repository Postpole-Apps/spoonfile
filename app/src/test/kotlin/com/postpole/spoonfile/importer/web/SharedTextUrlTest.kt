package com.postpole.spoonfile.importer.web

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SharedTextUrlTest {
    @Test
    fun plainUrl() {
        assertEquals("https://example.com/r/1", SharedTextUrl.extract("https://example.com/r/1"))
    }

    @Test
    fun titleThenUrl_takesTheUrl() {
        assertEquals("https://example.com/soup?id=3", SharedTextUrl.extract("Great soup https://example.com/soup?id=3"))
    }

    @Test
    fun firstUrlWins_trailingPunctuationTrimmed() {
        assertEquals("http://a.example.com/x", SharedTextUrl.extract("see (http://a.example.com/x), or https://b.example.com"))
    }

    @Test
    fun nonWebSchemesAndEmptyInputAreIgnored() {
        assertNull(SharedTextUrl.extract("ftp://example.com/file"))
        assertNull(SharedTextUrl.extract("javascript:alert(1)"))
        assertNull(SharedTextUrl.extract("intent://scan/#Intent;end"))
        assertNull(SharedTextUrl.extract("no link here"))
        assertNull(SharedTextUrl.extract(""))
        assertNull(SharedTextUrl.extract(null))
    }

    @Test
    fun urlWithoutHostIsRejected() {
        assertNull(SharedTextUrl.extract("https://"))
    }

    @Test
    fun inputBeyondFourKbIsIgnored() {
        val padding = "x".repeat(SharedTextUrl.MAX_INPUT_CHARS)
        assertNull(SharedTextUrl.extract("$padding https://example.com"))
    }

    @Test
    fun httpIsUpgradedToHttps_hostStripsWww() {
        assertEquals("https://example.com/a", SharedTextUrl.toHttps("http://example.com/a"))
        assertEquals("https://example.com/a", SharedTextUrl.toHttps("https://example.com/a"))
        assertEquals("example.com", SharedTextUrl.host("https://www.example.com/a"))
    }
}
